package com.example.duckduckdefer.useraccountclasses

import com.google.firebase.firestore.FirebaseFirestore

// Admin inherits from Users
open class AdminSubclass() : UserMembersClass(
    thisUID = "0", // User ID in Authentication Database, and Document ID in Firestore User Accounts Database
    thisEmail = "doej@mymacewan.ca",
    thisUserName = "User",
    thisRole = 0,
    thisApprovalStatus = 0, // 0 = Pending, 1 = Approved, -1 = Deactivated, -2 = Rejected
) {

    fun getPendingAccounts(onComplete: (List<String>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("PendingAccounts")
            .document("0")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val list = document.get("uids") as? List<String> ?: emptyList()
                    onComplete(list)
                } else {
                    onComplete(emptyList())
                }
            }
            .addOnFailureListener {
                onComplete(emptyList())
            }
    }

    fun searchUsers(query: String, onComplete: (List<UserMembersClass>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val results = mutableMapOf<String, UserMembersClass>()
        var queriesFinished = 0

        fun checkFinished() {
            queriesFinished++
            if (queriesFinished == 2) {
                onComplete(results.values.toList())
            }
        }

        // Prefix match on email
        db.collection("UserAccounts")
            .whereGreaterThanOrEqualTo("thisEmail", query)
            .whereLessThanOrEqualTo("thisEmail", query + "")
            .get()
            .addOnSuccessListener { snapshots ->
                snapshots.documents.forEach { doc ->
                    doc.toObject(UserMembersClass::class.java)?.let {
                        it.thisUID = doc.id
                        results[doc.id] = it
                    }
                }
                checkFinished()
            }
            .addOnFailureListener { checkFinished() }

        // Prefix match on username
        db.collection("UserAccounts")
            .whereGreaterThanOrEqualTo("thisUserName", query)
            .whereLessThanOrEqualTo("thisUserName", query + "")
            .get()
            .addOnSuccessListener { snapshots ->
                snapshots.documents.forEach { doc ->
                    doc.toObject(UserMembersClass::class.java)?.let {
                        it.thisUID = doc.id
                        results[doc.id] = it
                    }
                }
                checkFinished()
            }
            .addOnFailureListener { checkFinished() }
    }

    fun getUsersByRole(role: Int, onComplete: (List<UserMembersClass>) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .whereEqualTo("thisRole", role)
            .get()
            .addOnSuccessListener { snapshots ->
                val users = snapshots.documents.mapNotNull { it.toObject(UserMembersClass::class.java)?.apply { thisUID = it.id } }
                onComplete(users)
            }
            .addOnFailureListener { onComplete(emptyList()) }
    }

    fun getPotentialProctors(onComplete: (List<UserMembersClass>) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .whereIn("thisRole", listOf(1, 2))
            .get()
            .addOnSuccessListener { snapshots ->
                val users = snapshots.documents.mapNotNull { it.toObject(UserMembersClass::class.java)?.apply { thisUID = it.id } }
                onComplete(users)
            }
            .addOnFailureListener { onComplete(emptyList()) }
    }

    fun approveAccount(account: UserMembersClass, onComplete: (Boolean) -> Unit) {
        // Update local object data
        account.thisApprovalStatus = 1
        // Update firebase data
        account.updateApprovalStatus() { success ->
            if (success) {
                // Send notification to user
                DatabaseL.removePending(account.thisUID) { _ -> }
                // Gamification: Award admin XP for approving an account
                if (thisUID != "0") {
                    UserMembersClass.awardExperience(thisUID, 10)
                }
                onComplete(true)
            }
        }
    }

    fun rejectAccount(account: UserMembersClass, onComplete: (Boolean) -> Unit) {
        account.thisApprovalStatus = -2
        account.updateApprovalStatus() { success ->
            if (success) {
                // Send notification to user
                DatabaseL.removePending(account.thisUID) { _ -> }
                onComplete(true)
            }
        }
    }

    fun deactivateAccount(account: UserMembersClass, onComplete: (Boolean) -> Unit) {
        account.thisApprovalStatus = -1
        account.updateApprovalStatus() { success ->
            if (success) {
                // Send notification to user
                onComplete(true)
            }
        }
    }

    fun reactivateAccount(account: UserMembersClass, onComplete: (Boolean) -> Unit) {
        account.thisApprovalStatus = 1
        account.updateApprovalStatus() { success ->
            if (success) {
                // Send notification to user
                onComplete(true)
            }
        }
    }

    fun changeUserRole(account: UserMembersClass, newRole: Int, onComplete: (Boolean) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .document(account.thisUID)
            .update("thisRole", newRole)
            .addOnSuccessListener {
                account.thisRole = newRole
                onComplete(true)
            }
            .addOnFailureListener { onComplete(false) }
    }

    fun createNewSession(
        dateID: String,
        roomName: String,
        startTime: String,
        endTime: String,
        totalCap: Int,
        onComplete: (SessionsClass?) -> Unit
    ) {
        val newSession = SessionsClass()
        newSession.addRoomToSession(
            dateID = dateID,
            roomName = roomName,
            startTime = startTime,
            endTime = endTime,
            totalCapacity = totalCap
        ) { success ->
            if (success == true) {
                // Gamification: Award admin XP for creating a session
                if (thisUID != "0") {
                    UserMembersClass.awardExperience(thisUID, 15)
                }
                onComplete(newSession)
            } else {
                onComplete(null)
            }
        }
    }

    fun bookSession(
        examName: String,
        roomName: String,
        dateID: String,
        studentUID: String,
        facultyUID: String,
        startTime: String,
        endTime: String,
        specialInstructions: String = "",
        onComplete: (Boolean) -> Unit
    ) {
        val booking = BookingsClass()
        booking.checkStudentConflict(studentUID, dateID, startTime, endTime) { conflict ->
            if (conflict) {
                onComplete(false)
            } else {
                booking.createBooking(
                    studentUID = studentUID,
                    facultyUID = facultyUID,
                    examName = examName,
                    roomName = roomName,
                    startTime = startTime,
                    endTime = endTime,
                    sessionDateID = dateID,
                    specialInstructions = specialInstructions,
                    onComplete = { success ->
                        if (success) {
                            // Gamification: Award admin XP for booking a session
                            if (thisUID != "0") {
                                UserMembersClass.awardExperience(thisUID, 10)
                            }
                        }
                        onComplete(success)
                    }
                )
            }
        }
    }

    fun checkProctorAvailability(
        proctorUID: String,
        dateID: String,
        startTime: String,
        endTime: String,
        onComplete: (Boolean) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        db.collection("ProctorAvailability").document(proctorUID).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onComplete(false)
                    return@addOnSuccessListener
                }

                val availabilityRaw = doc.get("availability") as? List<*> ?: emptyList<Any>()
                val roomStart = SessionsClass.parseTime(startTime)
                val roomEnd = SessionsClass.parseTime(endTime)

                // Check if any availability slot for this date covers the room's time range
                val isAvailable = availabilityRaw.any { item ->
                    val map = item as? Map<*, *> ?: return@any false
                    if (map["date"] == dateID) {
                        val s = map["startTime"] as? String ?: return@any false
                        val e = map["endTime"] as? String ?: return@any false
                        val availStart = SessionsClass.parseTime(s)
                        val availEnd = SessionsClass.parseTime(e)

                        // Proctor must be available for the ENTIRE room duration
                        !availStart.isAfter(roomStart) && !availEnd.isBefore(roomEnd)
                    } else false
                }
                onComplete(isAvailable)
            }
            .addOnFailureListener { onComplete(false) }
    }

    fun assignProctor(
        dateID: String,
        roomName: String,
        proctorUID: String,
        onComplete: (Boolean) -> Unit
    ) {
        SessionsClass().assignProctorToRoom(dateID, roomName, proctorUID) { success ->
            if (success) {
                // Gamification: Award admin XP for assigning a proctor
                if (thisUID != "0") {
                    UserMembersClass.awardExperience(thisUID, 5)
                }
            }
            onComplete(success)
        }
    }

    fun removeProctor(
        dateID: String,
        roomName: String,
        proctorUID: String,
        onComplete: (Boolean) -> Unit
    ) {
        SessionsClass().removeProctorFromRoom(dateID, roomName, proctorUID, onComplete)
    }

    fun clearAssignmentsForDay(dateID: String, onComplete: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val sessionRef = db.collection("SessionsCollection").document(dateID)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(sessionRef)
            if (!snapshot.exists()) return@runTransaction false

            val session = snapshot.toObject(SessionsClass::class.java) ?: return@runTransaction false
            
            // For each room, collect all proctors to remove their assignment entries
            val proctorToRooms = mutableMapOf<String, MutableList<String>>()
            session.rooms.values.forEach { room ->
                room.assignedProctors.forEach { pUid ->
                    proctorToRooms.getOrPut(pUid) { mutableListOf() }.add(room.roomName)
                }
                room.assignedProctors.clear()
            }

            // Update session
            transaction.set(sessionRef, session)

            // Update each proctor's assignments
            proctorToRooms.forEach { (pUid, roomNames) ->
                val proctorRef = db.collection("ProctorAvailability").document(pUid)
                val proctorSnap = transaction.get(proctorRef)
                if (proctorSnap.exists()) {
                    val assignments = proctorSnap.get("assignments") as? List<Map<String, String>> ?: emptyList()
                    val updatedAssignments = assignments.filter { 
                        it["date"] != dateID || it["roomName"] !in roomNames 
                    }
                    transaction.update(proctorRef, "assignments", updatedAssignments)
                }
            }
            true
        }.addOnSuccessListener { onComplete(it as Boolean) }
         .addOnFailureListener { onComplete(false) }
    }

    fun autoAssignProctors(dateID: String, onComplete: (Boolean) -> Unit) {
        ProctorAssignment().runAutoAssignment(dateID, onComplete)
    }
}
