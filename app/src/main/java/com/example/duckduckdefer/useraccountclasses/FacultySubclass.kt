package com.example.duckduckdefer.useraccountclasses

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

// Faculty inherits from Users
open class FacultySubclass(
    thisUID: String = "0",
    thisEmail: String = "doej@mymacewan.ca",
    thisUserName: String = "User",
    thisRole: Int = 1,
    thisApprovalStatus: Int = 0,
) : UserMembersClass(
    thisUID = thisUID,
    thisEmail = thisEmail,
    thisUserName = thisUserName,
    thisRole = thisRole,
    thisApprovalStatus = thisApprovalStatus
) {

    constructor() : this(
        thisUID = "0",
        thisEmail = "doej@mymacewan.ca",
        thisUserName = "User",
        thisRole = 1,
        thisApprovalStatus = 0
    )

    /**
     * Allows a faculty member to book a room for an exam session.
     */
    fun bookSession(
        examName: String,
        roomName: String,
        dateID: String,
        studentUID: String,
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
                    facultyUID = this.thisUID,
                    examName = examName,
                    roomName = roomName,
                    startTime = startTime,
                    endTime = endTime,
                    sessionDateID = dateID,
                    specialInstructions = specialInstructions,
                    onComplete = onComplete
                )
            }
        }
    }

    fun confirmFeePaid(bookingID: String, onComplete: (Boolean) -> Unit) {
        updateBookingStatus(bookingID, 2, "Fee paid", onComplete)
    }

    fun confirmStudentAttendance(bookingID: String, onComplete: (Boolean) -> Unit) {
        updateBookingStatus(bookingID, 3, "Student checked in", onComplete)
    }

    fun confirmExamFinished(bookingID: String, onComplete: (Boolean) -> Unit) {
        updateBookingStatus(bookingID, 4, "Exam finished", onComplete)
    }

    fun confirmExamDroppedOff(bookingID: String, onComplete: (Boolean) -> Unit) {
        updateBookingStatus(bookingID, 5, "Exam dropped off at faculty office", onComplete)
    }

    fun confirmExamReceived(bookingID: String, onComplete: (Boolean) -> Unit) {
        updateBookingStatus(bookingID, 6, "Faculty confirmed exam receipt/completion", onComplete)
    }

    private fun updateBookingStatus(bookingID: String, newStatus: Int, actionDescription: String, onComplete: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val logEntry = ActivityLog(this.thisUserName, actionDescription)
        
        db.collection("BookingsCollection").document(bookingID)
            .update(
                "status", newStatus,
                "activityLog", FieldValue.arrayUnion(logEntry)
            )
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    /**
     * Retrieves all bookings made by this faculty member.
     */
    fun getMyBookings(onComplete: (List<String>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("FacultyBookings")
            .document(thisUID)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val list = document.get("bookings") as? List<String> ?: emptyList()
                    onComplete(list)
                } else {
                    onComplete(emptyList())
                }
            }
            .addOnFailureListener {
                onComplete(emptyList())
            }
    }

    fun setAvailability(dateID: String, ranges: List<Pair<String, String>>, onComplete: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("ProctorAvailability").document(thisUID)

        val newRanges = ranges.map {
            mapOf("date" to dateID, "startTime" to it.first, "endTime" to it.second)
        }

        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentList = (snapshot.get("availability") as? List<Map<String, String>>)?.toMutableList() ?: mutableListOf()

            // Remove old entries for this date
            val updatedList = currentList.filter { it["date"] != dateID }.toMutableList()
            updatedList.addAll(newRanges)

            if (snapshot.exists()) {
                transaction.update(docRef, "availability", updatedList)
            } else {
                transaction.set(docRef, mapOf("availability" to updatedList))
            }
            null
        }.addOnSuccessListener {
            // Gamification: Award XP for setting availability (only when adding ranges, not clearing)
            if (ranges.isNotEmpty() && thisUID != "0") {
                UserMembersClass.awardExperience(thisUID, 5)
            }
            onComplete(true)
        }
            .addOnFailureListener { onComplete(false) }
    }

    fun getAvailability(dateID: String, onComplete: (List<Pair<String, String>>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("ProctorAvailability")
            .document(thisUID)
            .get()
            .addOnSuccessListener { document ->
                val list = (document.get("availability") as? List<Map<String, String>>) ?: emptyList()
                val ranges = list.filter { it["date"] == dateID }.mapNotNull { entry ->
                    val start = entry["startTime"]
                    val end = entry["endTime"]
                    if (start != null && end != null) start to end else null
                }
                onComplete(ranges)
            }
            .addOnFailureListener { onComplete(emptyList()) }
    }

    fun getAssignmentForRoom(dateID: String, roomName: String, onComplete: (Pair<String, String>?) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("ProctorAvailability")
            .document(thisUID)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val list = (document.get("assignments") as? List<Map<String, String>>) ?: emptyList()
                    val assignment = list.find { it["date"] == dateID && it["roomName"] == roomName }
                    if (assignment != null) {
                        val start = assignment["startTime"] ?: ""
                        val end = assignment["endTime"] ?: ""
                        onComplete(start to end)
                    } else {
                        onComplete(null)
                    }
                } else {
                    onComplete(null)
                }
            }
            .addOnFailureListener { onComplete(null) }
    }

    /**
     * Allows a faculty member to edit existing deferrals/bookings.
     * Returns list of booking IDs that can be edited by this faculty member.
     */
    fun getEditableDeferrals(onComplete: (List<String>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("FacultyBookings")
            .document(thisUID)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val bookings = document.get("bookings") as? List<String> ?: emptyList()
                    // Filter to only recently created/editable bookings (within 7 days)
                    onComplete(bookings)
                } else {
                    onComplete(emptyList())
                }
            }
            .addOnFailureListener {
                onComplete(emptyList())
            }
    }

    /**
     * Cancels a deferral. Must be called by the faculty member who created the booking.
     */
    fun cancelDeferral(bookingID: String, reason: String = "Cancelled by faculty", onComplete: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val logEntry = ActivityLog(this.thisUserName, "Deferral cancelled: $reason")
        
        db.collection("BookingsCollection").document(bookingID)
            .update(
                "status", -1, // -1 indicates cancelled
                "activityLog", FieldValue.arrayUnion(logEntry),
                "cancelledBy", this.thisUID,
                "cancelledReason", reason
            )
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    /**
     * Toggles whether this faculty member also acts as a proctor.
     * If toggling to proctor, adds them to available proctors list.
     */
    fun toggleProctorRole(isBecomingProctor: Boolean, onComplete: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val userDocRef = db.collection("UserAccounts").document(thisUID)

        try {
            if (isBecomingProctor) {
                // Add to proctors when they want to be a proctor
                userDocRef.update("thisRole", 2) // 2 = Proctor
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { onComplete(false) }
            } else {
                // Change back to faculty only
                userDocRef.update("thisRole", 1) // 1 = Faculty
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { onComplete(false) }
            }
        } catch (e: Exception) {
            onComplete(false)
        }
    }

    /**
     * Gets current proctor status for this faculty member
     */
    fun getProctorStatus(onComplete: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("UserAccounts").document(thisUID)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getLong("thisRole")?.toInt() ?: 1
                    onComplete(role == 2) // true if they are a proctor (role 2)
                } else {
                    onComplete(false)
                }
            }
            .addOnFailureListener { onComplete(false) }
    }
}
