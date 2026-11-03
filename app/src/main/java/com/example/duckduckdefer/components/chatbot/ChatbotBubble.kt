package com.example.duckduckdefer.components.chatbot

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.example.duckduckdefer.viewmodels.ChatbotViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Floating AI chatbot bubble + expandable chat panel.
 *
 * Place this composable inside a Box that wraps your main content
 * so it overlays on top of every screen.
 */
@Composable
fun ChatbotBubble(
    viewModel: ChatbotViewModel,
    onNavigate: (routeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val bubbleSizeDp = 56.dp
    val bubbleSizePx = with(density) { bubbleSizeDp.toPx() }
    val defaultPaddingPx = with(density) { 16.dp.toPx() }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Bubble position offset from bottom-end corner (default 0,0 = bottom-end with 16dp padding)
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size }
    ) {
        // ── Expanded chat panel ─────────────────────────────
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp)
                .zIndex(100f)
        ) {
            ChatPanel(
                viewModel = viewModel,
                onNavigate = { routeId ->
                    isExpanded = false
                    onNavigate(routeId)
                },
                onClose = { isExpanded = false }
            )
        }

        // ── Floating bubble (draggable via long-press) ──────
        FloatingActionButton(
            onClick = { if (!isDragging) isExpanded = !isExpanded },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset {
                    IntOffset(offsetX.roundToInt(), offsetY.roundToInt())
                }
                .padding(16.dp)
                .size(bubbleSizeDp)
                .zIndex(101f)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = offsetX + dragAmount.x
                            val newY = offsetY + dragAmount.y

                            val maxLeft = -(containerSize.width - bubbleSizePx - defaultPaddingPx * 2)
                            val maxUp = -(containerSize.height - bubbleSizePx - defaultPaddingPx * 2)

                            offsetX = newX.coerceIn(maxLeft, 0f)
                            offsetY = newY.coerceIn(maxUp, 0f)
                        }
                    )
                }
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.SmartToy,
                contentDescription = if (isExpanded) "Close chat" else "Open AI assistant",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Chat Panel
// ─────────────────────────────────────────────────────────────

@Composable
private fun ChatPanel(
    viewModel: ChatbotViewModel,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val quickChips by viewModel.quickChips.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Auto-scroll when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Initialize PDFBox once per composition
    LaunchedEffect(Unit) {
        PDFBoxResourceLoader.init(context)
    }

    // PDF picker
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
                        viewModel.sendPdfText(text)
                    } else {
                        viewModel.sendMessage("I uploaded a PDF but couldn't extract text. The file may be image-based.")
                    }
                }
            } catch (e: Exception) {
                viewModel.sendMessage("Error reading PDF: ${e.localizedMessage}")
            }
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .width(340.dp)
            .heightIn(max = 520.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header ──────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "DuckBot",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.clearConversation() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reset chat",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // ── Messages ────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages) { msg ->
                    MessageBubble(
                        message = msg,
                        onNavigate = onNavigate,
                        onConfirm = { viewModel.confirmAction(it) },
                        onCancel = { viewModel.cancelAction(it) }
                    )
                }

                // Loading indicator
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Thinking…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── Quick chips (show only when few messages) ─
            if (messages.size <= 2 && quickChips.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(quickChips) { chip ->
                        SuggestionChip(
                            onClick = { viewModel.sendMessage(chip) },
                            label = {
                                Text(chip, fontSize = 12.sp, maxLines = 1)
                            },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }

            // ── Input bar ───────────────────────────────
            HorizontalDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // PDF upload button
                IconButton(
                    onClick = { pdfLauncher.launch("application/pdf") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Upload PDF",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask DuckBot…", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        }
                    )
                )

                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Single message bubble
// ─────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(
    message: ChatbotViewModel.ChatMessage,
    onNavigate: (String) -> Unit,
    onConfirm: (ChatbotViewModel.PendingAction) -> Unit,
    onCancel: (ChatbotViewModel.PendingAction) -> Unit
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isUser)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (message.isUser)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        val contentBlocks = remember(message.text) { parseMessageContent(message.text) }
        val hasTable = contentBlocks.any { it is ContentBlock.Table }

        Box(
            modifier = Modifier
                .widthIn(max = if (hasTable && !message.isUser) 320.dp else 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Column {
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
                                lineHeight = 20.sp
                            )
                        }
                        is ContentBlock.Table -> {
                            BubbleTable(
                                headers = block.headers,
                                rows = block.rows,
                                textColor = textColor
                            )
                        }
                    }
                }
            }
        }

        // Navigation button
        if (message.navigateRouteId != null) {
            val route = ChatbotRoutes.findRoute(message.navigateRouteId)
            if (route != null) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onNavigate(route.routeId) },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Go to ${route.displayName}", fontSize = 12.sp)
                }
            }
        }

        // Confirmation buttons for destructive actions
        if (message.pendingAction != null) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onConfirm(message.pendingAction) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Confirm", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onCancel(message.pendingAction) },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BubbleTable(
    headers: List<String>,
    rows: List<List<String>>,
    textColor: Color
) {
    val headerBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    val evenRowBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    val colCount = headers.size

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = BorderStroke(0.5.dp, dividerColor)
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = 180.dp)
        ) {
            Row(modifier = Modifier.background(headerBg).padding(vertical = 5.dp, horizontal = 4.dp)) {
                headers.forEach { header ->
                    Text(
                        text = header,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.widthIn(min = 56.dp).weight(1f).padding(horizontal = 6.dp)
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = dividerColor)

            rows.forEachIndexed { rowIdx, cells ->
                if (rowIdx > 0) HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
                val rowBg = if (rowIdx % 2 == 0) Color.Transparent else evenRowBg
                Row(modifier = Modifier.background(rowBg).padding(vertical = 4.dp, horizontal = 4.dp)) {
                    for (colIdx in 0 until colCount) {
                        Text(
                            text = cells.getOrElse(colIdx) { "" },
                            color = textColor.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            maxLines = 2,
                            modifier = Modifier.widthIn(min = 56.dp).weight(1f).padding(horizontal = 6.dp)
                        )
                    }
                }
            }
        }
    }
}


