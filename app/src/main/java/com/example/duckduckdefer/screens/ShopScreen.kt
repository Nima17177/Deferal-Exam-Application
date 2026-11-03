package com.example.duckduckdefer.screens

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.R
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import kotlinx.coroutines.launch

data class ShopItem(
    val id: Int,
    val name: String,
    val description: String,
    val cost: Int,
    val type: String // "THEME", "TITLE", or "SOUND"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    user: UserMembersClass,
    onBack: () -> Unit,
    onRefreshUser: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Point Shop") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700))
                        Spacer(Modifier.width(4.dp))
                        Text(user.points.toString(), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        ShopContent(
            user = user,
            modifier = Modifier.padding(padding),
            onRefreshUser = onRefreshUser,
            snackbarHostState = snackbarHostState
        )
    }
}

@Composable
fun ShopContent(
    user: UserMembersClass,
    modifier: Modifier = Modifier,
    onRefreshUser: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    val shopItems = listOf(
        ShopItem(3, "Neon Night", "A vibrant purple & cyan theme.", 500, "THEME"),
        ShopItem(4, "Ocean Breeze", "Calming blue & teal tones.", 500, "THEME"),
        ShopItem(5, "Forest Guardian", "Deep greens and earthy browns.", 750, "THEME"),
        ShopItem(6, "Cosmic Voyage", "Deep space purples, nebula pinks & starlight.", 600, "THEME"),
        ShopItem(7, "Sunset Boulevard", "Warm oranges, corals & golden hour hues.", 500, "THEME"),
        ShopItem(8, "Midnight Cherry", "Deep reds, dark burgundy & black.", 600, "THEME"),
        ShopItem(9, "Arctic Frost", "Icy whites, pale blues & silver.", 500, "THEME"),
        ShopItem(10, "Retrowave", "Hot pink, electric blue & neon yellow on black.", 750, "THEME"),
        ShopItem(11, "Lavender Dreams", "Soft purples, lilac & cream pastels.", 500, "THEME"),
        ShopItem(12, "Duck Mode \uD83E\uDD86", "Bright yellow, orange beak & white accents.", 1000, "THEME"),
        ShopItem(101, "The Early Bird", "Title for those who check-in early.", 200, "TITLE"),
        ShopItem(102, "Coffee Addict", "A fun title for late-night studiers.", 300, "TITLE"),
        ShopItem(103, "Legendary Proctor", "The ultimate title for assistants.", 1000, "TITLE"),
        ShopItem(104, "Night Owl", "For those who log in past midnight.", 200, "TITLE"),
        ShopItem(105, "Duck Whisperer", "The ultimate DuckDuckDefer fan.", 500, "TITLE"),
        ShopItem(106, "Streak Master", "For the most dedicated streak enthusiasts.", 400, "TITLE"),
        ShopItem(107, "Exam Survivor", "You've been through it all.", 300, "TITLE"),
        ShopItem(108, "Admin Overlord", "Only the most dedicated admins.", 1500, "TITLE"),
        ShopItem(109, "Proctor Supreme", "A step above Legendary.", 2000, "TITLE"),
        ShopItem(110, "Quack Lord", "The duckiest title of them all. \uD83E\uDD86", 800, "TITLE"),
        ShopItem(201, "Quack", "Custom notification sound. Enable it in Notification Settings.", 50, "SOUND")
    )

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            "Spend your points on exclusive customization!",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        
        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shopItems) { item ->
                val isUnlocked = when (item.type) {
                    "THEME" -> user.unlockedThemes.contains(item.id)
                    "TITLE" -> user.unlockedTitles.contains(item.name)
                    "SOUND" -> user.unlockedSounds.contains(item.name.lowercase())
                    else -> false
                }
                
                ShopCard(
                    item = item,
                    isUnlocked = isUnlocked,
                    canAfford = user.points >= item.cost,
                    onPurchase = {
                        val callback: (Boolean, String) -> Unit = { success, message ->
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                            if (success) {
                                onRefreshUser()
                            }
                        }

                        when (item.type) {
                            "THEME" -> user.purchaseTheme(item.id, item.cost, callback)
                            "TITLE" -> user.purchaseTitle(item.name, item.cost, callback)
                            "SOUND" -> user.purchaseSound(item.name.lowercase(), item.cost, callback)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ShopCard(
    item: ShopItem,
    isUnlocked: Boolean,
    canAfford: Boolean,
    onPurchase: () -> Unit
) {
    val context = LocalContext.current
    val containerColor = if (isUnlocked)
        MaterialTheme.colorScheme.surface
    else
        MaterialTheme.colorScheme.surface
    val borderColor = if (isUnlocked)
        Color(0xFF4CAF50).copy(alpha = 0.4f)
    else
        MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Theme preview swatch or icon
            if (item.type == "THEME") {
                val themeColors = when (item.id) {
                    3 -> listOf(Color(0xFFBB86FC), Color(0xFF03DAC5), Color(0xFF0D0D14)) // Neon Night
                    4 -> listOf(Color(0xFF4FC3F7), Color(0xFF80CBC4), Color(0xFF0A1A24)) // Ocean Breeze
                    5 -> listOf(Color(0xFF81C784), Color(0xFFA1887F), Color(0xFF0D1A0D)) // Forest Guardian
                    6 -> listOf(Color(0xFF9D6FFF), Color(0xFF56E8FF), Color(0xFFFF6BAE), Color(0xFF0B0D1A)) // Cosmic Voyage
                    7 -> listOf(Color(0xFFFF7043), Color(0xFFFF8A65), Color(0xFFFFCC80)) // Sunset Boulevard
                    8 -> listOf(Color(0xFFB71C1C), Color(0xFF880E4F), Color(0xFF1A0A0A)) // Midnight Cherry
                    9 -> listOf(Color(0xFFE1F5FE), Color(0xFF81D4FA), Color(0xFFB0BEC5)) // Arctic Frost
                    10 -> listOf(Color(0xFFFF006E), Color(0xFF3A86FF), Color(0xFFFFBE0B), Color(0xFF0D0D0D)) // Retrowave
                    11 -> listOf(Color(0xFFCE93D8), Color(0xFFE1BEE7), Color(0xFFFFF8E1)) // Lavender Dreams
                    12 -> listOf(Color(0xFFFFD600), Color(0xFFFF9100), Color(0xFFFFFFFF)) // Duck Mode 🦆
                    else -> listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(themeColors)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isUnlocked) {
                        Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isUnlocked -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                                item.type == "SOUND" -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            }
                        )
                        .clickable(enabled = item.type == "SOUND") {
                            if (item.name.lowercase() == "quack") {
                                val mediaPlayer = MediaPlayer.create(context, R.raw.quack)
                                mediaPlayer?.setOnCompletionListener { it.release() }
                                mediaPlayer?.start()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (item.type) {
                            "SOUND" -> Icons.Default.VolumeUp
                            else -> Icons.Default.ShoppingBag
                        },
                        contentDescription = null,
                        tint = when {
                            isUnlocked -> Color(0xFF4CAF50)
                            item.type == "SOUND" -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
            Text(
                item.description,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                color = Color.Gray,
                minLines = 2
            )
            
            Spacer(Modifier.height(12.dp))
            
            if (isUnlocked) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color(0xFF4CAF50).copy(alpha = 0.1f)
                ) {
                    Text(
                        "✓ OWNED",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            } else {
                Button(
                    onClick = onPurchase,
                    enabled = canAfford,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(item.cost.toString(), fontSize = 12.sp)
                }
            }
        }
    }
}
