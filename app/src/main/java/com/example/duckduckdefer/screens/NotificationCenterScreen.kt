package com.example.duckduckdefer.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.InAppNotification
import com.example.duckduckdefer.useraccountclasses.NotificationIconType
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import com.example.duckduckdefer.viewmodels.NotificationCenterViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    user: UserMembersClass,
    viewModel: NotificationCenterViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToBooking: (String) -> Unit = {},
    onNavigateToProctorAssignment: () -> Unit = {},
    onNavigateToManageUsers: () -> Unit = {}
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val notificationRepository = remember { com.example.duckduckdefer.useraccountclasses.NotificationRepository() }

    var showClearAllDialog by remember { mutableStateOf(false) }
    var showMarkAllReadDialog by remember { mutableStateOf(false) }

    LaunchedEffect(user.thisUID) {
        viewModel.initialize(user.thisUID)
    }

    // Clear all confirmation dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Notifications") },
            text = { Text("Are you sure you want to delete all notifications? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllNotifications()
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Mark all as read confirmation dialog
    if (showMarkAllReadDialog) {
        AlertDialog(
            onDismissRequest = { showMarkAllReadDialog = false },
            title = { Text("Mark All as Read") },
            text = { Text("Mark all notifications as read?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.markAllAsRead()
                    showMarkAllReadDialog = false
                }) {
                    Text("Mark All Read")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkAllReadDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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
                        .background(DuckGradients.heroGradient(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .statusBarsPadding()
                ) {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Notifications",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                if (unreadCount > 0) {
                                    Text(
                                        text = "$unreadCount unread",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = heroContentSecondary
                                    )
                                } else {
                                    Text(
                                        text = "All caught up!",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = heroContentSecondary
                                    )
                                }
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
                            // Mark all as read button
                            if (unreadCount > 0) {
                                IconButton(onClick = { showMarkAllReadDialog = true }) {
                                    Icon(
                                        Icons.Default.DoneAll,
                                        contentDescription = "Mark All Read",
                                        tint = heroContent
                                    )
                                }
                            }
                            // Clear all button
                            if (notifications.isNotEmpty()) {
                                IconButton(onClick = { showClearAllDialog = true }) {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = "Clear All",
                                        tint = heroContent
                                    )
                                }
                            }
                            IconButton(onClick = onOpenMenu) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Menu",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                notifications.isEmpty() -> {
                    EmptyNotificationsState(
                        onCreateTestNotification = {
                            notificationRepository.createTestNotification(user.thisUID)
                        }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = notifications,
                            key = { it.id }
                        ) { notification ->
                            NotificationItem(
                                notification = notification,
                                onMarkAsRead = { viewModel.markAsRead(notification.id) },
                                onDelete = { viewModel.deleteNotification(notification.id) },
                                onClick = {
                                    // Mark as read when clicked
                                    if (!notification.isRead) {
                                        viewModel.markAsRead(notification.id)
                                    }
                                    // Navigate based on type
                                    when (notification.type) {
                                        "chat" -> {
                                            notification.conversationId?.let { onNavigateToChat(it) }
                                        }
                                        "booking_created", "status_update", "incident" -> {
                                            notification.bookingId?.let { onNavigateToBooking(it) }
                                        }
                                        "proctor_assigned" -> {
                                            // Navigation to availability screen
                                            // Since we don't have a direct callback here yet, we can use the back handler 
                                            // or add a new callback. For consistency with MainActivity:
                                            // (Implicitly handles via onBack if needed, or we add callback)
                                        }
                                        "proctor_needed" -> {
                                            onNavigateToProctorAssignment()
                                        }
                                        "manage_users" -> {
                                            onNavigateToManageUsers()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotificationsState(
    onCreateTestNotification: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.NotificationsOff,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Notifications",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You're all caught up!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            // Debug: Test notification button
            OutlinedButton(onClick = onCreateTestNotification) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Test Notification")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationItem(
    notification: InAppNotification,
    onMarkAsRead: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true // Immediate dismissal
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (!notification.isRead) {
                        onMarkAsRead()
                    }
                    false // Snap back after marking as read
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            if (direction == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox

            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    else -> Color.Transparent
                },
                label = "swipe_color"
            )
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Done
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                else -> null
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .background(color, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                icon?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = if (direction == SwipeToDismissBoxValue.EndToStart)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        content = {
            NotificationCard(
                notification = notification,
                onClick = onClick
            )
        }
    )
}

@Composable
private fun NotificationCard(
    notification: InAppNotification,
    onClick: () -> Unit
) {
    val iconColor = getIconBackgroundColor(notification.getIconType())
    
    // Blending primaryContainer with surface to create an opaque highlight color
    val surfaceColor = if (notification.isRead) {
        MaterialTheme.colorScheme.surface
    } else {
        lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer, 0.15f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = surfaceColor,
        border = if (!notification.isRead) BorderStroke(1.dp, iconColor.copy(alpha = 0.15f)) else null,
        tonalElevation = if (notification.isRead) 2.dp else 6.dp,
        shadowElevation = if (notification.isRead) 4.dp else 10.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left color strip - Category branding
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.verticalGradient(
                            listOf(iconColor, iconColor.copy(alpha = 0.5f))
                        )
                    )
            )
//Test
            Row(
                modifier = Modifier
                    .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Category Icon with premium container
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    iconColor.copy(alpha = 0.12f),
                                    iconColor.copy(alpha = 0.02f)
                                )
                            )
                        )
                        .border(1.dp, iconColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getNotificationIcon(notification.getIconType()),
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Notification Content
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = notification.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (notification.isRead) FontWeight.Bold else FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = notification.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (notification.isRead) 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else 
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        // Category Chip
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = notification.getCategoryDisplayName(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        // Time ago label
                        notification.timestamp?.let { timestamp ->
                            Text(
                                text = formatTimestamp(timestamp.toDate()),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getNotificationIcon(type: NotificationIconType): ImageVector {
    return when (type) {
        NotificationIconType.MESSAGE -> Icons.AutoMirrored.Filled.Message
        NotificationIconType.BOOKING -> Icons.Default.Event
        NotificationIconType.UPDATE -> Icons.Default.Update
        NotificationIconType.WARNING -> Icons.Default.Warning
        NotificationIconType.ASSIGNMENT -> Icons.Default.AssignmentInd
        NotificationIconType.INFO -> Icons.Default.Info
    }
}

@Composable
private fun getIconBackgroundColor(type: NotificationIconType): Color {
    return when (type) {
        NotificationIconType.MESSAGE -> Color(0xFF2196F3) // Blue
        NotificationIconType.BOOKING -> Color(0xFF4CAF50) // Green
        NotificationIconType.UPDATE -> Color(0xFF9C27B0) // Purple
        NotificationIconType.WARNING -> Color(0xFFFF9800) // Orange
        NotificationIconType.ASSIGNMENT -> Color(0xFF00BCD4) // Cyan
        NotificationIconType.INFO -> Color(0xFF607D8B) // Blue Grey
    }
}

private fun formatTimestamp(date: Date): String {
    val now = System.currentTimeMillis()
    val diff = now - date.time

    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}
