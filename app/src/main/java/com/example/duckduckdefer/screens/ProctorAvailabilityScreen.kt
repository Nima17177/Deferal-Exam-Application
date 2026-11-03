package com.example.duckduckdefer.screens

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.duckduckdefer.components.AvailabilityDialog
import com.example.duckduckdefer.components.AvailabilityRangeItem
import com.example.duckduckdefer.components.CalendarPicker
import com.example.duckduckdefer.components.exportToCalendar
import com.example.duckduckdefer.components.shimmerEffect
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.FacultySubclass
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import com.example.duckduckdefer.viewmodels.ProctorAvailabilityViewModel
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ProctorAvailabilityScreen(
    user: UserMembersClass,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit = {},
    viewModel: ProctorAvailabilityViewModel = viewModel()
) {
    val facultyUser = remember(user) { user as? FacultySubclass }
    val context = LocalContext.current
    
    var showAddDialog by remember { mutableStateOf(false) }
    var rangeToEdit by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showClearDayDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.refreshTrigger) {
        viewModel.refreshStats(facultyUser)
    }

    LaunchedEffect(viewModel.selectedDate, viewModel.refreshTrigger) {
        viewModel.loadDetails(facultyUser)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
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
                                    "My Availability",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                Text(
                                    "Proctor Scheduling",
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
                            if (viewModel.availabilityRanges.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.availabilityRanges.forEach { range ->
                                        exportToCalendar(
                                            context = context,
                                            dateID = viewModel.selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                            startTimeStr = range.start,
                                            endTimeStr = range.end,
                                            title = if (range.isAssigned) "Proctoring Assignment" else "Scheduled Availability",
                                            location = "MacEwan University",
                                            description = if (range.isAssigned) "Assigned proctoring shift" else "Your scheduled availability for proctoring"
                                        )
                                    }
                                }) {
                                    Icon(Icons.Default.Event, contentDescription = "Export All to Calendar", tint = heroContent)
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
        },
        floatingActionButton = {
            if (!viewModel.selectedDate.isBefore(viewModel.minDate)) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Availability")
                }
            }
        }
    ) { innerPadding ->
        val heroContentSecondary = DuckGradients.heroContentColorSecondary()
        Box(modifier = Modifier.fillMaxSize()) {
            // FIXED BRAND GLOW
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        brush = DuckGradients.heroGradient(),
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // CALENDAR SECTION
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (viewModel.isMonthView) "Month View" else "Week View",
                                style = MaterialTheme.typography.labelLarge,
                                color = heroContentSecondary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Show Month", style = MaterialTheme.typography.bodySmall, color = heroContentSecondary)
                                Spacer(Modifier.width(8.dp))
                                Switch(
                                    checked = viewModel.isMonthView, 
                                    onCheckedChange = { 
                                        viewModel.isMonthView = it
                                        viewModel.currentPivotDate = if (it) viewModel.selectedDate.with(TemporalAdjusters.firstDayOfMonth())
                                                                     else viewModel.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
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

                        CalendarPicker(
                            currentPivotDate = viewModel.currentPivotDate,
                            selectedDates = setOf(viewModel.selectedDate),
                            onDateSelected = { viewModel.selectedDate = it },
                            onPrev = { viewModel.currentPivotDate = if (viewModel.isMonthView) viewModel.currentPivotDate.minusMonths(1) else viewModel.currentPivotDate.minusWeeks(1) },
                            onNext = { viewModel.currentPivotDate = if (viewModel.isMonthView) viewModel.currentPivotDate.plusMonths(1) else viewModel.currentPivotDate.plusWeeks(1) },
                            isMonthView = viewModel.isMonthView,
                            dayStats = viewModel.calendarDayStats
                        )
                    }
                }

                // SUMMARY SECTION
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(
                                    text = viewModel.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd")), 
                                    style = MaterialTheme.typography.titleLarge, 
                                    fontWeight = FontWeight.Bold
                                )
                                if (viewModel.currentAvailabilityForSelectedDate.isNotEmpty()) {
                                    Text(
                                        text = "Daily Summary: ${"%.1f".format(viewModel.totalHours)} hours scheduled", 
                                        style = MaterialTheme.typography.labelLarge, 
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            if (viewModel.currentAvailabilityForSelectedDate.isNotEmpty() && !viewModel.selectedDate.isBefore(viewModel.minDate)) {
                                val hasAssigned = viewModel.availabilityRanges.any { it.isAssigned }
                                IconButton(
                                    onClick = { 
                                        if (hasAssigned) {
                                            Toast.makeText(context, "This day has booked shifts. Please contact admin to clear.", Toast.LENGTH_LONG).show()
                                        } else {
                                            showClearDayDialog = true 
                                        }
                                    },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(Icons.Default.DeleteSweep, "Clear", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // RANGES LIST
                if (viewModel.isLoadingDetails) {
                    items(2) {
                        Box(Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
                    }
                } else if (viewModel.currentAvailabilityForSelectedDate.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), 
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.EventBusy, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No availability set for this date.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    val isEditable = !viewModel.selectedDate.isBefore(viewModel.minDate)
                    items(viewModel.availabilityRanges) { range ->
                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                            AvailabilityRangeItem(
                                startTime = range.start,
                                endTime = range.end,
                                isAssigned = range.isAssigned,
                                isEditable = isEditable,
                                onEdit = { 
                                    if (range.isAssigned) {
                                        Toast.makeText(context, "This shift is already booked. Please contact the administrator to make changes.", Toast.LENGTH_LONG).show()
                                    } else {
                                        rangeToEdit = range.start to range.end 
                                    }
                                },
                                onDelete = {
                                    if (range.isAssigned) {
                                        Toast.makeText(context, "This shift is already booked. Please contact the administrator to make changes.", Toast.LENGTH_LONG).show()
                                    } else {
                                        viewModel.deleteRange(facultyUser, viewModel.selectedDate, range.start to range.end)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AvailabilityDialog(
            title = "Add Availability",
            initialDates = setOf(viewModel.selectedDate),
            onDismiss = { showAddDialog = false },
            onConfirm = { dates, start, end, weeks ->
                viewModel.saveAvailability(facultyUser, dates, start, end, context, weeks) {
                    showAddDialog = false
                }
            },
            minDate = viewModel.minDate,
            facultyUser = facultyUser,
            allowRecurring = true
        )
    }

    if (rangeToEdit != null) {
        AvailabilityDialog(
            title = "Edit Availability",
            initialDates = setOf(viewModel.selectedDate),
            initialStartTime = rangeToEdit!!.first,
            initialEndTime = rangeToEdit!!.second,
            singleDateMode = true,
            onDismiss = { rangeToEdit = null },
            onConfirm = { _, start, end, _ ->
                viewModel.updateRange(facultyUser, viewModel.selectedDate, rangeToEdit!!, start, end, context)
                rangeToEdit = null
            },
            minDate = viewModel.minDate,
            facultyUser = facultyUser
        )
    }

    if (showClearDayDialog) {
        AlertDialog(
            onDismissRequest = { showClearDayDialog = false },
            title = { Text("Clear All Availability") },
            text = { Text("Are you sure you want to remove all scheduled availability for ${viewModel.selectedDate.format(DateTimeFormatter.ofPattern("MMM dd"))}?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDayDialog = false
                    viewModel.clearDay(facultyUser, context)
                }) { Text("Clear All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearDayDialog = false }) { Text("Cancel") } }
        )
    }
}
