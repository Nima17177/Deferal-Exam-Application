package com.example.duckduckdefer.screens.AdminScreens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.AdminSubclass
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

// ══════════════════════════════════════════════════════════
//  Data class for a feedback document
// ══════════════════════════════════════════════════════════

private data class FeedbackItem(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val category: String = "",
    val rating: Int = 0,
    val title: String = "",
    val body: String = "",
    val timestamp: Timestamp? = null,
    val status: String = "new"
)

// ══════════════════════════════════════════════════════════
//  View Feedback Screen (Admin only)
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewFeedbackScreen(
    user: AdminSubclass,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit = {}
) {
    var feedbackList by remember { mutableStateOf<List<FeedbackItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("All") }
    var expandedItemId by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    val filters = listOf("All", "General", "Bug Report", "Feature Request", "UI / Design", "Performance")

    // Load feedback from Firestore
    LaunchedEffect(Unit) {
        visible = true
        try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("Feedback")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            feedbackList = snapshot.documents.map { doc ->
                FeedbackItem(
                    id = doc.id,
                    userId = doc.getString("userId") ?: "",
                    userName = doc.getString("userName") ?: "Unknown",
                    category = doc.getString("category") ?: "General",
                    rating = doc.getLong("rating")?.toInt() ?: 0,
                    title = doc.getString("title") ?: "",
                    body = doc.getString("body") ?: "",
                    timestamp = doc.getTimestamp("timestamp"),
                    status = doc.getString("status") ?: "new"
                )
            }
        } catch (_: Exception) {
            // keep empty list on error
        }
        isLoading = false
    }

    val filteredList = if (selectedFilter == "All") feedbackList
    else feedbackList.filter { it.category == selectedFilter }

    // Stats
    val avgRating = if (feedbackList.isNotEmpty())
        feedbackList.map { it.rating }.average()
    else 0.0

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ) {
                val heroContent = DuckGradients.heroContentColor()
                val heroContentSecondary = DuckGradients.heroContentColorSecondary()
                Box(
                    modifier = Modifier
                        .background(
                            DuckGradients.heroGradient(),
                            RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                        )
                        .statusBarsPadding()
                ) {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "User Feedback",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                Text(
                                    text = "${feedbackList.size} submissions",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = heroContentSecondary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = heroContent
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = onOpenMenu) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Open Menu",
                                    tint = heroContent
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Stats Summary Card ────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(600)) + expandVertically(tween(600))
                    ) {
                        FeedbackStatsCard(
                            totalCount = feedbackList.size,
                            avgRating = avgRating,
                            bugCount = feedbackList.count { it.category == "Bug Report" },
                            featureCount = feedbackList.count { it.category == "Feature Request" }
                        )
                    }
                }

                // ── Filter Chips ──────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn()
                    ) {
                        FilterChipRow(
                            filters = filters,
                            selectedFilter = selectedFilter,
                            onFilterSelected = { selectedFilter = it }
                        )
                    }
                }

                // ── Feedback Items ────────────────────────────────
                if (filteredList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Inbox,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (selectedFilter == "All") "No feedback yet"
                                    else "No \"$selectedFilter\" feedback",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                } else {
                    items(filteredList, key = { it.id }) { item ->
                        FeedbackCard(
                            item = item,
                            isExpanded = expandedItemId == item.id,
                            onToggleExpand = {
                                expandedItemId = if (expandedItemId == item.id) null else item.id
                            },
                            onMarkReviewed = {
                                // Update Firestore status
                                FirebaseFirestore.getInstance()
                                    .collection("Feedback")
                                    .document(item.id)
                                    .update("status", "reviewed")
                                feedbackList = feedbackList.map {
                                    if (it.id == item.id) it.copy(status = "reviewed") else it
                                }
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  Stats Summary Card
// ══════════════════════════════════════════════════════════

@Composable
private fun FeedbackStatsCard(
    totalCount: Int,
    avgRating: Double,
    bugCount: Int,
    featureCount: Int
) {
    val heroContent = DuckGradients.heroContentColor()
    val heroContentSecondary = DuckGradients.heroContentColorSecondary()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DuckGradients.heroGradient())
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatMini(label = "Total", value = "$totalCount", color = heroContent)
                StatMini(
                    label = "Avg Rating",
                    value = String.format("%.1f", avgRating),
                    color = heroContent
                )
                StatMini(label = "Bugs", value = "$bugCount", color = heroContent)
                StatMini(label = "Ideas", value = "$featureCount", color = heroContent)
            }
        }
    }
}

@Composable
private fun StatMini(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f)
        )
    }
}

// ══════════════════════════════════════════════════════════
//  Filter Chips
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipRow(
    filters: List<String>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.take(3).forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(filter, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.drop(3).forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(filter, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  Individual Feedback Card
// ══════════════════════════════════════════════════════════

@Composable
private fun FeedbackCard(
    item: FeedbackItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onMarkReviewed: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    val formattedDate = item.timestamp?.toDate()?.let { dateFormat.format(it) } ?: "Unknown date"

    val categoryIcon = when (item.category) {
        "Bug Report" -> Icons.Default.BugReport
        "Feature Request" -> Icons.Default.Lightbulb
        "UI / Design" -> Icons.Default.Palette
        "Performance" -> Icons.Default.Speed
        else -> Icons.Default.ChatBubbleOutline
    }

    val isReviewed = item.status == "reviewed"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onToggleExpand),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 4.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category icon
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.userName} · ${item.category}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Stars
                Row {
                    repeat(item.rating) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expanded content
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )

                        if (isReviewed) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Reviewed",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            TextButton(onClick = onMarkReviewed) {
                                Icon(
                                    Icons.Default.DoneAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mark Reviewed", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}



