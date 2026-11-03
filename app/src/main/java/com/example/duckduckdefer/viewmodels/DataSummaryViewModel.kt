package com.example.duckduckdefer.viewmodels

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.duckduckdefer.useraccountclasses.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ──────────────────────────────────────────────────────
//  UI State
// ──────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
data class DataSummaryUiState(
    val isLoading: Boolean = true,

    // ── Global / Admin ──
    val totalSessions: Int = 0,
    val totalBookings: Int = 0,
    val totalRooms: Int = 0,
    val completedBookings: Int = 0,         // status == 6
    val cancelledBookings: Int = 0,
    val activeBookings: Int = 0,            // status 1-5
    val incidentReportCount: Int = 0,
    val totalStudents: Int = 0,
    val totalFaculty: Int = 0,
    val totalProctors: Int = 0,
    val totalAdmins: Int = 0,
    val pendingAccounts: Int = 0,
    val deactivatedAccounts: Int = 0,
    val avgCompletionRate: Float = 0f,      // 0..100

    // Bookings by month (Jan-Dec)
    val bookingsByMonth: List<Int> = List(12) { 0 },

    // Booking status breakdown
    val bookingsByStatus: Map<Int, Int> = emptyMap(), // status → count

    // Room utilisation: roomName → bookingCount
    val roomUtilisation: Map<String, Int> = emptyMap(),

    // Peak hour distribution: hourOfDay (0-23) → count
    val peakHours: Map<Int, Int> = emptyMap(),

    // Sessions by day-of-week (Mon=1..Sun=7)
    val sessionsByDayOfWeek: Map<Int, Int> = emptyMap(),

    // Top proctors by assignments
    val topProctors: List<Pair<String, Int>> = emptyList(), // name, count

    // Proctor coverage rate: rooms with proctors / total rooms
    val proctorCoverageRate: Float = 0f,

    // ── Personal (Faculty / Proctor / Student) ──
    val myTotalBookings: Int = 0,
    val myCompletedBookings: Int = 0,
    val myActiveBookings: Int = 0,
    val myBookingsByMonth: List<Int> = List(12) { 0 },
    val myBookingsByStatus: Map<Int, Int> = emptyMap(),
    val myIncidentReports: Int = 0,
    val myTotalXP: Int = 0,
    val myLevel: Int = 1,
    val myStreak: Int = 0,
    val myBadgeCount: Int = 0,
    val myProctorHours: Float = 0f,         // hours
    val myProctorAssignments: Int = 0,

    val error: String? = null
)

// ──────────────────────────────────────────────────────
//  ViewModel
// ──────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
class DataSummaryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DataSummaryUiState())
    val uiState: StateFlow<DataSummaryUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun loadSummary(user: UserMembersClass) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Parallel fetches
                val bookingsJob = launch { fetchAllBookings(user) }
                val sessionsJob = launch { fetchAllSessions() }
                val usersJob = launch { fetchUserCounts() }
                val personalJob = launch { fetchPersonalStats(user) }

                bookingsJob.join()
                sessionsJob.join()
                usersJob.join()
                personalJob.join()

                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── Bookings ──

    private suspend fun fetchAllBookings(user: UserMembersClass) {
        try {
            val snapshot = db.collection("BookingsCollection").get().await()
            val bookings = snapshot.documents.mapNotNull { doc ->
                doc.toObject(BookingsClass::class.java)
            }

            val total = bookings.size
            val completed = bookings.count { it.status == 6 }
            val active = bookings.count { it.status in 1..5 }
            val incidents = bookings.sumOf { it.incidentReports.size }

            // By month
            val byMonth = MutableList(12) { 0 }
            bookings.forEach { b ->
                try {
                    val date = LocalDate.parse(b.sessionDateID, dateFormatter)
                    val monthIdx = date.monthValue - 1
                    if (monthIdx in 0..11) byMonth[monthIdx]++
                } catch (_: Exception) {}
            }

            // By status
            val byStatus = bookings.groupBy { it.status }.mapValues { it.value.size }

            // Peak hours
            val peakHrs = mutableMapOf<Int, Int>()
            bookings.forEach { b ->
                try {
                    val hour = SessionsClass.parseTime(b.startTime).hour
                    peakHrs[hour] = (peakHrs[hour] ?: 0) + 1
                } catch (_: Exception) {}
            }

            // Room utilisation
            val roomUtil = bookings.groupBy { it.roomName }.mapValues { it.value.size }

            val completionRate = if (total > 0) (completed.toFloat() / total * 100f) else 0f

            // ── Personal bookings ──
            val myBookings = when (user.thisRole) {
                3 -> bookings.filter { it.studentUID == user.thisUID }
                1 -> bookings.filter { it.facultyUID == user.thisUID }
                2 -> bookings.filter { it.proctorUID == user.thisUID }
                else -> emptyList()
            }
            val myTotal = myBookings.size
            val myCompleted = myBookings.count { it.status == 6 }
            val myActive = myBookings.count { it.status in 1..5 }
            val myByMonth = MutableList(12) { 0 }
            myBookings.forEach { b ->
                try {
                    val date = LocalDate.parse(b.sessionDateID, dateFormatter)
                    val monthIdx = date.monthValue - 1
                    if (monthIdx in 0..11) myByMonth[monthIdx]++
                } catch (_: Exception) {}
            }
            val myByStatus = myBookings.groupBy { it.status }.mapValues { it.value.size }
            val myIncidents = myBookings.sumOf { it.incidentReports.size }

            _uiState.update {
                it.copy(
                    totalBookings = total,
                    completedBookings = completed,
                    activeBookings = active,
                    incidentReportCount = incidents,
                    bookingsByMonth = byMonth,
                    bookingsByStatus = byStatus,
                    peakHours = peakHrs,
                    roomUtilisation = roomUtil,
                    avgCompletionRate = completionRate,
                    myTotalBookings = myTotal,
                    myCompletedBookings = myCompleted,
                    myActiveBookings = myActive,
                    myBookingsByMonth = myByMonth,
                    myBookingsByStatus = myByStatus,
                    myIncidentReports = myIncidents
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Bookings: ${e.message}") }
        }
    }

    // ── Sessions ──

    private suspend fun fetchAllSessions() {
        try {
            val snapshot = db.collection("SessionsCollection").get().await()
            val sessions = snapshot.documents.mapNotNull { doc ->
                doc.toObject(SessionsClass::class.java)
            }

            val totalSessions = sessions.size
            var totalRooms = 0
            var roomsWithProctors = 0
            val proctorCounts = mutableMapOf<String, Int>()
            val dayOfWeekCounts = mutableMapOf<Int, Int>()
            var totalProctorMinutes = 0f

            sessions.forEach { session ->
                session.rooms.values.forEach { room ->
                    totalRooms++
                    if (room.assignedProctors.isNotEmpty()) roomsWithProctors++
                    room.assignedProctors.forEach { uid ->
                        proctorCounts[uid] = (proctorCounts[uid] ?: 0) + 1
                        // Estimate hours from room times
                        try {
                            val start = SessionsClass.parseTime(room.startTime)
                            val end = SessionsClass.parseTime(room.endTime)
                            totalProctorMinutes += java.time.Duration.between(start, end).toMinutes()
                        } catch (_: Exception) {}
                    }
                }

                try {
                    val date = LocalDate.parse(session.dateID, dateFormatter)
                    val dow = date.dayOfWeek.value
                    dayOfWeekCounts[dow] = (dayOfWeekCounts[dow] ?: 0) + 1
                } catch (_: Exception) {}
            }

            val coverageRate = if (totalRooms > 0) roomsWithProctors.toFloat() / totalRooms * 100f else 0f

            // Resolve top proctors (max 5)
            val topProctorUIDs = proctorCounts.entries.sortedByDescending { it.value }.take(5)
            val topProctorsList = mutableListOf<Pair<String, Int>>()
            for ((uid, count) in topProctorUIDs) {
                try {
                    val doc = db.collection("UserAccounts").document(uid).get().await()
                    val name = doc.getString("thisUserName") ?: uid.take(8)
                    topProctorsList.add(name to count)
                } catch (_: Exception) {
                    topProctorsList.add(uid.take(8) to count)
                }
            }

            _uiState.update {
                it.copy(
                    totalSessions = totalSessions,
                    totalRooms = totalRooms,
                    sessionsByDayOfWeek = dayOfWeekCounts,
                    proctorCoverageRate = coverageRate,
                    topProctors = topProctorsList
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Sessions: ${e.message}") }
        }
    }

    // ── User Counts ──

    private suspend fun fetchUserCounts() {
        try {
            val snapshot = db.collection("UserAccounts").get().await()
            var students = 0; var faculty = 0; var proctors = 0; var admins = 0
            var pending = 0; var deactivated = 0

            snapshot.documents.forEach { doc ->
                when (doc.getLong("thisRole")?.toInt()) {
                    0 -> admins++
                    1 -> faculty++
                    2 -> proctors++
                    3 -> students++
                }
                when (doc.getLong("thisApprovalStatus")?.toInt()) {
                    0 -> pending++
                    -1 -> deactivated++
                }
            }

            _uiState.update {
                it.copy(
                    totalStudents = students,
                    totalFaculty = faculty,
                    totalProctors = proctors,
                    totalAdmins = admins,
                    pendingAccounts = pending,
                    deactivatedAccounts = deactivated
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Users: ${e.message}") }
        }
    }

    // ── Personal Stats ──

    private suspend fun fetchPersonalStats(user: UserMembersClass) {
        _uiState.update {
            it.copy(
                myTotalXP = user.experience,
                myLevel = user.level,
                myStreak = user.dailyStreak,
                myBadgeCount = user.badges.size
            )
        }

        // If proctor or faculty, get proctor assignments
        if (user.thisRole == 1 || user.thisRole == 2) {
            try {
                val doc = db.collection("ProctorAvailability").document(user.thisUID).get().await()
                val assignments = doc.get("assignments") as? List<Map<String, String>> ?: emptyList()
                var totalMinutes = 0f
                assignments.forEach { a ->
                    try {
                        val start = SessionsClass.parseTime(a["startTime"] ?: "")
                        val end = SessionsClass.parseTime(a["endTime"] ?: "")
                        totalMinutes += java.time.Duration.between(start, end).toMinutes()
                    } catch (_: Exception) {}
                }
                _uiState.update {
                    it.copy(
                        myProctorAssignments = assignments.size,
                        myProctorHours = totalMinutes / 60f
                    )
                }
            } catch (_: Exception) {}
        }
    }
}


