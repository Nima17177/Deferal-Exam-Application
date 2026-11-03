package com.example.duckduckdefer.screens.SettingsScreens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySettingsScreen(
    user: UserMembersClass,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit = {},
    onThemeChanged: (Int) -> Unit = {},
    onFontSizeChanged: (Int) -> Unit = {},
) {
    var fontSizeIndex by remember { mutableStateOf(user.fontSizePreference.toFloat()) } // 0: Small, 1: Medium, 2: Large
    var themePreference by remember { mutableIntStateOf(user.themePreference) }
    var isLoading by remember { mutableStateOf(true) }

    val fontSizeLabels = listOf("Small", "Medium", "Large")

    // Theme names for the UI
    val themeOptions = listOf(
        0 to "System Default",
        1 to "Light Mode",
        2 to "Dark Mode",
        3 to "Neon Night",
        4 to "Ocean Breeze",
        5 to "Forest Guardian",
        6 to "Cosmic Voyage 🚀",
        7 to "Sunset Boulevard 🌅",
        8 to "Midnight Cherry 🍒",
        9 to "Arctic Frost ❄\uFE0F",
        10 to "Retrowave 🕹\uFE0F",
        11 to "Lavender Dreams 💜",
        12 to "Duck Mode 🦆"
    )

    // Fetch initial state from Firestore
    LaunchedEffect(user.thisUID) {
        FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .document(user.thisUID)
            .get()
            .addOnSuccessListener { snapshot ->
                val fsFontSize = (snapshot.getLong("fontSizePreference") ?: 1L).toInt()
                fontSizeIndex = fsFontSize.toFloat()
                user.fontSizePreference = fsFontSize
                onFontSizeChanged(fsFontSize)

                themePreference = (snapshot.getLong("themePreference") ?: 0L).toInt()
                user.themePreference = themePreference
                
                // Refresh unlocked themes from Firestore
                val unlocked = (snapshot.get("unlockedThemes") as? List<*>)?.mapNotNull { (it as? Long)?.toInt() ?: (it as? Int) } ?: listOf(0, 1, 2)
                user.unlockedThemes = unlocked
                
                onThemeChanged(themePreference)
                
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
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
                        .background(DuckGradients.heroGradient(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .statusBarsPadding()
                ) {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Appearance",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                Text(
                                    text = "Theme and font size",
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
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                AppearanceSection(title = "App Theme", icon = Icons.Default.Palette) {
                    themeOptions.forEachIndexed { index, pair ->
                        val themeId = pair.first
                        val label = pair.second
                        val isUnlocked = user.unlockedThemes.contains(themeId)
                        
                        if (isUnlocked) {
                            PremiumRadioItem(
                                label = label,
                                selected = themePreference == themeId,
                                onClick = {
                                    themePreference = themeId
                                    user.themePreference = themeId
                                    onThemeChanged(themeId)
                                    updateAccessibilitySetting(user.thisUID, "themePreference", themeId.toLong())
                                }
                            )
                            if (index < themeOptions.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                AppearanceSection(title = "Display Settings", icon = Icons.Default.FormatSize) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Font Size",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = fontSizeLabels[fontSizeIndex.toInt()],
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Slider(
                            value = fontSizeIndex,
                            onValueChange = {
                                fontSizeIndex = it
                                val newSize = it.toInt()
                                user.fontSizePreference = newSize
                                onFontSizeChanged(newSize)
                                updateAccessibilitySetting(user.thisUID, "fontSizePreference", newSize.toLong())
                            },
                            valueRange = 0f..2f,
                            steps = 1,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun AppearanceSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
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
                fontWeight = FontWeight.Bold
            )
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun PremiumRadioItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // Handled by Row selectable
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun updateAccessibilitySetting(userId: String, field: String, value: Long) {
    FirebaseFirestore.getInstance()
        .collection("UserAccounts")
        .document(userId)
        .update(field, value)
}
