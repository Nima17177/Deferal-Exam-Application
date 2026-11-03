package com.example.duckduckdefer.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.components.ProfileAvatar
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    currentUser: UserMembersClass,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    onRefreshUser: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {}
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Leaderboard", "Point Shop")
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ) {
                val heroContent = DuckGradients.heroContentColor()
                val heroContentSecondary = DuckGradients.heroContentColorSecondary()
                Column(
                    modifier = Modifier
                        .background(DuckGradients.heroGradient(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .statusBarsPadding()
                ) {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (selectedTabIndex == 0) "Leaderboard" else "Point Shop",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                Text(
                                    text = if (selectedTabIndex == 0) "Community Standing" else "Rewards Center",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = heroContentSecondary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = heroContent)
                            }
                        },
                        actions = {
                            if (selectedTabIndex == 1) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700))
                                    Spacer(Modifier.width(4.dp))
                                    Text(currentUser.points.toString(), fontWeight = FontWeight.Bold, color = heroContent)
                                }
                            }
                            IconButton(onClick = onOpenMenu) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = heroContent)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                    
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = heroContent,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = heroContent
                            )
                        },
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { 
                                    Text(
                                        title, 
                                        color = if (selectedTabIndex == index) heroContent else heroContentSecondary,
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                    ) 
                                },
                                icon = {
                                    Icon(
                                        if (index == 0) Icons.Default.EmojiEvents else Icons.Default.ShoppingBag,
                                        contentDescription = null,
                                        tint = if (selectedTabIndex == index) heroContent else heroContentSecondary
                                    )
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        bottomBar = {
            if (selectedTabIndex == 0) {
                CurrentUserStanding(currentUser, onNavigateToProfile)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (selectedTabIndex == 0) {
                LeaderboardContent(
                    currentUser = currentUser,
                    onNavigateToProfile = onNavigateToProfile
                )
            } else {
                ShopContent(
                    user = currentUser,
                    onRefreshUser = onRefreshUser,
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }
}

@Composable
fun CurrentUserStanding(currentUser: UserMembersClass, onNavigateToProfile: (String) -> Unit) {
    var rank by remember { mutableStateOf<Int?>(null) }
    var rankError by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser.experience) {
        rankError = false
        val db = FirebaseFirestore.getInstance()
        db.collection("UserAccounts")
            .whereGreaterThan("experience", currentUser.experience)
            .count()
            .get(AggregateSource.SERVER)
            .addOnSuccessListener { task ->
                rank = task.count.toInt() + 1
            }
            .addOnFailureListener {
                rankError = true
            }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToProfile(currentUser.thisUID) },
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Your Standing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    when {
                        rankError -> "Unable to load"
                        rank != null -> "#$rank Overall"
                        else -> "Calculating..."
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            
            Spacer(Modifier.weight(1f))
            
            Column(horizontalAlignment = Alignment.End) {
                Text("Level ${currentUser.level}", fontWeight = FontWeight.Bold)
                Text("${currentUser.experience} XP", style = MaterialTheme.typography.bodySmall)
            }
            
            Spacer(Modifier.width(16.dp))
            
            ProfileAvatar(currentUser.profilePictureUrl, currentUser.thisUserName, size = 40.dp)
        }
    }
}

@Composable
fun LeaderboardContent(currentUser: UserMembersClass, onNavigateToProfile: (String) -> Unit) {
    var leaderboardUsers by remember { mutableStateOf<List<UserMembersClass>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("UserAccounts")
            .orderBy("experience", Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot ->
                // Map documents to objects and manually set the UID from the document ID
                leaderboardUsers = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserMembersClass::class.java)?.apply {
                        thisUID = doc.id
                    }
                }
                isLoading = false
            }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Top Performers",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                HowToEarnXpCard()
            }

            itemsIndexed(leaderboardUsers) { index, user ->
                LeaderboardItem(
                    rank = index + 1,
                    user = user,
                    isCurrentUser = user.thisUID == currentUser.thisUID,
                    onClick = { onNavigateToProfile(user.thisUID) }
                )
            }
            
            item {
                Spacer(Modifier.height(80.dp)) // Padding for the bottom bar
            }
        }
    }
}

@Composable
fun LeaderboardItem(rank: Int, user: UserMembersClass, isCurrentUser: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isCurrentUser) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    val rankIconColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentUser) 4.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (rank <= 3) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = rankIconColor)
                } else {
                    Text(rank.toString(), fontWeight = FontWeight.Bold, color = Color.Gray)
                }
            }

            Spacer(Modifier.width(12.dp))

            ProfileAvatar(user.profilePictureUrl, user.thisUserName, size = 40.dp)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(user.thisUserName, fontWeight = FontWeight.Bold)
                Text("Level ${user.level}", style = MaterialTheme.typography.bodySmall)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("${user.experience} XP", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                if (user.dailyStreak > 0) {
                    Text("${user.dailyStreak}🔥", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  "How to Earn XP" Expandable Info Card
// ══════════════════════════════════════════════════════════

private data class XpAction(
    val icon: ImageVector,
    val action: String,
    val xp: String,
    val recipient: String
)

private val xpActions = listOf(
    XpAction(Icons.Default.Star, "Daily Login", "10 XP + Streak", "All Users"),
    XpAction(Icons.Default.EmojiEvents, "Student Check-in", "10 XP", "Student"),
    XpAction(Icons.Default.EmojiEvents, "Exam Finished", "50 XP", "Student"),
    XpAction(Icons.Default.EmojiEvents, "Proctor Check-in", "5 XP", "Proctor"),
    XpAction(Icons.Default.EmojiEvents, "Exam Dropped Off", "10 XP", "Proctor"),
    XpAction(Icons.Default.EmojiEvents, "Session Completed", "30 / 20 XP", "Proctor / Faculty"),
    XpAction(Icons.Default.EmojiEvents, "Report Incident", "15 XP", "Proctor / Admin"),
    XpAction(Icons.Default.EmojiEvents, "Set Availability", "5 XP", "Proctor"),
    XpAction(Icons.Default.Star, "Approve Account", "10 XP", "Admin"),
    XpAction(Icons.Default.Star, "Create Session", "15 XP", "Admin"),
    XpAction(Icons.Default.Star, "Assign Proctor", "5 XP", "Admin"),
    XpAction(Icons.Default.Star, "Book a Session", "10 XP", "Admin")
)

@Composable
fun HowToEarnXpCard() {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // Header row — always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "How to Earn XP",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Tap to see all the ways you can earn points",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expandable detail
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(300)) + fadeOut(tween(200))
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    // XP vs Points explainer
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "⭐ Every action that earns XP also gives you the same amount of spendable Points for the Shop. XP is permanent and determines your level & rank. Points can be spent on themes, titles, and sounds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    xpActions.forEach { item ->
                        XpInfoRow(item)
                        Spacer(Modifier.height(10.dp))
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Streak tip
                    Text(
                        text = "🔥 Streak Bonus",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Log in every day to build your streak! Your daily login bonus is 10 XP × your streak day count. A 7-day streak earns you a badge, and a 30-day streak unlocks the \"Monthly Habit\" badge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    // Leveling tip
                    Text(
                        text = "⬆️ Leveling Up",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Every 100 XP you earn advances you to the next level. Reaching a new level earns you a badge and a celebration!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun XpInfoRow(item: XpAction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.action,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                item.recipient,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Text(
                item.xp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

