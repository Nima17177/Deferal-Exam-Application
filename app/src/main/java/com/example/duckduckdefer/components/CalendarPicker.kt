package com.example.duckduckdefer.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.ui.theme.LocalIsDarkTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * A reusable calendar picker component that displays a week or month view with date selection.
 * Updated with a premium gradient look to match the dashboard theme.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CalendarPicker(
    currentPivotDate: LocalDate,
    selectedDates: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    isMonthView: Boolean = false,
    dayStats: Map<LocalDate, DayStats> = emptyMap()
) {
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    val shortMonthFormatter = DateTimeFormatter.ofPattern("MMM")

    val headerText = if (isMonthView) {
        currentPivotDate.format(monthFormatter)
    } else {
        val weekEnd = currentPivotDate.plusDays(6)
        if (currentPivotDate.month == weekEnd.month) {
            "${currentPivotDate.format(shortMonthFormatter)} ${currentPivotDate.dayOfMonth} - ${weekEnd.dayOfMonth}"
        } else {
            "${currentPivotDate.format(shortMonthFormatter)} ${currentPivotDate.dayOfMonth} - ${weekEnd.format(shortMonthFormatter)} ${weekEnd.dayOfMonth}"
        }
    }

    val contentColor = DuckGradients.heroContentColor()
    val secondaryContentColor = DuckGradients.heroContentColorSecondary()

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(modifier = Modifier.background(DuckGradients.heroGradient()).padding(16.dp)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onPrev) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = if (isMonthView) "Prev Month" else "Prev Week", tint = contentColor)
                        }
                        Text(
                            text = headerText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = contentColor
                        )
                        IconButton(onClick = onNext) {
                            Icon(Icons.Default.ChevronRight, contentDescription = if (isMonthView) "Next Month" else "Next Week", tint = contentColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val days = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        days.forEach {
                            Text(
                                text = it,
                                fontSize = 12.sp,
                                color = secondaryContentColor,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isMonthView) {
                        val firstOfMonth = currentPivotDate.with(TemporalAdjusters.firstDayOfMonth())
                        val lastOfMonth = currentPivotDate.with(TemporalAdjusters.lastDayOfMonth())
                        val firstDayOfWeek = firstOfMonth.dayOfWeek.value // 1 (Mon) to 7 (Sun)
                        val daysInMonth = lastOfMonth.dayOfMonth

                        val emptySlots = firstDayOfWeek - 1

                        Column {
                            var currentDay = 1
                            val totalSlots = emptySlots + daysInMonth
                            val numRows = (totalSlots + 6) / 7

                            for (row in 0 until numRows) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                    for (col in 0 until 7) {
                                        val slotIndex = row * 7 + col
                                        if (slotIndex < emptySlots || currentDay > daysInMonth) {
                                            Spacer(modifier = Modifier.size(44.dp).weight(1f))
                                        } else {
                                            val date = firstOfMonth.withDayOfMonth(currentDay)
                                            DayItem(
                                                date = date,
                                                isSelected = selectedDates.contains(date),
                                                onDateSelected = onDateSelected,
                                                dayStat = dayStats[date],
                                                modifier = Modifier.weight(1f),
                                                contentColor = contentColor
                                            )
                                            currentDay++
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            for (i in 0..6) {
                                val date = currentPivotDate.plusDays(i.toLong())
                                DayItem(
                                    date = date,
                                    isSelected = selectedDates.contains(date),
                                    onDateSelected = onDateSelected,
                                    dayStat = dayStats[date],
                                    modifier = Modifier.weight(1f),
                                    contentColor = contentColor
                                )
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
private fun DayItem(
    date: LocalDate,
    isSelected: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    dayStat: DayStats? = null,
    modifier: Modifier = Modifier,
    contentColor: Color = DuckGradients.heroContentColor()
) {
    val isToday = date == LocalDate.now()
    val isDark = LocalIsDarkTheme.current
    val selectedBg = if (isDark) contentColor else MaterialTheme.colorScheme.primary
    val selectedTextColor = if (isDark) Color.Black else MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = modifier
            .padding(vertical = 4.dp)
            .clickable { onDateSelected(date) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSelected -> selectedBg
                        isToday -> contentColor.copy(alpha = 0.15f)
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                color = if (isSelected) selectedTextColor else contentColor,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        if (dayStat != null) {
            DayIndicators(dayStat, isPremium = true)
        } else {
            Spacer(modifier = Modifier.height(9.dp))
        }
    }
}
