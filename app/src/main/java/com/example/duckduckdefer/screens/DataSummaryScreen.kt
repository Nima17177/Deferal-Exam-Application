package com.example.duckduckdefer.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.duckduckdefer.components.*
import com.example.duckduckdefer.ui.theme.*
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import com.example.duckduckdefer.viewmodels.DataSummaryUiState
import com.example.duckduckdefer.viewmodels.DataSummaryViewModel
import java.time.LocalDate

// ──────────────────────────────────────────────────────────
//  Top-level Screen
// ──────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSummaryScreen(
    user: UserMembersClass,
    onBack: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    viewModel: DataSummaryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(user) { viewModel.loadSummary(user) }

    val yearLabel = LocalDate.now().year.toString()

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ) {
                val heroContent = DuckGradients.heroContentColor()
                val heroContentSecondary = DuckGradients.heroContentColorSecondary()
                Box(
                    modifier = Modifier
                        .background(DuckGradients.heroGradient(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .statusBarsPadding()
                ) {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "My Summary",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                Text(
                                    text = "Year in Review $yearLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = heroContentSecondary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = heroContent
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = onOpenMenu) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Open Menu",
                                    tint = heroContent
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Crunching the numbers…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // ── Personal Card (everyone gets this) ──
                PersonalYearCard(user, uiState)

                // ── Role-specific sections ──
                when (user.thisRole) {
                    0 -> AdminSummaryContent(uiState)
                    1 -> FacultySummaryContent(uiState)
                    2 -> ProctorSummaryContent(uiState)
                    3 -> StudentSummaryContent(uiState)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  PERSONAL "YEAR CARD" — shown for every role
// ══════════════════════════════════════════════════════════

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PersonalYearCard(user: UserMembersClass, state: DataSummaryUiState) {
    val heroContent = DuckGradients.heroContentColor()
    val heroContentSecondary = DuckGradients.heroContentColorSecondary()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DuckGradients.heroGradient(), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(user.profilePictureUrl, user.thisUserName, size = 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("${user.thisUserName}'s Year", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = heroContent)
                        Text(
                            text = roleName(user.thisRole) + " · Level ${user.level}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = heroContentSecondary
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniHeroStat("XP Earned", state.myTotalXP.toString(), Icons.Default.AutoAwesome)
                    MiniHeroStat("Level", state.myLevel.toString(), Icons.Default.TrendingUp)
                    MiniHeroStat("Streak", if (state.myStreak > 0) "${state.myStreak} 🔥" else "0", Icons.Default.Whatshot)
                    MiniHeroStat("Badges", state.myBadgeCount.toString(), Icons.Default.MilitaryTech)
                }

                if (state.myTotalBookings > 0) {
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MiniHeroStat("Bookings", state.myTotalBookings.toString(), Icons.Default.Book)
                        MiniHeroStat("Completed", state.myCompletedBookings.toString(), Icons.Default.CheckCircle)
                        if (state.myIncidentReports > 0) {
                            MiniHeroStat("Incidents", state.myIncidentReports.toString(), Icons.Default.ReportProblem)
                        }
                    }
                }

                // Proctor hours for Faculty/Proctor
                if ((user.thisRole == 1 || user.thisRole == 2) && state.myProctorAssignments > 0) {
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MiniHeroStat("Assignments", state.myProctorAssignments.toString(), Icons.Default.Badge)
                        MiniHeroStat("Hours", String.format("%.1f", state.myProctorHours), Icons.Default.Schedule)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniHeroStat(label: String, value: String, icon: ImageVector) {
    val heroContent = DuckGradients.heroContentColor()
    val heroContentSecondary = DuckGradients.heroContentColorSecondary()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = heroContent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = heroContentSecondary)
    }
}

// ══════════════════════════════════════════════════════════
//  ADMIN SUMMARY — university-report ready
// ══════════════════════════════════════════════════════════

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun AdminSummaryContent(state: DataSummaryUiState) {

    // ─── 1. Key Performance Indicators ───
    SummarySection("📊 Key Performance Indicators") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KPICard("Total Sessions", state.totalSessions.toString(), Icons.Default.EventNote, Color(0xFF4CAF50), Modifier.weight(1f))
            KPICard("Total Bookings", state.totalBookings.toString(), Icons.Default.Book, Color(0xFF2196F3), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KPICard("Rooms Created", state.totalRooms.toString(), Icons.Default.MeetingRoom, Color(0xFF9C27B0), Modifier.weight(1f))
            KPICard("Incidents", state.incidentReportCount.toString(), Icons.Default.ReportProblem, Color(0xFFFF5722), Modifier.weight(1f))
        }
    }

    // ─── 2. Completion Rate Ring ───
    SummarySection("✅ Booking Completion Rate") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedRingStat(
                label = "Completed",
                value = state.avgCompletionRate,
                maxValue = 100f,
                color = Color(0xFF4CAF50),
                size = 100.dp,
                strokeWidth = 12.dp
            )
            AnimatedRingStat(
                label = "Active",
                value = state.activeBookings.toFloat(),
                maxValue = state.totalBookings.toFloat().coerceAtLeast(1f),
                color = Color(0xFF2196F3),
                size = 100.dp,
                strokeWidth = 12.dp,
                displayText = state.activeBookings.toString()
            )
            AnimatedRingStat(
                label = "Incidents",
                value = state.incidentReportCount.toFloat(),
                maxValue = state.totalBookings.toFloat().coerceAtLeast(1f),
                color = Color(0xFFFF5722),
                size = 100.dp,
                strokeWidth = 12.dp,
                displayText = state.incidentReportCount.toString()
            )
        }
    }

    // ─── 3. Monthly Booking Trend ───
    SummarySection("📈 Monthly Booking Trend") {
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val currentMonth = LocalDate.now().monthValue - 1
        val entries = state.bookingsByMonth.mapIndexed { i, v ->
            BarChartEntry(
                label = months[i],
                value = v.toFloat(),
                color = if (i == currentMonth) MaterialTheme.colorScheme.primary else Color(0xFF2196F3).copy(alpha = 0.7f)
            )
        }
        AnimatedBarChart(entries = entries, maxHeight = 140.dp, barWidth = 20.dp)
    }

    // ─── 4. Booking Status Pipeline ───
    SummarySection("🔄 Booking Status Pipeline") {
        val statusNames = mapOf(
            1 to "Booked", 2 to "Fee Paid", 3 to "Checked In",
            4 to "Exam Done", 5 to "Dropped Off", 6 to "Completed"
        )
        val statusColors = mapOf(
            1 to Color(0xFFFFA726), 2 to Color(0xFF42A5F5), 3 to Color(0xFF66BB6A),
            4 to Color(0xFFAB47BC), 5 to Color(0xFF26C6DA), 6 to Color(0xFF4CAF50)
        )

        val slices = (1..6).map { s ->
            DonutSlice(
                label = statusNames[s] ?: "Status $s",
                value = (state.bookingsByStatus[s] ?: 0).toFloat(),
                color = statusColors[s] ?: Color.Gray
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AnimatedDonutChart(
                slices = slices,
                centerValue = state.totalBookings.toString(),
                centerLabel = "Total",
                size = 140.dp,
                strokeWidth = 20.dp,
                modifier = Modifier.padding(8.dp)
            )
            Spacer(Modifier.width(16.dp))
            DonutLegend(slices = slices, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        // Stacked pipeline bar
        val segments = (1..6).map { s ->
            StackSegment(
                label = statusNames[s] ?: "",
                value = (state.bookingsByStatus[s] ?: 0).toFloat(),
                color = statusColors[s] ?: Color.Gray
            )
        }
        StackedProgressBar(segments = segments, height = 14.dp)
    }

    // ─── 5. Room Utilisation ───
    if (state.roomUtilisation.isNotEmpty()) {
        SummarySection("🏫 Room Utilisation") {
            val sorted = state.roomUtilisation.entries.sortedByDescending { it.value }.take(8)
            val entries = sorted.map { (name, count) ->
                BarChartEntry(name, count.toFloat(), MaterialTheme.colorScheme.primary)
            }
            AnimatedBarChart(entries = entries, maxHeight = 120.dp, barWidth = 32.dp)
        }
    }

    // ─── 6. Peak Hours ───
    if (state.peakHours.isNotEmpty()) {
        SummarySection("⏰ Peak Exam Hours") {
            val hours = (7..20).toList()
            val data = hours.map { (state.peakHours[it] ?: 0).toFloat() }
            Sparkline(data = data, lineColor = MaterialTheme.colorScheme.primary, height = 60.dp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("7 AM", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Noon", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("8 PM", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // ─── 7. User Breakdown ───
    SummarySection("👥 User Breakdown") {
        val slices = listOf(
            DonutSlice("Students", state.totalStudents.toFloat(), Color(0xFF42A5F5)),
            DonutSlice("Faculty", state.totalFaculty.toFloat(), Color(0xFF66BB6A)),
            DonutSlice("Proctors", state.totalProctors.toFloat(), Color(0xFFFFA726)),
            DonutSlice("Admins", state.totalAdmins.toFloat(), Color(0xFFAB47BC))
        )
        val totalUsers = state.totalStudents + state.totalFaculty + state.totalProctors + state.totalAdmins
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AnimatedDonutChart(
                slices = slices,
                centerValue = totalUsers.toString(),
                centerLabel = "Users",
                size = 120.dp,
                strokeWidth = 18.dp,
                modifier = Modifier.padding(8.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                DonutLegend(slices = slices)
                if (state.pendingAccounts > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Red))
                        Spacer(Modifier.width(8.dp))
                        Text("Pending: ${state.pendingAccounts}", style = MaterialTheme.typography.labelMedium, color = Color.Red)
                    }
                }
                if (state.deactivatedAccounts > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Gray))
                        Spacer(Modifier.width(8.dp))
                        Text("Deactivated: ${state.deactivatedAccounts}", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    }
                }
            }
        }
    }

    // ─── 8. Proctor Coverage ───
    SummarySection("🛡️ Proctor Coverage Rate") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            AnimatedRingStat(
                label = "Coverage",
                value = state.proctorCoverageRate,
                maxValue = 100f,
                color = if (state.proctorCoverageRate >= 80f) Color(0xFF4CAF50) else if (state.proctorCoverageRate >= 50f) MaterialTheme.colorScheme.primary else Color.Red,
                size = 110.dp,
                strokeWidth = 14.dp
            )
            Column {
                Text("Rooms Tracked: ${state.totalRooms}", style = MaterialTheme.typography.bodyMedium)
                val covered = (state.totalRooms * state.proctorCoverageRate / 100f).toInt()
                Text("With Proctors: $covered", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50))
                Text("Unassigned: ${state.totalRooms - covered}", style = MaterialTheme.typography.bodyMedium, color = Color.Red)
            }
        }
    }

    // ─── 9. Top Proctors ───
    if (state.topProctors.isNotEmpty()) {
        SummarySection("🏆 Top Proctors by Assignments") {
            val entries = state.topProctors.map { (name, count) ->
                BarChartEntry(name.split(" ").firstOrNull() ?: name, count.toFloat(), MaterialTheme.colorScheme.primary)
            }
            AnimatedBarChart(entries = entries, maxHeight = 100.dp, barWidth = 36.dp)
        }
    }

    // ─── 10. Sessions by Day of Week ───
    if (state.sessionsByDayOfWeek.isNotEmpty()) {
        SummarySection("📅 Sessions by Day of Week") {
            val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val entries = (1..7).map { dow ->
                BarChartEntry(days[dow - 1], (state.sessionsByDayOfWeek[dow] ?: 0).toFloat(), Color(0xFF7E57C2))
            }
            AnimatedBarChart(entries = entries, maxHeight = 100.dp, barWidth = 30.dp)
        }
    }
}

// ══════════════════════════════════════════════════════════
//  FACULTY SUMMARY
// ══════════════════════════════════════════════════════════

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun FacultySummaryContent(state: DataSummaryUiState) {
    // Booking trend
    SummarySection("📈 My Booking Trend") {
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val currentMonth = LocalDate.now().monthValue - 1
        val entries = state.myBookingsByMonth.mapIndexed { i, v ->
            BarChartEntry(months[i], v.toFloat(), if (i == currentMonth) MaterialTheme.colorScheme.primary else Color(0xFF66BB6A).copy(alpha = 0.7f))
        }
        AnimatedBarChart(entries = entries, maxHeight = 130.dp, barWidth = 20.dp)
    }

    // Pipeline
    SummarySection("🔄 My Booking Pipeline") {
        val statusNames = mapOf(1 to "Booked", 2 to "Fee Paid", 3 to "Checked In", 4 to "Exam Done", 5 to "Dropped Off", 6 to "Completed")
        val statusColors = mapOf(1 to Color(0xFFFFA726), 2 to Color(0xFF42A5F5), 3 to Color(0xFF66BB6A), 4 to Color(0xFFAB47BC), 5 to Color(0xFF26C6DA), 6 to Color(0xFF4CAF50))
        val slices = (1..6).map { s -> DonutSlice(statusNames[s] ?: "", (state.myBookingsByStatus[s] ?: 0).toFloat(), statusColors[s] ?: Color.Gray) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AnimatedDonutChart(slices = slices, centerValue = state.myTotalBookings.toString(), centerLabel = "Bookings", size = 120.dp, strokeWidth = 18.dp, modifier = Modifier.padding(8.dp))
            Spacer(Modifier.width(16.dp))
            DonutLegend(slices = slices, modifier = Modifier.weight(1f))
        }
    }

    // Completion rate
    SummarySection("✅ My Completion Rate") {
        val rate = if (state.myTotalBookings > 0) state.myCompletedBookings.toFloat() / state.myTotalBookings * 100f else 0f
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AnimatedRingStat("Completed", rate, 100f, Color(0xFF4CAF50), size = 100.dp, strokeWidth = 12.dp)
            AnimatedRingStat("Active", state.myActiveBookings.toFloat(), state.myTotalBookings.toFloat().coerceAtLeast(1f), Color(0xFF2196F3), size = 100.dp, strokeWidth = 12.dp, displayText = state.myActiveBookings.toString())
        }
    }

    // Proctoring
    if (state.myProctorAssignments > 0) {
        SummarySection("🛡️ My Proctoring Stats") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                KPICard("Assignments", state.myProctorAssignments.toString(), Icons.Default.Badge, Color(0xFF7E57C2), Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                KPICard("Hours", String.format("%.1f", state.myProctorHours), Icons.Default.Schedule, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  PROCTOR SUMMARY
// ══════════════════════════════════════════════════════════

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun ProctorSummaryContent(state: DataSummaryUiState) {
    SummarySection("🛡️ My Proctoring Year") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AnimatedRingStat(
                label = "Assignments",
                value = state.myProctorAssignments.toFloat(),
                maxValue = (state.myProctorAssignments + 10).toFloat(),
                color = MaterialTheme.colorScheme.primary,
                size = 100.dp,
                strokeWidth = 12.dp,
                displayText = state.myProctorAssignments.toString()
            )
            AnimatedRingStat(
                label = "Hours",
                value = state.myProctorHours,
                maxValue = (state.myProctorHours + 20f).coerceAtLeast(1f),
                color = Color(0xFF7E57C2),
                size = 100.dp,
                strokeWidth = 12.dp,
                displayText = String.format("%.1f", state.myProctorHours)
            )
        }
    }

    if (state.myTotalBookings > 0) {
        SummarySection("📈 My Booking Activity") {
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val entries = state.myBookingsByMonth.mapIndexed { i, v ->
                BarChartEntry(months[i], v.toFloat(), Color(0xFF7E57C2).copy(alpha = 0.8f))
            }
            AnimatedBarChart(entries = entries, maxHeight = 110.dp, barWidth = 20.dp)
        }
    }

    SummarySection("🎮 Gamification Progress") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AnimatedRingStat("XP", state.myTotalXP.toFloat(), ((state.myLevel + 1) * 100).toFloat(), MaterialTheme.colorScheme.primary, size = 80.dp, strokeWidth = 10.dp, displayText = state.myTotalXP.toString())
            AnimatedRingStat("Level", state.myLevel.toFloat(), 50f, Color(0xFF4CAF50), size = 80.dp, strokeWidth = 10.dp, displayText = state.myLevel.toString())
            AnimatedRingStat("Badges", state.myBadgeCount.toFloat(), (state.myBadgeCount + 5).toFloat(), Color(0xFF42A5F5), size = 80.dp, strokeWidth = 10.dp, displayText = state.myBadgeCount.toString())
        }
    }
}

// ══════════════════════════════════════════════════════════
//  STUDENT SUMMARY
// ══════════════════════════════════════════════════════════

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun StudentSummaryContent(state: DataSummaryUiState) {
    // Exam journey
    SummarySection("📚 My Exam Journey") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val completionRate = if (state.myTotalBookings > 0) state.myCompletedBookings.toFloat() / state.myTotalBookings * 100f else 0f
            AnimatedRingStat("Completion", completionRate, 100f, Color(0xFF4CAF50), size = 100.dp, strokeWidth = 12.dp)
            AnimatedRingStat("Total Exams", state.myTotalBookings.toFloat(), (state.myTotalBookings + 5).toFloat(), Color(0xFF42A5F5), size = 100.dp, strokeWidth = 12.dp, displayText = state.myTotalBookings.toString())
        }
    }

    // Monthly activity
    SummarySection("📈 Monthly Exam Activity") {
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val currentMonth = LocalDate.now().monthValue - 1
        val entries = state.myBookingsByMonth.mapIndexed { i, v ->
            BarChartEntry(months[i], v.toFloat(), if (i == currentMonth) MaterialTheme.colorScheme.primary else Color(0xFF42A5F5).copy(alpha = 0.7f))
        }
        AnimatedBarChart(entries = entries, maxHeight = 120.dp, barWidth = 20.dp)
    }

    // Status pipeline
    if (state.myTotalBookings > 0) {
        SummarySection("🔄 Exam Status Breakdown") {
            val statusNames = mapOf(1 to "Booked", 2 to "Fee Paid", 3 to "Checked In", 4 to "Exam Done", 5 to "Dropped Off", 6 to "Completed")
            val statusColors = mapOf(1 to Color(0xFFFFA726), 2 to Color(0xFF42A5F5), 3 to Color(0xFF66BB6A), 4 to Color(0xFFAB47BC), 5 to Color(0xFF26C6DA), 6 to Color(0xFF4CAF50))
            val slices = (1..6).map { s -> DonutSlice(statusNames[s] ?: "", (state.myBookingsByStatus[s] ?: 0).toFloat(), statusColors[s] ?: Color.Gray) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AnimatedDonutChart(slices = slices, centerValue = state.myTotalBookings.toString(), centerLabel = "Exams", size = 120.dp, strokeWidth = 18.dp, modifier = Modifier.padding(8.dp))
                Spacer(Modifier.width(16.dp))
                DonutLegend(slices = slices, modifier = Modifier.weight(1f))
            }
        }
    }

    // Gamification
    SummarySection("🎮 Achievements") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AnimatedRingStat("XP", state.myTotalXP.toFloat(), ((state.myLevel + 1) * 100).toFloat(), MaterialTheme.colorScheme.primary, size = 80.dp, strokeWidth = 10.dp, displayText = state.myTotalXP.toString())
            AnimatedRingStat("Level", state.myLevel.toFloat(), 50f, Color(0xFF4CAF50), size = 80.dp, strokeWidth = 10.dp, displayText = state.myLevel.toString())
            AnimatedRingStat("Streak", state.myStreak.toFloat(), 30f, Color(0xFFFF5722), size = 80.dp, strokeWidth = 10.dp, displayText = "${state.myStreak}")
            AnimatedRingStat("Badges", state.myBadgeCount.toFloat(), (state.myBadgeCount + 5).toFloat(), Color(0xFF42A5F5), size = 80.dp, strokeWidth = 10.dp, displayText = state.myBadgeCount.toString())
        }
    }
}

// ══════════════════════════════════════════════════════════
//  SHARED COMPONENTS
// ══════════════════════════════════════════════════════════

@Composable
private fun SummarySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            content()
        }
    }
}

@Composable
private fun KPICard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accentColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = accentColor)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun roleName(role: Int): String = when (role) {
    0 -> "Admin"
    1 -> "Faculty"
    2 -> "Proctor"
    3 -> "Student"
    else -> "User"
}


