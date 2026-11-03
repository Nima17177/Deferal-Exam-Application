package com.example.duckduckdefer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.ui.theme.LocalIsDarkTheme

@Composable
fun BookingProgressBar(currentStatus: Int, isPremium: Boolean = false) {
    val stages = listOf(
        "Booked", "Paid", "Attended", "Finished", "Dropped", "Received"
    )

    val isDark = LocalIsDarkTheme.current
    // Theme-aware active color for premium mode
    val activeColor = if (isPremium) DuckGradients.heroContentColor() else MaterialTheme.colorScheme.primary
    val inactiveColor = activeColor.copy(alpha = 0.15f)
    // Check icon needs to contrast against the filled circle
    val checkTint = if (isPremium) {
        if (isDark) MaterialTheme.colorScheme.primary else Color.White
    } else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        stages.forEachIndexed { index, stage ->
            val stageNum = index + 1
            val isCompleted = stageNum <= currentStatus
            val isCurrent = stageNum == currentStatus && currentStatus < 6

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // Connection Lines
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(if (index > 0 && stageNum <= currentStatus) activeColor else if (index > 0) inactiveColor else Color.Transparent)
                        )
                        Spacer(Modifier.size(18.dp)) // Circle size
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(if (index < stages.size - 1 && stageNum < currentStatus) activeColor else if (index < stages.size - 1) inactiveColor else Color.Transparent)
                        )
                    }

                    // Circle
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                if (isCompleted) activeColor else if (isCurrent) activeColor.copy(alpha = 0.1f) else Color.Transparent,
                                CircleShape
                            )
                            .border(
                                1.5.dp, 
                                if (stageNum <= currentStatus) activeColor else inactiveColor, 
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = checkTint,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stage,
                    fontSize = 8.sp,
                    fontWeight = if (isCurrent || (stageNum == 6 && currentStatus == 6)) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (stageNum <= currentStatus) activeColor else activeColor.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    lineHeight = 10.sp
                )
            }
        }
    }
}
