package com.example.duckduckdefer.screens.messaging

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
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
import com.example.duckduckdefer.components.ProfileAvatar
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.Conversation
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import com.example.duckduckdefer.viewmodels.MessagingViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsListScreen(
    user: UserMembersClass,
    viewModel: MessagingViewModel,
    onOpenMenu: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToNewConversation: () -> Unit,
    onNavigateToDuckBot: () -> Unit,
    onBack: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val participantProfiles by viewModel.participantProfiles.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    // Initialize and refresh conversations when screen is displayed
    LaunchedEffect(Unit) {
        viewModel.initialize(user.thisUID)
        viewModel.refreshConversations()
    }

    // Fetch latest profiles for all participants to ensure current profile pictures are shown
    LaunchedEffect(conversations) {
        conversations.forEach { conversation ->
            viewModel.getOtherParticipantId(conversation)?.let { otherId ->
                viewModel.fetchParticipantProfile(otherId)
            }
        }
    }

    val filteredConversations = remember(conversations, searchQuery, participantProfiles) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter {
            viewModel.getOtherParticipantName(it).contains(searchQuery, ignoreCase = true) ||
            viewModel.getOtherParticipantEmail(it).contains(searchQuery, ignoreCase = true)
        }
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
                Box(modifier = Modifier.background(DuckGradients.heroGradient(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).statusBarsPadding()) {
                    Column {
                        CenterAlignedTopAppBar(
                            title = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Messages",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = heroContent
                                    )
                                    Text(
                                        "Communication Hub",
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
                                IconButton(onClick = onOpenMenu) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = heroContent)
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                        )
                        
                        // Search Bar in the Top Section
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 24.dp),
                            placeholder = { Text("Search conversations...", color = heroContentSecondary) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = heroContent.copy(alpha = 0.8f)) },
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = heroContent.copy(alpha = 0.15f),
                                unfocusedContainerColor = heroContent.copy(alpha = 0.1f),
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = heroContent.copy(alpha = 0.3f),
                                focusedTextColor = heroContent,
                                unfocusedTextColor = heroContent
                            )
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToNewConversation,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Chat") },
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { paddingValues ->
        val showDuckBot = searchQuery.isBlank() ||
            "duckbot".contains(searchQuery, ignoreCase = true) ||
            "ai assistant".contains(searchQuery, ignoreCase = true)

        if (filteredConversations.isEmpty() && !showDuckBot) {
            EmptyConversationsPlaceholder(isSearch = searchQuery.isNotEmpty(), paddingValues = paddingValues)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showDuckBot) {
                    item(key = "duckbot") {
                        DuckBotConversationItem(onClick = onNavigateToDuckBot)
                    }
                }
                items(filteredConversations) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        viewModel = viewModel,
                        onClick = { onNavigateToChat(conversation.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    viewModel: MessagingViewModel,
    onClick: () -> Unit
) {
    val otherName = viewModel.getOtherParticipantName(conversation)
    val otherAvatar = viewModel.getOtherParticipantAvatar(conversation)
    val unreadCount = viewModel.getUnreadCount(conversation)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (unreadCount > 0) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                ProfileAvatar(
                    profilePictureUrl = otherAvatar,
                    name = otherName,
                    size = 52.dp
                )
                // Online indicator placeholder
                Surface(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    color = Color(0xFF4CAF50), // Green
                    shape = CircleShape
                ) {}
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val otherNameDisplay = if (otherName.isEmpty()) "User" else otherName
                    Text(
                        text = otherNameDisplay,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (unreadCount > 0) FontWeight.ExtraBold else FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    conversation.lastMessageTime?.let { timestamp ->
                        val dateText = formatTimestamp(timestamp.toDate())
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unreadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lastMsgText = if (conversation.lastMessage.isEmpty()) "Start a conversation" else conversation.lastMessage
                    Text(
                        text = lastMsgText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                            color = if (unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (unreadCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuckBotConversationItem(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DuckBot",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "AI Assistant - Ask me anything!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyConversationsPlaceholder(isSearch: Boolean, paddingValues: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (isSearch) "No results found" else "No conversations yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isSearch) "Try a different search term" else "Start a new chat to begin messaging",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatTimestamp(date: Date): String {
    val now = Calendar.getInstance()
    val msgTime = Calendar.getInstance().apply { time = date }

    return when {
        now.get(Calendar.DATE) == msgTime.get(Calendar.DATE) -> {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        }
        now.get(Calendar.WEEK_OF_YEAR) == msgTime.get(Calendar.WEEK_OF_YEAR) -> {
            SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        }
        else -> {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }
}
