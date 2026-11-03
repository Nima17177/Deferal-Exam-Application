package com.example.duckduckdefer.screens.messaging

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.components.chatbot.ChatbotRoutes
import com.example.duckduckdefer.components.chatbot.WizardInputType
import com.example.duckduckdefer.components.chatbot.ContentBlock
import com.example.duckduckdefer.components.chatbot.parseMarkdownToAnnotatedString
import com.example.duckduckdefer.components.chatbot.parseMessageContent
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.ui.theme.LocalIsDarkTheme
import com.example.duckduckdefer.ui.theme.LocalThemeId
import com.example.duckduckdefer.ui.theme.DuckOrange
import com.example.duckduckdefer.ui.theme.TextDark
import com.example.duckduckdefer.viewmodels.ChatbotViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuckBotChatScreen(
    chatbotViewModel: ChatbotViewModel,
    onBack: () -> Unit,
    onNavigate: (routeId: String) -> Unit
) {
    val messages by chatbotViewModel.messages.collectAsState()
    val isLoading by chatbotViewModel.isLoading.collectAsState()
    val quickChips by chatbotViewModel.quickChips.collectAsState()
    val isSearchActive by chatbotViewModel.isSearchActive.collectAsState()
    val searchQuery by chatbotViewModel.searchQuery.collectAsState()
    val searchResults by chatbotViewModel.searchResults.collectAsState()
    val hasMoreHistory by chatbotViewModel.hasMoreHistory.collectAsState()
    val wizardState by chatbotViewModel.wizardState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        PDFBoxResourceLoader.init(context)
    }

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
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val document = PDDocument.load(stream)
                    val stripper = PDFTextStripper()
                    val text = stripper.getText(document)
                    document.close()
                    if (text.isNotBlank()) {
                        chatbotViewModel.sendPdfText(text)
                    } else {
                        chatbotViewModel.sendMessage("I uploaded a PDF but couldn't extract text. The file may be image-based.")
                    }
                }
            } catch (e: Exception) {
                chatbotViewModel.sendMessage("Error reading PDF: ${e.localizedMessage}")
            }
        }
    }

    val isDark = LocalIsDarkTheme.current

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // FULL SCREEN BACKGROUND GLOW - matches ChatScreen pattern
        Box(modifier = Modifier.fillMaxSize().background(DuckGradients.surfaceGlowGradient()).alpha(0.4f))

        Scaffold(
            containerColor = Color.Transparent, // Let the background show through
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.SmartToy,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "DuckBot",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = heroContent,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            "AI Assistant",
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
                            actions = {
                                IconButton(onClick = { chatbotViewModel.toggleSearch() }) {
                                    Icon(
                                        if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = if (isSearchActive) "Close search" else "Search chat",
                                        tint = heroContent
                                    )
                                }
                                IconButton(onClick = { chatbotViewModel.clearConversation() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset chat", tint = heroContent)
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                        )
                    }
                }
            },
            bottomBar = {
                Column {
                    if (messages.size <= 2 && quickChips.isNotEmpty() && wizardState == null) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isDark) Color.Black.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(quickChips) { chip ->
                                SuggestionChip(
                                    onClick = { chatbotViewModel.sendMessage(chip) },
                                    label = {
                                        Text(
                                            chip,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            color = if (isDark) Color.White.copy(alpha = 0.9f) else TextDark
                                        )
                                    },
                                    modifier = Modifier.height(32.dp),
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.8f),
                                        labelColor = if (isDark) Color.White.copy(alpha = 0.9f) else TextDark
                                    ),
                                    border = BorderStroke(
                                        0.5.dp,
                                        if (isDark) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }

                    if (wizardState != null) {
                        WizardPickerBar(
                            wizardState = wizardState!!,
                            isDark = isDark,
                            onSelect = { chatbotViewModel.advanceWizard(it) },
                            onCancel = { chatbotViewModel.cancelWizard() }
                        )
                    }

                    DuckBotInputBar(
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        isLoading = isLoading,
                        onSend = {
                            if (inputText.isNotBlank()) {
                                chatbotViewModel.sendMessage(inputText)
                                inputText = ""
                                scope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
                            }
                        },
                        onPdfUpload = { pdfLauncher.launch("application/pdf") }
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search bar
                    AnimatedVisibility(visible = isSearchActive) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isDark) Color.Black.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { chatbotViewModel.setSearchQuery(it) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                placeholder = { Text("Search messages...", fontSize = 14.sp) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { chatbotViewModel.setSearchQuery("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    focusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White,
                                    unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White
                                )
                            )
                        }
                    }

                    // Search results overlay or normal chat
                    if (isSearchActive && searchQuery.isNotBlank()) {
                        SearchResultsList(
                            results = searchResults,
                            query = searchQuery,
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                    } else if (messages.isEmpty()) {
                        Box(modifier = Modifier.weight(1f)) { DuckBotEmptyPlaceholder() }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                            ) {
                                if (hasMoreHistory) {
                                    item {
                                        TextButton(
                                            onClick = { chatbotViewModel.loadMoreHistory() },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Load earlier messages", fontSize = 12.sp)
                                        }
                                    }
                                }

                                itemsIndexed(messages) { index, msg ->
                                    val isLastBot = !msg.isUser && (index == messages.lastIndex || messages.subList(index + 1, messages.size).all { it.isUser })
                                    AnimatedMessageItem(index) {
                                        DuckBotMessageBubble(
                                            message = msg,
                                            isLastBotMessage = isLastBot,
                                            onNavigate = onNavigate,
                                            onConfirm = { chatbotViewModel.confirmAction(it) },
                                            onCancel = { chatbotViewModel.cancelAction(it) },
                                            onQuickReply = { chip ->
                                                chatbotViewModel.sendMessage(chip)
                                                scope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
                                            }
                                        )
                                    }
                                }

                                if (isLoading) {
                                    item { TypingIndicatorBubble() }
                                }
                            }

                            if (!isAtBottom && messages.size > 5) {
                                FloatingActionButton(
                                    onClick = { scope.launch { listState.animateScrollToItem(messages.lastIndex) } },
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = 8.dp).size(44.dp),
                                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to bottom", modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuckBotEmptyPlaceholder() {
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
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("DuckBot", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = heroContent.copy(alpha = 0.8f))
        Spacer(modifier = Modifier.height(4.dp))
        Text("Your AI assistant is ready to help!", style = MaterialTheme.typography.bodyMedium, color = heroContent.copy(alpha = 0.5f))
    }
}

@Composable
private fun TypingIndicatorBubble() {
    val isDark = LocalIsDarkTheme.current
    val bubbleColor = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
    val dotColor = if (isDark) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)

    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val offsets = (0 until 3).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    0f at 0
                    -6f at 200 + i * 150 using FastOutSlowInEasing
                    0f at 400 + i * 150 using FastOutSlowInEasing
                    0f at 1200
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "dot$i"
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Surface(
            shape = shape,
            color = bubbleColor,
            border = BorderStroke(
                0.5.dp,
                if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                offsets.forEach { anim ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer { translationY = anim.value.dp.toPx() }
                            .background(dotColor, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    headers: List<String>,
    rows: List<List<String>>,
    textColor: Color,
    isDark: Boolean
) {
    val headerBg = if (isDark) Color.White.copy(alpha = 0.08f) else DuckOrange.copy(alpha = 0.12f)
    val evenRowBg = if (isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    val colCount = headers.size

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        border = BorderStroke(0.5.dp, dividerColor)
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = 200.dp)
        ) {
            Row(modifier = Modifier.background(headerBg).padding(vertical = 6.dp, horizontal = 4.dp)) {
                headers.forEach { header ->
                    Text(
                        text = header,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.widthIn(min = 60.dp).weight(1f).padding(horizontal = 8.dp)
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = dividerColor)

            rows.forEachIndexed { rowIdx, cells ->
                if (rowIdx > 0) HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
                val rowBg = if (rowIdx % 2 == 0) Color.Transparent else evenRowBg
                Row(modifier = Modifier.background(rowBg).padding(vertical = 5.dp, horizontal = 4.dp)) {
                    for (colIdx in 0 until colCount) {
                        Text(
                            text = cells.getOrElse(colIdx) { "" },
                            color = textColor.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            modifier = Modifier.widthIn(min = 60.dp).weight(1f).padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuckBotMessageBubble(
    message: ChatbotViewModel.ChatMessage,
    isLastBotMessage: Boolean = false,
    onNavigate: (String) -> Unit,
    onConfirm: (ChatbotViewModel.PendingAction) -> Unit,
    onCancel: (ChatbotViewModel.PendingAction) -> Unit,
    onQuickReply: (String) -> Unit = {}
) {
    val isDark = LocalIsDarkTheme.current
    val isUser = message.isUser
    val bubbleBrush = if (isUser) DuckGradients.primaryGradient() else null
    val bubbleColor = if (isUser) {
        Color.Transparent
    } else {
        if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        Color.White
    } else {
        if (isDark) Color.White.copy(alpha = 0.9f) else TextDark
    }

    val shape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            val contentBlocks = remember(message.text) { parseMessageContent(message.text) }
            val hasTable = contentBlocks.any { it is ContentBlock.Table }
            val hasStructured = message.structuredData != null

            Surface(
                shape = shape,
                color = bubbleColor,
                modifier = Modifier.widthIn(max = if ((hasTable || hasStructured) && !isUser) 340.dp else 300.dp).then(
                    if (bubbleBrush != null) Modifier.background(bubbleBrush, shape) else Modifier
                ),
                border = if (!isUser) {
                    BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                } else null,
                shadowElevation = if (isUser) 4.dp else 0.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    contentBlocks.forEachIndexed { idx, block ->
                        if (idx > 0) Spacer(Modifier.height(6.dp))
                        when (block) {
                            is ContentBlock.RichText -> {
                                Text(
                                    text = parseMarkdownToAnnotatedString(
                                        text = block.text,
                                        baseColor = textColor,
                                        baseFontSize = 14.sp
                                    ),
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp)
                                )
                            }
                            is ContentBlock.Table -> {
                                MarkdownTable(
                                    headers = block.headers,
                                    rows = block.rows,
                                    textColor = textColor,
                                    isDark = isDark
                                )
                            }
                        }
                    }

                    if (message.structuredData != null) {
                        Spacer(Modifier.height(8.dp))
                        StructuredDataCards(message.structuredData, isDark)
                    }
                }
            }

            if (message.navigateRouteId != null) {
                val route = ChatbotRoutes.findRoute(message.navigateRouteId)
                if (route != null) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { onNavigate(route.routeId) },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Go to ${route.displayName}", fontSize = 12.sp)
                    }
                }
            }

            if (message.pendingAction != null) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onConfirm(message.pendingAction) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Confirm", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onCancel(message.pendingAction) },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                }
            }

            if (isLastBotMessage && message.quickReplies.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    message.quickReplies.forEach { chip ->
                        SuggestionChip(
                            onClick = { onQuickReply(chip) },
                            label = { Text(chip, fontSize = 11.sp, maxLines = 1) },
                            modifier = Modifier.height(30.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isDark) Color.White.copy(alpha = 0.08f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                labelColor = if (isDark) Color.White.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.primary
                            ),
                            border = BorderStroke(
                                0.5.dp,
                                if (isDark) Color.White.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StructuredDataCards(data: ChatbotViewModel.StructuredData, isDark: Boolean) {
    when (data) {
        is ChatbotViewModel.StructuredData.BookingList -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                data.bookings.forEach { booking -> BookingCardView(booking, isDark) }
            }
        }
        is ChatbotViewModel.StructuredData.BookingDetail -> {
            BookingCardView(data.booking, isDark)
        }
        is ChatbotViewModel.StructuredData.SessionList -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                data.rooms.forEach { room -> RoomCardView(room, isDark) }
            }
        }
        is ChatbotViewModel.StructuredData.DeferralList -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                data.deferrals.forEach { deferral -> DeferralCardView(deferral, isDark) }
            }
        }
        is ChatbotViewModel.StructuredData.Stats -> {
            StatsCardView(data.stats, isDark)
        }
        is ChatbotViewModel.StructuredData.Checklist -> {
            ChecklistCardView(data, isDark)
        }
        is ChatbotViewModel.StructuredData.NotificationList -> {
            NotificationCardView(data, isDark)
        }
    }
}

@Composable
private fun BookingCardView(booking: ChatbotViewModel.BookingCard, isDark: Boolean) {
    val statusColor = when {
        booking.statusCode >= 4 -> Color(0xFF4CAF50)
        booking.statusCode == 3 -> Color(0xFF2196F3)
        booking.statusCode == 0 -> Color(0xFFF44336)
        else -> Color(0xFFFF9800)
    }
    val cardBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.7f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val textPrimary = if (isDark) Color.White.copy(alpha = 0.9f) else TextDark
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(0.5.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EventNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(booking.examName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(booking.status, fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = textSecondary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("${booking.date}  ${booking.startTime}-${booking.endTime}", fontSize = 11.sp, color = textSecondary)
            }
            Spacer(Modifier.height(2.dp))
            Row {
                Icon(Icons.Default.Room, contentDescription = null, tint = textSecondary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(booking.room, fontSize = 11.sp, color = textSecondary)
            }
            Text("ID: ${booking.id}", fontSize = 9.sp, color = textSecondary.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun RoomCardView(room: ChatbotViewModel.RoomCard, isDark: Boolean) {
    val cardBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.7f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val textPrimary = if (isDark) Color.White.copy(alpha = 0.9f) else TextDark
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray
    val fillRatio = if (room.totalCap > 0) room.availableSeats.toFloat() / room.totalCap else 0f
    val barColor = when {
        fillRatio > 0.5f -> Color(0xFF4CAF50)
        fillRatio > 0.2f -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(0.5.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MeetingRoom, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(room.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary, modifier = Modifier.weight(1f))
                Text("${room.startTime}-${room.endTime}", fontSize = 11.sp, color = textSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${room.availableSeats}/${room.totalCap} seats", fontSize = 11.sp, color = textSecondary)
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (isDark) Color.White.copy(alpha = 0.1f) else Color.LightGray.copy(alpha = 0.3f))) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fillRatio).clip(RoundedCornerShape(3.dp)).background(barColor))
                }
            }
        }
    }
}

@Composable
private fun DeferralCardView(deferral: ChatbotViewModel.DeferralCard, isDark: Boolean) {
    val statusColor = when (deferral.status.lowercase()) {
        "approved" -> Color(0xFF4CAF50)
        "rejected" -> Color(0xFFF44336)
        else -> Color(0xFFFF9800)
    }
    val cardBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.7f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val textPrimary = if (isDark) Color.White.copy(alpha = 0.9f) else TextDark
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(0.5.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(deferral.courseName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(deferral.status.replaceFirstChar { it.uppercase() }, fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Reason: ${deferral.reason}", fontSize = 11.sp, color = textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("ID: ${deferral.id}", fontSize = 9.sp, color = textSecondary.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StatsCardView(stats: List<ChatbotViewModel.StatItem>, isDark: Boolean) {
    val cardBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.7f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val textPrimary = if (isDark) Color.White.copy(alpha = 0.9f) else TextDark
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray

    Surface(shape = RoundedCornerShape(12.dp), color = cardBg, border = BorderStroke(0.5.dp, borderColor)) {
        Column(modifier = Modifier.padding(12.dp)) {
            val chunked = stats.chunked(2)
            chunked.forEachIndexed { idx, row ->
                if (idx > 0) Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item ->
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(item.value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(item.label, fontSize = 10.sp, color = textSecondary)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ChecklistCardView(data: ChatbotViewModel.StructuredData.Checklist, isDark: Boolean) {
    val cardBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.7f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val textPrimary = if (isDark) Color.White.copy(alpha = 0.9f) else TextDark
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray

    Surface(shape = RoundedCornerShape(12.dp), color = cardBg, border = BorderStroke(0.5.dp, borderColor)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(data.exam, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
            Spacer(Modifier.height(2.dp))
            Text("${data.date}  ${data.time}  ${data.room}", fontSize = 11.sp, color = textSecondary)
            Spacer(Modifier.height(8.dp))
            data.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(
                        if (item.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (item.completed) Color(0xFF4CAF50) else textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(item.label, fontSize = 12.sp, color = if (item.completed) textPrimary else textSecondary)
                }
            }
        }
    }
}

@Composable
private fun NotificationCardView(data: ChatbotViewModel.StructuredData.NotificationList, isDark: Boolean) {
    val cardBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.7f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val textPrimary = if (isDark) Color.White.copy(alpha = 0.9f) else TextDark
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray

    Surface(shape = RoundedCornerShape(12.dp), color = cardBg, border = BorderStroke(0.5.dp, borderColor)) {
        Column(modifier = Modifier.padding(12.dp)) {
            data.notifications.take(5).forEach { notif ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                    if (!notif.read) {
                        Box(modifier = Modifier.padding(top = 4.dp).size(6.dp).background(Color(0xFFF44336), CircleShape))
                    } else {
                        Spacer(Modifier.size(6.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(notif.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (notif.body.isNotBlank()) {
                            Text(notif.body, fontSize = 11.sp, color = textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (data.notifications.size > 5) {
                Spacer(Modifier.height(4.dp))
                Text("+ ${data.notifications.size - 5} more", fontSize = 10.sp, color = textSecondary)
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<ChatbotViewModel.ChatMessage>,
    query: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val textPrimary = if (isDark) Color.White.copy(alpha = 0.9f) else TextDark
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

    if (results.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("No results found", color = textSecondary, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Text("${results.size} result(s)", fontSize = 12.sp, color = textSecondary, modifier = Modifier.padding(bottom = 4.dp))
            }
            items(results) { msg ->
                val highlighted = buildAnnotatedString {
                    val text = msg.text
                    val lowerText = text.lowercase()
                    val lowerQuery = query.lowercase()
                    var lastIndex = 0
                    var searchFrom = 0
                    while (true) {
                        val idx = lowerText.indexOf(lowerQuery, searchFrom)
                        if (idx < 0) break
                        append(text.substring(lastIndex, idx))
                        withStyle(SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold)) {
                            append(text.substring(idx, idx + query.length))
                        }
                        lastIndex = idx + query.length
                        searchFrom = lastIndex
                    }
                    if (lastIndex < text.length) append(text.substring(lastIndex))
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (msg.isUser) Icons.Default.Person else Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = highlighted,
                            fontSize = 13.sp,
                            color = textPrimary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardPickerBar(
    wizardState: ChatbotViewModel.WizardState,
    isDark: Boolean,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit
) {
    val step = wizardState.steps.getOrNull(wizardState.currentStepIndex) ?: return
    val barBg = if (isDark) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = barBg
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Step ${wizardState.currentStepIndex + 1}/${wizardState.steps.size}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel, modifier = Modifier.height(28.dp)) {
                    Text("Cancel", fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(4.dp))

            when (step.inputType) {
                WizardInputType.DATE_PICKER -> {
                    WizardDatePicker(isDark = isDark, onSelect = onSelect)
                }
                WizardInputType.ROOM_PICKER -> {
                    if (wizardState.availableOptions.isNotEmpty()) {
                        WizardOptionPicker(
                            options = wizardState.availableOptions,
                            icon = Icons.Default.MeetingRoom,
                            isDark = isDark,
                            onSelect = onSelect
                        )
                    }
                }
                WizardInputType.TIME_PICKER -> {
                    WizardTimePicker(isDark = isDark, onSelect = onSelect)
                }
                WizardInputType.BOOLEAN -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("true" to "Yes", "false" to "No").forEach { (value, label) ->
                            SuggestionChip(
                                onClick = { onSelect(value) },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }
                else -> { /* TEXT and EMAIL: user types in the input bar */ }
            }
        }
    }
}

@Composable
private fun WizardDatePicker(isDark: Boolean, onSelect: (String) -> Unit) {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val dayFmt = java.text.SimpleDateFormat("EEE", java.util.Locale.US)
    val dates = (0 until 14).map { offset ->
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, offset)
        val dateStr = fmt.format(cal.time)
        val dayStr = if (offset == 0) "Today" else if (offset == 1) "Tmrw" else dayFmt.format(cal.time)
        val dayNum = java.text.SimpleDateFormat("d", java.util.Locale.US).format(cal.time)
        Triple(dateStr, dayStr, dayNum)
    }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        dates.forEach { (dateStr, dayStr, dayNum) ->
            Surface(
                onClick = { onSelect(dateStr) },
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(dayStr, fontSize = 10.sp, color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Gray)
                    Text(dayNum, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun WizardTimePicker(isDark: Boolean, onSelect: (String) -> Unit) {
    val times = (8..18).flatMap { h -> listOf("${"%02d".format(h)}:00", "${"%02d".format(h)}:30") }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        times.forEach { time ->
            SuggestionChip(
                onClick = { onSelect(time) },
                label = { Text(time, fontSize = 12.sp) },
                modifier = Modifier.height(32.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ),
                border = BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun WizardOptionPicker(
    options: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            SuggestionChip(
                onClick = { onSelect(option) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(option, fontSize = 12.sp)
                    }
                },
                modifier = Modifier.height(32.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ),
                border = BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun DuckBotInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    onPdfUpload: () -> Unit,
    onVoiceInput: () -> Unit = {}
) {
    val isDark = LocalIsDarkTheme.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier
            .background(
                DuckGradients.heroGradient().takeIf { !isDark || LocalThemeId.current >= 3 }
                    ?: Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)),
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
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
                IconButton(
                    onClick = onPdfUpload,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Upload PDF",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onVoiceInput,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voice input",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    modifier = Modifier.weight(1f),
                    color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Ask DuckBot...",
                                color = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Gray
                            )
                        },
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
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    shape = CircleShape,
                    color = if (inputText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary
                            else if (isDark) Color.White.copy(alpha = 0.1f)
                            else Color.LightGray.copy(alpha = 0.3f),
                    interactionSource = interactionSource,
                    shadowElevation = if (inputText.isNotBlank()) 4.dp else 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank() && !isLoading) Color.White
                                   else if (isDark) Color.White.copy(alpha = 0.3f)
                                   else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
