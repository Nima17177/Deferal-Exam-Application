package com.example.duckduckdefer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Reusable profile avatar component that displays either:
 * - The user's profile picture (if available)
 * - Initials-based fallback avatar
 *
 * @param profilePictureUrl URL to the profile picture (can be empty)
 * @param name User's display name (used for initials fallback)
 * @param size Size of the avatar
 * @param onClick Optional click handler (e.g., navigate to profile)
 * @param showMessageBadge Whether to show a message icon badge
 * @param onMessageClick Click handler for the message badge
 */
@Composable
fun ProfileAvatar(
    profilePictureUrl: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    onClick: (() -> Unit)? = null,
    showMessageBadge: Boolean = false,
    onMessageClick: (() -> Unit)? = null
) {
    val initials = getInitials(name)
    val backgroundColor = getColorForName(name)

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
    ) {
        if (profilePictureUrl.isNotEmpty()) {
            AsyncImage(
                model = profilePictureUrl,
                contentDescription = "Profile picture of $name",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Initials fallback
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                color = backgroundColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.4f).sp
                    )
                }
            }
        }

        // Message badge
        if (showMessageBadge && onMessageClick != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.4f)
                    .clickable(onClick = onMessageClick),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Message",
                        modifier = Modifier.size(size * 0.24f),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/**
 * Get initials from a name
 */
private fun getInitials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts.first().first()}${parts.last().first()}".uppercase()
    }
}

/**
 * Generate a consistent color based on the name
 */
private fun getColorForName(name: String): Color {
    val colors = listOf(
        Color(0xFF1976D2), // Blue
        Color(0xFF388E3C), // Green
        Color(0xFFD32F2F), // Red
        Color(0xFF7B1FA2), // Purple
        Color(0xFFF57C00), // Orange
        Color(0xFF0097A7), // Cyan
        Color(0xFF5D4037), // Brown
        Color(0xFF455A64), // Blue Grey
        Color(0xFFC2185B), // Pink
        Color(0xFF00796B)  // Teal
    )

    val hash = name.hashCode().let { if (it < 0) -it else it }
    return colors[hash % colors.size]
}
