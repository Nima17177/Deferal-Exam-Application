package com.example.duckduckdefer.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A notification bell icon button with badge support.
 * Shows unread count and animates when there are new notifications.
 */
@Composable
fun NotificationBell(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    showAnimation: Boolean = true
) {
    // Animation for bell shake when there are unread notifications
    var shouldAnimate by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (shouldAnimate) 15f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bell_rotation"
    )

    // Trigger animation when unread count changes and is > 0
    LaunchedEffect(unreadCount) {
        if (unreadCount > 0 && showAnimation) {
            shouldAnimate = true
            delay(100)
            shouldAnimate = false
            delay(100)
            shouldAnimate = true
            delay(100)
            shouldAnimate = false
        }
    }

    Box(modifier = modifier) {
        IconButton(onClick = onClick) {
            BadgedBox(
                badge = {
                    if (unreadCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (unreadCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                    contentDescription = "Notifications${if (unreadCount > 0) " ($unreadCount unread)" else ""}",
                    tint = tint,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

/**
 * A compact version of the notification bell for use in tight spaces.
 */
@Composable
fun NotificationBellCompact(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Box(modifier = modifier) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp)
        ) {
            Box {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
                if (unreadCount > 0) {
                    Badge(
                        modifier = Modifier.offset(x = 8.dp, y = (-4).dp),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text(
                            text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

