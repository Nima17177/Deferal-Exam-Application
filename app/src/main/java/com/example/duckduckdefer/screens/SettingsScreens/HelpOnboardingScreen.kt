package com.example.duckduckdefer.screens.SettingsScreens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.screens.SettingsItem
import com.example.duckduckdefer.screens.SettingsSection
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════
//  Help & Onboarding Screen
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpOnboardingScreen(
    user: UserMembersClass,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    var visible by remember { mutableStateOf(false) }
    var showWalkthrough by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    if (showWalkthrough) {
        OnboardingWalkthrough(
            userRole = user.thisRole,
            onDismiss = { showWalkthrough = false }
        )
        return
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
                                    text = "Help & Onboarding",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                Text(
                                    text = "Guides, FAQs, and support",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── Welcome / Start Walkthrough Card ─────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600)) + expandVertically(tween(600))
            ) {
                WelcomeCard(onStartWalkthrough = { showWalkthrough = true })
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn()
                ) {
                    Column {
                        // ── Quick-Start Guide (role-aware) ───────────
                        QuickStartSection(
                            userRole = user.thisRole,
                            onNavigate = onNavigate
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // ── Feature Highlights ───────────────────────
                        FeatureHighlightsSection(onNavigate = onNavigate)

                        Spacer(modifier = Modifier.height(24.dp))

                        // ── Earning Points & XP Guide ─────────────────
                        EarningPointsSection()

                        Spacer(modifier = Modifier.height(24.dp))

                        // ── FAQ Section ──────────────────────────────
                        FaqSection()

                        Spacer(modifier = Modifier.height(24.dp))

                        // ── Contact Support ──────────────────────────
                        ContactSupportSection(onNavigate = onNavigate)

                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  Welcome Hero Card
// ══════════════════════════════════════════════════════════

@Composable
private fun WelcomeCard(onStartWalkthrough: () -> Unit) {
    val heroContent = DuckGradients.heroContentColor()
    val heroContentSecondary = DuckGradients.heroContentColorSecondary()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(DuckGradients.heroGradient())
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.RocketLaunch,
                contentDescription = null,
                tint = heroContent,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Welcome to DuckDuckDefer!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = heroContent,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "New here? Take a quick tour to learn how everything works.",
                style = MaterialTheme.typography.bodyMedium,
                color = heroContentSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStartWalkthrough,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Walkthrough", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  Onboarding Walkthrough (HorizontalPager)
// ══════════════════════════════════════════════════════════

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val bullets: List<String> = emptyList()
)

private fun getOnboardingPages(userRole: Int): List<OnboardingPage> {
    val common = listOf(
        OnboardingPage(
            icon = Icons.Default.Dashboard,
            title = "Your Dashboard",
            description = "Your home base for everything happening in DuckDuckDefer.",
            bullets = listOf(
                "See today's sessions at a glance",
                "Track your XP, streak, and level",
                "Quick-access buttons for bookings & messages"
            )
        ),
        OnboardingPage(
            icon = Icons.Default.Email,
            title = "Messaging",
            description = "Communicate with anyone in the system directly from the app.",
            bullets = listOf(
                "Start conversations with any user",
                "Get real-time message notifications",
                "Chat with DuckBot for instant help"
            )
        ),
        OnboardingPage(
            icon = Icons.Default.EmojiEvents,
            title = "Gamification & Rewards",
            description = "Stay motivated with XP, levels, streaks, and unlockable rewards.",
            bullets = listOf(
                "Earn XP by logging in and completing sessions",
                "Maintain daily streaks for bonus XP",
                "Unlock titles, themes, and sounds in the Shop"
            )
        ),
        OnboardingPage(
            icon = Icons.Default.SmartToy,
            title = "Meet DuckBot",
            description = "Your AI-powered assistant, built right into the app.",
            bullets = listOf(
                "Ask questions about your schedule or the app",
                "DuckBot can navigate you to any screen",
                "Upload documents for AI-powered analysis"
            )
        )
    )

    val roleSpecific = when (userRole) {
        0 -> listOf(
            OnboardingPage(
                icon = Icons.Default.AdminPanelSettings,
                title = "Admin Tools",
                description = "You have full control over the system as an Admin.",
                bullets = listOf(
                    "Approve or reject new user accounts",
                    "Create weekly exam session schedules",
                    "Assign proctors to rooms and sessions"
                )
            )
        )
        1 -> listOf(
            OnboardingPage(
                icon = Icons.Default.BookOnline,
                title = "Booking Exams",
                description = "As Faculty, you manage exam bookings for your courses.",
                bullets = listOf(
                    "Request exam proctoring sessions",
                    "Specify date, time, and room requirements",
                    "Track booking status in My Bookings"
                )
            )
        )
        2 -> listOf(
            OnboardingPage(
                icon = Icons.Default.EventAvailable,
                title = "Setting Your Availability",
                description = "As a Proctor, let admins know when you can work.",
                bullets = listOf(
                    "Mark your available time slots each week",
                    "Get assigned to sessions that match your schedule",
                    "Earn XP and points for completed sessions"
                )
            )
        )
        else -> listOf(
            OnboardingPage(
                icon = Icons.Default.School,
                title = "Your Exam Sessions",
                description = "Stay on top of your upcoming exams.",
                bullets = listOf(
                    "View all your upcoming exam sessions",
                    "Check-in with QR codes on exam day",
                    "Get notified about schedule changes"
                )
            )
        )
    }

    return common + roleSpecific + listOf(
        OnboardingPage(
            icon = Icons.Default.CheckCircle,
            title = "You're All Set!",
            description = "You can always come back to Settings → Help & Onboarding to replay this guide or browse FAQs."
        )
    )
}

@Composable
private fun OnboardingWalkthrough(
    userRole: Int,
    onDismiss: () -> Unit
) {
    val pages = remember(userRole) { getOnboardingPages(userRole) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val heroContent = DuckGradients.heroContentColor()
    val heroContentSecondary = DuckGradients.heroContentColorSecondary()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar: step counter + skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} of ${pages.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onDismiss) {
                    Text("Skip", fontWeight = FontWeight.Bold)
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1).toFloat() / pages.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(4.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            )

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                val page = pages[pageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Icon on gradient card
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .shadow(12.dp, CircleShape)
                            .clip(CircleShape)
                            .background(DuckGradients.heroGradient()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            tint = heroContent,
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = page.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    // Bullet points
                    if (page.bullets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            page.bullets.forEach { bullet ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircleOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = bullet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Page dot indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                val isLast = pagerState.currentPage == pages.size - 1
                Button(
                    onClick = {
                        if (isLast) {
                            onDismiss()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        if (isLast) "Get Started" else "Next",
                        fontWeight = FontWeight.Bold
                    )
                    if (!isLast) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  Quick-Start Guide (role-aware)
// ══════════════════════════════════════════════════════════

@Composable
private fun QuickStartSection(userRole: Int, onNavigate: (String) -> Unit) {
    val roleName = when (userRole) {
        0 -> "Admin"
        1 -> "Faculty"
        2 -> "Proctor"
        else -> "Student"
    }

    SettingsSection(title = "Quick Start — $roleName Guide", icon = Icons.Default.Bolt) {
        // Common items
        SettingsItem(
            icon = Icons.Default.Dashboard,
            title = "Go to Dashboard",
            subtitle = "Your home screen with quick actions",
            onClick = { onNavigate("dashboard") }
        )
        SettingsItem(
            icon = Icons.Default.Email,
            title = "Messages",
            subtitle = "Chat with other users",
            onClick = { onNavigate("messages") }
        )

        // Role-specific items
        when (userRole) {
            0 -> {
                SettingsItem(
                    icon = Icons.Default.People,
                    title = "Manage Users",
                    subtitle = "Approve, reject, or deactivate accounts",
                    onClick = { onNavigate("manage_users") }
                )
                SettingsItem(
                    icon = Icons.Default.CalendarMonth,
                    title = "Create Weekly Sessions",
                    subtitle = "Set up exam proctoring schedules",
                    onClick = { onNavigate("detailed_weekly_sessions") }
                )
                SettingsItem(
                    icon = Icons.Default.AssignmentInd,
                    title = "Assign Proctors",
                    subtitle = "Assign proctors to sessions",
                    onClick = { onNavigate("proctor_assignment") }
                )
            }
            1 -> {
                SettingsItem(
                    icon = Icons.Default.BookOnline,
                    title = "My Bookings",
                    subtitle = "View and manage your exam bookings",
                    onClick = { onNavigate("my_bookings") }
                )
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notification Preferences",
                    subtitle = "Customize what alerts you receive",
                    onClick = { onNavigate("notificationSettings") }
                )
            }
            2 -> {
                SettingsItem(
                    icon = Icons.Default.EventAvailable,
                    title = "Set Availability",
                    subtitle = "Tell us when you can proctor",
                    onClick = { onNavigate("proctor_availability") }
                )
                SettingsItem(
                    icon = Icons.Default.EmojiEvents,
                    title = "Leaderboard",
                    subtitle = "See how you rank among proctors",
                    onClick = { onNavigate("leaderboard") }
                )
                SettingsItem(
                    icon = Icons.Default.ShoppingCart,
                    title = "Shop",
                    subtitle = "Spend points on themes, titles & sounds",
                    onClick = { onNavigate("shop") }
                )
            }
            else -> {
                SettingsItem(
                    icon = Icons.Default.BookOnline,
                    title = "My Bookings",
                    subtitle = "View your upcoming exam sessions",
                    onClick = { onNavigate("my_bookings") }
                )
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notification Preferences",
                    subtitle = "Stay on top of important updates",
                    onClick = { onNavigate("notificationSettings") }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  Feature Highlights
// ══════════════════════════════════════════════════════════

private data class FeatureHighlight(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val route: String
)

private val featureHighlights = listOf(
    FeatureHighlight(Icons.Default.SmartToy, "DuckBot AI", "Your personal assistant", "duckbot_chat"),
    FeatureHighlight(Icons.Default.EmojiEvents, "Leaderboard", "Compete & climb ranks", "leaderboard"),
    FeatureHighlight(Icons.Default.ShoppingCart, "Shop", "Unlock themes & titles", "shop"),
    FeatureHighlight(Icons.Default.Palette, "Themes", "Personalize your look", "accessibilitySettings"),
    FeatureHighlight(Icons.Default.Notifications, "Alerts", "Never miss an update", "notificationSettings")
)

@Composable
private fun FeatureHighlightsSection(onNavigate: (String) -> Unit) {
    SettingsSection(title = "Feature Highlights", icon = Icons.Default.AutoAwesome) {
        featureHighlights.forEach { feature ->
            SettingsItem(
                icon = feature.icon,
                title = feature.title,
                subtitle = feature.subtitle,
                onClick = { onNavigate(feature.route) }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  Earning Points & XP Guide
// ══════════════════════════════════════════════════════════

private data class XpGuideItem(
    val icon: ImageVector,
    val action: String,
    val xp: String,
    val detail: String
)

private val xpGuideItems = listOf(
    XpGuideItem(Icons.AutoMirrored.Filled.Login, "Daily Login", "10 XP × Streak Day", "Log in every day to build your streak. Your XP bonus grows with each consecutive day!"),
    XpGuideItem(Icons.Default.HowToReg, "Student Check-in", "10 XP", "Students earn XP when they check in to an exam session."),
    XpGuideItem(Icons.Default.TaskAlt, "Exam Finished", "50 XP", "Students earn a big XP reward when their exam session is completed."),
    XpGuideItem(Icons.Default.HowToReg, "Proctor Check-in", "5 XP", "Proctors earn XP when they perform a student check-in."),
    XpGuideItem(Icons.Default.LocalShipping, "Exam Dropped Off", "10 XP", "Proctors earn XP when they drop off exams."),
    XpGuideItem(Icons.Default.Verified, "Session Completed", "30 / 20 XP", "Proctors earn 30 XP and Faculty earn 20 XP on final completion."),
    XpGuideItem(Icons.Default.Report, "Report Incident", "15 XP", "Proctors and Admins earn XP for filing incident reports during exams."),
    XpGuideItem(Icons.Default.EventAvailable, "Set Availability", "5 XP", "Proctors earn XP for setting their availability for a date."),
    XpGuideItem(Icons.Default.CheckCircle, "Approve Account", "10 XP", "Admins earn XP for approving new user accounts."),
    XpGuideItem(Icons.Default.CalendarMonth, "Create Session", "15 XP", "Admins earn XP for creating new exam sessions."),
    XpGuideItem(Icons.Default.AssignmentInd, "Assign Proctor", "5 XP", "Admins earn XP for assigning proctors to rooms."),
    XpGuideItem(Icons.Default.BookOnline, "Book a Session", "10 XP", "Admins earn XP for booking exam sessions for students.")
)

@Composable
private fun EarningPointsSection() {
    SettingsSection(title = "Earning Points & XP", icon = Icons.Default.Star) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Everything you do in the app earns Experience Points (XP). Every XP you earn also gives you the same amount of spendable Points for the Shop.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // XP vs Points distinction
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "⭐ XP vs Points",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• XP (Experience) is permanent — it determines your level and leaderboard rank.\n• Points are spendable — use them in the Shop for themes, titles, and sounds.\n• Buying items costs Points but never reduces your XP or level.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // XP action rows
            xpGuideItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                item.action,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    item.xp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            item.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Streak & Leveling Tips
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "🔥 Streak Tips",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• Log in daily — your streak bonus grows every day (10 XP × streak day)\n• 7-day streak → \"7-Day Streak\" badge\n• 30-day streak → \"Monthly Habit\" badge\n• Miss a day? Your streak resets to 1",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "⬆️ Leveling Up",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• Every 100 XP = 1 new level\n• Each level earned unlocks a \"Level X\" badge\n• Level ups trigger a celebration animation!\n• Track your progress on your Profile or the Settings header",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  FAQ Section (expandable accordion)
// ══════════════════════════════════════════════════════════

private data class FaqItem(
    val question: String,
    val answer: String
)

private val faqItems = listOf(
    FaqItem(
        question = "How do I get my account approved?",
        answer = "After registering, an admin will review your account. You'll receive a notification once approved. If it takes a while, try reaching out via Messages."
    ),
    FaqItem(
        question = "How does the booking process work?",
        answer = "Faculty members can create exam bookings by selecting a time slot and specifying requirements. Admins then assign proctors to those sessions. You can track everything in 'My Bookings'."
    ),
    FaqItem(
        question = "What are XP, levels, and streaks?",
        answer = "You earn XP (Experience Points) for using the app. Daily logins award 10 XP multiplied by your current streak day. Students earn XP for check-ins (10) and finished exams (50). Proctors earn XP for check-ins (5), drop-offs (10), completions (30), incident reports (15), and setting availability (5). Admins earn XP for approving accounts (10), creating sessions (15), assigning proctors (5), and booking sessions (10). Every 100 XP advances your level and earns a badge. Keep your streak alive for bonus XP!"
    ),
    FaqItem(
        question = "How do I unlock new themes and titles?",
        answer = "Visit the Shop from the sidebar or dashboard. Spend earned points to unlock premium themes (Neon Night, Ocean Breeze, etc.), custom titles, and notification sounds."
    ),
    FaqItem(
        question = "How do I set my proctor availability?",
        answer = "Go to 'Proctor Availability' from the sidebar or dashboard. Select the time slots when you're available, and admins will match you to sessions."
    ),
    FaqItem(
        question = "Can I change my notification preferences?",
        answer = "Yes! Head to Settings → Notifications. You can toggle push notifications, email alerts, and fine-tune which events trigger notifications."
    ),
    FaqItem(
        question = "What is DuckBot?",
        answer = "DuckBot is your AI-powered assistant built right into the app. Ask it questions about the app, your schedule, or let it navigate you to any screen. Access it from the sidebar."
    ),
    FaqItem(
        question = "How do I contact support?",
        answer = "You can chat with DuckBot for instant help, or send an email to our support team using the 'Email Support' option at the bottom of this page."
    )
)

@Composable
private fun FaqSection() {
    SettingsSection(title = "Frequently Asked Questions", icon = Icons.Default.Quiz) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            faqItems.forEachIndexed { index, faq ->
                FaqAccordionItem(faq = faq)
                if (index < faqItems.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FaqAccordionItem(faq: FaqItem) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = faq.question,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
            exit = shrinkVertically(tween(300)) + fadeOut(tween(200))
        ) {
            Text(
                text = faq.answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
                modifier = Modifier.padding(start = 60.dp, top = 8.dp, end = 8.dp)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  Contact Support Section
// ══════════════════════════════════════════════════════════

@Composable
private fun ContactSupportSection(onNavigate: (String) -> Unit) {
    val context = LocalContext.current

    SettingsSection(title = "Contact Support", icon = Icons.Default.SupportAgent) {
        SettingsItem(
            icon = Icons.Default.SmartToy,
            title = "Ask DuckBot",
            subtitle = "Get instant AI-powered help",
            onClick = { onNavigate("duckbot_chat") }
        )
        SettingsItem(
            icon = Icons.Default.Email,
            title = "Email Support",
            subtitle = "duckduckdefer.support@macewan.ca",
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:duckduckdefer.support@macewan.ca")
                    putExtra(Intent.EXTRA_SUBJECT, "DuckDuckDefer Support Request")
                }
                context.startActivity(Intent.createChooser(intent, "Send Email"))
            }
        )
    }
}

