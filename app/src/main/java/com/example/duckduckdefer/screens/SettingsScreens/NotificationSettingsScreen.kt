package com.example.duckduckdefer.screens.SettingsScreens

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.duckduckdefer.R
import com.example.duckduckdefer.services.SoundPreferenceCache
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.useraccountclasses.UserMembersClass
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Notification preferences data class
 */
data class NotificationPreferences(
    val pushEnabled: Boolean = true,
    val emailEnabled: Boolean = true,
    // Granular notification preferences
    val messagesEnabled: Boolean = true,
    val bookingsEnabled: Boolean = true,
    val sessionUpdatesEnabled: Boolean = true,
    val paymentAlertsEnabled: Boolean = true,
    val examStatusEnabled: Boolean = true,
    val incidentReportsEnabled: Boolean = true,
    val dailyRemindersEnabled: Boolean = true,
    val accountAlertsEnabled: Boolean = true,
    // Sound preference
    val notificationSound: String = "default",
    val quackUnlocked: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    user: UserMembersClass,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit = {}
) {
    var prefs by remember { mutableStateOf(NotificationPreferences()) }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch initial state from Firestore
    LaunchedEffect(user.thisUID) {
        FirebaseFirestore.getInstance()
            .collection("UserAccounts")
            .document(user.thisUID)
            .get()
            .addOnSuccessListener { snapshot ->
                val unlockedSounds = (snapshot.get("unlockedSounds") as? List<*>)?.filterIsInstance<String>() ?: listOf("default")
                prefs = NotificationPreferences(
                    pushEnabled = snapshot.getBoolean("pushNotificationsEnabled") ?: true,
                    emailEnabled = snapshot.getBoolean("emailNotificationsEnabled") ?: true,
                    messagesEnabled = snapshot.getBoolean("notifyMessages") ?: true,
                    bookingsEnabled = snapshot.getBoolean("notifyBookings") ?: true,
                    sessionUpdatesEnabled = snapshot.getBoolean("notifySessionUpdates") ?: true,
                    paymentAlertsEnabled = snapshot.getBoolean("notifyPayments") ?: true,
                    examStatusEnabled = snapshot.getBoolean("notifyExamStatus") ?: true,
                    incidentReportsEnabled = snapshot.getBoolean("notifyIncidents") ?: true,
                    dailyRemindersEnabled = snapshot.getBoolean("notifyDailyReminders") ?: true,
                    accountAlertsEnabled = snapshot.getBoolean("notifyAccountAlerts") ?: true,
                    notificationSound = snapshot.getString("notificationSound") ?: "default",
                    quackUnlocked = unlockedSounds.contains("quack")
                )
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
                                    text = "Notifications",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = heroContent
                                )
                                Text(
                                    text = "Alerts and updates",
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
                // MASTER CONTROLS
                SectionHeader("Master Controls")
                
                NotificationCard {
                    NotificationToggleItem(
                        icon = Icons.Default.Notifications,
                        title = "Push Notifications",
                        subtitle = "Enable all push notifications",
                        checked = prefs.pushEnabled,
                        onCheckedChange = { checked ->
                            prefs = prefs.copy(pushEnabled = checked)
                            updateNotificationSetting(user.thisUID, "pushNotificationsEnabled", checked)
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    NotificationToggleItem(
                        icon = Icons.Default.Email,
                        title = "Email Notifications",
                        subtitle = "Receive email updates",
                        checked = prefs.emailEnabled,
                        onCheckedChange = { checked ->
                            prefs = prefs.copy(emailEnabled = checked)
                            updateNotificationSetting(user.thisUID, "emailNotificationsEnabled", checked)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // NOTIFICATION TYPES
                SectionHeader("Notification Types")
                
                NotificationCard {
                    // Messages
                    NotificationToggleItem(
                        icon = Icons.AutoMirrored.Filled.Message,
                        title = "Messages",
                        subtitle = "Chat messages from other users",
                        checked = prefs.messagesEnabled,
                        enabled = prefs.pushEnabled,
                        onCheckedChange = { checked ->
                            prefs = prefs.copy(messagesEnabled = checked)
                            updateNotificationSetting(user.thisUID, "notifyMessages", checked)
                        }
                    )
                    
                    NotificationDivider()
                    
                    // Bookings
                    NotificationToggleItem(
                        icon = Icons.Default.Event,
                        title = "Bookings",
                        subtitle = "New bookings and booking changes",
                        checked = prefs.bookingsEnabled,
                        enabled = prefs.pushEnabled,
                        onCheckedChange = { checked ->
                            prefs = prefs.copy(bookingsEnabled = checked)
                            updateNotificationSetting(user.thisUID, "notifyBookings", checked)
                        }
                    )
                    
                    NotificationDivider()
                    
                    // Session Updates
                    NotificationToggleItem(
                        icon = Icons.Default.CalendarMonth,
                        title = "Session Updates",
                        subtitle = "New sessions and assignment changes",
                        checked = prefs.sessionUpdatesEnabled,
                        enabled = prefs.pushEnabled,
                        onCheckedChange = { checked ->
                            prefs = prefs.copy(sessionUpdatesEnabled = checked)
                            updateNotificationSetting(user.thisUID, "notifySessionUpdates", checked)
                        }
                    )
                    
                    NotificationDivider()
                    
                    // Payment Alerts
                    NotificationToggleItem(
                        icon = Icons.Default.Payment,
                        title = "Payment Alerts",
                        subtitle = "Payment confirmations and receipts",
                        checked = prefs.paymentAlertsEnabled,
                        enabled = prefs.pushEnabled,
                        onCheckedChange = { checked ->
                            prefs = prefs.copy(paymentAlertsEnabled = checked)
                            updateNotificationSetting(user.thisUID, "notifyPayments", checked)
                        }
                    )
                    
                    NotificationDivider()
                    
                    // Exam Status
                    NotificationToggleItem(
                        icon = Icons.Default.Checklist,
                        title = "Exam Status",
                        subtitle = "Check-in, drop-off, and completion",
                        checked = prefs.examStatusEnabled,
                        enabled = prefs.pushEnabled,
                        onCheckedChange = { checked ->
                            prefs = prefs.copy(examStatusEnabled = checked)
                            updateNotificationSetting(user.thisUID, "notifyExamStatus", checked)
                        }
                    )
                    
                    NotificationDivider()
                    
                    // Incident Reports
                    NotificationToggleItem(
                        icon = Icons.Default.Warning,
                        title = "Incident Reports",
                        subtitle = "Urgent alerts about exam issues",
                        checked = prefs.incidentReportsEnabled,
                        enabled = prefs.pushEnabled,
                        onCheckedChange = { checked ->
                            prefs = prefs.copy(incidentReportsEnabled = checked)
                            updateNotificationSetting(user.thisUID, "notifyIncidents", checked)
                        }
                    )
                    
                    NotificationDivider()
                    
                    // Daily Reminders
                    NotificationToggleItem(
                        icon = Icons.Default.Alarm,
                        title = "Daily Reminders",
                        subtitle = "Morning reminders for exams today",
                        checked = prefs.dailyRemindersEnabled,
                        enabled = prefs.pushEnabled,
                        onCheckedChange = { checked ->
                            prefs = prefs.copy(dailyRemindersEnabled = checked)
                            updateNotificationSetting(user.thisUID, "notifyDailyReminders", checked)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                // NOTIFICATION SOUND
                SectionHeader("Notification Sound")

                val context = LocalContext.current

                NotificationCard(accentColor = MaterialTheme.colorScheme.tertiary) {
                    // Default sound (always available)
                    NotificationToggleItem(
                        icon = Icons.Default.NotificationsActive,
                        title = "Default Sound",
                        subtitle = "System notification sound",
                        checked = prefs.notificationSound == "default",
                        enabled = prefs.pushEnabled,
                        onCheckedChange = {
                            if (it) {
                                prefs = prefs.copy(notificationSound = "default")
                                updateNotificationSetting(user.thisUID, "notificationSound", "default")
                                user.notificationSound = "default"
                                SoundPreferenceCache.save(context, "default")
                            }
                        }
                    )

                    if (prefs.quackUnlocked) {
                        NotificationDivider()

                        // Quack sound row with preview
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isQuackActive = prefs.notificationSound == "quack"
                            val alpha = if (prefs.pushEnabled) 1f else 0.5f
                            val iconColor = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha)

                            // Tap-to-preview icon
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                iconColor.copy(alpha = 0.12f),
                                                iconColor.copy(alpha = 0.04f)
                                            )
                                        )
                                    )
                                    .border(1.dp, iconColor.copy(alpha = 0.15f), CircleShape)
                                    .clickable {
                                        val mp = MediaPlayer.create(context, R.raw.quack)
                                        mp?.setOnCompletionListener { it.release() }
                                        mp?.start()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🦆", style = MaterialTheme.typography.titleMedium)
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Quack Sound",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                                )
                                Text(
                                    text = "Tap the duck to preview",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f * alpha)
                                )
                            }

                            Switch(
                                checked = isQuackActive && prefs.pushEnabled,
                                onCheckedChange = { checked ->
                                    if (prefs.pushEnabled) {
                                        val newSound = if (checked) "quack" else "default"
                                        prefs = prefs.copy(notificationSound = newSound)
                                        updateNotificationSetting(user.thisUID, "notificationSound", newSound)
                                        user.notificationSound = newSound
                                        SoundPreferenceCache.save(context, newSound)
                                    }
                                },
                                enabled = prefs.pushEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    disabledCheckedThumbColor = Color.White.copy(alpha = 0.5f),
                                    disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    disabledUncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                                    disabledUncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )
                            )
                        }
                    } else {
                        NotificationDivider()

                        // Locked hint
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.03f)
                                            )
                                        )
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Quack Sound 🦆",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "Purchase from the Point Shop to unlock",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Info notice with matching style
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 6.dp
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        // Left accent strip
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight()
                                .align(Alignment.CenterStart)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.secondary,
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                        )
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.04f)
                                            )
                                        )
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Turn off master push notifications to disable all alerts. Individual settings only work when push is enabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
    )
}

@Composable
private fun NotificationCard(
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left accent strip (like UserListItem)
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.verticalGradient(
                            listOf(accentColor, accentColor.copy(alpha = 0.5f))
                        )
                    )
            )
            Column(
                modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun NotificationDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 24.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun NotificationToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val alpha = if (enabled) 1f else 0.5f
    val iconColor = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular icon container (like UserListItem)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            iconColor.copy(alpha = 0.12f),
                            iconColor.copy(alpha = 0.04f)
                        )
                    )
                )
                .border(1.dp, iconColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title, 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f * alpha)
            )
        }
        
        Switch(
            checked = checked && enabled,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                disabledCheckedThumbColor = Color.White.copy(alpha = 0.5f),
                disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                disabledUncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                disabledUncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
        )
    }
}

private fun updateNotificationSetting(userId: String, field: String, value: Boolean) {
    FirebaseFirestore.getInstance()
        .collection("UserAccounts")
        .document(userId)
        .update(field, value)
}

private fun updateNotificationSetting(userId: String, field: String, value: String) {
    FirebaseFirestore.getInstance()
        .collection("UserAccounts")
        .document(userId)
        .update(field, value)
}

