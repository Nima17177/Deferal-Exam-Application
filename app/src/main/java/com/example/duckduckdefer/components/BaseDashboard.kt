package com.example.duckduckdefer.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun BaseDashboard(
    modifier: Modifier = Modifier,
    userName: String,
    // roleName removed to reduce redundancy since it's already in the TopAppBar
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    weeklyStats: List<DayStats>,
    onPrevWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    statCards: @Composable RowScope.() -> Unit,
    listHeader: @Composable (ColumnScope.() -> Unit)? = null,
    listContent: LazyListScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Greeting Header
        Text(
            text = "Welcome, $userName",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 12.dp)
        )

        // Weekly Stats / Date Selector moved to top for better hierarchy
        if (weeklyStats.isNotEmpty()) {
            WeeklyStatsView(
                stats = weeklyStats,
                selectedDate = selectedDate,
                onDateSelected = onDateSelected,
                onPrevWeek = onPrevWeek,
                onNextWeek = onNextWeek
            )
        }

        // Daily Summary Title
        Text(
            text = "Status for ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd"))}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            statCards()
        }

        Spacer(modifier = Modifier.height(24.dp))

        listHeader?.invoke(this)

        // List Content
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            listContent()
        }
    }
}
