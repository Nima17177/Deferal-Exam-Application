package com.example.duckduckdefer.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.duckduckdefer.ui.theme.*
import kotlinx.coroutines.launch
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import kotlin.math.cos
import kotlin.math.sin

// ──────────────────────────────────────────────────────────────────────
//  Subtle animated glow that floats behind the drawer header
// ──────────────────────────────────────────────────────────────────────
@Composable
private fun DrawerHeaderGlow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "header_glow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(8000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "glow_phase"
    )
    val angle = phase * 2f * Math.PI.toFloat()

    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = if (LocalThemeId.current == 6) SpaceCyan else DuckYellowAccent

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .drawBehind {
                val cx = size.width * (0.35f + 0.15f * cos(angle))
                val cy = size.height * (0.45f + 0.15f * sin(angle))
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to primaryColor.copy(alpha = 0.35f),
                            0.5f to accentColor.copy(alpha = 0.12f),
                            1.0f to Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = size.width * 0.55f
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(cx, cy)
                )
                // second, smaller accent orb
                val cx2 = size.width * (0.75f + 0.10f * sin(angle * 1.3f))
                val cy2 = size.height * (0.30f + 0.12f * cos(angle * 0.7f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to primaryColor.copy(alpha = 0.25f),
                            0.6f to primaryColor.copy(alpha = 0.06f),
                            1.0f to Color.Transparent
                        ),
                        center = Offset(cx2, cy2),
                        radius = size.width * 0.35f
                    ),
                    radius = size.width * 0.35f,
                    center = Offset(cx2, cy2)
                )
            }
    )
}

// ──────────────────────────────────────────────────────────────────────
//  AppDrawer – wrapper that owns ModalNavigationDrawer
// ──────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawer(
    user: UserMembersClass?,
    navController: NavController,
    unreadCount: Int = 0,
    notificationUnreadCount: Int = 0,
    onLogout: () -> Unit,
    content: @Composable (onOpenMenu: () -> Unit) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = user != null,
            scrimColor = Color.Black.copy(alpha = 0.45f),
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        modifier = Modifier
                            .width(310.dp)
                            .padding(vertical = 48.dp),
                        drawerShape = RoundedCornerShape(
                            topStart = 28.dp,
                            bottomStart = 28.dp,
                            topEnd = 0.dp,
                            bottomEnd = 0.dp
                        ),
                        drawerTonalElevation = 0.dp,
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MenuSidebar(
                            user = user,
                            currentRoute = currentRoute,
                            onClose = { scope.launch { drawerState.close() } },
                            onLogout = {
                                scope.launch {
                                    drawerState.close()
                                    onLogout()
                                }
                            },
                            navController = navController,
                            unreadCount = unreadCount,
                            notificationUnreadCount = notificationUnreadCount
                        )
                    }
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                content {
                    scope.launch {
                        drawerState.open()
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  MenuSidebar – the actual drawer content
// ──────────────────────────────────────────────────────────────────────
@Composable
fun MenuSidebar(
    user: UserMembersClass?,
    currentRoute: String?,
    onClose: () -> Unit,
    onLogout: () -> Unit,
    navController: NavController,
    unreadCount: Int = 0,
    notificationUnreadCount: Int = 0
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // ── Premium Header with animated glow ────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
        ) {
            // Gradient background
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (LocalIsDarkTheme.current)
                                listOf(BlackSurfaceVariant, BlackSurface)
                            else
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    MaterialTheme.colorScheme.surface
                                )
                        )
                    )
            )
            // Floating orb glow
            DrawerHeaderGlow()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 20.dp)
            ) {
                // Top row: brand mark + close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Branded icon with gradient ring
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "DuckDuckDefer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = "Exam Scheduling",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    FilledIconButton(
                        onClick = onClose,
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // User quick-glance card inside header (if logged in)
                if (user != null) {
                    Spacer(modifier = Modifier.height(18.dp))

                    Surface(
                        onClick = {
                            onClose()
                            navController.navigate("settings")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar with orange ring
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                            ),
                                            shape = CircleShape
                                        )
                                )
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                ) {
                                    ProfileAvatar(
                                        profilePictureUrl = user.profilePictureUrl,
                                        name = user.thisUserName,
                                        size = 44.dp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.thisUserName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val roleName = when (user.thisRole) {
                                    0 -> "Admin"
                                    1 -> "Faculty"
                                    2 -> "Proctor"
                                    3 -> "Student"
                                    else -> "User"
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Level badge
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text(
                                            text = "Lv ${user.level}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(
                                                horizontal = 6.dp,
                                                vertical = 2.dp
                                            )
                                        )
                                    }
                                    Text(
                                        text = roleName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Scrollable Navigation Items ──────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp)
        ) {
            DrawerSectionHeader("General")

            DrawerItem(
                label = "Dashboard",
                icon = Icons.Default.Dashboard,
                selected = currentRoute == "dashboard",
                onClick = {
                    onClose()
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )

            DrawerItem(
                label = "Leaderboard",
                icon = Icons.Default.EmojiEvents,
                selected = currentRoute == "leaderboard",
                onClick = {
                    onClose()
                    navController.navigate("leaderboard")
                }
            )

            if (user != null) {
                DrawerItem(
                    label = "Messages",
                    icon = Icons.Default.Email,
                    selected = currentRoute == "messages",
                    badgeCount = unreadCount,
                    onClick = {
                        onClose()
                        navController.navigate("messages")
                    }
                )

                DrawerItem(
                    label = "Notifications",
                    icon = Icons.Default.Notifications,
                    selected = currentRoute == "notification_center",
                    badgeCount = notificationUnreadCount,
                    onClick = {
                        onClose()
                        navController.navigate("notification_center")
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- Role Specific Section ---
            user?.let {
                val roleLabel = when (it.thisRole) {
                    0 -> "Admin Panel"
                    1 -> "Faculty Tools"
                    2 -> "Proctor Tools"
                    3 -> "Student Portal"
                    else -> "Menu"
                }
                DrawerSectionHeader(roleLabel)

                when (it.thisRole) {
                    0 -> AdminMenuItems(onClose, navController, currentRoute)
                    1 -> FacultyMenuItems(onClose, navController, currentRoute)
                    2 -> ProctorMenuItems(onClose, navController, currentRoute)
                    3 -> StudentMenuItems(onClose, navController, currentRoute)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Footer: Logout ───────────────────────────────────────────
        if (user != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  Section header with decorative accent line
// ──────────────────────────────────────────────────────────────────────
@Composable
private fun DrawerSectionHeader(title: String) {
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                    ),
                    shape = RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            letterSpacing = 1.2.sp
        )
    }
}

// ──────────────────────────────────────────────────────────────────────
//  Premium drawer item with animated selection indicator
// ──────────────────────────────────────────────────────────────────────
@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else
            Color.Transparent,
        animationSpec = tween(250),
        label = "item_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "item_content"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection accent bar
            if (selected) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            ),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            if (badgeCount > 0) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ) {
                            Text(
                                if (badgeCount > 99) "99+" else badgeCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                letterSpacing = if (selected) 0.sp else (-0.1).sp
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  Role-specific menu items  (unchanged navigation logic)
// ──────────────────────────────────────────────────────────────────────
@Composable
private fun AdminMenuItems(onClose: () -> Unit, navController: NavController, currentRoute: String?) {
    DrawerItem(
        label = "Manage Sessions",
        icon = Icons.Default.EventNote,
        selected = currentRoute == "detailed_weekly_sessions",
        onClick = {
            onClose()
            navController.navigate("detailed_weekly_sessions")
        }
    )

    DrawerItem(
        label = "Manage Users",
        icon = Icons.Default.Group,
        selected = currentRoute == "manage_users",
        onClick = {
            onClose()
            navController.navigate("manage_users")
        }
    )

    DrawerItem(
        label = "Proctor Assignment",
        icon = Icons.Default.AutoFixHigh,
        selected = currentRoute == "proctor_assignment",
        onClick = {
            onClose()
            navController.navigate("proctor_assignment")
        }
    )

    DrawerItem(
        label = "View Feedback",
        icon = Icons.Default.RateReview,
        selected = currentRoute == "view_feedback",
        onClick = {
            onClose()
            navController.navigate("view_feedback")
        }
    )
}

@Composable
private fun FacultyMenuItems(onClose: () -> Unit, navController: NavController, currentRoute: String?) {
    DrawerItem(
        label = "Browse Sessions",
        icon = Icons.Default.Search,
        selected = currentRoute == "detailed_weekly_sessions",
        onClick = {
            onClose()
            navController.navigate("detailed_weekly_sessions")
        }
    )

    DrawerItem(
        label = "My Bookings",
        icon = Icons.Default.Book,
        selected = currentRoute == "my_bookings",
        onClick = {
            onClose()
            navController.navigate("my_bookings")
        }
    )

    DrawerItem(
        label = "Manage Availability",
        icon = Icons.Default.EditCalendar,
        selected = currentRoute == "proctor_availability",
        onClick = {
            onClose()
            navController.navigate("proctor_availability")
        }
    )
}

@Composable
private fun ProctorMenuItems(onClose: () -> Unit, navController: NavController, currentRoute: String?) {
    DrawerItem(
        label = "Session Schedule",
        icon = Icons.Default.CalendarToday,
        selected = currentRoute == "detailed_weekly_sessions",
        onClick = {
            onClose()
            navController.navigate("detailed_weekly_sessions")
        }
    )

    DrawerItem(
        label = "My Availability",
        icon = Icons.Default.EventAvailable,
        selected = currentRoute == "proctor_availability",
        onClick = {
            onClose()
            navController.navigate("proctor_availability")
        }
    )
}

@Composable
private fun StudentMenuItems(onClose: () -> Unit, navController: NavController, currentRoute: String?) {
    DrawerItem(
        label = "My Exam Schedule",
        icon = Icons.Default.Assignment,
        selected = currentRoute == "my_bookings",
        onClick = {
            onClose()
            navController.navigate("my_bookings")
        }
    )
}
