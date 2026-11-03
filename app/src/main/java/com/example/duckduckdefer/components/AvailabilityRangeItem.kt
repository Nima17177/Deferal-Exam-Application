package com.example.duckduckdefer.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.ui.theme.DuckGradients
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AvailabilityRangeItem(
    startTime: String,
    endTime: String,
    isAssigned: Boolean,
    isEditable: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val outFmt = DateTimeFormatter.ofPattern("h:mm a")
    val inFmt = DateTimeFormatter.ofPattern("HH:mm")
    
    val startParsed = LocalTime.parse(startTime, inFmt)
    val endParsed = LocalTime.parse(endTime, inFmt)
    
    // Using a darker shade of brand orange for a premium, integrated look
    val proctorAccent = Color(0xFFE6761A)
    val surfaceColor = if (isAssigned) proctorAccent.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isAssigned) 6.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Accent Bar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(
                        if (isAssigned) proctorAccent 
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
            )

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isAssigned) Icons.Default.Lock else Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isAssigned) proctorAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isAssigned) "Assigned Shift" else "Time Range",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isAssigned) proctorAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            letterSpacing = 0.5.sp
                        )
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = startParsed.format(outFmt).lowercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isAssigned) proctorAccent else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = " — ",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Text(
                            text = endParsed.format(outFmt).lowercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isAssigned) proctorAccent else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    if (isAssigned) {
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = proctorAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "CONFIRMED BY ADMIN",
                                color = proctorAccent,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                if (isEditable && !isAssigned) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = onEdit,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        
                        Spacer(Modifier.width(8.dp))
                        
                        Surface(
                            onClick = onDelete,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                } else if (isAssigned) {
                    // Accent icon for assigned shifts
                    Surface(
                        shape = CircleShape,
                        color = proctorAccent.copy(alpha = 0.1f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = proctorAccent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
