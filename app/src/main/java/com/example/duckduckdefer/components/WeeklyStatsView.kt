package com.example.duckduckdefer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.ui.theme.DuckGradients
import java.time.LocalDate

data class DayStats(
    val date: LocalDate,
    var available: Int = 0,
    var booked: Int = 0,
    var proctored: Int = 0,
    var needsAttention: Boolean = false // Added for Admin alerts
)

@Composable
fun DayIndicators(dayStat: DayStats, isPremium: Boolean = false) {
    val today = LocalDate.now()
    val isPast = dayStat.date.isBefore(today)
    
    val premiumContent = if (isPremium) DuckGradients.heroContentColor() else Color.Unspecified
    val baseIndicatorColor = if (isPremium) premiumContent else Color.Gray
    // Switched to Darker Brand Orange (Burnt Orange) for consistency
    val proctorAccent = if (isPremium) premiumContent else Color(0xFFE6761A)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.height(11.dp) 
    ) {
        if (dayStat.needsAttention && !isPast) {
            // Admin Alert Dot
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(if (isPremium) Color(0xFFFFB4AB) else Color.Red, CircleShape)
            )
        }

        if (isPast) {
            // Indicator for past days
            if (dayStat.available > 0 || dayStat.booked > 0 || dayStat.proctored > 0) {
                Box(modifier = Modifier.width(12.dp).height(2.dp).background(baseIndicatorColor.copy(alpha = 0.4f)))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                // Sessions Made/Available (Blue or White)
                if (dayStat.available > 0) {
                    Box(modifier = Modifier.width(4.dp).height(2.dp).background(if (isPremium) premiumContent.copy(alpha = 0.8f) else Color.Blue))
                }
                // Sessions Booked (Green or Brand Orange)
                if (dayStat.booked > 0) {
                    Box(modifier = Modifier.width(4.dp).height(2.dp).background(if (isPremium) Color(0xFFFF8C2A) else Color(0xFF4CAF50)))
                }
                // Proctor Sessions Booked (Burnt Orange or White)
                if (dayStat.proctored > 0) {
                    Box(modifier = Modifier.width(4.dp).height(2.dp).background(proctorAccent))
                }
            }
        }
    }
}

@Composable
fun WeeklyStatsView(
    stats: List<DayStats>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val daysOfWeek = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevWeek, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Previous Week",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            stats.forEachIndexed { index, dayStat ->
                val isToday = dayStat.date == today
                val isSelected = dayStat.date == selectedDate
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(40.dp)
                        .clickable { onDateSelected(dayStat.date) }
                ) {
                    Text(
                        text = if (index < daysOfWeek.size) daysOfWeek[index] else "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = dayStat.date.dayOfMonth.toString(),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    DayIndicators(dayStat)
                }
            }
        }

        IconButton(onClick = onNextWeek, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Next Week",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
