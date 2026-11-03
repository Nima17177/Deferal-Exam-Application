package com.example.duckduckdefer.components.chatbot

import com.example.duckduckdefer.useraccountclasses.AdminSubclass
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import com.example.duckduckdefer.viewmodels.ChatbotViewModel.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

/**
 * Bridge between Gemini's intent and Firestore data.
 * Each function returns a human-readable result string that gets
 * fed back to Gemini for natural-language formatting.
 */
class ChatbotActionExecutor {

    private val db = FirebaseFirestore.getInstance()
    private val admin = AdminSubclass()
    private val secureActionService = ChatbotSecureActionService()

    // ── Admin Actions ───────────────────────────────────────

    suspend fun searchUserByEmail(email: String): String =
        suspendCancellableCoroutine { cont ->
            admin.searchUsers(email) { users ->
                if (users.isEmpty()) {
                    cont.resume("No users found matching \"$email\".")
                } else {
                    val lines = users.joinToString("\n") { u ->
                        "• ${u.thisUserName} (${u.thisEmail}) — status: ${statusLabel(u.thisApprovalStatus)}, role: ${roleLabel(u.thisRole)}"
                    }
                    cont.resume("Found ${users.size} user(s):\n$lines")
                }
            }
        }

    suspend fun deactivateAccount(email: String): String {
        val users = searchUsersInternal(email)
        if (users.isEmpty()) return "No user found with email \"$email\". Cannot deactivate."
        val target = users.first()
        return suspendCancellableCoroutine { cont ->
            admin.deactivateAccount(target) { success ->
                if (success) cont.resume("✅ Account ${target.thisEmail} has been deactivated.")
                else cont.resume("❌ Failed to deactivate ${target.thisEmail}.")
            }
        }
    }

    suspend fun reactivateAccount(email: String): String {
        val users = searchUsersInternal(email)
        if (users.isEmpty()) return "No user found with email \"$email\". Cannot reactivate."
        val target = users.first()
        return suspendCancellableCoroutine { cont ->
            admin.reactivateAccount(target) { success ->
                if (success) cont.resume("✅ Account ${target.thisEmail} has been reactivated.")
                else cont.resume("❌ Failed to reactivate ${target.thisEmail}.")
            }
        }
    }

    suspend fun getPendingAccounts(): String =
        suspendCancellableCoroutine { cont ->
            admin.getPendingAccounts { uids ->
                if (uids.isEmpty()) {
                    cont.resume("There are no pending accounts at the moment.")
                } else {
                    cont.resume("There are ${uids.size} pending account(s): ${uids.joinToString(", ")}")
                }
            }
        }

    suspend fun listSessionsByDate(dateID: String): String =
        secureActionService.listSessionsByDate(dateID)

    suspend fun bookSession(
        studentEmail: String,
        examName: String,
        dateID: String,
        roomName: String,
        startTime: String,
        endTime: String,
        facultyEmail: String?
    ): String = secureActionService.bookSession(
        studentEmail = studentEmail,
        examName = examName,
        dateID = dateID,
        roomName = roomName,
        startTime = startTime,
        endTime = endTime,
        facultyEmail = facultyEmail
    )

    suspend fun deleteSession(sessionId: String): String =
        suspendCancellableCoroutine { cont ->
            db.collection("SessionsCollection")
                .document(sessionId)
                .delete()
                .addOnSuccessListener {
                    cont.resume("✅ Session $sessionId has been deleted.")
                }
                .addOnFailureListener {
                    cont.resume("❌ Failed to delete session $sessionId: ${it.message}")
                }
        }

    // ── Faculty Actions ─────────────────────────────────────

    suspend fun getDeferralCount(facultyUid: String): String =
        suspendCancellableCoroutine { cont ->
            db.collection("Deferrals")
                .whereEqualTo("facultyUid", facultyUid)
                .get()
                .addOnSuccessListener { snapshots ->
                    cont.resume("You currently have ${snapshots.size()} deferral request(s).")
                }
                .addOnFailureListener {
                    cont.resume("Error fetching deferrals: ${it.message}")
                }
        }

    suspend fun getFacultyAvailability(facultyUid: String): String =
        suspendCancellableCoroutine { cont ->
            db.collection("BookingsCollection")
                .whereEqualTo("facultyUID", facultyUid)
                .get()
                .addOnSuccessListener { snapshots ->
                    if (snapshots.isEmpty) {
                        cont.resume("You have no scheduled sessions this week.")
                    } else {
                        val lines = snapshots.documents.joinToString("\n") { doc ->
                            val date = doc.getString("sessionDateID") ?: "?"
                            val start = doc.getString("startTime") ?: "?"
                            val end = doc.getString("endTime") ?: "?"
                            val room = doc.getString("roomName") ?: "?"
                            "• $date at $start-$end in room $room"
                        }
                        cont.resume("Your scheduled sessions:\n$lines")
                    }
                }
                .addOnFailureListener {
                    cont.resume("Error fetching availability: ${it.message}")
                }
        }

    // ── Phase 1: Student Actions ────────────────────────────

    suspend fun getMyBookings(uid: String, role: Int): String {
        return try {
            val collectionName = if (role == 3) "StudentBookings" else "FacultyBookings"
            val indexDoc = db.collection(collectionName).document(uid).get().await()
            if (!indexDoc.exists()) return "You have no bookings."

            @Suppress("UNCHECKED_CAST")
            val bookingIds = indexDoc.get("bookings") as? List<String> ?: emptyList()
            if (bookingIds.isEmpty()) return "You have no bookings."

            val lines = mutableListOf<String>()
            for (id in bookingIds.takeLast(20)) {
                val doc = db.collection("BookingsCollection").document(id).get().await()
                if (!doc.exists()) continue
                val exam = doc.getString("examName") ?: "?"
                val date = doc.getString("sessionDateID") ?: "?"
                val start = doc.getString("startTime") ?: "?"
                val end = doc.getString("endTime") ?: "?"
                val room = doc.getString("roomName") ?: "?"
                val status = bookingStatusLabel(doc.getLong("status")?.toInt() ?: 0)
                lines.add("• **$exam** — $date at $start-$end in $room — Status: $status (ID: $id)")
            }
            if (lines.isEmpty()) return "You have no active bookings."
            "Your bookings (${lines.size}):\n${lines.joinToString("\n")}"
        } catch (e: Exception) {
            "Error fetching bookings: ${e.message}"
        }
    }

    suspend fun getBookingStatus(bookingId: String): String {
        return try {
            val doc = db.collection("BookingsCollection").document(bookingId).get().await()
            if (!doc.exists()) return "No booking found with ID \"$bookingId\"."

            val exam = doc.getString("examName") ?: "?"
            val date = doc.getString("sessionDateID") ?: "?"
            val start = doc.getString("startTime") ?: "?"
            val end = doc.getString("endTime") ?: "?"
            val room = doc.getString("roomName") ?: "?"
            val status = bookingStatusLabel(doc.getLong("status")?.toInt() ?: 0)
            val proctorUid = doc.getString("proctorUID").orEmpty()
            val proctorInfo = if (proctorUid.isNotBlank()) "Proctor assigned" else "No proctor yet"

            "Booking **$bookingId**:\n• Exam: $exam\n• Date: $date\n• Time: $start-$end\n• Room: $room\n• Status: $status\n• $proctorInfo"
        } catch (e: Exception) {
            "Error fetching booking: ${e.message}"
        }
    }

    suspend fun getMyDeferrals(uid: String): String {
        return try {
            val snap = db.collection("Deferrals")
                .whereEqualTo("studentUid", uid)
                .get().await()

            if (snap.isEmpty) return "You have no deferral requests."

            val lines = snap.documents.map { doc ->
                val course = doc.getString("courseName") ?: "?"
                val reason = doc.getString("reason") ?: "?"
                val status = doc.getString("status") ?: "pending"
                "• **$course** — Reason: $reason — Status: $status (ID: ${doc.id})"
            }
            "Your deferrals (${lines.size}):\n${lines.joinToString("\n")}"
        } catch (e: Exception) {
            "Error fetching deferrals: ${e.message}"
        }
    }


    suspend fun getExamChecklist(uid: String): String {
        return try {
            val indexDoc = db.collection("StudentBookings").document(uid).get().await()
            if (!indexDoc.exists()) return "You have no bookings. Nothing to prepare for."

            @Suppress("UNCHECKED_CAST")
            val bookingIds = indexDoc.get("bookings") as? List<String> ?: emptyList()
            if (bookingIds.isEmpty()) return "You have no bookings."

            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            var nextBooking: Map<String, Any?>? = null
            var nextBookingId = ""

            for (id in bookingIds) {
                val doc = db.collection("BookingsCollection").document(id).get().await()
                if (!doc.exists()) continue
                val date = doc.getString("sessionDateID") ?: continue
                if (date >= today) {
                    val status = doc.getLong("status")?.toInt() ?: 0
                    if (status in 1..3) {
                        nextBooking = doc.data
                        nextBookingId = id
                        break
                    }
                }
            }

            if (nextBooking == null) return "No upcoming exams found."

            val exam = nextBooking["examName"] as? String ?: "?"
            val date = nextBooking["sessionDateID"] as? String ?: "?"
            val start = nextBooking["startTime"] as? String ?: "?"
            val end = nextBooking["endTime"] as? String ?: "?"
            val room = nextBooking["roomName"] as? String ?: "?"
            val status = (nextBooking["status"] as? Long)?.toInt() ?: 0
            val hasDoc = !(nextBooking["examDocumentUrl"] as? String).isNullOrBlank()

            buildString {
                appendLine("📋 **Exam Checklist** for $exam (ID: $nextBookingId)")
                appendLine("• Date: $date")
                appendLine("• Time: $start - $end")
                appendLine("• Room: $room")
                appendLine("• ${if (status >= 2) "✅ Payment: Completed" else "⬜ Payment: Pending"}")
                appendLine("• ${if (status >= 3) "✅ Check-in: Done" else "⬜ Check-in: Not yet"}")
                appendLine("• ${if (hasDoc) "✅ Exam document: Uploaded" else "⬜ Exam document: Not uploaded"}")
                append("• Bring valid student ID")
            }
        } catch (e: Exception) {
            "Error building checklist: ${e.message}"
        }
    }

    // ── Phase 2: Proctor Actions ────────────────────────────

    suspend fun getMyAssignedSessions(proctorUid: String): String {
        return try {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val snap = db.collection("SessionsCollection")
                .whereGreaterThanOrEqualTo("dateID", today)
                .orderBy("dateID")
                .limit(14)
                .get().await()

            val lines = mutableListOf<String>()
            for (doc in snap.documents) {
                val dateId = doc.id
                @Suppress("UNCHECKED_CAST")
                val rooms = doc.get("rooms") as? Map<String, Map<String, Any>> ?: continue
                for ((roomName, roomData) in rooms) {
                    @Suppress("UNCHECKED_CAST")
                    val proctors = roomData["assignedProctors"] as? List<String> ?: emptyList()
                    if (proctorUid in proctors) {
                        val start = roomData["startTime"] as? String ?: "?"
                        val end = roomData["endTime"] as? String ?: "?"
                        lines.add("• $dateId in **$roomName** ($start-$end)")
                    }
                }
            }
            if (lines.isEmpty()) return "You have no upcoming assigned sessions."
            "Your assigned sessions:\n${lines.joinToString("\n")}"
        } catch (e: Exception) {
            "Error fetching assigned sessions: ${e.message}"
        }
    }

    suspend fun updateAvailability(dateID: String, available: Boolean): String =
        secureActionService.updateAvailability(dateID, available)

    suspend fun getBookingDetails(bookingId: String): String {
        return try {
            val doc = db.collection("BookingsCollection").document(bookingId).get().await()
            if (!doc.exists()) return "No booking found with ID \"$bookingId\"."

            val studentUid = doc.getString("studentUID") ?: "?"
            val facultyUid = doc.getString("facultyUID") ?: "?"
            val exam = doc.getString("examName") ?: "?"
            val date = doc.getString("sessionDateID") ?: "?"
            val start = doc.getString("startTime") ?: "?"
            val end = doc.getString("endTime") ?: "?"
            val room = doc.getString("roomName") ?: "?"
            val status = bookingStatusLabel(doc.getLong("status")?.toInt() ?: 0)
            val proctorUid = doc.getString("proctorUID").orEmpty()

            var studentName = studentUid
            var facultyName = facultyUid
            try {
                val sDoc = db.collection("UserAccounts").document(studentUid).get().await()
                studentName = sDoc.getString("thisUserName") ?: sDoc.getString("thisEmail") ?: studentUid
                val fDoc = db.collection("UserAccounts").document(facultyUid).get().await()
                facultyName = fDoc.getString("thisUserName") ?: fDoc.getString("thisEmail") ?: facultyUid
            } catch (_: Exception) { }

            buildString {
                appendLine("Booking **$bookingId**:")
                appendLine("• Exam: $exam")
                appendLine("• Student: $studentName")
                appendLine("• Faculty: $facultyName")
                appendLine("• Date: $date at $start-$end")
                appendLine("• Room: $room")
                appendLine("• Status: $status")
                if (proctorUid.isNotBlank()) appendLine("• Proctor UID: $proctorUid")
                @Suppress("UNCHECKED_CAST")
                val incidents = doc.get("incidentReports") as? List<Map<String, Any>> ?: emptyList()
                if (incidents.isNotEmpty()) append("• ${incidents.size} incident report(s)")
            }
        } catch (e: Exception) {
            "Error fetching booking details: ${e.message}"
        }
    }

    suspend fun checkinStudent(bookingId: String): String =
        secureActionService.checkinStudent(bookingId)

    suspend fun reportIncident(bookingId: String, description: String): String =
        secureActionService.reportIncident(bookingId, description)

    suspend fun markExamComplete(bookingId: String): String =
        secureActionService.markExamComplete(bookingId)

    // ── Phase 3: Enhanced Faculty Actions ────────────────────

    suspend fun cancelBooking(bookingId: String): String =
        secureActionService.cancelBooking(bookingId)

    suspend fun getMyDeferralRequests(facultyUid: String): String {
        return try {
            val snap = db.collection("Deferrals")
                .whereEqualTo("facultyUid", facultyUid)
                .get().await()

            if (snap.isEmpty) return "You have no deferral requests."

            val lines = snap.documents.map { doc ->
                val course = doc.getString("courseName") ?: "?"
                val student = doc.getString("studentEmail") ?: doc.getString("studentUid") ?: "?"
                val reason = doc.getString("reason") ?: "?"
                val status = doc.getString("status") ?: "pending"
                "• **$course** by $student — Reason: $reason — Status: $status (ID: ${doc.id})"
            }
            "Deferral requests (${lines.size}):\n${lines.joinToString("\n")}"
        } catch (e: Exception) {
            "Error fetching deferral requests: ${e.message}"
        }
    }


    suspend fun sendMessageToUser(recipientEmail: String, messageText: String): String =
        secureActionService.sendMessageToUser(recipientEmail, messageText)

    // ── Phase 4: Enhanced Admin Actions ─────────────────────

    suspend fun approveAccount(email: String): String =
        secureActionService.approveAccount(email)

    suspend fun rejectAccount(email: String): String =
        secureActionService.rejectAccount(email)

    suspend fun assignProctor(proctorEmail: String, dateID: String, roomName: String): String =
        secureActionService.assignProctor(proctorEmail, dateID, roomName)

    suspend fun getSystemStats(): String {
        return try {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val usersSnap = db.collection("UserAccounts").get().await()
            val roleCounts = mutableMapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0)
            var pendingCount = 0
            for (doc in usersSnap.documents) {
                val role = doc.getLong("thisRole")?.toInt() ?: -1
                val approval = doc.getLong("thisApprovalStatus")?.toInt() ?: 0
                if (approval == 1) roleCounts[role] = (roleCounts[role] ?: 0) + 1
                if (approval == 0) pendingCount++
            }

            val todayBookings = db.collection("BookingsCollection")
                .whereEqualTo("sessionDateID", today).get().await()
            val weekEnd = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).let {
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DAY_OF_YEAR, 7)
                it.format(cal.time)
            }
            val upcomingSessions = db.collection("SessionsCollection")
                .whereGreaterThanOrEqualTo("dateID", today)
                .whereLessThanOrEqualTo("dateID", weekEnd)
                .get().await()

            buildString {
                appendLine("📊 **System Statistics**")
                appendLine("• Active admins: ${roleCounts[0]}")
                appendLine("• Active faculty: ${roleCounts[1]}")
                appendLine("• Active proctors: ${roleCounts[2]}")
                appendLine("• Active students: ${roleCounts[3]}")
                appendLine("• Pending accounts: $pendingCount")
                appendLine("• Today's bookings: ${todayBookings.size()}")
                append("• Sessions this week: ${upcomingSessions.size()}")
            }
        } catch (e: Exception) {
            "Error fetching system stats: ${e.message}"
        }
    }

    suspend fun sendAnnouncement(message: String, targetRole: String?): String =
        secureActionService.sendAnnouncement(message, targetRole)

    suspend fun manageRoom(dateID: String, roomName: String, totalCap: Int, startTime: String, endTime: String): String =
        secureActionService.manageRoom(dateID, roomName, totalCap, startTime, endTime)

    suspend fun generateReport(startDate: String, endDate: String): String {
        return try {
            val bookingsSnap = db.collection("BookingsCollection")
                .whereGreaterThanOrEqualTo("sessionDateID", startDate)
                .whereLessThanOrEqualTo("sessionDateID", endDate)
                .get().await()

            if (bookingsSnap.isEmpty) return "No bookings found between $startDate and $endDate."

            var total = 0; var completed = 0; var incidentCount = 0
            val roomUsage = mutableMapOf<String, Int>()

            for (doc in bookingsSnap.documents) {
                total++
                val status = doc.getLong("status")?.toInt() ?: 0
                if (status >= 4) completed++
                @Suppress("UNCHECKED_CAST")
                val incidents = doc.get("incidentReports") as? List<*> ?: emptyList<Any>()
                incidentCount += incidents.size
                val room = doc.getString("roomName") ?: "Unknown"
                roomUsage[room] = (roomUsage[room] ?: 0) + 1
            }

            val completionRate = if (total > 0) (completed * 100 / total) else 0
            val topRooms = roomUsage.entries.sortedByDescending { it.value }.take(5)
                .joinToString("\n") { "  • ${it.key}: ${it.value} bookings" }

            buildString {
                appendLine("📊 **Report: $startDate to $endDate**")
                appendLine("• Total bookings: $total")
                appendLine("• Completed: $completed ($completionRate%)")
                appendLine("• Incidents: $incidentCount")
                appendLine("• Top rooms:")
                append(topRooms)
            }
        } catch (e: Exception) {
            "Error generating report: ${e.message}"
        }
    }

    // ── Phase 6: Cross-role Actions ─────────────────────────

    suspend fun getNotificationSummary(uid: String): String {
        return try {
            val snap = db.collection("UserNotifications").document(uid)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get().await()

            if (snap.isEmpty) return "You have no recent notifications."

            var unread = 0
            val lines = snap.documents.map { doc ->
                val title = doc.getString("title") ?: "Notification"
                val body = doc.getString("body") ?: ""
                val read = doc.getBoolean("read") ?: false
                if (!read) unread++
                val indicator = if (read) "  " else "🔴"
                "$indicator **$title**: $body"
            }

            buildString {
                appendLine("📬 **Notifications** ($unread unread of ${lines.size} recent):")
                append(lines.joinToString("\n"))
            }
        } catch (e: Exception) {
            "Error fetching notifications: ${e.message}"
        }
    }

    // ── Structured-data variants ────────────────────────────

    suspend fun getMyBookingsStructured(uid: String, role: Int): ActionResult {
        return try {
            val collectionName = if (role == 3) "StudentBookings" else "FacultyBookings"
            val indexDoc = db.collection(collectionName).document(uid).get().await()
            if (!indexDoc.exists()) return ActionResult("You have no bookings.")

            @Suppress("UNCHECKED_CAST")
            val bookingIds = indexDoc.get("bookings") as? List<String> ?: emptyList()
            if (bookingIds.isEmpty()) return ActionResult("You have no bookings.")

            val cards = mutableListOf<BookingCard>()
            for (id in bookingIds.takeLast(20)) {
                val doc = db.collection("BookingsCollection").document(id).get().await()
                if (!doc.exists()) continue
                val exam = doc.getString("examName") ?: "?"
                val date = doc.getString("sessionDateID") ?: "?"
                val start = doc.getString("startTime") ?: "?"
                val end = doc.getString("endTime") ?: "?"
                val room = doc.getString("roomName") ?: "?"
                val statusCode = doc.getLong("status")?.toInt() ?: 0
                val status = bookingStatusLabel(statusCode)
                cards.add(BookingCard(id, exam, date, start, end, room, status, statusCode))
            }
            if (cards.isEmpty()) return ActionResult("You have no active bookings.")
            ActionResult(
                text = "Here are your bookings (${cards.size}):",
                structuredData = StructuredData.BookingList(cards)
            )
        } catch (e: Exception) {
            ActionResult("Error fetching bookings: ${e.message}")
        }
    }

    suspend fun getBookingStatusStructured(bookingId: String): ActionResult {
        return try {
            val doc = db.collection("BookingsCollection").document(bookingId).get().await()
            if (!doc.exists()) return ActionResult("No booking found with ID \"$bookingId\".")

            val exam = doc.getString("examName") ?: "?"
            val date = doc.getString("sessionDateID") ?: "?"
            val start = doc.getString("startTime") ?: "?"
            val end = doc.getString("endTime") ?: "?"
            val room = doc.getString("roomName") ?: "?"
            val statusCode = doc.getLong("status")?.toInt() ?: 0
            val status = bookingStatusLabel(statusCode)
            val card = BookingCard(bookingId, exam, date, start, end, room, status, statusCode)
            ActionResult("Here are the details for booking $bookingId:", StructuredData.BookingDetail(card))
        } catch (e: Exception) {
            ActionResult("Error fetching booking: ${e.message}")
        }
    }

    suspend fun getBookingDetailsStructured(bookingId: String): ActionResult {
        return try {
            val doc = db.collection("BookingsCollection").document(bookingId).get().await()
            if (!doc.exists()) return ActionResult("No booking found with ID \"$bookingId\".")

            val exam = doc.getString("examName") ?: "?"
            val date = doc.getString("sessionDateID") ?: "?"
            val start = doc.getString("startTime") ?: "?"
            val end = doc.getString("endTime") ?: "?"
            val room = doc.getString("roomName") ?: "?"
            val statusCode = doc.getLong("status")?.toInt() ?: 0
            val status = bookingStatusLabel(statusCode)
            val card = BookingCard(bookingId, exam, date, start, end, room, status, statusCode)
            ActionResult("Here are the details for booking $bookingId:", StructuredData.BookingDetail(card))
        } catch (e: Exception) {
            ActionResult("Error fetching booking details: ${e.message}")
        }
    }

    suspend fun listSessionsByDateStructured(dateID: String): ActionResult {
        return try {
            val doc = db.collection("SessionsCollection").document(dateID).get().await()
            if (!doc.exists()) return ActionResult("No sessions found on $dateID.")

            @Suppress("UNCHECKED_CAST")
            val rooms = doc.get("rooms") as? Map<String, Map<String, Any>> ?: emptyMap()
            if (rooms.isEmpty()) return ActionResult("No rooms configured for $dateID.")

            val cards = mutableListOf<RoomCard>()
            for ((roomName, data) in rooms) {
                val start = data["startTime"] as? String ?: "?"
                val end = data["endTime"] as? String ?: "?"
                val totalCap = (data["totalCap"] as? Number)?.toInt() ?: 0
                @Suppress("UNCHECKED_CAST")
                val seats = (data["availableSeats"] as? List<*>)?.size ?: 0
                cards.add(RoomCard(roomName, start, end, totalCap, seats))
            }
            ActionResult("Sessions on $dateID:", StructuredData.SessionList(dateID, cards))
        } catch (e: Exception) {
            ActionResult(secureActionService.listSessionsByDate(dateID))
        }
    }

    suspend fun getMyDeferralsStructured(uid: String): ActionResult {
        return try {
            val snap = db.collection("Deferrals")
                .whereEqualTo("studentUid", uid)
                .get().await()

            if (snap.isEmpty) return ActionResult("You have no deferral requests.")

            val cards = mutableListOf<DeferralCard>()
            for (doc in snap.documents) {
                val course = doc.getString("courseName") ?: "?"
                val reason = doc.getString("reason") ?: "?"
                val status = doc.getString("status") ?: "pending"
                cards.add(DeferralCard(doc.id, course, reason, status))
            }
            ActionResult(
                text = "Here are your deferrals (${cards.size}):",
                structuredData = StructuredData.DeferralList(cards)
            )
        } catch (e: Exception) {
            ActionResult("Error fetching deferrals: ${e.message}")
        }
    }

    suspend fun getMyDeferralRequestsStructured(facultyUid: String): ActionResult {
        return try {
            val snap = db.collection("Deferrals")
                .whereEqualTo("facultyUid", facultyUid)
                .get().await()

            if (snap.isEmpty) return ActionResult("You have no deferral requests.")

            val cards = mutableListOf<DeferralCard>()
            for (doc in snap.documents) {
                val course = doc.getString("courseName") ?: "?"
                val reason = doc.getString("reason") ?: "?"
                val status = doc.getString("status") ?: "pending"
                cards.add(DeferralCard(doc.id, course, reason, status))
            }
            ActionResult(
                text = "Deferral requests (${cards.size}):",
                structuredData = StructuredData.DeferralList(cards)
            )
        } catch (e: Exception) {
            ActionResult("Error fetching deferral requests: ${e.message}")
        }
    }

    suspend fun getExamChecklistStructured(uid: String): ActionResult {
        return try {
            val indexDoc = db.collection("StudentBookings").document(uid).get().await()
            if (!indexDoc.exists()) return ActionResult("You have no bookings. Nothing to prepare for.")

            @Suppress("UNCHECKED_CAST")
            val bookingIds = indexDoc.get("bookings") as? List<String> ?: emptyList()
            if (bookingIds.isEmpty()) return ActionResult("You have no bookings.")

            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            var nextBooking: Map<String, Any?>? = null
            var nextBookingId = ""

            for (id in bookingIds) {
                val doc = db.collection("BookingsCollection").document(id).get().await()
                if (!doc.exists()) continue
                val date = doc.getString("sessionDateID") ?: continue
                if (date >= today) {
                    val status = doc.getLong("status")?.toInt() ?: 0
                    if (status in 1..3) { nextBooking = doc.data; nextBookingId = id; break }
                }
            }

            if (nextBooking == null) return ActionResult("No upcoming exams found.")

            val exam = nextBooking["examName"] as? String ?: "?"
            val date = nextBooking["sessionDateID"] as? String ?: "?"
            val start = nextBooking["startTime"] as? String ?: "?"
            val end = nextBooking["endTime"] as? String ?: "?"
            val room = nextBooking["roomName"] as? String ?: "?"
            val status = (nextBooking["status"] as? Long)?.toInt() ?: 0
            val hasDoc = !(nextBooking["examDocumentUrl"] as? String).isNullOrBlank()

            val items = listOf(
                ChecklistItem("Payment: ${if (status >= 2) "Completed" else "Pending"}", status >= 2),
                ChecklistItem("Check-in: ${if (status >= 3) "Done" else "Not yet"}", status >= 3),
                ChecklistItem("Exam document: ${if (hasDoc) "Uploaded" else "Not uploaded"}", hasDoc),
                ChecklistItem("Bring valid student ID", false)
            )

            ActionResult(
                text = "Here's your exam checklist:",
                structuredData = StructuredData.Checklist(items, exam, date, "$start - $end", room)
            )
        } catch (e: Exception) {
            ActionResult("Error building checklist: ${e.message}")
        }
    }

    suspend fun getSystemStatsStructured(): ActionResult {
        return try {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val usersSnap = db.collection("UserAccounts").get().await()
            val roleCounts = mutableMapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0)
            var pendingCount = 0
            for (doc in usersSnap.documents) {
                val role = doc.getLong("thisRole")?.toInt() ?: -1
                val approval = doc.getLong("thisApprovalStatus")?.toInt() ?: 0
                if (approval == 1) roleCounts[role] = (roleCounts[role] ?: 0) + 1
                if (approval == 0) pendingCount++
            }
            val todayBookings = db.collection("BookingsCollection")
                .whereEqualTo("sessionDateID", today).get().await()

            val stats = listOf(
                StatItem("Admins", "${roleCounts[0]}", "admin"),
                StatItem("Faculty", "${roleCounts[1]}", "school"),
                StatItem("Proctors", "${roleCounts[2]}", "person"),
                StatItem("Students", "${roleCounts[3]}", "student"),
                StatItem("Pending", "$pendingCount", "pending"),
                StatItem("Today's Bookings", "${todayBookings.size()}", "event")
            )

            ActionResult("Here are the current system stats:", StructuredData.Stats(stats))
        } catch (e: Exception) {
            ActionResult("Error fetching system stats: ${e.message}")
        }
    }

    suspend fun getNotificationSummaryStructured(uid: String): ActionResult {
        return try {
            val snap = db.collection("UserNotifications").document(uid)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get().await()

            if (snap.isEmpty) return ActionResult("You have no recent notifications.")

            var unread = 0
            val items = snap.documents.map { doc ->
                val title = doc.getString("title") ?: "Notification"
                val body = doc.getString("body") ?: ""
                val read = doc.getBoolean("read") ?: false
                if (!read) unread++
                NotificationItem(title, body, read)
            }

            ActionResult("Here are your recent notifications:", StructuredData.NotificationList(unread, items))
        } catch (e: Exception) {
            ActionResult("Error fetching notifications: ${e.message}")
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private suspend fun searchUsersInternal(email: String): List<UserMembersClass> =
        suspendCancellableCoroutine { cont ->
            admin.searchUsers(email) { users -> cont.resume(users) }
        }

    private fun statusLabel(status: Int): String = when (status) {
        0 -> "Pending"
        1 -> "Approved"
        -1 -> "Deactivated"
        -2 -> "Rejected"
        else -> "Unknown"
    }

    private fun roleLabel(role: Int): String = when (role) {
        0 -> "Admin"
        1 -> "Faculty"
        2 -> "Proctor"
        3 -> "Student"
        else -> "Unknown"
    }

    private fun bookingStatusLabel(status: Int): String = when (status) {
        0 -> "Cancelled"
        1 -> "Booked"
        2 -> "Fee Paid"
        3 -> "Checked In"
        4 -> "Exam Finished"
        5 -> "Dropped Off"
        6 -> "Completed"
        else -> "Unknown ($status)"
    }
}

