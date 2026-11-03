package com.example.duckduckdefer.screens.messaging

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.components.ProfileAvatar
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.ui.theme.LocalIsDarkTheme
import com.example.duckduckdefer.ui.theme.LocalThemeId
import com.example.duckduckdefer.ui.theme.TextDark
import com.example.duckduckdefer.useraccountclasses.Message
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import com.example.duckduckdefer.viewmodels.MessagingViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    currentUser: UserMembersClass,
    viewModel: MessagingViewModel,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {}
) {
    val messages by viewModel.currentMessages.collectAsState()
    val conversation by viewModel.currentConversation.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(conversationId) {
        viewModel.loadMessages(conversationId)
    }

    // Auto-scroll logic
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0) true
            else {
                val lastVisibleItem = visibleItemsInfo.lastOrNull()
                (lastVisibleItem?.index ?: 0) >= layoutInfo.totalItemsCount - 2
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastMessage = messages.last()
            if (isAtBottom || lastMessage.senderId == currentUser.thisUID) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    val otherParticipantName = conversation?.let { viewModel.getOtherParticipantName(it) } ?: "Chat"
    val otherParticipantId = conversation?.participants?.find { it != currentUser.thisUID } ?: ""
    val otherParticipantAvatar = conversation?.participantAvatars?.get(otherParticipantId) ?: ""

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // FULL SCREEN BACKGROUND GLOW - This fixes the "gaps" behind rounded corners
        Box(modifier = Modifier.fillMaxSize().background(DuckGradients.surfaceGlowGradient()).alpha(0.4f))

        Scaffold(
            containerColor = Color.Transparent, // Let the background show through
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent, // Surface background transparent to show gradient Box
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                ) {
                    val heroContent = DuckGradients.heroContentColor()
                    val heroContentSecondary = DuckGradients.heroContentColorSecondary()
                    Box(modifier = Modifier
                        .background(DuckGradients.heroGradient(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .statusBarsPadding()
                    ) {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        if (otherParticipantId.isNotEmpty()) onNavigateToProfile(otherParticipantId)
                                    }
                                ) {
                                    ProfileAvatar(profilePictureUrl = otherParticipantAvatar, name = otherParticipantName, size = 40.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(otherParticipantName, style = MaterialTheme.typography.titleMedium, color = heroContent, fontWeight = FontWeight.ExtraBold)
                                        Text(
                                            text = if (conversation?.lastMessageTime != null) "Active recently" else "Connecting...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = heroContentSecondary
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = heroContent)
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                        )
                    }
                }
            },
            bottomBar = {
                MessageInputBar(
                    messageText = messageText,
                    onMessageChange = { messageText = it },
                    onSend = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendMessage(conversationId, currentUser.thisUserName, messageText)
                            messageText = ""
                            scope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size) }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (messages.isEmpty()) {
                    EmptyChatPlaceholder()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                    ) {
                        itemsIndexed(messages, key = { _, msg -> msg.id }) { index, message ->
                            val prevMessage = if (index > 0) messages[index - 1] else null
                            val nextMessage = if (index < messages.size - 1) messages[index + 1] else null

                            val showDateHeader = shouldShowDateHeader(prevMessage, message)
                            if (showDateHeader) DateHeader(message.timestamp)

                            val isCurrentUser = message.senderId == currentUser.thisUID
                            val isSameSenderAsPrev = prevMessage?.senderId == message.senderId && !showDateHeader
                            val isSameSenderAsNext = nextMessage?.senderId == message.senderId && !shouldShowDateHeader(message, nextMessage)

                            val isFirstInGroup = !isSameSenderAsPrev
                            val isLastInGroup = !isSameSenderAsNext

                            val senderAvatar = conversation?.participantAvatars?.get(message.senderId) ?: ""
                            val senderName = conversation?.participantNames?.get(message.senderId) ?: message.senderName

                            AnimatedMessageItem(index) {
                                MessageBubble(
                                    message = message,
                                    isCurrentUser = isCurrentUser,
                                    senderAvatar = senderAvatar,
                                    senderName = senderName,
                                    showAvatar = !isCurrentUser && isLastInGroup,
                                    isFirstInGroup = isFirstInGroup,
                                    isLastInGroup = isLastInGroup,
                                    onAvatarClick = { if (message.senderId != currentUser.thisUID) onNavigateToProfile(message.senderId) }
                                )
                            }

                            if (isLastInGroup) Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Premium Scroll to bottom button
                AnimatedVisibility(
                    visible = !isAtBottom,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = 8.dp)
                ) {
                    FloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(messages.size - 1) } },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to bottom", modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedMessageItem(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInHorizontally(tween(400)) { if (index % 2 == 0) -40 else 40 }
    ) { content() }
}

@Composable
private fun EmptyChatPlaceholder() {
    val heroContent = DuckGradients.heroContentColor()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = heroContent.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, heroContent.copy(alpha = 0.1f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("📬", fontSize = 48.sp)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("No messages yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = heroContent.copy(alpha = 0.8f))
        Text("Be the first to say hello!", style = MaterialTheme.typography.bodyMedium, color = heroContent.copy(alpha = 0.5f))
    }
}

@Composable
private fun DateHeader(timestamp: Timestamp?) {
    val isDark = LocalIsDarkTheme.current
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Text(
                text = formatDateHeader(timestamp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isCurrentUser: Boolean,
    senderAvatar: String,
    senderName: String,
    showAvatar: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    onAvatarClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val bubbleBrush = if (isCurrentUser) DuckGradients.primaryGradient() else null
    val bubbleColor = if (isCurrentUser) {
        Color.Transparent
    } else {
        if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isCurrentUser) {
        Color.White
    } else {
        if (isDark) Color.White.copy(alpha = 0.9f) else TextDark
    }

    val shape = if (isCurrentUser) {
        RoundedCornerShape(
            topStart = 20.dp,
            topEnd = if (isFirstInGroup) 20.dp else 6.dp,
            bottomEnd = if (isLastInGroup) 6.dp else 6.dp,
            bottomStart = 20.dp
        )
    } else {
        RoundedCornerShape(
            topStart = if (isFirstInGroup) 20.dp else 6.dp,
            topEnd = 20.dp,
            bottomEnd = 20.dp,
            bottomStart = if (isLastInGroup) 6.dp else 6.dp
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isCurrentUser) {
            if (showAvatar) {
                ProfileAvatar(profilePictureUrl = senderAvatar, name = senderName, size = 36.dp, onClick = onAvatarClick)
            } else {
                Spacer(modifier = Modifier.width(36.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Surface(
                shape = shape,
                color = bubbleColor,
                modifier = Modifier.widthIn(max = 300.dp).then(
                    if (bubbleBrush != null) Modifier.background(bubbleBrush, shape) else Modifier
                ),
                border = if (!isCurrentUser) {
                    BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                } else null,
                shadowElevation = if (isCurrentUser) 4.dp else 0.dp
            ) {
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            if (isLastInGroup) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
                ) {
                    message.timestamp?.let { timestamp ->
                        Text(
                            text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(timestamp.toDate()),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    if (isCurrentUser) {
                        Icon(
                            imageVector = if (message.isRead) Icons.Default.DoneAll else Icons.Default.Done,
                            contentDescription = if (message.isRead) "Read" else "Sent",
                            modifier = Modifier.size(14.dp),
                            tint = if (message.isRead) MaterialTheme.colorScheme.primary else if (isDark) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent, // Surface background transparent
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier
            .background(DuckGradients.heroGradient().takeIf { !isDark || LocalThemeId.current >= 3 } ?: Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(top = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = onMessageChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Type a message...", color = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Gray) },
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = if (isDark) Color.White else TextDark,
                            unfocusedTextColor = if (isDark) Color.White else TextDark,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, label = "sendScale")

                Surface(
                    onClick = onSend,
                    enabled = messageText.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    shape = CircleShape,
                    color = if (messageText.isNotBlank()) MaterialTheme.colorScheme.primary else if (isDark) Color.White.copy(alpha = 0.1f) else Color.LightGray.copy(alpha = 0.3f),
                    interactionSource = interactionSource,
                    shadowElevation = if (messageText.isNotBlank()) 4.dp else 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (messageText.isNotBlank()) Color.White else if (isDark) Color.White.copy(alpha = 0.3f) else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// Helpers
private fun shouldShowDateHeader(prev: Message?, curr: Message): Boolean {
    if (prev == null) return true
    val prevDate = prev.timestamp?.toDate() ?: return true
    val currDate = curr.timestamp?.toDate() ?: return true

    val cal1 = Calendar.getInstance().apply { time = prevDate }
    val cal2 = Calendar.getInstance().apply { time = currDate }

    return cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR) ||
           cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR)
}

private fun formatDateHeader(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    val now = Calendar.getInstance()
    val msgDate = Calendar.getInstance().apply { time = date }

    return when {
        now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR) -> "TODAY"

        now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - 1 == msgDate.get(Calendar.DAY_OF_YEAR) -> "YESTERDAY"

        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date).uppercase()
    }
}
