package com.example.duckduckdefer.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * A reusable time picker component using Material 3 ExposedDropdownMenuBox.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TimeSlotPicker(
    label: String,
    selectedTime: String,
    onTimeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    minTime: String? = null,
    maxTime: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val displayFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val dataFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    val parseTimeHelper = remember {
        { s: String? ->
            if (s == null || s.isBlank() || s.contains("Time") || s == "Start" || s == "End") {
                null
            } else {
                val formats = listOf("HH:mm", "h:mm a", "H:mm")
                var parsed: LocalTime? = null
                for (f in formats) {
                    try {
                        parsed = LocalTime.parse(s, DateTimeFormatter.ofPattern(f))
                        break
                    } catch (_: DateTimeParseException) {}
                }
                parsed ?: try { LocalTime.parse(s) } catch (_: Exception) { null }
            }
        }
    }

    val displayText = remember(selectedTime) {
        parseTimeHelper(selectedTime)?.format(displayFormatter) ?: ""
    }

    val timeSlots = remember(minTime, maxTime, label) {
        val slots = mutableListOf<Pair<String, String>>()
        
        // Default range: 8 AM to 8 PM
        var startLimit = parseTimeHelper(minTime) ?: LocalTime.of(8, 0)
        val endLimit = parseTimeHelper(maxTime) ?: LocalTime.of(20, 0)

        // If this is an "End" or "Until" picker, it must start AFTER the start time
        if (label.contains("End", ignoreCase = true) || label.contains("Until", ignoreCase = true)) {
            // Note: if minTime is already 8 PM, we can't add 30 mins, but usually business logic prevents this
            if (startLimit.isBefore(LocalTime.of(23, 30))) {
                startLimit = startLimit.plusMinutes(30)
            }
        }

        var curr = startLimit
        if (!curr.isAfter(endLimit)) {
            while (!curr.isAfter(endLimit)) {
                slots.add(curr.format(displayFormatter) to curr.format(dataFormatter))
                if (curr == endLimit) break
                val next = curr.plusMinutes(30)
                if (next.isBefore(curr)) break 
                curr = next
            }
        }
        slots
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            if (timeSlots.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No available slots") },
                    onClick = { expanded = false },
                    enabled = false
                )
            } else {
                timeSlots.forEach { (display, data) ->
                    DropdownMenuItem(
                        text = { Text(display) },
                        onClick = {
                            onTimeSelected(data)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
