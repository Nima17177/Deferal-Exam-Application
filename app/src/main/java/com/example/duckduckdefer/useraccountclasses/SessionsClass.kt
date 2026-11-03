package com.example.duckduckdefer.useraccountclasses

import com.example.duckduckdefer.components.DayStats
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import java.time.format.DateTimeFormatter

data class RoomData(
    var roomName: String = "",
    var startTime: String = "",
    var endTime: String = "",
    var totalCap: Int = 0,
    var availableSeats: MutableList<Int> = mutableListOf(),
    var bookings: MutableList<String> = mutableListOf(), // Booking document IDs
    var assignedProctors: MutableList<String> = mutableListOf() // Proctor UIDs
)

data class SessionsClass(
    var dateID: String = "",
    var rooms: MutableMap<String, RoomData> = mutableMapOf()
) {
    companion object {
        fun parseTime(time: String): LocalTime {
            val formats = listOf("HH:mm", "h:mm a", "H:mm", "h:mma")
            for (format in formats) {
                try {
                    return LocalTime.parse(time, DateTimeFormatter.ofPattern(format))
                } catch (e: Exception) {}
            }
            return try { LocalTime.parse(time) } catch (e: Exception) { LocalTime.MIDNIGHT }
        }

        fun formatTime(time: String): String {
            if (time.isBlank()) return ""
            val parsed = parseTime(time)
            return parsed.format(DateTimeFormatter.ofPattern("h:mm a"))
        }
    }

    /**
     * Unified Logic to calculate calendar dots (Weekly Stats).
     * This ensures consistency across Admin, Student, and Faculty dashboards.
     */
    fun calculateDayStats(
        date: LocalDate,
        userUID: String,
        userRole: Int,
        userBookings: List<String>
    ): DayStats {
        val stats = DayStats(date)
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        if (this.dateID == dateStr) {
            rooms.values.forEach { room ->
                // Blue dot: Session exists and has capacity
                if (room.totalCap > 0) stats.available++
                
                // Green dot: User has a booking in this room
                val myBookingsCount = room.bookings.count { it in userBookings }
                if (myBookingsCount > 0) stats.booked += myBookingsCount
                
                // Purple dot: User is proctoring this room
                if (room.assignedProctors.contains(userUID)) stats.proctored++

                // Red dot (Admin only): Any room has zero proctors
                if (userRole == 0 && room.assignedProctors.isEmpty()) {
                    stats.needsAttention = true
                }
            }
        }
        return stats
    }

    fun getSession(dateID: String, onComplete: (Boolean) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("SessionsCollection")
            .document(dateID)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val session = document.toObject(SessionsClass::class.java)
                    if (session != null) {
                        this.dateID = session.dateID
                        this.rooms = session.rooms
                        onComplete(true)
                    } else {
                        this.dateID = ""
                        this.rooms = mutableMapOf()
                        onComplete(false)
                    }
                } else {
                    this.dateID = ""
                    this.rooms = mutableMapOf()
                    onComplete(false)
                }
            }
            .addOnFailureListener {
                onComplete(false)
            }
    }

    fun getSessionsInRange(startDate: String, endDate: String, onComplete: (List<SessionsClass>) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("SessionsCollection")
            .whereGreaterThanOrEqualTo("dateID", startDate)
            .whereLessThanOrEqualTo("dateID", endDate)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val sessions = querySnapshot.toObjects(SessionsClass::class.java)
                onComplete(sessions)
            }
            .addOnFailureListener {
                onComplete(emptyList())
            }
    }

    fun addRoomToSession(
        dateID: String,
        roomName: String,
        startTime: String,
        endTime: String,
        totalCapacity: Int,
        onComplete: (Boolean?) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val sessionRef = db.collection("SessionsCollection").document(dateID)

        val roomData = createRoomData(roomName, startTime, endTime, totalCapacity)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(sessionRef)
            val roomsMap: MutableMap<String, RoomData> =
                if (snapshot.exists()) {
                    val existingRooms = snapshot.toObject(SessionsClass::class.java)?.rooms ?: emptyMap()
                    existingRooms.toMutableMap()
                } else {
                    mutableMapOf()
                }

            if (roomsMap.containsKey(roomName)) {
                return@runTransaction null
            }

            roomsMap[roomData.roomName] = roomData
            val updatedSession = SessionsClass(dateID = dateID, rooms = roomsMap)
            transaction.set(sessionRef, updatedSession)
            true
        }
            .addOnSuccessListener { result -> onComplete(result) }
            .addOnFailureListener { onComplete(false) }
    }

    fun updateRoomInSession(
        dateID: String,
        oldRoomName: String,
        newRoomName: String,
        newStartTime: String,
        newEndTime: String,
        newTotalCapacity: Int,
        onComplete: (Boolean) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val sessionRef = db.collection("SessionsCollection").document(dateID)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(sessionRef)
            if (!snapshot.exists()) throw Exception("Session does not exist")

            val session = snapshot.toObject(SessionsClass::class.java)
                ?: throw Exception("Failed to parse session")

            val roomsMap = session.rooms.toMutableMap()
            val existingRoomData = roomsMap[oldRoomName] ?: throw Exception("Room $oldRoomName does not exist")

            val diff = newTotalCapacity - existingRoomData.totalCap
            var updatedAvailableSeats = existingRoomData.availableSeats.map { 
                (it + diff).coerceAtLeast(0) 
            }.toMutableList()

            // Handle time range changes
            if (existingRoomData.startTime != newStartTime || existingRoomData.endTime != newEndTime) {
                val oldStart = parseTime(existingRoomData.startTime)
                val newStart = parseTime(newStartTime)
                val oldEnd = parseTime(existingRoomData.endTime)
                val newEnd = parseTime(newEndTime)

                // Adjust slots at the beginning
                val startOffset = (Duration.between(newStart, oldStart).toMinutes() / 30).toInt()
                if (startOffset > 0) {
                    repeat(startOffset) { updatedAvailableSeats.add(0, newTotalCapacity) }
                } else if (startOffset < 0) {
                    repeat(-startOffset) { if (updatedAvailableSeats.isNotEmpty()) updatedAvailableSeats.removeAt(0) }
                }

                // Adjust slots at the end
                val endDiff = (Duration.between(oldEnd, newEnd).toMinutes() / 30).toInt()
                if (endDiff > 0) {
                    repeat(endDiff) { updatedAvailableSeats.add(newTotalCapacity) }
                } else if (endDiff < 0) {
                    repeat(-endDiff) { if (updatedAvailableSeats.isNotEmpty()) updatedAvailableSeats.removeAt(updatedAvailableSeats.size - 1) }
                }
            }

            val updatedRoomData = existingRoomData.copy(
                roomName = newRoomName,
                startTime = newStartTime,
                endTime = newEndTime,
                totalCap = newTotalCapacity,
                availableSeats = updatedAvailableSeats
            )
            if (oldRoomName != newRoomName) {
                roomsMap.remove(oldRoomName)
            }
            roomsMap[newRoomName] = updatedRoomData

            val updatedSession = session.copy(rooms = roomsMap)
            transaction.set(sessionRef, updatedSession)
        }
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                onComplete(false)
            }
    }

    fun createRoomData(
        roomName: String,
        startTime: String,
        endTime: String,
        totalCapacity: Int
    ): RoomData {
        val start = parseTime(startTime)
        val end = parseTime(endTime)
        val numSlots = (Duration.between(start, end).toMinutes() / 30).toInt().coerceAtLeast(0)

        return RoomData(
            roomName = roomName,
            startTime = startTime,
            endTime = endTime,
            totalCap = totalCapacity,
            availableSeats = MutableList(numSlots) { totalCapacity },
            bookings = mutableListOf(),
            assignedProctors = mutableListOf()
        )
    }

    fun addBookingToRoom(
        dateID: String,
        roomName: String,
        booking: BookingsClass,
        onComplete: (Boolean) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val sessionRef = db.collection("SessionsCollection").document(dateID)
        attemptAddBookingToRoomTransaction(db, sessionRef, roomName, booking, 0, onComplete)
    }

    private fun attemptAddBookingToRoomTransaction(
        db: FirebaseFirestore,
        sessionRef: com.google.firebase.firestore.DocumentReference,
        roomName: String,
        booking: BookingsClass,
        retryCount: Int,
        onComplete: (Boolean) -> Unit
    ) {
        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(sessionRef)
                if (!snapshot.exists()) throw Exception("Session does not exist")

                val session = snapshot.toObject(SessionsClass::class.java)
                    ?: throw Exception("Failed to parse session")

                val room = session.rooms[roomName]
                    ?: throw Exception("Room $roomName does not exist in this session")

                if (!room.bookings.contains(booking.bookingID)) {
                    val roomStart = parseTime(room.startTime)
                    val bookStart = parseTime(booking.startTime)
                    val bookEnd = parseTime(booking.endTime)

                    val startIdx = (Duration.between(roomStart, bookStart).toMinutes() / 30).toInt()
                    val endIdx = (Duration.between(roomStart, bookEnd).toMinutes() / 30).toInt()

                    // Validate availability first
                    for (i in startIdx until endIdx) {
                        if (i in room.availableSeats.indices) {
                            if (room.availableSeats[i] <= 0) {
                                throw Exception("No seats available in $roomName at the requested time.")
                            }
                        }
                    }

                    // Apply the booking
                    for (i in startIdx until endIdx) {
                        if (i in room.availableSeats.indices) {
                            room.availableSeats[i] -= 1
                        }
                    }
                    room.bookings.add(booking.bookingID)
                }

                session.rooms[roomName] = room
                transaction.set(sessionRef, session)
            }
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { e ->
                    handleSessionBookingError(e, db, sessionRef, roomName, booking, retryCount, onComplete)
                }
        } catch (e: Throwable) {
            // Catch any synchronous exceptions (e.g., GMS initialization errors)
            android.util.Log.e("SessionsClass", "Synchronous error in transaction setup (attempt ${retryCount + 1}): ${e.message}", e)
            handleSessionBookingError(e, db, sessionRef, roomName, booking, retryCount, onComplete)
        }
    }

    private fun handleSessionBookingError(
        error: Throwable,
        db: FirebaseFirestore,
        sessionRef: com.google.firebase.firestore.DocumentReference,
        roomName: String,
        booking: BookingsClass,
        retryCount: Int,
        onComplete: (Boolean) -> Unit
    ) {
        val errorMessage = error.message ?: error.toString()
        android.util.Log.e("SessionsClass", "addBookingToRoom failed (attempt ${retryCount + 1}): $errorMessage")
        
        // Check for GMS-related errors and retry
        if ((errorMessage.contains("Unknown calling package", ignoreCase = true) || 
             errorMessage.contains("SecurityException", ignoreCase = true) ||
             errorMessage.contains("GMS", ignoreCase = true) ||
             errorMessage.contains("broker", ignoreCase = true)) && retryCount < 3) {
            android.util.Log.w("SessionsClass", "GMS initialization error detected, retrying in ${500L * (retryCount + 1)}ms...")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                attemptAddBookingToRoomTransaction(db, sessionRef, roomName, booking, retryCount + 1, onComplete)
            }, 500L * (retryCount + 1))
        } else {
            onComplete(false)
        }
    }

    fun removeBookingFromRoom(
        dateID: String,
        roomName: String,
        booking: BookingsClass,
        onComplete: (Boolean) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val sessionRef = db.collection("SessionsCollection").document(dateID)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(sessionRef)
            if (!snapshot.exists()) return@runTransaction false

            val session = snapshot.toObject(SessionsClass::class.java) ?: return@runTransaction false
            val room = session.rooms[roomName] ?: return@runTransaction false

            if (room.bookings.contains(booking.bookingID)) {
                room.bookings.remove(booking.bookingID)

                val roomStart = parseTime(room.startTime)
                val bookStart = parseTime(booking.startTime)
                val bookEnd = parseTime(booking.endTime)

                val startIdx = (Duration.between(roomStart, bookStart).toMinutes() / 30).toInt()
                val endIdx = (Duration.between(roomStart, bookEnd).toMinutes() / 30).toInt()

                for (i in startIdx until endIdx) {
                    if (i in room.availableSeats.indices) {
                        room.availableSeats[i] = (room.availableSeats[i] + 1).coerceAtMost(room.totalCap)
                    }
                }

                session.rooms[roomName] = room
                transaction.set(sessionRef, session)
                true
            } else {
                false
            }
        }
            .addOnSuccessListener { onComplete(it as Boolean) }
            .addOnFailureListener { onComplete(false) }
    }

    fun assignProctorToRoom(
        dateID: String,
        roomName: String,
        proctorUID: String,
        onComplete: (Boolean) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val sessionRef = db.collection("SessionsCollection").document(dateID)
        val proctorRef = db.collection("ProctorAvailability").document(proctorUID)

        db.runTransaction { transaction ->
            // READS
            val sessionSnapshot = transaction.get(sessionRef)
            val proctorSnapshot = transaction.get(proctorRef)
            
            if (!sessionSnapshot.exists()) throw Exception("Session does not exist")

            val session = sessionSnapshot.toObject(SessionsClass::class.java)
                ?: throw Exception("Failed to parse session")

            val room = session.rooms[roomName]
                ?: throw Exception("Room $roomName does not exist in this session")

            // WRITES
            if (!room.assignedProctors.contains(proctorUID)) {
                room.assignedProctors.add(proctorUID)
                
                // Update Session
                session.rooms[roomName] = room
                transaction.set(sessionRef, session)
                
                // Update ProctorAvailability
                val assignmentMap = mapOf(
                    "date" to dateID,
                    "startTime" to room.startTime,
                    "endTime" to room.endTime,
                    "roomName" to roomName
                )
                if (proctorSnapshot.exists()) {
                    transaction.update(proctorRef, "assignments", FieldValue.arrayUnion(assignmentMap))
                } else {
                    transaction.set(proctorRef, mapOf("assignments" to listOf(assignmentMap)))
                }
            }
        }
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun removeProctorFromRoom(
        dateID: String,
        roomName: String,
        proctorUID: String,
        onComplete: (Boolean) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val sessionRef = db.collection("SessionsCollection").document(dateID)
        val proctorRef = db.collection("ProctorAvailability").document(proctorUID)

        db.runTransaction { transaction ->
            // 1. ALL READS FIRST
            val sessionSnapshot = transaction.get(sessionRef)
            val proctorSnapshot = transaction.get(proctorRef)

            if (!sessionSnapshot.exists()) return@runTransaction false

            val session = sessionSnapshot.toObject(SessionsClass::class.java) ?: return@runTransaction false
            val room = session.rooms[roomName] ?: return@runTransaction false

            if (room.assignedProctors.contains(proctorUID)) {
                // 2. ALL WRITES LAST
                room.assignedProctors.remove(proctorUID)
                
                // Update Session
                session.rooms[roomName] = room
                transaction.set(sessionRef, session)
                
                // Remove from ProctorAvailability assignments
                if (proctorSnapshot.exists()) {
                    val list = proctorSnapshot.get("assignments") as? List<Map<String, String>> ?: emptyList()
                    val toRemove = list.find {
                        it["date"] == dateID && it["roomName"] == roomName 
                    }
                    if (toRemove != null) {
                        transaction.update(proctorRef, "assignments", FieldValue.arrayRemove(toRemove))
                    }
                }
                true
            } else {
                false
            }
        }
            .addOnSuccessListener { onComplete(it as Boolean) }
            .addOnFailureListener { onComplete(false) }
    }

    fun deleteSession(dateID: String, onComplete: (Boolean) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("SessionsCollection")
            .document(dateID)
            .delete()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun deleteRoomFromSession(dateID: String, roomName: String, onComplete: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val sessionRef = db.collection("SessionsCollection").document(dateID)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(sessionRef)
            if (snapshot.exists()) {
                val session = snapshot.toObject(SessionsClass::class.java)
                session?.let {
                    if (it.rooms.containsKey(roomName)) {
                        it.rooms.remove(roomName)
                        transaction.set(sessionRef, it)
                    }
                }
            }
        }
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }
}
