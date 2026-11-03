package com.example.duckduckdefer.screens.AdminScreens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.duckduckdefer.components.AppTextField
import com.example.duckduckdefer.components.TimeSlotPicker
import com.example.duckduckdefer.useraccountclasses.SessionsClass
import com.example.duckduckdefer.useraccountclasses.RoomData
import java.time.Duration

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RoomSetupScreen(
    initialSession: SessionsClass? = null,
    existingRooms: Map<String, RoomData> = emptyMap(),
    onAddSession: (SessionsClass) -> Unit
) {
    val initialRoom = initialSession?.rooms?.values?.firstOrNull()
    var roomNumber by remember { mutableStateOf(initialRoom?.roomName ?: "") }
    var capacity by remember { mutableStateOf(initialRoom?.totalCap?.toString() ?: "") }
    var startTime by remember { mutableStateOf(initialRoom?.startTime ?: "Start Time") }
    var endTime by remember { mutableStateOf(initialRoom?.endTime ?: "End Time") }

    val conflicts = remember(roomNumber, capacity, startTime, endTime, existingRooms) {
        val warnings = mutableListOf<String>()
        val capInt = capacity.toIntOrNull() ?: 0
        val oldRoomName = initialSession?.rooms?.keys?.firstOrNull() ?: ""

        // 1. Name Conflict
        if (initialSession == null) {
            if (existingRooms.containsKey(roomNumber)) {
                warnings.add("Room '$roomNumber' already has a session on this date.")
            }
        } else {
            if (roomNumber != oldRoomName && existingRooms.containsKey(roomNumber)) {
                warnings.add("Another session for '$roomNumber' exists. Saving will overwrite it.")
            }
        }

        // 2. General bookings warning
        initialRoom?.let { room ->
            if (room.bookings.isNotEmpty()) {
                warnings.add("⚠ This session has ${room.bookings.size} active booking(s). Changes may affect them.")
            }
        }

        // 3. Existing Bookings Conflict (When Editing)
        initialRoom?.let { room ->
            try {
                val oldStart = SessionsClass.parseTime(room.startTime)
                val oldEnd = SessionsClass.parseTime(room.endTime)
                val newStart = if (startTime != "Start Time") SessionsClass.parseTime(startTime) else null
                val newEnd = if (endTime != "End Time") SessionsClass.parseTime(endTime) else null

                if (newStart != null && newEnd != null) {
                    val startOffset = (Duration.between(newStart, oldStart).toMinutes() / 30).toInt()
                    if (startOffset < 0) {
                        val numCut = -startOffset
                        for (i in 0 until numCut.coerceAtMost(room.availableSeats.size)) {
                            if (room.availableSeats[i] < room.totalCap) {
                                warnings.add("New start time cuts off existing bookings!")
                                break
                            }
                        }
                    }

                    val endDiff = (Duration.between(oldEnd, newEnd).toMinutes() / 30).toInt()
                    if (endDiff < 0) {
                        val startIndex = (room.availableSeats.size + endDiff).coerceAtLeast(0)
                        for (i in startIndex until room.availableSeats.size) {
                            if (room.availableSeats[i] < room.totalCap) {
                                warnings.add("New end time cuts off existing bookings!")
                                break
                            }
                        }
                    }

                    if (capInt < room.totalCap) {
                        val maxBooked = room.availableSeats.minOfOrNull { room.totalCap - it } ?: 0
                        if (maxBooked > capInt) {
                            warnings.add("Capacity ($capInt) is less than current bookings ($maxBooked)!")
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        warnings
    }

    var showConfirmDialog by remember { mutableStateOf(false) }

    // Helper to build the final session and submit
    val doSave: () -> Unit = {
        if (roomNumber.isNotBlank() && capacity.isNotBlank() &&
            startTime != "Start Time" && endTime != "End Time" &&
            startTime < endTime) {

            val roomData = RoomData(
                roomName = roomNumber,
                totalCap = capacity.toIntOrNull() ?: 0,
                startTime = startTime,
                endTime = endTime
            )

            onAddSession(
                SessionsClass(
                    dateID = initialSession?.dateID ?: "",
                    rooms = mutableMapOf(roomNumber to roomData)
                )
            )

            if (initialSession == null) {
                roomNumber = ""
                capacity = ""
                startTime = "Start Time"
                endTime = "End Time"
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm Changes", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("This session has active bookings. Are you sure you want to save these changes?")
                    Spacer(Modifier.height(12.dp))
                    conflicts.forEach { warning ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(warning, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showConfirmDialog = false; doSave() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Save Anyway", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (initialSession == null) "Room Setup" else "Edit Room & Time",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            AppTextField(
                value = roomNumber,
                onValueChange = { roomNumber = it },
                label = "Room Number",
                leadingIcon = Icons.Default.MeetingRoom,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(
                value = capacity,
                onValueChange = { capacity = it },
                label = "Total Capacity",
                leadingIcon = Icons.Default.People,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TimeSlotPicker(
                    label = "Start",
                    selectedTime = startTime,
                    onTimeSelected = {
                        startTime = it
                        if (endTime != "End Time" && it >= endTime) {
                            endTime = "End Time"
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                TimeSlotPicker(
                    label = "End",
                    selectedTime = endTime,
                    onTimeSelected = { endTime = it },
                    minTime = if (startTime != "Start Time") startTime else null,
                    modifier = Modifier.weight(1f)
                )
            }

            if (conflicts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        conflicts.forEach { warning ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    warning,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (roomNumber.isNotBlank() && capacity.isNotBlank() &&
                        startTime != "Start Time" && endTime != "End Time" &&
                        startTime < endTime) {

                        if (conflicts.isNotEmpty() && initialSession != null) {
                            showConfirmDialog = true
                        } else {
                            doSave()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (conflicts.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    if (initialSession == null) "Add Room" else "Save Changes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
