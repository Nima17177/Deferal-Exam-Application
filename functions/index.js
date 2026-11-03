const functions = require("firebase-functions");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

const smtpTransport = nodemailer.createTransport({
    host: process.env.SMTP_HOST || "smtp.gmail.com",
    port: parseInt(process.env.SMTP_PORT || "587", 10),
    secure: false,
    auth: {
        user: process.env.SMTP_USER,
        pass: process.env.SMTP_PASS,
    },
});

const SMTP_FROM = process.env.SMTP_FROM || process.env.SMTP_USER;

/**
 * Notification type to preference field mapping
 */
const NOTIFICATION_PREF_MAP = {
    "chat": "notifyMessages",
    "booking_created": "notifyBookings",
    "status_update": "notifyExamStatus",
    "proctor_assigned": "notifySessionUpdates",
    "proctor_needed": "notifySessionUpdates",
    "incident": "notifyIncidents",
    "payment": "notifyPayments",
    "daily_reminder": "notifyDailyReminders",
    "manage_users": "notifyAccountAlerts"
};

/**
 * Send an email to a user if email notifications are enabled and SMTP is configured.
 */
async function sendEmailToUser(userId, userData, title, body) {
    try {
        if (!process.env.SMTP_USER || !process.env.SMTP_PASS) return null;
        if (userData.emailNotificationsEnabled === false) return null;

        const email = userData.thisEmail;
        if (!email) return null;

        await smtpTransport.sendMail({
            from: SMTP_FROM,
            to: email,
            subject: title,
            text: body,
            html: `
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background-color: #FFA726; padding: 16px 24px; border-radius: 8px 8px 0 0;">
                        <h2 style="color: #fff; margin: 0;">DuckDuckDefer</h2>
                    </div>
                    <div style="background-color: #ffffff; padding: 24px; border: 1px solid #e0e0e0; border-top: none; border-radius: 0 0 8px 8px;">
                        <h3 style="margin-top: 0;">${title}</h3>
                        <p style="color: #333; line-height: 1.6;">${body}</p>
                        <hr style="border: none; border-top: 1px solid #eee; margin: 24px 0;" />
                        <p style="color: #999; font-size: 12px;">
                            You received this email because notifications are enabled on your DuckDuckDefer account.
                            You can disable email notifications in Settings &gt; Notification Preferences.
                        </p>
                    </div>
                </div>
            `,
        });
        console.log(`Email sent to ${email} for user ${userId}`);
        return true;
    } catch (error) {
        console.error(`Error sending email to user ${userId}:`, error);
        return null;
    }
}

/**
 * Helper to send a notification to a specific user UID
 * Checks user's notification preferences before sending.
 * Also sends an email copy if email notifications are enabled.
 */
async function sendNotificationToUser(userId, title, body, data = {}, channelId = "system_channel") {
    try {
        const userDoc = await db.collection("UserAccounts").doc(userId).get();
        if (!userDoc.exists) return null;

        const userData = userDoc.data();

        const notificationType = data.type || "system";
        const prefField = NOTIFICATION_PREF_MAP[notificationType];

        if (prefField) {
            const prefEnabled = userData[prefField] !== false;
            if (!prefEnabled) {
                console.log(`Notification skipped for ${userId}: ${notificationType} notifications disabled`);
                return null;
            }
        }

        const results = {};

        // Push notification
        const fcmToken = userData.fcmToken;
        const pushEnabled = userData.pushNotificationsEnabled !== false;
        if (fcmToken && pushEnabled) {
            const message = {
                token: fcmToken,
                notification: { title, body },
                data: {
                    ...data,
                    title,
                    body,
                    android_channel_id: channelId
                },
                android: {
                    priority: "high",
                    notification: {
                        channelId: channelId,
                        clickAction: "TOP_LEVEL_ACTIVITY"
                    }
                },
            };
            results.push = await messaging.send(message);
        }

        // Email notification
        results.email = await sendEmailToUser(userId, userData, title, body);

        return results;
    } catch (error) {
        console.error(`Error sending to user ${userId}:`, error);
        return null;
    }
}

function requireString(data, field) {
    const value = (data[field] || "").toString().trim();
    if (!value) {
        throw new functions.https.HttpsError("invalid-argument", `Missing required field: ${field}`);
    }
    return value;
}

function parseTimeToMinutes(value) {
    const match = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(value);
    if (!match) return null;
    return Number(match[1]) * 60 + Number(match[2]);
}

function rangesOverlap(startA, endA, startB, endB) {
    return startA < endB && endA > startB;
}

async function getApprovedAuthedUser(context, allowedRoles) {
    if (!context.auth || !context.auth.uid) {
        throw new functions.https.HttpsError("unauthenticated", "You must be signed in to run this action.");
    }

    const uid = context.auth.uid;
    const userDoc = await db.collection("UserAccounts").doc(uid).get();
    if (!userDoc.exists) {
        throw new functions.https.HttpsError("permission-denied", "User account not found.");
    }

    const userData = userDoc.data() || {};
    if (userData.thisApprovalStatus !== 1) {
        throw new functions.https.HttpsError("permission-denied", "Your account is not approved for this action.");
    }
    if (!allowedRoles.includes(userData.thisRole)) {
        throw new functions.https.HttpsError("permission-denied", "You do not have permission to run this action.");
    }

    return { uid, ...userData };
}

async function getApprovedAuthedUserFromRequest(req, allowedRoles) {
    const authHeader = req.headers.authorization || req.headers.Authorization || "";
    const match = /^Bearer\s+(.+)$/.exec(authHeader);
    if (!match) {
        throw new functions.https.HttpsError("unauthenticated", "Missing Authorization Bearer token.");
    }

    let decoded;
    try {
        decoded = await admin.auth().verifyIdToken(match[1]);
    } catch (_e) {
        throw new functions.https.HttpsError("unauthenticated", "Invalid auth token.");
    }

    const uid = decoded.uid;
    const userDoc = await db.collection("UserAccounts").doc(uid).get();
    if (!userDoc.exists) {
        throw new functions.https.HttpsError("permission-denied", "User account not found.");
    }

    const userData = userDoc.data() || {};
    if (userData.thisApprovalStatus !== 1) {
        throw new functions.https.HttpsError("permission-denied", "Your account is not approved for this action.");
    }
    if (!allowedRoles.includes(userData.thisRole)) {
        throw new functions.https.HttpsError("permission-denied", "You do not have permission to run this action.");
    }

    return { uid, ...userData };
}

async function findApprovedUserByEmail(email, expectedRole) {
    const trimmed = email.trim();
    const variants = [trimmed];
    if (trimmed.toLowerCase() !== trimmed) variants.push(trimmed.toLowerCase());

    for (const e of variants) {
        const snap = await db.collection("UserAccounts")
            .where("thisEmail", "==", e)
            .where("thisRole", "==", expectedRole)
            .limit(1)
            .get();

        if (!snap.empty) {
            const doc = snap.docs[0];
            const data = doc.data() || {};
            if (data.thisApprovalStatus === 1) {
                return { uid: doc.id, ...data };
            }
        }
    }

    return null;
}

exports.chatbotListSessions = functions.https.onCall(async (data, context) => {
    console.log("[chatbotListSessions] invoked", {
        hasAuth: !!(context.auth && context.auth.uid),
        uid: context.auth?.uid || null,
        dateID: data?.dateID || null,
    });
    const caller = await getApprovedAuthedUser(context, [0, 1]);
    const dateID = requireString(data, "dateID");

    if (!/^\d{4}-\d{2}-\d{2}$/.test(dateID)) {
        throw new functions.https.HttpsError("invalid-argument", "dateID must be in YYYY-MM-DD format.");
    }

    const sessionDoc = await db.collection("SessionsCollection").doc(dateID).get();
    if (!sessionDoc.exists) {
        return { message: `No sessions found on ${dateID}.` };
    }

    const rooms = sessionDoc.data().rooms || {};
    const roomNames = Object.keys(rooms);
    if (roomNames.length === 0) {
        return { message: `No rooms are configured for ${dateID}.` };
    }

    const lines = roomNames.map((roomName) => {
        const room = rooms[roomName] || {};
        const seats = Array.isArray(room.availableSeats) ? room.availableSeats : [];
        const minSeats = seats.length > 0 ? Math.min(...seats) : 0;
        return `- ${roomName}: ${room.startTime || "?"}-${room.endTime || "?"}, cap ${room.totalCap || 0}, min seats left ${minSeats}`;
    });

    return {
        message: `Sessions for ${dateID}:\n${lines.join("\n")}`,
        role: caller.thisRole,
    };
});

exports.chatbotListSessionsHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") {
        res.status(405).json({ message: "Method Not Allowed" });
        return;
    }
    try {
        const data = req.body?.data || req.body || {};
        console.log("[chatbotListSessionsHttp] invoked", {
            hasAuth: !!req.headers.authorization,
            dateID: data?.dateID || null,
        });

        const caller = await getApprovedAuthedUserFromRequest(req, [0, 1]);
        const dateID = requireString(data, "dateID");
        if (!/^\d{4}-\d{2}-\d{2}$/.test(dateID)) {
            throw new functions.https.HttpsError("invalid-argument", "dateID must be in YYYY-MM-DD format.");
        }

        const sessionDoc = await db.collection("SessionsCollection").doc(dateID).get();
        if (!sessionDoc.exists) {
            res.status(200).json({ message: `No sessions found on ${dateID}.` });
            return;
        }

        const rooms = sessionDoc.data().rooms || {};
        const roomNames = Object.keys(rooms);
        if (roomNames.length === 0) {
            res.status(200).json({ message: `No rooms are configured for ${dateID}.` });
            return;
        }

        const lines = roomNames.map((roomName) => {
            const room = rooms[roomName] || {};
            const seats = Array.isArray(room.availableSeats) ? room.availableSeats : [];
            const minSeats = seats.length > 0 ? Math.min(...seats) : 0;
            return `- ${roomName}: ${room.startTime || "?"}-${room.endTime || "?"}, cap ${room.totalCap || 0}, min seats left ${minSeats}`;
        });

        res.status(200).json({
            message: `Sessions for ${dateID}:\n${lines.join("\n")}`,
            role: caller.thisRole,
        });
    } catch (e) {
        const code = e?.code === "invalid-argument" ? 400 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to list sessions." });
    }
});

exports.chatbotBookSession = functions.https.onCall(async (data, context) => {
    console.log("[chatbotBookSession] invoked", {
        hasAuth: !!(context.auth && context.auth.uid),
        uid: context.auth?.uid || null,
        dateID: data?.dateID || null,
        roomName: data?.roomName || null,
        studentEmail: data?.studentEmail || null,
    });
    const caller = await getApprovedAuthedUser(context, [0, 1]);

    const studentEmail = requireString(data, "studentEmail");
    const examName = requireString(data, "examName");
    const dateID = requireString(data, "dateID");
    const roomName = requireString(data, "roomName");
    const startTime = requireString(data, "startTime");
    const endTime = requireString(data, "endTime");
    const facultyEmail = (data.facultyEmail || "").toString().trim();

    if (!/^\d{4}-\d{2}-\d{2}$/.test(dateID)) {
        throw new functions.https.HttpsError("invalid-argument", "dateID must be in YYYY-MM-DD format.");
    }

    const startMinutes = parseTimeToMinutes(startTime);
    const endMinutes = parseTimeToMinutes(endTime);
    if (startMinutes == null || endMinutes == null) {
        throw new functions.https.HttpsError("invalid-argument", "startTime and endTime must use HH:mm format.");
    }
    if (startMinutes >= endMinutes) {
        throw new functions.https.HttpsError("invalid-argument", "startTime must be earlier than endTime.");
    }

    const student = await findApprovedUserByEmail(studentEmail, 3);
    if (!student) {
        throw new functions.https.HttpsError("not-found", `No approved student found for ${studentEmail}.`);
    }

    let facultyUID = caller.uid;
    let facultyName = caller.thisUserName || "Faculty";
    if (caller.thisRole === 0) {
        if (!facultyEmail) {
            throw new functions.https.HttpsError("invalid-argument", "facultyEmail is required when an admin books a session.");
        }
        const faculty = await findApprovedUserByEmail(facultyEmail, 1);
        if (!faculty) {
            throw new functions.https.HttpsError("not-found", `No approved faculty found for ${facultyEmail}.`);
        }
        facultyUID = faculty.uid;
        facultyName = faculty.thisUserName || faculty.thisEmail || "Faculty";
    }

    const bookingRef = db.collection("BookingsCollection").doc();
    const sessionRef = db.collection("SessionsCollection").doc(dateID);
    const facultyRef = db.collection("FacultyBookings").doc(facultyUID);
    const studentRef = db.collection("StudentBookings").doc(student.uid);

    await db.runTransaction(async (transaction) => {
        const sessionSnap = await transaction.get(sessionRef);
        if (!sessionSnap.exists) {
            throw new functions.https.HttpsError("not-found", `Session ${dateID} does not exist.`);
        }

        const session = sessionSnap.data() || {};
        const rooms = session.rooms || {};
        const room = rooms[roomName];
        if (!room) {
            throw new functions.https.HttpsError("not-found", `Room ${roomName} does not exist for ${dateID}.`);
        }

        const roomStart = parseTimeToMinutes(room.startTime || "");
        const roomEnd = parseTimeToMinutes(room.endTime || "");
        if (roomStart == null || roomEnd == null) {
            throw new functions.https.HttpsError("failed-precondition", "Room schedule is invalid.");
        }
        if (startMinutes < roomStart || endMinutes > roomEnd) {
            throw new functions.https.HttpsError("failed-precondition", "Requested time is outside the room schedule.");
        }

        const startIdx = Math.floor((startMinutes - roomStart) / 30);
        const endIdx = Math.floor((endMinutes - roomStart) / 30);
        if (startIdx < 0 || endIdx <= startIdx) {
            throw new functions.https.HttpsError("invalid-argument", "Requested time range is not valid for 30-minute slots.");
        }

        const seats = Array.isArray(room.availableSeats) ? [...room.availableSeats] : [];
        for (let i = startIdx; i < endIdx; i++) {
            if (i < 0 || i >= seats.length || seats[i] <= 0) {
                throw new functions.https.HttpsError("failed-precondition", `No seats available in ${roomName} for the requested slot.`);
            }
        }

        const conflictQuery = db.collection("BookingsCollection")
            .where("studentUID", "==", student.uid)
            .where("sessionDateID", "==", dateID);
        const conflictSnap = await transaction.get(conflictQuery);
        for (const doc of conflictSnap.docs) {
            const b = doc.data() || {};
            const existingStart = parseTimeToMinutes((b.startTime || "").toString());
            const existingEnd = parseTimeToMinutes((b.endTime || "").toString());
            if (existingStart != null && existingEnd != null && rangesOverlap(startMinutes, endMinutes, existingStart, existingEnd)) {
                throw new functions.https.HttpsError("already-exists", "Student is already booked for another exam in that time range.");
            }
        }

        for (let i = startIdx; i < endIdx; i++) {
            seats[i] -= 1;
        }

        const updatedRoom = {
            ...room,
            availableSeats: seats,
            bookings: Array.from(new Set([...(room.bookings || []), bookingRef.id])),
        };
        const updatedRooms = { ...rooms, [roomName]: updatedRoom };

        transaction.set(sessionRef, { ...session, dateID, rooms: updatedRooms });

        const bookingData = {
            bookingID: bookingRef.id,
            studentUID: student.uid,
            facultyUID: facultyUID,
            proctorUID: "",
            examName,
            roomName,
            startTime,
            endTime,
            sessionDateID: dateID,
            examDocumentUrl: null,
            examDocumentName: null,
            status: 1,
            incidentReports: [],
            activityLog: [{
                actorName: caller.thisUserName || "DuckBot",
                action: `Booking created via chatbot for faculty ${facultyName}`,
                timestamp: Date.now(),
            }],
        };

        transaction.set(bookingRef, bookingData);
        transaction.set(facultyRef, { bookings: admin.firestore.FieldValue.arrayUnion(bookingRef.id) }, { merge: true });
        transaction.set(studentRef, { bookings: admin.firestore.FieldValue.arrayUnion(bookingRef.id) }, { merge: true });
    });

    return {
        bookingId: bookingRef.id,
        message: `Booked \"${examName}\" for ${student.thisUserName || student.thisEmail} on ${dateID} at ${startTime}-${endTime} in ${roomName}.`,
    };
});

exports.chatbotBookSessionHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") {
        res.status(405).json({ message: "Method Not Allowed" });
        return;
    }

    try {
        const data = req.body?.data || req.body || {};
        console.log("[chatbotBookSessionHttp] invoked", {
            hasAuth: !!req.headers.authorization,
            dateID: data?.dateID || null,
            roomName: data?.roomName || null,
            studentEmail: data?.studentEmail || null,
        });

        const caller = await getApprovedAuthedUserFromRequest(req, [0, 1]);

        const studentEmail = requireString(data, "studentEmail");
        const examName = requireString(data, "examName");
        const dateID = requireString(data, "dateID");
        const roomName = requireString(data, "roomName");
        const startTime = requireString(data, "startTime");
        const endTime = requireString(data, "endTime");
        const facultyEmail = (data.facultyEmail || "").toString().trim();

        if (!/^\d{4}-\d{2}-\d{2}$/.test(dateID)) {
            throw new functions.https.HttpsError("invalid-argument", "dateID must be in YYYY-MM-DD format.");
        }

        const startMinutes = parseTimeToMinutes(startTime);
        const endMinutes = parseTimeToMinutes(endTime);
        if (startMinutes == null || endMinutes == null) {
            throw new functions.https.HttpsError("invalid-argument", "startTime and endTime must use HH:mm format.");
        }
        if (startMinutes >= endMinutes) {
            throw new functions.https.HttpsError("invalid-argument", "startTime must be earlier than endTime.");
        }

        const student = await findApprovedUserByEmail(studentEmail, 3);
        if (!student) {
            throw new functions.https.HttpsError("not-found", `No approved student found for ${studentEmail}.`);
        }

        let facultyUID = caller.uid;
        let facultyName = caller.thisUserName || "Faculty";
        if (caller.thisRole === 0) {
            if (!facultyEmail) {
                throw new functions.https.HttpsError("invalid-argument", "facultyEmail is required when an admin books a session.");
            }
            const faculty = await findApprovedUserByEmail(facultyEmail, 1);
            if (!faculty) {
                throw new functions.https.HttpsError("not-found", `No approved faculty found for ${facultyEmail}.`);
            }
            facultyUID = faculty.uid;
            facultyName = faculty.thisUserName || faculty.thisEmail || "Faculty";
        }

        const bookingRef = db.collection("BookingsCollection").doc();
        const sessionRef = db.collection("SessionsCollection").doc(dateID);
        const facultyRef = db.collection("FacultyBookings").doc(facultyUID);
        const studentRef = db.collection("StudentBookings").doc(student.uid);

        await db.runTransaction(async (transaction) => {
            const sessionSnap = await transaction.get(sessionRef);
            if (!sessionSnap.exists) {
                throw new functions.https.HttpsError("not-found", `Session ${dateID} does not exist.`);
            }

            const session = sessionSnap.data() || {};
            const rooms = session.rooms || {};
            const room = rooms[roomName];
            if (!room) {
                throw new functions.https.HttpsError("not-found", `Room ${roomName} does not exist for ${dateID}.`);
            }

            const roomStart = parseTimeToMinutes(room.startTime || "");
            const roomEnd = parseTimeToMinutes(room.endTime || "");
            if (roomStart == null || roomEnd == null) {
                throw new functions.https.HttpsError("failed-precondition", "Room schedule is invalid.");
            }
            if (startMinutes < roomStart || endMinutes > roomEnd) {
                throw new functions.https.HttpsError("failed-precondition", "Requested time is outside the room schedule.");
            }

            const startIdx = Math.floor((startMinutes - roomStart) / 30);
            const endIdx = Math.floor((endMinutes - roomStart) / 30);
            if (startIdx < 0 || endIdx <= startIdx) {
                throw new functions.https.HttpsError("invalid-argument", "Requested time range is not valid for 30-minute slots.");
            }

            const seats = Array.isArray(room.availableSeats) ? [...room.availableSeats] : [];
            for (let i = startIdx; i < endIdx; i++) {
                if (i < 0 || i >= seats.length || seats[i] <= 0) {
                    throw new functions.https.HttpsError("failed-precondition", `No seats available in ${roomName} for the requested slot.`);
                }
            }

            const conflictQuery = db.collection("BookingsCollection")
                .where("studentUID", "==", student.uid)
                .where("sessionDateID", "==", dateID);
            const conflictSnap = await transaction.get(conflictQuery);
            for (const doc of conflictSnap.docs) {
                const b = doc.data() || {};
                const existingStart = parseTimeToMinutes((b.startTime || "").toString());
                const existingEnd = parseTimeToMinutes((b.endTime || "").toString());
                if (existingStart != null && existingEnd != null && rangesOverlap(startMinutes, endMinutes, existingStart, existingEnd)) {
                    throw new functions.https.HttpsError("already-exists", "Student is already booked for another exam in that time range.");
                }
            }

            for (let i = startIdx; i < endIdx; i++) {
                seats[i] -= 1;
            }

            const updatedRoom = {
                ...room,
                availableSeats: seats,
                bookings: Array.from(new Set([...(room.bookings || []), bookingRef.id])),
            };
            const updatedRooms = { ...rooms, [roomName]: updatedRoom };

            transaction.set(sessionRef, { ...session, dateID, rooms: updatedRooms });

            const bookingData = {
                bookingID: bookingRef.id,
                studentUID: student.uid,
                facultyUID: facultyUID,
                proctorUID: "",
                examName,
                roomName,
                startTime,
                endTime,
                sessionDateID: dateID,
                examDocumentUrl: null,
                examDocumentName: null,
                status: 1,
                incidentReports: [],
                activityLog: [{
                    actorName: caller.thisUserName || "DuckBot",
                    action: `Booking created via chatbot for faculty ${facultyName}`,
                    timestamp: Date.now(),
                }],
            };

            transaction.set(bookingRef, bookingData);
            transaction.set(facultyRef, { bookings: admin.firestore.FieldValue.arrayUnion(bookingRef.id) }, { merge: true });
            transaction.set(studentRef, { bookings: admin.firestore.FieldValue.arrayUnion(bookingRef.id) }, { merge: true });
        });

        res.status(200).json({
            bookingId: bookingRef.id,
            message: `Booked \"${examName}\" for ${student.thisUserName || student.thisEmail} on ${dateID} at ${startTime}-${endTime} in ${roomName}.`,
        });
    } catch (e) {
        const code = e?.code === "invalid-argument" ? 400 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : e?.code === "not-found" ? 404 : 500;
        res.status(code).json({ message: e?.message || "Failed to create booking." });
    }
});

// ═══════════════════════════════════════════════════════════
// Phase 1: Student chatbot actions
// ═══════════════════════════════════════════════════════════

exports.chatbotRequestDeferralHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [3]);
        const courseName = requireString(data, "courseName");
        const reason = requireString(data, "reason");

        const deferralRef = db.collection("Deferrals").doc();
        await deferralRef.set({
            deferralId: deferralRef.id,
            studentUid: caller.uid,
            studentEmail: caller.thisEmail || "",
            courseName,
            reason,
            status: "pending",
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        res.status(200).json({ message: `Deferral request submitted for "${courseName}" (ID: ${deferralRef.id}). Status: pending.` });
    } catch (e) {
        const code = e?.code === "invalid-argument" ? 400 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to request deferral." });
    }
});

exports.chatbotCancelDeferralHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [3]);
        const deferralId = requireString(data, "deferralId");

        const docRef = db.collection("Deferrals").doc(deferralId);
        const doc = await docRef.get();
        if (!doc.exists) throw new functions.https.HttpsError("not-found", "Deferral not found.");
        if (doc.data().studentUid !== caller.uid) throw new functions.https.HttpsError("permission-denied", "This deferral does not belong to you.");
        if (doc.data().status !== "pending") throw new functions.https.HttpsError("failed-precondition", "Only pending deferrals can be cancelled.");

        await docRef.update({ status: "cancelled" });
        res.status(200).json({ message: `Deferral ${deferralId} has been cancelled.` });
    } catch (e) {
        const code = e?.code === "not-found" ? 404 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to cancel deferral." });
    }
});

// ═══════════════════════════════════════════════════════════
// Phase 2: Proctor chatbot actions
// ═══════════════════════════════════════════════════════════

exports.chatbotUpdateAvailabilityHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [2]);
        const dateID = requireString(data, "dateID");
        const available = data.available !== false;

        await db.collection("ProctorAvailability").doc(caller.uid).set(
            { [dateID]: available },
            { merge: true }
        );
        res.status(200).json({ message: `Availability for ${dateID} set to ${available ? "available" : "unavailable"}.` });
    } catch (e) {
        const code = e?.code === "unauthenticated" ? 401 : e?.code === "permission-denied" ? 403 : 500;
        res.status(code).json({ message: e?.message || "Failed to update availability." });
    }
});

exports.chatbotCheckinStudentHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [0, 2]);
        const bookingId = requireString(data, "bookingId");

        const ref = db.collection("BookingsCollection").doc(bookingId);
        const doc = await ref.get();
        if (!doc.exists) throw new functions.https.HttpsError("not-found", "Booking not found.");
        const booking = doc.data();
        if (booking.status >= 3) throw new functions.https.HttpsError("failed-precondition", "Student is already checked in or beyond.");

        await ref.update({
            status: 3,
            activityLog: admin.firestore.FieldValue.arrayUnion({
                actorName: caller.thisUserName || "Proctor",
                action: "Student checked in via chatbot",
                timestamp: Date.now(),
            }),
        });
        res.status(200).json({ message: `Student checked in for booking ${bookingId}.` });
    } catch (e) {
        const code = e?.code === "not-found" ? 404 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to check in student." });
    }
});

exports.chatbotReportIncidentHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [0, 2]);
        const bookingId = requireString(data, "bookingId");
        const description = requireString(data, "description");

        const ref = db.collection("BookingsCollection").doc(bookingId);
        const doc = await ref.get();
        if (!doc.exists) throw new functions.https.HttpsError("not-found", "Booking not found.");

        await ref.update({
            incidentReports: admin.firestore.FieldValue.arrayUnion({
                reportedBy: caller.uid,
                reporterName: caller.thisUserName || "Proctor",
                message: description,
                timestamp: Date.now(),
            }),
            activityLog: admin.firestore.FieldValue.arrayUnion({
                actorName: caller.thisUserName || "Proctor",
                action: `Incident reported via chatbot: ${description.substring(0, 100)}`,
                timestamp: Date.now(),
            }),
        });
        res.status(200).json({ message: `Incident reported for booking ${bookingId}.` });
    } catch (e) {
        const code = e?.code === "not-found" ? 404 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to report incident." });
    }
});

exports.chatbotMarkExamCompleteHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [0, 2]);
        const bookingId = requireString(data, "bookingId");

        const ref = db.collection("BookingsCollection").doc(bookingId);
        const doc = await ref.get();
        if (!doc.exists) throw new functions.https.HttpsError("not-found", "Booking not found.");
        if (doc.data().status >= 4) throw new functions.https.HttpsError("failed-precondition", "Exam is already completed.");

        await ref.update({
            status: 4,
            activityLog: admin.firestore.FieldValue.arrayUnion({
                actorName: caller.thisUserName || "Proctor",
                action: "Exam marked as finished via chatbot",
                timestamp: Date.now(),
            }),
        });
        res.status(200).json({ message: `Exam marked complete for booking ${bookingId}.` });
    } catch (e) {
        const code = e?.code === "not-found" ? 404 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to mark exam complete." });
    }
});

// ═══════════════════════════════════════════════════════════
// Phase 3: Enhanced Faculty chatbot actions
// ═══════════════════════════════════════════════════════════

exports.chatbotCancelBookingHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [0, 1]);
        const bookingId = requireString(data, "bookingId");

        const ref = db.collection("BookingsCollection").doc(bookingId);

        await db.runTransaction(async (transaction) => {
            const doc = await transaction.get(ref);
            if (!doc.exists) throw new functions.https.HttpsError("not-found", "Booking not found.");
            const booking = doc.data();

            if (caller.thisRole === 1 && booking.facultyUID !== caller.uid) {
                throw new functions.https.HttpsError("permission-denied", "You can only cancel your own bookings.");
            }

            // Restore seat capacity
            const sessionRef = db.collection("SessionsCollection").doc(booking.sessionDateID);
            const sessionSnap = await transaction.get(sessionRef);
            if (sessionSnap.exists) {
                const session = sessionSnap.data() || {};
                const rooms = session.rooms || {};
                const room = rooms[booking.roomName];
                if (room) {
                    const roomStart = parseTimeToMinutes(room.startTime || "");
                    const bookingStart = parseTimeToMinutes(booking.startTime || "");
                    const bookingEnd = parseTimeToMinutes(booking.endTime || "");
                    if (roomStart != null && bookingStart != null && bookingEnd != null) {
                        const seats = Array.isArray(room.availableSeats) ? [...room.availableSeats] : [];
                        const startIdx = Math.floor((bookingStart - roomStart) / 30);
                        const endIdx = Math.floor((bookingEnd - roomStart) / 30);
                        for (let i = startIdx; i < endIdx && i < seats.length; i++) {
                            seats[i] += 1;
                        }
                        const updatedRoom = { ...room, availableSeats: seats };
                        updatedRoom.bookings = (updatedRoom.bookings || []).filter(id => id !== bookingId);
                        transaction.update(sessionRef, { [`rooms.${booking.roomName}`]: updatedRoom });
                    }
                }
            }

            transaction.update(ref, {
                status: 0,
                activityLog: admin.firestore.FieldValue.arrayUnion({
                    actorName: caller.thisUserName || "DuckBot",
                    action: "Booking cancelled via chatbot",
                    timestamp: Date.now(),
                }),
            });
        });

        res.status(200).json({ message: `Booking ${bookingId} has been cancelled and seats restored.` });
    } catch (e) {
        const code = e?.code === "not-found" ? 404 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to cancel booking." });
    }
});

exports.chatbotApproveDeferralHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [0, 1]);
        const deferralId = requireString(data, "deferralId");

        const ref = db.collection("Deferrals").doc(deferralId);
        const doc = await ref.get();
        if (!doc.exists) throw new functions.https.HttpsError("not-found", "Deferral not found.");
        const deferral = doc.data();
        if (deferral.status !== "pending") throw new functions.https.HttpsError("failed-precondition", `Deferral is already ${deferral.status}.`);

        await ref.update({
            status: "approved",
            reviewedBy: caller.uid,
            reviewedAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        if (deferral.studentUid) {
            await sendNotificationToUser(deferral.studentUid, "Deferral Approved",
                `Your deferral for "${deferral.courseName}" has been approved.`,
                { type: "status_update" }, "bookings_channel");
        }

        res.status(200).json({ message: `Deferral ${deferralId} for "${deferral.courseName}" has been approved.` });
    } catch (e) {
        const code = e?.code === "not-found" ? 404 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to approve deferral." });
    }
});

exports.chatbotRejectDeferralHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [0, 1]);
        const deferralId = requireString(data, "deferralId");

        const ref = db.collection("Deferrals").doc(deferralId);
        const doc = await ref.get();
        if (!doc.exists) throw new functions.https.HttpsError("not-found", "Deferral not found.");
        const deferral = doc.data();
        if (deferral.status !== "pending") throw new functions.https.HttpsError("failed-precondition", `Deferral is already ${deferral.status}.`);

        await ref.update({
            status: "rejected",
            reviewedBy: caller.uid,
            reviewedAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        if (deferral.studentUid) {
            await sendNotificationToUser(deferral.studentUid, "Deferral Rejected",
                `Your deferral for "${deferral.courseName}" has been rejected.`,
                { type: "status_update" }, "bookings_channel");
        }

        res.status(200).json({ message: `Deferral ${deferralId} for "${deferral.courseName}" has been rejected.` });
    } catch (e) {
        const code = e?.code === "not-found" ? 404 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to reject deferral." });
    }
});

exports.chatbotSendMessageHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [0, 1]);
        const recipientEmail = requireString(data, "recipientEmail");
        const messageText = requireString(data, "messageText");

        // Find recipient by email (any role)
        const trimmed = recipientEmail.trim();
        let recipientDoc = null;
        const snap = await db.collection("UserAccounts")
            .where("thisEmail", "==", trimmed).limit(1).get();
        if (!snap.empty) recipientDoc = snap.docs[0];

        if (!recipientDoc) {
            const snapLower = await db.collection("UserAccounts")
                .where("thisEmail", "==", trimmed.toLowerCase()).limit(1).get();
            if (!snapLower.empty) recipientDoc = snapLower.docs[0];
        }

        if (!recipientDoc) throw new functions.https.HttpsError("not-found", `No user found for ${recipientEmail}.`);

        const recipientUid = recipientDoc.id;
        const participantIds = [caller.uid, recipientUid].sort();

        // Find or create conversation
        let conversationId = null;
        const convSnap = await db.collection("conversations")
            .where("participants", "==", participantIds).limit(1).get();

        if (!convSnap.empty) {
            conversationId = convSnap.docs[0].id;
        } else {
            const newConv = await db.collection("conversations").add({
                participants: participantIds,
                lastMessage: messageText,
                lastMessageTimestamp: admin.firestore.FieldValue.serverTimestamp(),
            });
            conversationId = newConv.id;
        }

        await db.collection("conversations").doc(conversationId).collection("messages").add({
            senderId: caller.uid,
            senderName: caller.thisUserName || caller.thisEmail || "Unknown",
            content: messageText,
            timestamp: admin.firestore.FieldValue.serverTimestamp(),
            isRead: false,
        });

        await db.collection("conversations").doc(conversationId).update({
            lastMessage: messageText,
            lastMessageTimestamp: admin.firestore.FieldValue.serverTimestamp(),
        });

        res.status(200).json({ message: `Message sent to ${recipientEmail}.` });
    } catch (e) {
        const code = e?.code === "not-found" ? 404 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to send message." });
    }
});

// ═══════════════════════════════════════════════════════════
// Phase 4: Enhanced Admin chatbot actions
// ═══════════════════════════════════════════════════════════

exports.chatbotApproveAccountHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [0]);
        const email = requireString(data, "email");

        const snap = await db.collection("UserAccounts")
            .where("thisEmail", "==", email.trim()).limit(1).get();
        if (snap.empty) {
            const snapLower = await db.collection("UserAccounts")
                .where("thisEmail", "==", email.trim().toLowerCase()).limit(1).get();
            if (snapLower.empty) throw new functions.https.HttpsError("not-found", `No user found for ${email}.`);
            const doc = snapLower.docs[0];
            await doc.ref.update({ thisApprovalStatus: 1 });
            res.status(200).json({ message: `Account ${email} has been approved.` });
            return;
        }
        const doc = snap.docs[0];
        await doc.ref.update({ thisApprovalStatus: 1 });
        res.status(200).json({ message: `Account ${email} has been approved.` });
    } catch (e) {
        const code = e?.code === "not-found" ? 404 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to approve account." });
    }
});

exports.chatbotRejectAccountHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [0]);
        const email = requireString(data, "email");

        const snap = await db.collection("UserAccounts")
            .where("thisEmail", "==", email.trim()).limit(1).get();
        if (snap.empty) {
            const snapLower = await db.collection("UserAccounts")
                .where("thisEmail", "==", email.trim().toLowerCase()).limit(1).get();
            if (snapLower.empty) throw new functions.https.HttpsError("not-found", `No user found for ${email}.`);
            await snapLower.docs[0].ref.update({ thisApprovalStatus: -2 });
            res.status(200).json({ message: `Account ${email} has been rejected.` });
            return;
        }
        await snap.docs[0].ref.update({ thisApprovalStatus: -2 });
        res.status(200).json({ message: `Account ${email} has been rejected.` });
    } catch (e) {
        const code = e?.code === "not-found" ? 404 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to reject account." });
    }
});

exports.chatbotAssignProctorHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [0]);
        const proctorEmail = requireString(data, "proctorEmail");
        const dateID = requireString(data, "dateID");
        const roomName = requireString(data, "roomName");

        const proctor = await findApprovedUserByEmail(proctorEmail, 2);
        if (!proctor) throw new functions.https.HttpsError("not-found", `No approved proctor found for ${proctorEmail}.`);

        const sessionRef = db.collection("SessionsCollection").doc(dateID);
        const sessionDoc = await sessionRef.get();
        if (!sessionDoc.exists) throw new functions.https.HttpsError("not-found", `No session on ${dateID}.`);

        const rooms = sessionDoc.data().rooms || {};
        const room = rooms[roomName];
        if (!room) throw new functions.https.HttpsError("not-found", `Room ${roomName} not found on ${dateID}.`);

        const proctors = room.assignedProctors || [];
        if (proctors.includes(proctor.uid)) {
            res.status(200).json({ message: `${proctorEmail} is already assigned to ${roomName} on ${dateID}.` });
            return;
        }

        await sessionRef.update({
            [`rooms.${roomName}.assignedProctors`]: admin.firestore.FieldValue.arrayUnion(proctor.uid)
        });

        res.status(200).json({ message: `Proctor ${proctorEmail} assigned to ${roomName} on ${dateID}.` });
    } catch (e) {
        const code = e?.code === "not-found" ? 404 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to assign proctor." });
    }
});

exports.chatbotSendAnnouncementHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [0]);
        const message = requireString(data, "message");
        const targetRole = data.targetRole != null ? parseInt(data.targetRole, 10) : null;

        let query = db.collection("UserAccounts").where("thisApprovalStatus", "==", 1);
        if (targetRole != null && !isNaN(targetRole)) {
            query = query.where("thisRole", "==", targetRole);
        }

        const usersSnap = await query.get();
        const promises = [];
        usersSnap.forEach(doc => {
            promises.push(sendNotificationToUser(
                doc.id, "Announcement", message,
                { type: "system" }, "system_channel"
            ));
        });
        await Promise.all(promises);

        const roleLabel = targetRole != null ? ` (role ${targetRole})` : "";
        res.status(200).json({ message: `Announcement sent to ${usersSnap.size} user(s)${roleLabel}.` });
    } catch (e) {
        const code = e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to send announcement." });
    }
});

exports.chatbotManageRoomHttp = functions.https.onRequest(async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ message: "Method Not Allowed" }); return; }
    try {
        const data = req.body?.data || req.body || {};
        const caller = await getApprovedAuthedUserFromRequest(req, [0]);
        const dateID = requireString(data, "dateID");
        const roomName = requireString(data, "roomName");
        const totalCap = parseInt(data.totalCap, 10);
        const startTime = requireString(data, "startTime");
        const endTime = requireString(data, "endTime");

        if (isNaN(totalCap) || totalCap <= 0)
            throw new functions.https.HttpsError("invalid-argument", "totalCap must be a positive number.");

        const startMin = parseTimeToMinutes(startTime);
        const endMin = parseTimeToMinutes(endTime);
        if (startMin == null || endMin == null)
            throw new functions.https.HttpsError("invalid-argument", "Times must be HH:mm format.");
        if (startMin >= endMin)
            throw new functions.https.HttpsError("invalid-argument", "startTime must be before endTime.");

        const slotCount = Math.ceil((endMin - startMin) / 30);
        const availableSeats = Array(slotCount).fill(totalCap);

        const sessionRef = db.collection("SessionsCollection").doc(dateID);
        await sessionRef.set({
            dateID,
            [`rooms`]: {
                [roomName]: {
                    startTime, endTime, totalCap, availableSeats,
                    assignedProctors: [], bookings: [],
                }
            }
        }, { merge: true });

        res.status(200).json({ message: `Room "${roomName}" on ${dateID} created/updated (cap: ${totalCap}, ${startTime}-${endTime}, ${slotCount} slots).` });
    } catch (e) {
        const code = e?.code === "invalid-argument" ? 400 : e?.code === "permission-denied" ? 403 : e?.code === "unauthenticated" ? 401 : 500;
        res.status(code).json({ message: e?.message || "Failed to manage room." });
    }
});

/**
 * Helper to notify all admins
 */
async function notifyAdmins(title, body, data = {}) {
    const adminsSnap = await db.collection("UserAccounts").where("thisRole", "==", 0).get();
    const adminPromises = [];
    adminsSnap.forEach(doc => {
        adminPromises.push(sendNotificationToUser(
            doc.id,
            title,
            body,
            { ...data, type: "proctor_needed" },
            "bookings_channel"
        ));
    });
    return Promise.all(adminPromises);
}

/**
 * Chat notifications
 */
exports.sendMessageNotification = functions.firestore
    .document("conversations/{conversationId}/messages/{messageId}")
    .onCreate(async (snapshot, context) => {
        const { conversationId } = context.params;
        const { senderId, senderName, content } = snapshot.data();

        try {
            const conversationDoc = await db.collection("conversations").doc(conversationId).get();
            if (!conversationDoc.exists) return null;

            const participants = conversationDoc.data().participants || [];
            const recipientId = participants.find((id) => id !== senderId);
            if (!recipientId) return null;

            // Fetch sender photo for large icon
            const senderDoc = await db.collection("UserAccounts").doc(senderId).get();
            const senderPhoto = senderDoc.exists ? senderDoc.data().profileImageUrl : null;

            return await sendNotificationToUser(
                recipientId,
                senderName || "New Message",
                content ? content.substring(0, 100) : "You have a new message",
                {
                    type: "chat",
                    conversationId,
                    senderPhoto: senderPhoto || ""
                },
                "messages_channel"
            );
        } catch (error) {
            return null;
        }
    });

/**
 * SESSION CREATED - Notify Proctors about new session availability (NOT a booking)
 * Proctors need to know a session is available for them to be assigned to
 */
exports.onSessionCreated = functions.firestore
    .document("SessionsCollection/{dateId}")
    .onCreate(async (snapshot, context) => {
        const data = snapshot.data();
        const dateId = context.params.dateId;
        const notifications = [];

        // Notify assigned proctors that a new session is available
        for (const roomName in data.rooms) {
            const proctors = data.rooms[roomName].assignedProctors || [];
            for (const proctorUID of proctors) {
                notifications.push(sendNotificationToUser(
                    proctorUID,
                    "New Session Available",
                    `A new exam session has been created for ${dateId} in ${roomName}. You have been assigned to proctor this session. No bookings yet.`,
                    { type: "proctor_assigned", roomName, dateId },
                    "bookings_channel"
                ));
            }
        }

        await Promise.all(notifications);
    });

/**
 * SESSION UPDATED - Notify proctors when assigned/unassigned
 */
exports.onSessionUpdated = functions.firestore
    .document("SessionsCollection/{dateId}")
    .onUpdate(async (change, context) => {
        const newData = change.after.data();
        const oldData = change.before.data();
        const dateId = context.params.dateId;
        const notifications = [];

        const allRoomNames = new Set([...Object.keys(oldData.rooms || {}), ...Object.keys(newData.rooms || {})]);

        for (const roomName of allRoomNames) {
            const oldProctors = oldData.rooms?.[roomName]?.assignedProctors || [];
            const newProctors = newData.rooms?.[roomName]?.assignedProctors || [];

            // Proctors added to this room
            const added = newProctors.filter(p => !oldProctors.includes(p));
            for (const proctorUID of added) {
                notifications.push(sendNotificationToUser(
                    proctorUID,
                    "New Proctoring Assignment",
                    `You have been assigned to proctor ${roomName} on ${dateId}. Tap to view your schedule.`,
                    { type: "proctor_assigned", roomName, dateId },
                    "bookings_channel"
                ));
            }

            // Proctors removed from this room
            const removed = oldProctors.filter(p => !newProctors.includes(p));
            for (const proctorUID of removed) {
                notifications.push(sendNotificationToUser(
                    proctorUID,
                    "Proctoring Assignment Cancelled",
                    `Your assignment to proctor ${roomName} on ${dateId} has been cancelled.`,
                    { type: "proctor_assigned", roomName, dateId },
                    "bookings_channel"
                ));
            }

            // CHECK: If no proctors left but room has bookings, notify admins
            if (newProctors.length === 0 && oldProctors.length > 0) {
                const bookingsSnap = await db.collection("BookingsCollection")
                    .where("sessionDateID", "==", dateId)
                    .where("roomName", "==", roomName)
                    .limit(1)
                    .get();

                if (!bookingsSnap.empty) {
                    notifications.push(notifyAdmins(
                        "Urgent: Proctor Required",
                        `The last proctor was removed from ${roomName} on ${dateId}, but active bookings exist.`,
                        { dateId, roomName }
                    ));
                }
            }
        }

        await Promise.all(notifications);
    });

/**
 * BOOKING CREATED - Notify Student, Faculty, and Proctors
 * All parties need to know when a booking is made (typically by admin)
 */
exports.onBookingCreated = functions.firestore
    .document("BookingsCollection/{bookingId}")
    .onCreate(async (snapshot, context) => {
        const b = snapshot.data();
        const bookingId = snapshot.id;
        const notifications = [];

        // Notify Student - Booking confirmation
        notifications.push(sendNotificationToUser(
            b.studentUID,
            "Booking Confirmed",
            `Your exam "${b.examName}" has been booked for ${b.sessionDateID} at ${b.startTime} in ${b.roomName}.`,
            { type: "booking_created", bookingId },
            "bookings_channel"
        ));

        // Notify Faculty - New booking for their student
        notifications.push(sendNotificationToUser(
            b.facultyUID,
            "New Student Booking",
            `An exam "${b.examName}" has been booked for your student on ${b.sessionDateID} at ${b.startTime} in ${b.roomName}.`,
            { type: "booking_created", bookingId },
            "bookings_channel"
        ));

        // Notify Proctor(s) assigned to this room
        const sessionDoc = await db.collection("SessionsCollection").doc(b.sessionDateID).get();
        if (sessionDoc.exists) {
            const room = (sessionDoc.data().rooms || {})[b.roomName];
            const proctors = room ? (room.assignedProctors || []) : [];

            if (proctors.length === 0) {
                // ALERT ADMINS: Booking exists but no proctor
                notifications.push(notifyAdmins(
                    "Proctor Required",
                    `A new booking was made for ${b.roomName} on ${b.sessionDateID}, but no proctor is assigned yet.`,
                    { bookingId, dateId: b.sessionDateID, roomName: b.roomName }
                ));
            } else {
                for (const proctorUID of proctors) {
                    notifications.push(sendNotificationToUser(
                        proctorUID,
                        "New Student Booking",
                        `A student has booked "${b.examName}" in ${b.roomName} on ${b.sessionDateID} at ${b.startTime}.`,
                        { type: "booking_created", bookingId },
                        "bookings_channel"
                    ));
                }
            }
        }

        await Promise.all(notifications);
    });

/**
 * BOOKING UPDATED - Handle status changes and booking modifications
 *
 * Status Flow:
 * 1: Booked
 * 2: Fee Paid → Notify Faculty
 * 3: Student Checked In (Confirmed) → Notify Faculty
 * 4: Exam Finished
 * 5: Exam Dropped Off → Notify Faculty
 * 6: Faculty Received/Completed → Notify Student
 */
exports.onBookingUpdated = functions.firestore
    .document("BookingsCollection/{bookingId}")
    .onUpdate(async (change, context) => {
        const newData = change.after.data();
        const oldData = change.before.data();
        const bookingId = context.params.bookingId;
        const notifications = [];

        // BOOKING RESCHEDULED - Notify Student, Faculty, AND Proctors
        if (newData.startTime !== oldData.startTime || newData.sessionDateID !== oldData.sessionDateID || newData.roomName !== oldData.roomName) {
            // Notify Student
            notifications.push(sendNotificationToUser(
                newData.studentUID,
                "Booking Rescheduled",
                `Your exam "${newData.examName}" has been rescheduled to ${newData.sessionDateID} at ${newData.startTime} in ${newData.roomName}.`,
                { type: "status_update", bookingId },
                "bookings_channel"
            ));

            // Notify Faculty
            notifications.push(sendNotificationToUser(
                newData.facultyUID,
                "Booking Rescheduled",
                `The exam "${newData.examName}" for your student has been rescheduled to ${newData.sessionDateID} at ${newData.startTime} in ${newData.roomName}.`,
                { type: "status_update", bookingId },
                "bookings_channel"
            ));

            // Notify Proctors in the NEW room
            const sessionDoc = await db.collection("SessionsCollection").doc(newData.sessionDateID).get();
            if (sessionDoc.exists) {
                const room = (sessionDoc.data().rooms || {})[newData.roomName];
                if (room && room.assignedProctors) {
                    for (const proctorUID of room.assignedProctors) {
                        notifications.push(sendNotificationToUser(
                            proctorUID,
                            "Booking Update",
                            `A booking for "${newData.examName}" in ${newData.roomName} has been updated. New time: ${newData.startTime}.`,
                            { type: "status_update", bookingId },
                            "bookings_channel"
                        ));
                    }
                }
            }
        }

        // STATUS CHANGES
        if (newData.status !== oldData.status) {

            // Status 2: Fee Paid → Notify Faculty
            if (newData.status === 2) {
                notifications.push(sendNotificationToUser(
                    newData.facultyUID,
                    "Payment Received",
                    `The student has paid for their exam "${newData.examName}" scheduled for ${newData.sessionDateID} at ${newData.startTime}.`,
                    { type: "payment", bookingId },
                    "bookings_channel"
                ));
            }

            // Status 3: Student Checked In (Confirmed) → Notify Faculty
            if (newData.status === 3) {
                notifications.push(sendNotificationToUser(
                    newData.facultyUID,
                    "Student Confirmed",
                    `The student has checked in and is confirmed for "${newData.examName}" in ${newData.roomName}.`,
                    { type: "status_update", bookingId },
                    "bookings_channel"
                ));
            }

            // Status 5: Exam Dropped Off → Notify Faculty
            if (newData.status === 5) {
                notifications.push(sendNotificationToUser(
                    newData.facultyUID,
                    "Exam Dropped Off",
                    `The exam "${newData.examName}" has been dropped off and is ready for pickup in ${newData.roomName}.`,
                    { type: "status_update", bookingId },
                    "bookings_channel"
                ));
            }

            // Status 6: Faculty Received/Completed → Notify Student
            if (newData.status === 6) {
                notifications.push(sendNotificationToUser(
                    newData.studentUID,
                    "Exam Received",
                    `Your exam "${newData.examName}" has been received by your faculty. The process is complete.`,
                    { type: "status_update", bookingId },
                    "bookings_channel"
                ));
            }
        }

        // INCIDENT REPORTS → Notify Faculty
        const newReports = newData.incidentReports || [];
        const oldReports = oldData.incidentReports || [];
        if (newReports.length > oldReports.length) {
            const lastReport = newReports[newReports.length - 1];
            notifications.push(sendNotificationToUser(
                newData.facultyUID,
                "Incident Report",
                `An incident was reported for "${newData.examName}": ${lastReport.message}`,
                { type: "incident", bookingId },
                "bookings_channel"
            ));
        }

        await Promise.all(notifications);
    });

/**
 * Admin Notifications for new accounts
 */
exports.onAccountCreated = functions.firestore
    .document("UserAccounts/{uid}")
    .onCreate(async (snapshot, context) => {
        const userData = snapshot.data();
        if (userData.thisApprovalStatus === 0) {
            const adminsSnap = await db.collection("UserAccounts").where("thisRole", "==", 0).get();
            const adminPromises = [];
            adminsSnap.forEach(doc => {
                adminPromises.push(sendNotificationToUser(
                    doc.id,
                    "Action Required: New Account Pending",
                    `A new account for ${userData.thisUserName} is waiting for approval.`,
                    { type: "manage_users" },
                    "system_channel"
                ));
            });
            await Promise.all(adminPromises);
        }
    });

/**
 * Daily Reminders - Notify students about their exams today
 */
exports.dailyReminders = functions.pubsub.schedule("0 8 * * *")
    .timeZone("America/Edmonton")
    .onRun(async (context) => {
        const today = new Date().toISOString().split("T")[0];
        const bookingsSnap = await db.collection("BookingsCollection").where("sessionDateID", "==", today).get();
        const promises = [];

        bookingsSnap.forEach((doc) => {
            const b = doc.data();
            promises.push(sendNotificationToUser(
                b.studentUID,
                "Exam Today!",
                `Reminder: You have "${b.examName}" today at ${b.startTime} in ${b.roomName}.`,
                { type: "daily_reminder", bookingId: doc.id },
                "bookings_channel"
            ));
        });

        await Promise.all(promises);
        return null;
    });

/**
 * Placeholder triggers kept to avoid deleting deployed functions.
 */
exports.onAccountStatusUpdated = functions.firestore
    .document("UserAccounts/{uid}")
    .onUpdate(async () => null);

exports.onBookingDeleted = functions.firestore
    .document("BookingsCollection/{bookingId}")
    .onDelete(async () => null);
