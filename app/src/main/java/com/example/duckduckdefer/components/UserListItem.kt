package com.example.duckduckdefer.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.useraccountclasses.UserMembersClass

@Composable
fun UserListItem(
    user: UserMembersClass,
    onApprove: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    onDeactivate: (() -> Unit)? = null,
    onReactivate: (() -> Unit)? = null,
    onPromote: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val statusColor = when (user.thisApprovalStatus) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> Color(0xFF4CAF50)
        -1 -> MaterialTheme.colorScheme.error
        else -> Color.Gray
    }

    val targetCardColor = when (user.thisApprovalStatus) {
        0 -> MaterialTheme.colorScheme.surface
        1 -> MaterialTheme.colorScheme.surface
        -1 -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }

    val cardColor by animateColorAsState(
        targetValue = targetCardColor,
        animationSpec = tween(durationMillis = 500),
        label = "cardColorAnimation"
    )

    val roleIcon: ImageVector = when (user.thisRole) {
        0 -> Icons.Default.AdminPanelSettings
        1 -> Icons.Default.School
        2 -> Icons.Default.Badge
        else -> Icons.Default.Person
    }

    val roleName = when (user.thisRole) {
        0 -> "Admin"
        1 -> "Faculty"
        2 -> "Proctor"
        else -> "Student"
    }

    val statusText = when (user.thisApprovalStatus) {
        0 -> "PENDING APPROVAL"
        1 -> "ACTIVE"
        -1 -> "DEACTIVATED"
        else -> "UNKNOWN"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp
    ) {
        // Premium accent border on the left
        Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left color strip
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.verticalGradient(
                            listOf(statusColor, statusColor.copy(alpha = 0.5f))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Role Icon with premium styling
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    statusColor.copy(alpha = 0.12f),
                                    statusColor.copy(alpha = 0.02f)
                                )
                            )
                        )
                        .border(1.dp, statusColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = roleIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.thisUserName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = user.thisEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Role Badge
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = roleName,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        // Status Text
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = statusColor.copy(alpha = 0.9f),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    when (user.thisApprovalStatus) {
                        0 -> { // Pending
                            IconButton(
                                onClick = { onDecline?.invoke() },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                )
                            ) {
                                Icon(Icons.Default.Close, "Reject", tint = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { onApprove?.invoke() },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                                )
                            ) {
                                Icon(Icons.Default.Check, "Approve", tint = Color(0xFF2E7D32))
                            }
                        }
                        1 -> { // Active
                            if (user.thisRole == 1) { // Faculty
                                IconButton(
                                    onClick = { onPromote?.invoke() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    )
                                ) {
                                    Icon(Icons.Default.Shield, "Promote to Admin", tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            onDeactivate?.let {
                                FilledTonalButton(
                                    onClick = it,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Deactivate", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
