package com.example.duckduckdefer.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.duckduckdefer.useraccountclasses.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class LiveBookingViewModel : ViewModel() {
    var booking by mutableStateOf<BookingsClass?>(null)
    var studentObj by mutableStateOf<UserMembersClass?>(null)
    var facultyObj by mutableStateOf<UserMembersClass?>(null)
    var proctorsWithTimes by mutableStateOf<List<Pair<UserMembersClass, String>>>(emptyList())
    var isLoading by mutableStateOf(true)
    
    private var listenerRegistration: ListenerRegistration? = null
    private var sessionListenerRegistration: ListenerRegistration? = null
    private var lastListenedDate: String = ""
    private var lastListenedRoom: String = ""

    fun startListening(bookingID: String) {
        if (listenerRegistration != null) {
            listenerRegistration?.remove()
            listenerRegistration = null
        }
        
        isLoading = true
        val db = FirebaseFirestore.getInstance()
        listenerRegistration = db.collection("BookingsCollection")
            .document(bookingID)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    isLoading = false
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val b = snapshot.toObject(BookingsClass::class.java) ?: BookingsClass()
                    b.bookingID = snapshot.id
                    
                    // Manually map incident reports
                    val reportsData = snapshot.get("incidentReports") as? List<Map<String, Any>>
                    b.incidentReports = reportsData?.map {
                        IncidentReport(
                            reporterUID = it["reporterUID"] as? String ?: "",
                            reporterName = it["reporterName"] as? String ?: "",
                            timestamp = it["timestamp"] as? Long ?: 0L,
                            message = it["message"] as? String ?: "",
                            imageUrl = it["imageUrl"] as? String,
                            visibleToStudent = it["visibleToStudent"] as? Boolean ?: false
                        )
                    } ?: emptyList()

                    // Manually map activity log
                    val logData = snapshot.get("activityLog") as? List<Map<String, Any>>
                    b.activityLog = logData?.map {
                        ActivityLog(
                            actorName = it["actorName"] as? String ?: "",
                            action = it["action"] as? String ?: "",
                            timestamp = it["timestamp"] as? Long ?: 0L
                        )
                    } ?: emptyList()
                    
                    booking = b

                    // Fetch student and faculty data
                    if (studentObj == null || studentObj?.thisUID != b.studentUID) {
                        val s = UserMembersClass()
                        s.getUserData(b.studentUID) { if (it) studentObj = s }
                    }
                    if (facultyObj == null || facultyObj?.thisUID != b.facultyUID) {
                        val f = UserMembersClass()
                        f.getUserData(b.facultyUID) { if (it) facultyObj = f }
                    }

                    // Handle Proctor Assignment
                    if (b.proctorUID.isNotEmpty()) {
                        // Directly assigned to booking, stop session listener if any
                        sessionListenerRegistration?.remove()
                        sessionListenerRegistration = null
                        val p = UserMembersClass()
                        p.getUserData(b.proctorUID) { success ->
                            if (success) {
                                FacultySubclass(thisUID = b.proctorUID).getAssignmentForRoom(b.sessionDateID, b.roomName) { assignment ->
                                    val timeStr = if (assignment != null) "${assignment.first} - ${assignment.second}" else "${b.startTime} - ${b.endTime}"
                                    proctorsWithTimes = listOf(p to timeStr)
                                }
                            }
                        }
                    } else if (b.sessionDateID.isNotEmpty() && b.roomName.isNotEmpty()) {
                        // Fallback to session proctor
                        if (sessionListenerRegistration == null || lastListenedDate != b.sessionDateID || lastListenedRoom != b.roomName) {
                            listenToSession(b.sessionDateID, b.roomName)
                        }
                    } else {
                        proctorsWithTimes = emptyList()
                    }
                } else {
                    booking = null
                }
                isLoading = false
            }
    }

    private fun listenToSession(dateID: String, roomName: String) {
        sessionListenerRegistration?.remove()
        lastListenedDate = dateID
        lastListenedRoom = roomName
        
        val db = FirebaseFirestore.getInstance()
        sessionListenerRegistration = db.collection("SessionsCollection")
            .document(dateID)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val session = snapshot.toObject(SessionsClass::class.java)
                    val room = session?.rooms?.get(roomName)
                    val pUIDs = room?.assignedProctors ?: emptyList()
                    
                    if (pUIDs.isNotEmpty()) {
                        val loadedProctors = mutableListOf<Pair<UserMembersClass, String>>()
                        var loadedCount = 0
                        pUIDs.forEach { uid ->
                            val p = UserMembersClass()
                            p.getUserData(uid) { success ->
                                if (success) {
                                    FacultySubclass(thisUID = uid).getAssignmentForRoom(dateID, roomName) { assignment ->
                                        val timeStr = if (assignment != null) "${assignment.first} - ${assignment.second}" else "N/A"
                                        loadedProctors.add(p to timeStr)
                                        loadedCount++
                                        if (loadedCount == pUIDs.size) proctorsWithTimes = loadedProctors
                                    }
                                } else {
                                    loadedCount++
                                    if (loadedCount == pUIDs.size) proctorsWithTimes = loadedProctors
                                }
                            }
                        }
                    } else {
                        proctorsWithTimes = emptyList()
                    }
                } else {
                    proctorsWithTimes = emptyList()
                }
            }
    }

    fun updateStatus(newStatus: Int, actorName: String, onComplete: (Boolean) -> Unit) {
        booking?.let { b ->
            val statusNames = mapOf(
                1 to "Booked",
                2 to "Fee Paid",
                3 to "Student Checked In",
                4 to "Exam Finished",
                5 to "Exam Dropped Off",
                6 to "Faculty Received/Completed"
            )
            
            val newAction = "Status changed to ${statusNames[newStatus] ?: "Unknown"}"
            val newLogEntry = ActivityLog(actorName, newAction)
            val updatedLog = b.activityLog + newLogEntry
            
            val db = FirebaseFirestore.getInstance()
            db.collection("BookingsCollection").document(b.bookingID)
                .update(
                    "status", newStatus,
                    "activityLog", updatedLog
                )
                .addOnSuccessListener {
                    // Gamification: Award XP for reaching key statuses
                    when (newStatus) {
                        3 -> {
                            UserMembersClass.awardExperience(b.studentUID, 10) // Student check-in
                            if (b.proctorUID.isNotEmpty()) {
                                UserMembersClass.awardExperience(b.proctorUID, 5) // Proctor performed check-in
                            }
                        }
                        4 -> UserMembersClass.awardExperience(b.studentUID, 50) // Finishing exam
                        5 -> {
                            if (b.proctorUID.isNotEmpty()) {
                                UserMembersClass.awardExperience(b.proctorUID, 10) // Proctor drop-off
                            }
                        }
                        6 -> {
                            UserMembersClass.awardExperience(b.facultyUID, 20) // Faculty completing
                            if (b.proctorUID.isNotEmpty()) {
                                UserMembersClass.awardExperience(b.proctorUID, 30) // Proctor completing
                            }
                        }
                    }
                    onComplete(true)
                }
                .addOnFailureListener { onComplete(false) }
        } ?: onComplete(false)
    }

    fun revertStatus(actorName: String, onComplete: (Boolean) -> Unit) {
        booking?.let { b ->
            if (b.status > 1) {
                val newStatus = b.status - 1
                val newLogEntry = ActivityLog(actorName, "Status reverted to ${newStatus}")
                val updatedLog = b.activityLog + newLogEntry
                
                val db = FirebaseFirestore.getInstance()
                db.collection("BookingsCollection").document(b.bookingID)
                    .update(
                        "status", newStatus,
                        "activityLog", updatedLog
                    )
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { onComplete(false) }
            } else {
                onComplete(false)
            }
        } ?: onComplete(false)
    }

    fun addIncidentReport(report: IncidentReport, onComplete: (Boolean) -> Unit) {
        booking?.let { b ->
            val updatedReports = b.incidentReports + report
            val db = FirebaseFirestore.getInstance()
            db.collection("BookingsCollection").document(b.bookingID)
                .update("incidentReports", updatedReports)
                .addOnSuccessListener {
                    // Gamification: Award XP for filing an incident report
                    if (report.reporterUID.isNotEmpty()) {
                        UserMembersClass.awardExperience(report.reporterUID, 15)
                    }
                    onComplete(true)
                }
                .addOnFailureListener { onComplete(false) }
        } ?: onComplete(false)
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
        sessionListenerRegistration?.remove()
    }
}
