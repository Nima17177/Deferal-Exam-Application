package com.example.duckduckdefer.screens.SettingsScreens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import com.example.duckduckdefer.viewmodels.FeedbackViewModel
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════
//  Feedback Screen
// ══════════════════════════════════════════════════════════

private val feedbackCategories = listOf(
    "General" to Icons.Default.ChatBubbleOutline,
    "Bug Report" to Icons.Default.BugReport,
    "Feature Request" to Icons.Default.Lightbulb,
    "UI / Design" to Icons.Default.Palette,
    "Performance" to Icons.Default.Speed
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    user: UserMembersClass,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit = {},
    feedbackViewModel: FeedbackViewModel = viewModel()
) {
    val uiState by feedbackViewModel.uiState.collectAsState()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    // Success dialog
    if (uiState.isSubmitted) {
        AlertDialog(
            onDismissRequest = {
                feedbackViewModel.resetForm()
                onBack()
            },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "Thank You!",
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    "Your feedback has been submitted successfully. We appreciate you helping us improve DuckDuckDefer!",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        feedbackViewModel.resetForm()
                        onBack()
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { feedbackViewModel.resetForm() }
                ) {
                    Text("Submit Another")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    // Error snackbar
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(3000)
            feedbackViewModel.clearError()
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
                                    text = "Feedback",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                Text(
                                    text = "Help us improve",
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
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        val titleBringIntoView = remember { BringIntoViewRequester() }
        val bodyBringIntoView = remember { BringIntoViewRequester() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Hero Card
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600)) + expandVertically(tween(600))
            ) {
                FeedbackHeroCard()
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn()
                ) {
                    Column {
                        // ── Error Banner ──────────────────────────────
                        AnimatedVisibility(visible = uiState.errorMessage != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = uiState.errorMessage ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // ── Category Selection ────────────────────────
                        FeedbackSectionHeader(
                            title = "Category",
                            icon = Icons.Default.Category
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CategorySelector(
                            categories = feedbackCategories,
                            selectedCategory = uiState.category,
                            onCategorySelected = feedbackViewModel::onCategoryChanged
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // ── Rating ────────────────────────────────────
                        FeedbackSectionHeader(
                            title = "Rating",
                            icon = Icons.Default.Star
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RatingSelector(
                            rating = uiState.rating,
                            onRatingChanged = feedbackViewModel::onRatingChanged
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // ── Title ─────────────────────────────────────
                        FeedbackSectionHeader(
                            title = "Title",
                            icon = Icons.Default.Title
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.title,
                            onValueChange = feedbackViewModel::onTitleChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(titleBringIntoView)
                                .onFocusEvent { state ->
                                    if (state.isFocused) {
                                        coroutineScope.launch { titleBringIntoView.bringIntoView() }
                                    }
                                },
                            placeholder = { Text("Brief summary of your feedback") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            supportingText = {
                                Text(
                                    "${uiState.title.length}/100",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ── Description ───────────────────────────────
                        FeedbackSectionHeader(
                            title = "Description",
                            icon = Icons.Default.Description
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.body,
                            onValueChange = feedbackViewModel::onBodyChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 150.dp)
                                .bringIntoViewRequester(bodyBringIntoView)
                                .onFocusEvent { state ->
                                    if (state.isFocused) {
                                        coroutineScope.launch { bodyBringIntoView.bringIntoView() }
                                    }
                                },
                            placeholder = { Text("Tell us more about your experience, ideas, or the issue you encountered…") },
                            maxLines = 8,
                            shape = RoundedCornerShape(16.dp),
                            supportingText = {
                                Text(
                                    "${uiState.body.length}/1000",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // ── Submit Button ─────────────────────────────
                        Button(
                            onClick = {
                                feedbackViewModel.submitFeedback(
                                    userId = user.thisUID,
                                    userName = user.thisUserName
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = !uiState.isSubmitting,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (uiState.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Submitting…", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Submit Feedback",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  Hero Card
// ══════════════════════════════════════════════════════════

@Composable
private fun FeedbackHeroCard() {
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Default.RateReview,
                contentDescription = null,
                tint = heroContent,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "We'd Love Your Feedback!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = heroContent,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your thoughts help us build a better experience for everyone.",
                style = MaterialTheme.typography.bodyMedium,
                color = heroContentSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  Section Header
// ══════════════════════════════════════════════════════════

@Composable
private fun FeedbackSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

// ══════════════════════════════════════════════════════════
//  Category Selector
// ══════════════════════════════════════════════════════════

@Composable
private fun CategorySelector(
    categories: List<Pair<String, ImageVector>>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1: first 3 items
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.take(3).forEach { (label, icon) ->
                CategoryChip(
                    label = label,
                    icon = icon,
                    isSelected = selectedCategory == label,
                    onClick = { onCategorySelected(label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // Row 2: remaining items
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.drop(3).forEach { (label, icon) ->
                CategoryChip(
                    label = label,
                    icon = icon,
                    isSelected = selectedCategory == label,
                    onClick = { onCategorySelected(label) },
                    modifier = Modifier.weight(1f)
                )
            }
            // Fill remaining space
            if (categories.size > 3 && (categories.size - 3) < 3) {
                repeat(3 - (categories.size - 3)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    val contentColor = if (isSelected)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        shadowElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  Star Rating
// ══════════════════════════════════════════════════════════

@Composable
private fun RatingSelector(
    rating: Int,
    onRatingChanged: (Int) -> Unit
) {
    val labels = listOf("", "Poor", "Fair", "Good", "Great", "Excellent")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 1..5) {
                    val starColor = if (i <= rating)
                        Color(0xFFFFB300)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

                    IconButton(
                        onClick = { onRatingChanged(i) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarOutline,
                            contentDescription = "Rate $i star",
                            tint = starColor,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = rating > 0) {
                Text(
                    text = labels.getOrElse(rating) { "" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}







