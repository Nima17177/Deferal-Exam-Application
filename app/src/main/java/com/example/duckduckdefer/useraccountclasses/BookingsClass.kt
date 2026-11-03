package com.example.duckduckdefer.useraccountclasses
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class IncidentReport(
    val reporterUID: String = "",
    val reporterName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val message: String = "",
    val imageUrl: String? = null,
    val visibleToStudent: Boolean = false
)

data class ActivityLog(
    val actorName: String = "",
    val action: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class BookingsClass(
    var bookingID: String = "",
    var studentUID: String = "",
    var facultyUID: String = "",
    var proctorUID: String = "",
    var examName: String = "",
    var roomName: String = "",
    var startTime: String = "",
    var endTime: String = "",
    var sessionDateID: String = "",
    var examDocumentUrl: String? = null,
    var examDocumentName: String? = null,
    var specialInstructions: String = "",
    var status: Int = 1, // 1: Booked, 2: Fee Paid, 3: Student Checked In, 4: Exam Finished, 5: Exam Dropped Off, 6: Faculty Received/Completed
    var incidentReports: List<IncidentReport> = emptyList(),
    var activityLog: List<ActivityLog> = emptyList()
) {

    fun getBooking(bookingID: String, onComplete: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()

        db.collection("BookingsCollection")
            .document(bookingID)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Populate the object with data from Firestore
                    this.bookingID = document.getString("bookingID") ?: ""
                    this.studentUID = document.getString("studentUID") ?: ""
                    this.facultyUID = document.getString("facultyUID") ?: ""
                    this.proctorUID = document.getString("proctorUID") ?: ""
                    this.examName = document.getString("examName") ?: ""
                    this.roomName = document.getString("roomName") ?: ""
                    this.startTime = document.getString("startTime") ?: ""
                    this.endTime = document.getString("endTime") ?: ""
                    this.sessionDateID = document.getString("sessionDateID") ?: ""
                    this.examDocumentUrl = document.getString("examDocumentUrl")
                    this.examDocumentName = document.getString("examDocumentName")
                    this.specialInstructions = document.getString("specialInstructions") ?: ""
                    this.status = document.getLong("status")?.toInt() ?: 1
                    
                    this.incidentReports = (document.get("incidentReports") as? List<Map<String, Any>>)?.map {
                        IncidentReport(
                            reporterUID = it["reporterUID"] as? String ?: "",
                            reporterName = it["reporterName"] as? String ?: "",
                            timestamp = it["timestamp"] as? Long ?: 0L,
                            message = it["message"] as? String ?: "",
                            imageUrl = it["imageUrl"] as? String,
                            visibleToStudent = it["visibleToStudent"] as? Boolean ?: false
                        )
                    } ?: emptyList()

                    this.activityLog = (document.get("activityLog") as? List<Map<String, Any>>)?.map {
                        ActivityLog(
                            actorName = it["actorName"] as? String ?: "",
                            action = it["action"] as? String ?: "",
                            timestamp = it["timestamp"] as? Long ?: 0L
                        )
                    } ?: emptyList()

                    onComplete(true)
                } else {
                    onComplete(false)
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                onComplete(false)
            }
    }

    private fun parseTime(time: String): LocalTime {
        val formats = listOf("HH:mm", "h:mm a", "H:mm")
        for (format in formats) {
            try {
                return LocalTime.parse(time, DateTimeFormatter.ofPattern(format))
            } catch (e: Exception) {}
        }
        return try { LocalTime.parse(time) } catch (e: Exception) { LocalTime.MIDNIGHT }
    }

    fun checkStudentConflict(
        studentUID: String,
        sessionDateID: String,
        startTime: String,
        endTime: String,
        excludeBookingID: String? = null,
        onComplete: (Boolean) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        db.collection("BookingsCollection")
            .whereEqualTo("studentUID", studentUID)
            .whereEqualTo("sessionDateID", sessionDateID)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val bookings = querySnapshot.toObjects(BookingsClass::class.java)
                val newStart = parseTime(startTime)
                val newEnd = parseTime(endTime)

                val conflict = bookings.any { existing ->
                    if (excludeBookingID != null && existing.bookingID == excludeBookingID) return@any false
                    val existingStart = parseTime(existing.startTime)
                    val existingEnd = parseTime(existing.endTime)
                    newStart.isBefore(existingEnd) && newEnd.isAfter(existingStart)
                }
                onComplete(conflict)
            }
            .addOnFailureListener {
                onComplete(false)
            }
    }

    fun createBooking(
        studentUID: String,
        facultyUID: String,
        proctorUID: String = "",
        examName: String,
        roomName: String,
        startTime: String,
        endTime: String,
        sessionDateID: String,
        examDocumentUrl: String? = null,
        examDocumentName: String? = null,
        specialInstructions: String = "",
        onComplete: (Boolean) -> Unit
    ) {
        if (this.bookingID.isEmpty()) {
            this.bookingID = java.util.UUID.randomUUID().toString()
        }
        this.studentUID = studentUID
        this.facultyUID = facultyUID
        this.proctorUID = proctorUID
        this.examName = examName
        this.roomName = roomName
        this.startTime = startTime
        this.endTime = endTime
        this.sessionDateID = sessionDateID
        this.examDocumentUrl = examDocumentUrl
        this.examDocumentName = examDocumentName
        this.specialInstructions = specialInstructions
        this.status = 1
        this.incidentReports = emptyList()
        this.activityLog = listOf(ActivityLog("System", "Booking Created"))

        val db = FirebaseFirestore.getInstance()
        val sessionRef = db.collection("SessionsCollection").document(sessionDateID)
        val bookingRef = db.collection("BookingsCollection").document(this.bookingID)
        val facultyRef = db.collection("FacultyBookings").document(facultyUID)
        val studentRef = db.collection("StudentBookings").document(studentUID)

        // Attempt transaction with retry logic for GMS initialization issues
        attemptCreateBookingTransaction(db, sessionRef, bookingRef, facultyRef, studentRef, 0, onComplete)
    }

    private fun attemptCreateBookingTransaction(
        db: FirebaseFirestore,
        sessionRef: com.google.firebase.firestore.DocumentReference,
        bookingRef: com.google.firebase.firestore.DocumentReference,
        facultyRef: com.google.firebase.firestore.DocumentReference,
        studentRef: com.google.firebase.firestore.DocumentReference,
        retryCount: Int,
        onComplete: (Boolean) -> Unit
    ) {
        try {
            db.runTransaction { transaction ->
                val sessionSnap = transaction.get(sessionRef)
                if (!sessionSnap.exists()) throw Exception("Session ${this.sessionDateID} does not exist")
                val session = sessionSnap.toObject(SessionsClass::class.java) ?: throw Exception("Failed to parse session")
                val room = session.rooms[roomName] ?: throw Exception("Room $roomName does not exist in this session")

                val roomStart = parseTime(room.startTime)
                val bookStart = parseTime(startTime)
                val bookEnd = parseTime(endTime)
                
                val startIdx = (Duration.between(roomStart, bookStart).toMinutes() / 30).toInt()
                val endIdx = (Duration.between(roomStart, bookEnd).toMinutes() / 30).toInt()

                for (i in startIdx until endIdx) {
                    if (i in room.availableSeats.indices) {
                        if (room.availableSeats[i] <= 0) {
                            throw Exception("No seats available in $roomName at the requested time.")
                        }
                    }
                }

                for (i in startIdx until endIdx) {
                    if (i in room.availableSeats.indices) {
                        room.availableSeats[i] -= 1
                    }
                }
                if (!room.bookings.contains(this.bookingID)) {
                    room.bookings.add(this.bookingID)
                }
                session.rooms[roomName] = room

                val bookingData = hashMapOf(
                    "bookingID" to this.bookingID,
                    "studentUID" to this.studentUID,
                    "facultyUID" to this.facultyUID,
                    "proctorUID" to this.proctorUID,
                    "examName" to this.examName,
                    "roomName" to this.roomName,
                    "startTime" to this.startTime,
                    "endTime" to this.endTime,
                    "sessionDateID" to this.sessionDateID,
                    "examDocumentUrl" to this.examDocumentUrl,
                    "examDocumentName" to this.examDocumentName,
                    "specialInstructions" to this.specialInstructions,
                    "status" to this.status,
                    "incidentReports" to this.incidentReports,
                    "activityLog" to this.activityLog
                )
                
                val bookingUpdate = hashMapOf("bookings" to FieldValue.arrayUnion(this.bookingID))

                transaction.set(sessionRef, session)
                transaction.set(bookingRef, bookingData)
                transaction.set(facultyRef, bookingUpdate, SetOptions.merge())
                transaction.set(studentRef, bookingUpdate, SetOptions.merge())

                true
            }.addOnSuccessListener {
                onComplete(true)
            }.addOnFailureListener { e ->
                handleBookingError(e, db, sessionRef, bookingRef, facultyRef, studentRef, retryCount, onComplete)
            }
        } catch (e: Throwable) {
            // Catch any synchronous exceptions (e.g., GMS initialization errors)
            Log.e("BookingsClass", "Synchronous error in transaction setup (attempt ${retryCount + 1}): ${e.message}", e)
            handleBookingError(e, db, sessionRef, bookingRef, facultyRef, studentRef, retryCount, onComplete)
        }
    }

    private fun handleBookingError(
        error: Throwable,
        db: FirebaseFirestore,
        sessionRef: com.google.firebase.firestore.DocumentReference,
        bookingRef: com.google.firebase.firestore.DocumentReference,
        facultyRef: com.google.firebase.firestore.DocumentReference,
        studentRef: com.google.firebase.firestore.DocumentReference,
        retryCount: Int,
        onComplete: (Boolean) -> Unit
    ) {
        val errorMessage = error.message ?: error.toString()
        Log.e("BookingsClass", "createBooking failed (attempt ${retryCount + 1}): $errorMessage")
        
        // GMS broker error pattern matching - MORE AGGRESSIVE
        val isGMSBrokerError = errorMessage.contains("Unknown calling package", ignoreCase = true) ||
                               errorMessage.contains("SecurityException", ignoreCase = true) ||
                               errorMessage.contains("GMS", ignoreCase = true) ||
                               errorMessage.contains("broker", ignoreCase = true) ||
                               errorMessage.contains("calling package", ignoreCase = true) ||
                               errorMessage.contains("Failed to get service", ignoreCase = true)
        
        // Check if we should retry (increased to 8 retries for GMS broker errors)
        if (isGMSBrokerError && retryCount < 8) {
            val delayMs = 300L * (retryCount + 1)  // Exponential backoff: 300ms, 600ms, 900ms, etc.
            Log.w("BookingsClass", "GMS broker error detected (attempt ${retryCount + 1}), retrying in ${delayMs}ms...")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                attemptCreateBookingTransaction(db, sessionRef, bookingRef, facultyRef, studentRef, retryCount + 1, onComplete)
            }, delayMs)
        } else if (!isGMSBrokerError && retryCount < 3) {
            // For non-GMS errors, retry fewer times
            val delayMs = 500L * (retryCount + 1)
            Log.w("BookingsClass", "Transient error detected, retrying in ${delayMs}ms...")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                attemptCreateBookingTransaction(db, sessionRef, bookingRef, facultyRef, studentRef, retryCount + 1, onComplete)
            }, delayMs)
        } else {
            Log.e("BookingsClass", "All retries exhausted. Booking failed.")
            onComplete(false)
        }
    }

    fun updateBooking(onComplete: (Boolean) -> Unit) {
        if (bookingID.isEmpty()) {
            onComplete(false)
            return
        }
        val db = FirebaseFirestore.getInstance()
        val bookingData = hashMapOf(
            "bookingID" to this.bookingID,
            "studentUID" to this.studentUID,
            "facultyUID" to this.facultyUID,
            "proctorUID" to this.proctorUID,
            "examName" to this.examName,
            "roomName" to this.roomName,
            "startTime" to this.startTime,
            "endTime" to this.endTime,
            "sessionDateID" to this.sessionDateID,
            "examDocumentUrl" to this.examDocumentUrl,
            "examDocumentName" to this.examDocumentName,
            "specialInstructions" to this.specialInstructions,
            "status" to this.status,
            "incidentReports" to this.incidentReports,
            "activityLog" to this.activityLog
        )
        db.collection("BookingsCollection").document(this.bookingID)
            .set(bookingData, SetOptions.merge())
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun deleteBooking(onComplete: (Boolean) -> Unit) {
        if (bookingID.isEmpty()) {
            onComplete(false)
            return
        }
        val db = FirebaseFirestore.getInstance()

        SessionsClass().removeBookingFromRoom(sessionDateID, roomName, this) { _ ->
            val batch = db.batch()
            val bookingRef = db.collection("BookingsCollection").document(bookingID)
            val facultyRef = db.collection("FacultyBookings").document(facultyUID)
            val studentRef = db.collection("StudentBookings").document(studentUID)

            val bookingUpdate = hashMapOf("bookings" to FieldValue.arrayRemove(bookingID))

            batch.delete(bookingRef)
            batch.set(facultyRef, bookingUpdate, SetOptions.merge())
            batch.set(studentRef, bookingUpdate, SetOptions.merge())

            batch.commit()
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        }
    }

    fun updateBookingFields(
        studentUID: String,
        facultyUID: String,
        proctorUID: String? = null,
        examName: String,
        roomName: String? = null,
        startTime: String? = null,
        endTime: String? = null,
        sessionDateID: String? = null,
        examDocumentUrl: String? = null,
        examDocumentName: String? = null,
        specialInstructions: String? = null,
        status: Int? = null,
        incidentReports: List<IncidentReport>? = null,
        activityLog: List<ActivityLog>? = null,
        onComplete: (Boolean) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()

        db.collection("BookingsCollection")
            .whereEqualTo("studentUID", studentUID)
            .whereEqualTo("facultyUID", facultyUID)
            .whereEqualTo("examName", examName)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val docRef = db.collection("BookingsCollection").document(document.id)

                    val updates = hashMapOf<String, Any>()
                    proctorUID?.let { updates["proctorUID"] = it }
                    roomName?.let { updates["roomName"] = it }
                    startTime?.let { updates["startTime"] = it }
                    endTime?.let { updates["endTime"] = it }
                    sessionDateID?.let { updates["sessionDateID"] = it }
                    examDocumentUrl?.let { updates["examDocumentUrl"] = it }
                    examDocumentName?.let { updates["examDocumentName"] = it }
                    specialInstructions?.let { updates["specialInstructions"] = it }
                    status?.let { updates["status"] = it }
                    incidentReports?.let { updates["incidentReports"] = it }
                    activityLog?.let { updates["activityLog"] = it }

                    if (updates.isEmpty()) {
                        onComplete(true)
                        return@addOnSuccessListener
                    }

                    docRef.update(updates)
                        .addOnSuccessListener {
                            proctorUID?.let { this.proctorUID = it }
                            roomName?.let { this.roomName = it }
                            startTime?.let { this.startTime = it }
                            endTime?.let { this.endTime = it }
                            sessionDateID?.let { this.sessionDateID = it }
                            examDocumentUrl?.let { this.examDocumentUrl = it }
                            examDocumentName?.let { this.examDocumentName = it }
                            specialInstructions?.let { this.specialInstructions = it }
                            status?.let { this.status = it }
                            incidentReports?.let { this.incidentReports = it }
                            activityLog?.let { this.activityLog = it }
                            onComplete(true)
                        }
                        .addOnFailureListener { e ->
                            e.printStackTrace()
                            onComplete(false)
                        }
                } else {
                    onComplete(false)
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                onComplete(false)
            }
    }
}
