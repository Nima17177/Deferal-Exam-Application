package com.example.duckduckdefer.components

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.FacultySubclass
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AvailabilityDialog(
    title: String,
    initialDates: Set<LocalDate> = emptySet(),
    initialStartTime: String = "08:00",
    initialEndTime: String = "17:00",
    singleDateMode: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Set<LocalDate>, String, String, Int) -> Unit,
    minDate: LocalDate = LocalDate.now(),
    facultyUser: FacultySubclass? = null,
    allowRecurring: Boolean = false
) {
    var dialogSelectedDates by remember { mutableStateOf(initialDates) }
    var startTime by remember { mutableStateOf(initialStartTime) }
    var endTime by remember { mutableStateOf(initialEndTime) }
    var repeatWeeks by remember { mutableIntStateOf(1) }
    var currentPivotDate by remember { mutableStateOf((dialogSelectedDates.firstOrNull() ?: LocalDate.now()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
    val context = LocalContext.current

    val isTimeValid = remember(startTime, endTime) {
        try {
            val start = LocalTime.parse(startTime)
            val end = LocalTime.parse(endTime)
            start.isBefore(end)
        } catch (e: Exception) {
            false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp, horizontal = 16.dp)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Hero Gradient
                val heroContent = DuckGradients.heroContentColor()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DuckGradients.heroGradient())
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = heroContent.copy(alpha = 0.2f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (singleDateMode) Icons.Default.Edit else Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = heroContent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = heroContent,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Quick Templates
                    SectionLabel("Quick Templates")
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TemplateButton("Morning", "8 AM - 12 PM", Modifier.weight(1f)) { startTime = "08:00"; endTime = "12:00" }
                        TemplateButton("Afternoon", "1 PM - 5 PM", Modifier.weight(1f)) { startTime = "13:00"; endTime = "17:00" }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TemplateButton("Full Shift", "8:00 AM - 5:00 PM", Modifier.fillMaxWidth()) { startTime = "08:00"; endTime = "17:00" }
                    
                    DividerWithPadding()

                    // Selection Logic
                    if (!singleDateMode) {
                        SectionLabel("Select Dates", Icons.Default.CalendarToday)
                        
                        CalendarPicker(
                            currentPivotDate = currentPivotDate,
                            selectedDates = dialogSelectedDates,
                            onDateSelected = { date ->
                                if (!date.isBefore(minDate)) dialogSelectedDates = if (dialogSelectedDates.contains(date)) dialogSelectedDates - date else dialogSelectedDates + date
                                else Toast.makeText(context, "Cannot select past dates", Toast.LENGTH_SHORT).show()
                            },
                            onPrev = { currentPivotDate = currentPivotDate.minusWeeks(1) },
                            onNext = { currentPivotDate = currentPivotDate.plusWeeks(1) }
                        )
                        
                        BadgeLabel("${dialogSelectedDates.size} dates selected")
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Updating: ${dialogSelectedDates.firstOrNull()?.format(DateTimeFormatter.ofPattern("EEEE, MMM dd"))}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    DividerWithPadding()

                    // Time Picker
                    SectionLabel("Availability Hours", Icons.Default.Schedule)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TimeSlotPicker(
                            label = "From", 
                            selectedTime = startTime, 
                            onTimeSelected = { 
                                startTime = it 
                                // Auto-adjust end time if it becomes invalid
                                try {
                                    val s = LocalTime.parse(it)
                                    val e = LocalTime.parse(endTime)
                                    if (!s.isBefore(e)) {
                                        endTime = s.plusMinutes(30).format(DateTimeFormatter.ofPattern("HH:mm"))
                                    }
                                } catch (_: Exception) {}
                            }, 
                            modifier = Modifier.weight(1f)
                        )
                        TimeSlotPicker(
                            label = "Until", 
                            selectedTime = endTime, 
                            onTimeSelected = { 
                                endTime = it 
                                // Auto-adjust start time if it becomes invalid
                                try {
                                    val e = LocalTime.parse(it)
                                    val s = LocalTime.parse(startTime)
                                    if (!s.isBefore(e)) {
                                        startTime = e.minusMinutes(30).format(DateTimeFormatter.ofPattern("HH:mm"))
                                    }
                                } catch (_: Exception) {}
                            }, 
                            modifier = Modifier.weight(1f), 
                            minTime = startTime
                        )
                    }

                    if (!isTimeValid) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "End time must be after start time.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (allowRecurring) {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionLabel("Recurring Schedule")
                        Text(
                            "Automatically repeat this availability for future weeks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
                        )
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(1, 4, 8, 12).forEach { w ->
                                RecurringOption(
                                    text = if (w == 1) "Once" else "${w}w",
                                    selected = repeatWeeks == w,
                                    onClick = { repeatWeeks = w },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                    
                    // Final Actions
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(0.8f).height(54.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { 
                                if (dialogSelectedDates.isEmpty()) {
                                    Toast.makeText(context, "Please select a date", Toast.LENGTH_SHORT).show() 
                                } else if (!isTimeValid) {
                                    Toast.makeText(context, "Invalid time range", Toast.LENGTH_SHORT).show()
                                } else {
                                    onConfirm(dialogSelectedDates, startTime, endTime, repeatWeeks) 
                                }
                            },
                            modifier = Modifier.weight(1.2f).height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = isTimeValid && dialogSelectedDates.isNotEmpty(),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Text("Save Schedule", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun RecurringOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text, 
                style = MaterialTheme.typography.labelLarge, 
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun BadgeLabel(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(top = 16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DividerWithPadding() {
    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
fun TemplateButton(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
            Text(
                text = subtitle, 
                style = MaterialTheme.typography.labelSmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
