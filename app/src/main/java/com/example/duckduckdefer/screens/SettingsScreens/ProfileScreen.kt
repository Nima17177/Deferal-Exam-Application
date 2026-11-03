package com.example.duckduckdefer.screens.SettingsScreens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.components.MessageUserButton
import com.example.duckduckdefer.components.ProfileAvatar
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.ProfileRepository
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import com.example.duckduckdefer.viewmodels.MessagingViewModel
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    currentUser: UserMembersClass,
    messagingViewModel: MessagingViewModel,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onOpenMenu: () -> Unit = {}
) {
    val profileRepository = remember { ProfileRepository() }
    var profileUser by remember { mutableStateOf<UserMembersClass?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    
    // Entrance animation state
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    val isOwnProfile = userId == currentUser.thisUID

    // Function to reload profile data
    val refreshProfile = {
        isLoading = true
        profileRepository.getUserProfile(userId) { user ->
            profileUser = user ?: if (isOwnProfile) currentUser else null
            isLoading = false
        }
    }

    LaunchedEffect(userId, currentUser) {
        refreshProfile()
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
                                    text = "Profile",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                Text(
                                    text = "Details and achievements",
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
                            if (isOwnProfile) {
                                IconButton(onClick = onEditProfile) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Profile",
                                        tint = heroContent
                                    )
                                }
                            }
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
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (profileUser == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "User not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            val user = profileUser!!
            val roleName = when (user.thisRole) {
                0 -> "Administrator"
                1 -> "Faculty Member"
                2 -> "Proctor"
                3 -> "Student"
                else -> "User"
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = paddingValues.calculateTopPadding())
                    .verticalScroll(scrollState)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                    // --- COLLAPSING HEADER WITH PARALLAX ---
                    val heroContent = DuckGradients.heroContentColor()
                    val heroContentSecondary = DuckGradients.heroContentColorSecondary()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = DuckGradients.heroGradient(),
                                shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
                            )
                            .padding(bottom = 32.dp)
                            .graphicsLayer {
                                // Background Parallax: moves slower than scroll
                                translationY = scrollState.value * 0.5f
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.graphicsLayer {
                                // CALCULATE COLLAPSE EFFECT
                                // 0 at top, 1 when scrolled 500px
                                val collapseProgress = (scrollState.value.toFloat() / 600f).coerceIn(0f, 1f)
                                
                                // Shrink avatar and fade content as we scroll down
                                scaleX = 1f - (collapseProgress * 0.35f)
                                scaleY = 1f - (collapseProgress * 0.35f)
                                alpha = 1f - (collapseProgress * 1.5f).coerceIn(0f, 1f)
                                
                                // Content Parallax: subtle upward drift
                                translationY = scrollState.value * 0.2f
                            }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(136.dp)
                                    .border(4.dp, heroContent.copy(alpha = 0.3f), CircleShape)
                                    .padding(4.dp)
                            ) {
                                ProfileAvatar(
                                    profilePictureUrl = user.profilePictureUrl,
                                    name = user.thisUserName,
                                    size = 120.dp
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = user.thisUserName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = heroContent
                            )
                            
                            if (user.currentTitle.isNotEmpty()) {
                                Text(
                                    text = user.currentTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = heroContentSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = heroContent.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, heroContent.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = roleName,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = heroContent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // --- CONTENT CARDS WITH STAGGERED ENTRANCE ---
                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInVertically(initialOffsetY = { 100 }) + fadeIn(animationSpec = tween(600, delayMillis = 200))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .offset(y = (-20).dp), // Slight overlap with header
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Stats Card
                            Card(
                                modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProfileStatItem("Level", user.level.toString(), Icons.AutoMirrored.Filled.TrendingUp)
                                    VerticalDivider(modifier = Modifier.height(40.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                    
                                    val streakColor = if (user.dailyStreak > 0) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant
                                    ProfileStatItem(
                                        label = "Streak", 
                                        value = if (user.dailyStreak > 0) "${user.dailyStreak}" else "0", 
                                        icon = Icons.Default.Whatshot,
                                        iconColor = streakColor
                                    )
                                    
                                    VerticalDivider(modifier = Modifier.height(40.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                    ProfileStatItem("Points", user.points.toString(), Icons.Default.Token)
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Gamification Card
                            PremiumGamificationCard(user, isOwnProfile) { newTitle ->
                                if (isOwnProfile) {
                                    FirebaseFirestore.getInstance()
                                        .collection("UserAccounts")
                                        .document(user.thisUID)
                                        .update("currentTitle", newTitle)
                                        .addOnSuccessListener {
                                            // Trigger refresh after update
                                            currentUser.currentTitle = newTitle
                                            refreshProfile()
                                        }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))

                            // Message button
                            if (!isOwnProfile) {
                                val canMessage = !(currentUser.thisRole == 3 && user.thisRole == 3)
                                if (canMessage) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        MessageUserButton(
                                            currentUser = currentUser,
                                            targetUser = user,
                                            viewModel = messagingViewModel,
                                            onConversationStarted = { onNavigateToChat(it) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }

                            // Biography section
                            if (user.biography.isNotEmpty()) {
                                PremiumSection(title = "Biography", icon = Icons.Default.Info) {
                                    Text(
                                        text = user.biography,
                                        style = MaterialTheme.typography.bodyLarge,
                                        lineHeight = 24.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            // Contact Details section
                            if (user.thisEmail.isNotEmpty() || user.contactEmails.isNotEmpty() || user.phoneNumbers.isNotEmpty()) {
                                PremiumSection(title = "Contact Details", icon = Icons.Default.ContactPage) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (user.thisEmail.isNotEmpty()) {
                                            ContactItem(Icons.Default.AlternateEmail, user.thisEmail, "Account Email")
                                        }
                                        user.contactEmails.forEach { email ->
                                            ContactItem(Icons.Default.Email, email)
                                        }
                                        user.phoneNumbers.forEach { phone ->
                                            ContactItem(Icons.Default.Phone, phone)
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }
                }
            }
        }
    }


@Composable
fun ProfileStatItem(
    label: String, 
    value: String, 
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.primary
) {
    // Small pop-in animation for stats
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnim = true }
    val scale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PremiumSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Box(modifier = Modifier.padding(20.dp)) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PremiumGamificationCard(user: UserMembersClass, isOwnProfile: Boolean = false, onTitleSelected: (String) -> Unit = {}) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Achievements & Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        
        Card(
            modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Level Progress", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${user.experience % 100} / 100 XP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(user.level.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val progress = (user.experience % 100) / 100f
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
                )
                
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                )
                
                if (user.badges.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Unlocked Badges", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 8.dp)
                    ) {
                        items(user.badges) { badge ->
                            PremiumBadgeItem(badge)
                        }
                    }
                }

                if (isOwnProfile && user.unlockedTitles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Equip Unlocked Title", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        user.unlockedTitles.forEach { title ->
                            val isSelected = user.currentTitle == title
                            FilterChip(
                                selected = isSelected,
                                onClick = { onTitleSelected(title) },
                                label = { Text(title) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumBadgeItem(name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(64.dp).shadow(2.dp, RoundedCornerShape(16.dp)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.AutoAwesome, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.onSecondaryContainer, 
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name, 
            style = MaterialTheme.typography.labelSmall, 
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 12.sp
        )
    }
}

@Composable
fun ContactItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, label: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}
