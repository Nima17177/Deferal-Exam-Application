package com.example.duckduckdefer.screens.AdminScreens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.duckduckdefer.components.*
import com.example.duckduckdefer.useraccountclasses.*
import com.example.duckduckdefer.ui.theme.DuckGradients
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import androidx.annotation.RequiresApi
import android.os.Build

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Suppress("NewApi")
@Composable
fun ProctorAssignmentScreen(
    user: AdminSubclass,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentPivotDate by remember {
        mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
    var isMonthView by remember { mutableStateOf(false) }
    
    var sessionForDate by remember { mutableStateOf<SessionsClass?>(null) }
    var weekSessions by remember { mutableStateOf<List<SessionsClass>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isAssigning by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }
    var filterUnassignedOnly by remember { mutableStateOf(false) }

    var showSearchDialog by remember { mutableStateOf<RoomData?>(null) }
    var proctorToRemove by remember { mutableStateOf<Pair<RoomData, String>?>(null) }
    var pendingProctorAssignment by remember { mutableStateOf<Pair<UserMembersClass, RoomData>?>(null) }

    val dateID = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

    // Calculate DayStats for the calendar
    val dayStats = remember(weekSessions, refreshTrigger, isMonthView, currentPivotDate) {
        val statsMap = mutableMapOf<LocalDate, DayStats>()
        val startDate = if (isMonthView) currentPivotDate.with(TemporalAdjusters.firstDayOfMonth()) else currentPivotDate
        val endDate = if (isMonthView) currentPivotDate.with(TemporalAdjusters.lastDayOfMonth()) else currentPivotDate.plusDays(6)
        
        var curr = startDate
        while (!curr.isAfter(endDate)) {
            val dateStr = curr.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val session = weekSessions.find { it.dateID == dateStr } ?: SessionsClass(dateID = dateStr)
            statsMap[curr] = session.calculateDayStats(curr, user.thisUID, user.thisRole, emptyList())
            curr = curr.plusDays(1)
        }
        statsMap
    }

    // Load sessions
    LaunchedEffect(selectedDate, refreshTrigger) {
        isLoading = true
        val manager = SessionsClass()
        manager.getSession(dateID) { success ->
            sessionForDate = if (success) manager else null
            isLoading = false
        }
    }

    LaunchedEffect(currentPivotDate, isMonthView, refreshTrigger) {
        val startDate = if (isMonthView) currentPivotDate.with(TemporalAdjusters.firstDayOfMonth()) else currentPivotDate
        val endDate = if (isMonthView) currentPivotDate.with(TemporalAdjusters.lastDayOfMonth()) else currentPivotDate.plusDays(6)
        
        SessionsClass().getSessionsInRange(
            startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        ) { list ->
            weekSessions = list
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // INTEGRATED HEADER: FIXED HEIGHT
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ) {
                val heroContent = DuckGradients.heroContentColor()
                val heroContentSecondary = DuckGradients.heroContentColorSecondary()
                Box(modifier = Modifier
                    .background(DuckGradients.heroGradient(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .statusBarsPadding()) {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Proctor Assignment",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                Text(
                                    "Resource Allocation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = heroContentSecondary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = heroContent)
                            }
                        },
                        actions = {
                            if (sessionForDate != null) {
                                IconButton(onClick = { showClearDialog = true }) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = heroContentSecondary)
                                }
                            }
                            IconButton(onClick = onOpenMenu) {
                                Icon(Icons.Default.Menu, contentDescription = "Open Menu", tint = heroContent)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    ) { innerPadding ->
        val heroContentSecondary = DuckGradients.heroContentColorSecondary()
        Box(modifier = Modifier.fillMaxSize()) {
            // FIXED BRAND GLOW: Does not expand with the calendar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp) 
                    .background(
                        brush = DuckGradients.heroGradient(),
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
            )

            val rooms = sessionForDate?.rooms?.values?.toList() ?: emptyList()
            val roomsList = if (filterUnassignedOnly) rooms.filter { it.assignedProctors.isEmpty() } else rooms.sortedBy { it.startTime }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isMonthView) "Month View" else "Week View",
                                style = MaterialTheme.typography.labelLarge,
                                color = heroContentSecondary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Show Month", style = MaterialTheme.typography.bodySmall, color = heroContentSecondary)
                                Spacer(Modifier.width(8.dp))
                                Switch(
                                    checked = isMonthView,
                                    onCheckedChange = { 
                                        isMonthView = it
                                        currentPivotDate = if (it) selectedDate.with(TemporalAdjusters.firstDayOfMonth()) 
                                                           else selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                            }
                        }

                        // Calendar Card: It will expand, but the background glow behind it won't.
                        @Suppress("NewApi")
                        run {
                            CalendarPicker(
                                currentPivotDate = currentPivotDate,
                                selectedDates = setOf(selectedDate),
                                onDateSelected = { selectedDate = it },
                                onPrev = { currentPivotDate = if (isMonthView) currentPivotDate.minusMonths(1) else currentPivotDate.minusWeeks(1) },
                                onNext = { currentPivotDate = if (isMonthView) currentPivotDate.plusMonths(1) else currentPivotDate.plusWeeks(1) },
                                isMonthView = isMonthView,
                                dayStats = dayStats
                            )
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        if (!isLoading && sessionForDate != null) {
                            val totalRooms = rooms.size
                            val coveredCount = rooms.count { it.assignedProctors.isNotEmpty() }
                            val coveragePercent = if (totalRooms > 0) coveredCount.toFloat() / totalRooms else 1f

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(24.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Shield, null, tint = if (coveragePercent >= 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Day Coverage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Text("${(coveragePercent * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (coveragePercent >= 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    val animatedCoverage by animateFloatAsState(coveragePercent, label = "coverage")
                                    LinearProgressIndicator(progress = { animatedCoverage }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = if (coveragePercent >= 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Search Filters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                FilterChip(
                                    selected = filterUnassignedOnly,
                                    onClick = { filterUnassignedOnly = !filterUnassignedOnly },
                                    label = { Text("Need Proctors") },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), selectedLabelColor = MaterialTheme.colorScheme.primary),
                                    border = FilterChipDefaults.filterChipBorder(selectedBorderColor = MaterialTheme.colorScheme.primary, enabled = true, selected = filterUnassignedOnly)
                                )
                            }
                        }
                    }
                }

                if (isLoading) {
                    items(3) {
                        Box(Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(20.dp)).shimmerEffect())
                    }
                } else if (sessionForDate == null) {
                    item { EmptySessionState(modifier = Modifier.padding(vertical = 40.dp)) }
                } else if (roomsList.isEmpty() && filterUnassignedOnly) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Verified, null, Modifier.size(48.dp), tint = Color(0xFF4CAF50).copy(alpha = 0.3f))
                                Text("All rooms have proctors! 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(roomsList, key = { it.roomName }) { room ->
                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            AnimatedAssignmentItem {
                                RoomAssignmentCard(
                                    room = room,
                                    onRemoveProctor = { uid -> proctorToRemove = room to uid },
                                    onAddProctor = { showSearchDialog = room }
                                )
                            }
                        }
                    }
                }
            }

            // FLOATING BUTTON: Fixed to account for Navigation Bar
            if (sessionForDate != null && !isLoading) {
                val totalRooms = rooms.size
                val coveredCount = rooms.count { it.assignedProctors.isNotEmpty() }
                val coveragePercent = if (totalRooms > 0) coveredCount.toFloat() / totalRooms else 1f
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding()), // Lift above navigation bar
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val buttonBrush = if (coveragePercent < 1f) DuckGradients.primaryGradient() else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outlineVariant))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .height(60.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(enabled = !isAssigning && coveragePercent < 1f) {
                                isAssigning = true
                                user.autoAssignProctors(dateID) { success ->
                                    isAssigning = false
                                    if (success) { 
                                        scope.launch { snackbarHostState.showSnackbar("Auto-fill complete!") }
                                        refreshTrigger++ 
                                    } else {
                                        scope.launch { snackbarHostState.showSnackbar("No available proctors found.") }
                                    }
                                }
                            },
                        color = Color.Transparent, 
                        shadowElevation = 12.dp
                    ) {
                        Box(modifier = Modifier.background(buttonBrush), contentAlignment = Alignment.Center) {
                            if (isAssigning) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 3.dp)
                                    Spacer(Modifier.width(16.dp))
                                    Text("Intelligent Assignment...", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoFixHigh, null, tint = Color.White)
                                    Spacer(Modifier.width(12.dp))
                                    Text(if (coveragePercent >= 1f) "Coverage Complete" else "Smart Auto-Fill Coverage", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (proctorToRemove != null) {
        AlertDialog(
            onDismissRequest = { proctorToRemove = null },
            title = { Text("Remove Proctor?") },
            text = { Text("Remove this staff member from ${proctorToRemove?.first?.roomName}?") },
            confirmButton = { 
                TextButton(onClick = {
                    val (room, uid) = proctorToRemove!!
                    user.removeProctor(dateID, room.roomName, uid) { if (it) refreshTrigger++ }
                    proctorToRemove = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) } 
            },
            dismissButton = { TextButton(onClick = { proctorToRemove = null }) { Text("Cancel") } }
        )
    }

    if (pendingProctorAssignment != null) {
        val (proctor, room) = pendingProctorAssignment!!
        AlertDialog(
            onDismissRequest = { pendingProctorAssignment = null },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Availability Warning")
                }
            },
            text = { Text("${proctor.thisUserName} has not listed availability for this time slot (${SessionsClass.formatTime(room.startTime)} - ${SessionsClass.formatTime(room.endTime)}). Assign anyway?") },
            confirmButton = {
                TextButton(onClick = {
                    user.assignProctor(dateID, room.roomName, proctor.thisUID) { if (it) refreshTrigger++ }
                    pendingProctorAssignment = null
                }) { Text("Assign Anyway", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingProctorAssignment = null }) { Text("Cancel") } }
        )
    }

    if (showSearchDialog != null) {
        ProctorSearchDialog(
            room = showSearchDialog!!,
            user = user,
            dateID = dateID,
            onDismiss = { showSearchDialog = null },
            onAssign = { proctor, isAvailable ->
                if (isAvailable) {
                    user.assignProctor(dateID, showSearchDialog!!.roomName, proctor.thisUID) { if (it) refreshTrigger++ }
                    showSearchDialog = null
                } else {
                    pendingProctorAssignment = proctor to showSearchDialog!!
                    showSearchDialog = null
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Assignments?") },
            text = { Text("This will remove ALL proctors from ALL rooms for $dateID. This action cannot be undone.") },
            confirmButton = {
                HoldToConfirmButton(
                    text = "Clear All",
                    onConfirm = {
                        user.clearAssignmentsForDay(dateID) { if (it) refreshTrigger++ }
                        showClearDialog = false
                    }
                )
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AnimatedAssignmentItem(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 4 }
    ) { content() }
}

@Composable
fun EmptySessionState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(16.dp))
            Text(
                "No sessions created for this day.", 
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Check another date or create a session first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun RoomAssignmentCard(
    room: RoomData,
    onRemoveProctor: (String) -> Unit,
    onAddProctor: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        room.roomName, 
                        fontWeight = FontWeight.ExtraBold, 
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${SessionsClass.formatTime(room.startTime)} - ${SessionsClass.formatTime(room.endTime)}", 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                val isCovered = room.assignedProctors.isNotEmpty()
                val statusColor = if (!isCovered) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)) 
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            if (!isCovered) "VACANT" else "STAFFED",
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            
            if (room.assignedProctors.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddProctor() },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Assign Proctor Manually", 
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                room.assignedProctors.forEach { uid ->
                    var proctorName by remember { mutableStateOf("Loading...") }
                    var profileUrl by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(uid) {
                        val u = UserMembersClass()
                        u.getUserData(uid) { 
                            if (it) {
                                proctorName = u.thisUserName
                                profileUrl = u.profilePictureUrl
                            }
                        }
                    }
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileAvatar(profilePictureUrl = profileUrl ?: "", name = proctorName, size = 48.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    proctorName, 
                                    style = MaterialTheme.typography.bodyLarge, 
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Verified Staff", 
                                    style = MaterialTheme.typography.labelSmall, 
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(
                                onClick = { onRemoveProctor(uid) }, 
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close, 
                                    "Remove", 
                                    tint = MaterialTheme.colorScheme.error, 
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                
                TextButton(
                    onClick = onAddProctor, 
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Another Staff", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProctorSearchDialog(
    room: RoomData,
    user: AdminSubclass,
    dateID: String,
    onDismiss: () -> Unit,
    onAssign: (UserMembersClass, Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserMembersClass>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Assign Proctor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
                
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("${room.roomName} • ${SessionsClass.formatTime(room.startTime)} - ${SessionsClass.formatTime(room.endTime)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
                }
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = query,
                    onValueChange = { 
                        query = it 
                        if (it.length >= 2) {
                            isSearching = true
                            DatabaseL.searchPotentialProctors(it) { list: List<UserMembersClass> -> results = list; isSearching = false }
                        } else results = emptyList()
                    },
                    placeholder = { Text("Search by name or email...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = if (isSearching) { { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary) } } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, cursorColor = MaterialTheme.colorScheme.primary)
                )
                
                Spacer(Modifier.height(20.dp))
                
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(results) { proctor ->
                        var isAvailable by remember { mutableStateOf<Boolean?>(null) }
                        LaunchedEffect(proctor.thisUID) {
                            user.checkProctorAvailability(proctor.thisUID, dateID = dateID, startTime = room.startTime, endTime = room.endTime) { isAvailable = it }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onAssign(proctor, isAvailable ?: false) },
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProfileAvatar(profilePictureUrl = proctor.profilePictureUrl, name = proctor.thisUserName, size = 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(proctor.thisUserName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text(proctor.thisEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isAvailable != null) Surface(color = (if (isAvailable!!) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error).copy(alpha = 0.1f), shape = CircleShape) {
                                    Text(if (isAvailable!!) "Available" else "Busy", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (isAvailable!!) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Close", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
