package com.example.duckduckdefer.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.ui.theme.LocalIsDarkTheme
import com.example.duckduckdefer.useraccountclasses.*
import com.example.duckduckdefer.utils.CampusLocations
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AvailabilityIntervals(room: RoomData, isPremium: Boolean = false) {
    val startTime = remember(room.startTime) { SessionsClass.parseTime(room.startTime) }
    val premiumContent = if (isPremium) DuckGradients.heroContentColor() else Color.Unspecified

    Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        ) {
            Icon(
                Icons.Default.EventSeat,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isPremium) premiumContent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Time Slots & Availability",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (isPremium) premiumContent.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
            )
        }
        
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            room.availableSeats.forEachIndexed { index, available ->
                val slotStart = startTime.plusMinutes(index * 30L)
                val timeStr = slotStart.format(DateTimeFormatter.ofPattern("h:mm"))
                val amPm = slotStart.format(DateTimeFormatter.ofPattern("a"))
                
                Surface(
                    color = if (isPremium) {
                        if (available <= 0) premiumContent.copy(alpha = 0.05f) 
                        else premiumContent.copy(alpha = 0.12f)
                    } else {
                        if (available <= 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f) 
                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = if (isPremium && available > 0) BorderStroke(0.5.dp, premiumContent.copy(alpha = 0.1f)) else null
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPremium) premiumContent else MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = amPm.lowercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPremium) premiumContent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(start = 1.dp, bottom = 1.dp)
                            )
                        }
                        Text(
                            text = if (available > 0) "$available left" else "Full",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = if (isPremium) {
                                if (available <= 0) premiumContent.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary
                            } else {
                                if (available <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            }
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun exportToCalendar(
    context: android.content.Context,
    dateID: String,
    startTimeStr: String,
    endTimeStr: String,
    title: String,
    location: String,
    description: String
) {
    try {
        val date = LocalDate.parse(dateID)
        val startTime = SessionsClass.parseTime(startTimeStr)
        val endTime = SessionsClass.parseTime(endTimeStr)

        val startDateTime = LocalDateTime.of(date, startTime)
        val endDateTime = LocalDateTime.of(date, endTime)

        val startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)

        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun exportBookingToCalendar(context: android.content.Context, booking: BookingsClass) {
    exportToCalendar(
        context = context,
        dateID = booking.sessionDateID,
        startTimeStr = booking.startTime,
        endTimeStr = booking.endTime,
        title = "Exam: ${booking.examName}",
        location = booking.roomName,
        description = "Room: ${booking.roomName}\nBooking ID: ${booking.bookingID}"
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DetailedSessionItem(
    session: SessionsClass,
    userRole: Int,
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {},
    onBook: () -> Unit = {},
    onBookingDeleted: () -> Unit = {},
    onNavigateToLiveBooking: (String) -> Unit = {},
    examName: String? = null,
    otherUserName: String? = null,
    otherUserLabel: String? = null,
    showCapacity: Boolean = true,
    bookingData: BookingsClass? = null,
    isPastDate: Boolean = false,
    isAssignedToMe: Boolean = false
) {
    val room = session.rooms.values.firstOrNull() ?: return
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showDeleteBookingDialog by remember { mutableStateOf<BookingsClass?>(null) }

    val isBooked = bookingData != null
    val useGradient = true
    val isDark = LocalIsDarkTheme.current
    val contentColor = if (useGradient) DuckGradients.heroContentColor() else MaterialTheme.colorScheme.onSurface
    val secondaryColor = if (useGradient) DuckGradients.heroContentColorSecondary() else contentColor.copy(alpha = 0.7f)

    val bToDelete = showDeleteBookingDialog
    if (bToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteBookingDialog = null },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to delete the booking for '${bToDelete.examName}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteBookingDialog = null
                        bToDelete.deleteBooking { success ->
                            if (success) {
                                onBookingDeleted()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBookingDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { 
                if (bookingData != null) {
                    onNavigateToLiveBooking(bookingData.bookingID)
                } else {
                    expanded = !expanded 
                }
            },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (useGradient) 4.dp else 4.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (useGradient) Color.Transparent else MaterialTheme.colorScheme.surface, 
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(24.dp),
        border = if (useGradient && !isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f).copy(alpha = 0.25f)) else null
    ) {
        Box(modifier = if (useGradient) Modifier.background(DuckGradients.heroGradient()) else Modifier) {
            Column(modifier = Modifier.padding(bottom = if (isBooked) 16.dp else 0.dp)) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = examName ?: "Room ${room.roomName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        
                        if (examName != null) {
                            Text(
                                text = "Location: ${room.roomName}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = secondaryColor
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, null, Modifier.size(14.dp), tint = secondaryColor.copy(alpha = 0.5f))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${SessionsClass.formatTime(room.startTime)} - ${SessionsClass.formatTime(room.endTime)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryColor.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (isBooked) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (otherUserName != null) {
                                    Surface(
                                        color = if (useGradient) contentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "$otherUserLabel: $otherUserName",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor
                                        )
                                    }
                                }
                                
                                if (bookingData?.examDocumentUrl != null) {
                                    Surface(
                                        modifier = Modifier.clickable {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(bookingData.examDocumentUrl))
                                            context.startActivity(intent)
                                        },
                                        color = if (useGradient) contentColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Description, null, Modifier.size(14.dp), tint = if (useGradient) contentColor else MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = bookingData.examDocumentName ?: "Exam",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (useGradient) contentColor else MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (showCapacity && room.totalCap > 0) {
                            AvailabilityIntervals(room, isPremium = useGradient)
                        }
                    }
                    
                    // End Actions
                    Column(horizontalAlignment = Alignment.End) {
                        if (!isPastDate) {
                            if (userRole == 1 && !isBooked) {
                                Button(
                                    onClick = onBook,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    colors = if (useGradient) ButtonDefaults.buttonColors(containerColor = contentColor, contentColor = if (isDark) Color.Black else Color.White) else ButtonDefaults.buttonColors()
                                ) {
                                    Text("Book", style = MaterialTheme.typography.labelLarge)
                                }
                            } else {
                                Row {
                                    if (userRole == 0) {
                                        IconButton(onClick = onBook, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.AddCircle, null, tint = if (useGradient) contentColor else MaterialTheme.colorScheme.primary) }
                                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, tint = if (useGradient) contentColor else MaterialTheme.colorScheme.primary) }
                                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.DeleteOutline, null, tint = if (useGradient) Color(0xFFFFB4AB) else MaterialTheme.colorScheme.error) }
                                    } else if (userRole == 3 || isBooked || isAssignedToMe) {
                                        // Directions button – opens Google Maps walking navigation
                                        IconButton(onClick = {
                                            CampusLocations.openDirectionsToRoom(context, room.roomName)
                                        }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Directions, "Directions", tint = if (useGradient) contentColor else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(onClick = {
                                            if (bookingData != null) exportBookingToCalendar(context, bookingData)
                                            else if (isAssignedToMe) exportToCalendar(context, session.dateID, room.startTime, room.endTime, "Proctoring: ${room.roomName}", room.roomName, "Assignment")
                                        }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.CalendarToday, null, tint = if (useGradient) contentColor else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (bookingData == null) {
                            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = secondaryColor.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            IconButton(onClick = { onNavigateToLiveBooking(bookingData.bookingID) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = if (useGradient) contentColor else MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                if (isBooked && bookingData != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = if (useGradient) contentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    BookingProgressBar(currentStatus = bookingData.status, isPremium = useGradient)
                }

                AnimatedVisibility(visible = expanded && bookingData == null) {
                    AdminSessionDetails(
                        session = session,
                        room = room,
                        userRole = userRole,
                        onDeleteBookingRequested = { showDeleteBookingDialog = it },
                        onNavigateToLiveBooking = onNavigateToLiveBooking,
                        isPastDate = isPastDate,
                        showBookingsList = true,
                        onRefresh = onBookingDeleted,
                        isPremium = useGradient
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AdminSessionDetails(
    session: SessionsClass,
    room: RoomData,
    userRole: Int,
    onDeleteBookingRequested: (BookingsClass) -> Unit = {},
    onNavigateToLiveBooking: (String) -> Unit = {},
    isPastDate: Boolean = false,
    showBookingsList: Boolean = true,
    onRefresh: () -> Unit = {},
    isPremium: Boolean = false
) {
    val bookingsData = remember { mutableStateListOf<Triple<BookingsClass, String, String>>() }
    val proctorsData = remember { mutableStateListOf<Pair<String, String>>() }
    var isLoading by remember { mutableStateOf(false) }
    var showProctorDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val contentColor = if (isPremium) DuckGradients.heroContentColor() else MaterialTheme.colorScheme.onSurface
    val primaryColor = if (isPremium) DuckGradients.heroContentColor() else MaterialTheme.colorScheme.primary
    val secondaryColor = if (isPremium) DuckGradients.heroContentColorSecondary() else MaterialTheme.colorScheme.onSurfaceVariant

    val isDark = LocalIsDarkTheme.current

    LaunchedEffect(room.bookings, room.assignedProctors, showBookingsList) {
        bookingsData.clear()
        proctorsData.clear()
        if (room.bookings.isEmpty() && room.assignedProctors.isEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true

        // Load bookings efficiently without nested callbacks
        if (showBookingsList && room.bookings.isNotEmpty()) {
            room.bookings.forEach { bId ->
                try {
                    val booking = BookingsClass()
                    booking.getBooking(bId) { bSuccess ->
                        if (bSuccess) {
                            val faculty = UserMembersClass()
                            val student = UserMembersClass()
                            
                            // Load faculty data
                            faculty.getUserData(booking.facultyUID) { fSuccess ->
                                // Load student data
                                student.getUserData(booking.studentUID) { sSuccess ->
                                    bookingsData.add(
                                        Triple(
                                            booking,
                                            if (fSuccess) faculty.thisUserName else "Unknown Faculty",
                                            if (sSuccess) student.thisUserName else "Unknown Student"
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Silently handle errors
                }
            }
        }

        // Load proctors efficiently
        if (room.assignedProctors.isNotEmpty()) {
            room.assignedProctors.forEach { pUid ->
                try {
                    val proctor = UserMembersClass()
                    proctor.getUserData(pUid) { pSuccess ->
                        if (pSuccess) {
                            FacultySubclass(thisUID = pUid).getAssignmentForRoom(session.dateID, room.roomName) { assignment ->
                                val timeStr = if (assignment != null) {
                                    "${SessionsClass.formatTime(assignment.first)} - ${SessionsClass.formatTime(assignment.second)}"
                                } else {
                                    "N/A"
                                }
                                proctorsData.add(proctor.thisUserName to timeStr)
                            }
                        } else {
                            proctorsData.add("Unknown Proctor ($pUid)" to "N/A")
                        }
                    }
                } catch (e: Exception) {
                    // Silently handle errors
                }
            }
        }

        isLoading = false
    }

    if (showProctorDialog) {
        ProctorAssignmentDialog(
            dateID = session.dateID,
            startTime = room.startTime,
            endTime = room.endTime,
            onDismiss = { showProctorDialog = false },
            onAssign = { proctor ->
                AdminSubclass().assignProctor(session.dateID, room.roomName, proctor.thisUID) { success ->
                    if (success) {
                        showProctorDialog = false
                        onRefresh()
                    }
                }
            }
        )
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), thickness = 0.5.dp, color = if (isPremium) contentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant)

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = primaryColor)
            }
        } else {
            if (showBookingsList) {
                val uniqueFaculties = bookingsData.map { it.first.facultyUID }.distinct().size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bookings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryColor
                    )
                    if (userRole == 0) {
                        Surface(
                            color = if (isPremium) contentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$uniqueFaculties Faculties",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = contentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                if (bookingsData.isEmpty()) {
                    Text("No bookings for this session.", style = MaterialTheme.typography.bodySmall, color = secondaryColor.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                } else {
                    bookingsData.forEach { (booking, faculty, student) ->
                        Card(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .fillMaxWidth()
                                .clickable { onNavigateToLiveBooking(booking.bookingID) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPremium) {
                                    if (isDark) contentColor.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f).copy(alpha = 0.08f)
                                } else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = if (isPremium && !isDark) BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f).copy(alpha = 0.2f)) else null
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = booking.examName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = contentColor
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onNavigateToLiveBooking(booking.bookingID) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.OpenInNew, "Live", tint = primaryColor, modifier = Modifier.size(18.dp))
                                        }
                                        if (userRole != 3 && booking.examDocumentUrl != null) {
                                            IconButton(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(booking.examDocumentUrl))
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Download, "Download", tint = primaryColor, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        if (userRole == 0 && !isPastDate) {
                                            IconButton(
                                                onClick = { onDeleteBookingRequested(booking) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, "Delete", tint = if (isPremium) Color(0xFFFFB4AB) else MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = "Faculty: $faculty",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryColor
                                )
                                Text(
                                    text = "Student: $student",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryColor
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                    Icon(Icons.Default.Schedule, null, Modifier.size(12.dp), tint = secondaryColor.copy(alpha = 0.7f))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "${SessionsClass.formatTime(booking.startTime)} - ${SessionsClass.formatTime(booking.endTime)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (booking.specialInstructions.isNotBlank()) {
                                    Surface(
                                        color = if (isPremium) contentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = booking.specialInstructions,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryColor,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                BookingProgressBar(currentStatus = booking.status, isPremium = isPremium)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Proctor Assignment",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryColor
                )
                if (userRole == 0 && !isPastDate) {
                    TextButton(onClick = { showProctorDialog = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = primaryColor)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Assign", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = primaryColor)
                    }
                }
            }

            if (proctorsData.isEmpty()) {
                Surface(
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    color = if (isPremium) contentColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.ErrorOutline, null, tint = if (isPremium) Color(0xFFFFB4AB) else MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("No proctors assigned yet.", style = MaterialTheme.typography.bodySmall, color = if (isPremium) Color(0xFFFFB4AB) else MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    proctorsData.forEach { (name, time) ->
                        Surface(
                            color = if (isPremium) contentColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(32.dp).background(if (isPremium) contentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp), tint = primaryColor)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = contentColor)
                                    Text(
                                        text = "Assigned Time: $time",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ProctorAssignmentDialog(
    dateID: String,
    startTime: String,
    endTime: String,
    onDismiss: () -> Unit,
    onAssign: (UserMembersClass) -> Unit
) {
    var potentialProctors by remember { mutableStateOf<List<Pair<UserMembersClass, Boolean>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(dateID, startTime, endTime) {
        AdminSubclass().getPotentialProctors { users ->
            val results = mutableListOf<Pair<UserMembersClass, Boolean>>()
            var processed = 0
            if (users.isEmpty()) {
                isLoading = false
                return@getPotentialProctors
            }

            users.forEach { user ->
                FacultySubclass(thisUID = user.thisUID).getAvailability(dateID) { ranges ->
                    val isAvailable = ranges.any { range ->
                        val rs = LocalTime.parse(range.first)
                        val re = LocalTime.parse(range.second)
                        val targetS = LocalTime.parse(startTime)
                        val targetE = LocalTime.parse(endTime)
                        (rs.isBefore(targetS) || rs == targetS) && (re.isAfter(targetE) || re == targetE)
                    }
                    results.add(user to isAvailable)
                    processed++
                    if (processed == users.size) {
                        potentialProctors = results.sortedByDescending { it.second }
                        isLoading = false
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Proctor", fontWeight = FontWeight.Black) },
        text = {
            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (potentialProctors.isEmpty()) {
                Text("No potential proctors found.")
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(items = potentialProctors) { (proctor, isAvailable) ->
                        Surface(
                            onClick = { if (isAvailable) onAssign(proctor) },
                            enabled = isAvailable,
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAvailable) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { 
                                    Text(
                                        proctor.thisUserName, 
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAvailable) MaterialTheme.colorScheme.onSurface else Color.Gray
                                    ) 
                                },
                                supportingContent = { 
                                    Text(
                                        if (isAvailable) "Available" else "Not scheduled for this time",
                                        color = if (isAvailable) Color(0xFF4CAF50) else Color.Red,
                                        style = MaterialTheme.typography.labelSmall
                                    ) 
                                },
                                leadingContent = {
                                    Icon(Icons.Default.Person, null, tint = if (isAvailable) MaterialTheme.colorScheme.primary else Color.Gray)
                                },
                                trailingContent = {
                                    if (isAvailable) {
                                        Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

data class BookingDisplayData(
    val session: SessionsClass,
    val examName: String? = null,
    val otherUserName: String? = null,
    val otherUserLabel: String? = null,
    val bookingData: BookingsClass? = null
)
