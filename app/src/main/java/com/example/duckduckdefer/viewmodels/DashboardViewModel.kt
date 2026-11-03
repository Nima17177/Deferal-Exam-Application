package com.example.duckduckdefer.viewmodels

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.duckduckdefer.useraccountclasses.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
data class DashboardUiState(
    val isLoading: Boolean = true,
    val weekSessions: List<SessionsClass> = emptyList(),
    val userBookings: List<String> = emptyList(),
    val pendingUsers: List<UserMembersClass> = emptyList(),
    val topUsers: List<UserMembersClass> = emptyList(),
    val error: String? = null,

    // ── Snapshot stats for the "My Activity" widget ──
    val snapshotLoaded: Boolean = false,
    val myTotalBookings: Int = 0,
    val myCompletedBookings: Int = 0,
    val myActiveBookings: Int = 0,
    val myBookingsByMonth: List<Int> = List(12) { 0 },
    // Admin-only
    val totalBookingsAll: Int = 0,
    val completedBookingsAll: Int = 0,
    val totalUsersAll: Int = 0,
    val proctorCoverageRate: Float = 0f,
    // Proctor
    val myProctorAssignments: Int = 0,
    val myProctorHours: Float = 0f
)

@RequiresApi(Build.VERSION_CODES.O)
class DashboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun loadDashboardData(user: UserMembersClass) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val today = LocalDate.now()
            val startOfWeek = today.with(DayOfWeek.MONDAY).format(formatter)
            val endOfWeek = today.with(DayOfWeek.SUNDAY).format(formatter)

            // 1. Fetch Leaderboard Data (Top 5 for Dashboard)
            fetchLeaderboard()

            // 2. Fetch Pending Users if Admin
            if (user is AdminSubclass && user.thisRole == 0) {
                fetchPendingUsers(user)
            }

            // 3. Fetch Bookings and Sessions
            val onBookingsLoaded: (List<String>) -> Unit = { bookings ->
                SessionsClass().getSessionsInRange(startOfWeek, endOfWeek) { sessions ->
                    _uiState.update { 
                        it.copy(
                            userBookings = bookings,
                            weekSessions = sessions,
                            isLoading = false
                        )
                    }
                }
            }

            when (user) {
                is StudentSubclass -> user.getMyBookings(onBookingsLoaded)
                is FacultySubclass -> user.getMyBookings(onBookingsLoaded)
                else -> onBookingsLoaded(emptyList())
            }

            // 4. Fetch lightweight snapshot stats (non-blocking)
            launch { fetchSnapshotStats(user) }
        }
    }

    private fun fetchLeaderboard() {
        FirebaseFirestore.getInstance().collection("UserAccounts")
            .orderBy("experience", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserMembersClass::class.java)?.apply {
                        thisUID = doc.id
                    }
                }
                _uiState.update { it.copy(topUsers = users) }
            }
            .addOnFailureListener { e ->
                _uiState.update { it.copy(error = "Failed to load leaderboard: ${e.message}") }
            }
    }

    private fun fetchPendingUsers(admin: AdminSubclass) {
        admin.getPendingAccounts { uids ->
            if (uids.isEmpty()) {
                _uiState.update { it.copy(pendingUsers = emptyList()) }
                return@getPendingAccounts
            }
            
            val list = mutableListOf<UserMembersClass>()
            var count = uids.size
            uids.forEach { uid ->
                val u = UserMembersClass()
                u.getUserData(uid) { success ->
                    if (success) list.add(u)
                    count--
                    if (count <= 0) {
                        _uiState.update { it.copy(pendingUsers = list) }
                    }
                }
            }
        }
    }

    private suspend fun fetchSnapshotStats(user: UserMembersClass) {
        val db = FirebaseFirestore.getInstance()
        try {
            val bookingsSnap = db.collection("BookingsCollection").get().await()
            val allBookings = bookingsSnap.documents.mapNotNull { it.toObject(BookingsClass::class.java) }

            val totalAll = allBookings.size
            val completedAll = allBookings.count { it.status == 6 }

            // Per-month for current user
            val myBookings = when (user.thisRole) {
                3 -> allBookings.filter { it.studentUID == user.thisUID }
                1 -> allBookings.filter { it.facultyUID == user.thisUID }
                2 -> allBookings.filter { it.proctorUID == user.thisUID }
                else -> allBookings // admin sees all
            }
            val myTotal = myBookings.size
            val myCompleted = myBookings.count { it.status == 6 }
            val myActive = myBookings.count { it.status in 1..5 }

            val byMonth = MutableList(12) { 0 }
            myBookings.forEach { b ->
                try {
                    val date = LocalDate.parse(b.sessionDateID, formatter)
                    val idx = date.monthValue - 1
                    if (idx in 0..11) byMonth[idx]++
                } catch (_: Exception) {}
            }

            // Admin extras
            var totalUsers = 0
            var coverageRate = 0f
            if (user.thisRole == 0) {
                try {
                    val usersSnap = db.collection("UserAccounts").get().await()
                    totalUsers = usersSnap.size()
                } catch (_: Exception) {}
                try {
                    val sessSnap = db.collection("SessionsCollection").get().await()
                    val sessions = sessSnap.documents.mapNotNull { it.toObject(SessionsClass::class.java) }
                    var rooms = 0; var covered = 0
                    sessions.forEach { s -> s.rooms.values.forEach { r ->
                        rooms++
                        if (r.assignedProctors.isNotEmpty()) covered++
                    } }
                    coverageRate = if (rooms > 0) covered.toFloat() / rooms * 100f else 0f
                } catch (_: Exception) {}
            }

            // Proctor extras
            var proctorAssignments = 0; var proctorHours = 0f
            if (user.thisRole == 1 || user.thisRole == 2) {
                try {
                    val doc = db.collection("ProctorAvailability").document(user.thisUID).get().await()
                    @Suppress("UNCHECKED_CAST")
                    val assignments = doc.get("assignments") as? List<Map<String, String>> ?: emptyList()
                    proctorAssignments = assignments.size
                    assignments.forEach { a ->
                        try {
                            val start = SessionsClass.parseTime(a["startTime"] ?: "")
                            val end = SessionsClass.parseTime(a["endTime"] ?: "")
                            proctorHours += java.time.Duration.between(start, end).toMinutes() / 60f
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }

            _uiState.update {
                it.copy(
                    snapshotLoaded = true,
                    myTotalBookings = myTotal,
                    myCompletedBookings = myCompleted,
                    myActiveBookings = myActive,
                    myBookingsByMonth = byMonth,
                    totalBookingsAll = totalAll,
                    completedBookingsAll = completedAll,
                    totalUsersAll = totalUsers,
                    proctorCoverageRate = coverageRate,
                    myProctorAssignments = proctorAssignments,
                    myProctorHours = proctorHours
                )
            }
        } catch (_: Exception) {
            // Snapshot is best-effort; don't fail the dashboard
        }
    }

    fun refresh(user: UserMembersClass) {
        loadDashboardData(user)
    }
}
