package com.example.duckduckdefer.components

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.duckduckdefer.utils.NfcAttendanceManager

/**
 * NFC scanning mode for the attendance dialog.
 */
enum class NfcScanMode {
    /** Student broadcasts attendance data via HCE (phone acts as NFC tag) */
    WRITE,
    /** Proctor/Faculty reads attendance data from student's phone or NFC tag */
    READ
}

/**
 * Dialog for NFC-based attendance with QR fallback.
 *
 * For PROCTORS (READ mode):
 *   - Primary: Tap an NFC tag written by the student
 *   - Fallback: Switch to QR scanner
 *
 * For STUDENTS (WRITE mode):
 *   - Primary: Tap an NFC tag to write attendance data
 *   - Fallback: Show QR code on screen
 */
@Composable
fun NfcAttendanceDialog(
    mode: NfcScanMode,
    bookingID: String,
    studentUID: String,
    nfcPayload: String? = null,
    isNfcAvailable: Boolean,
    isNfcEnabled: Boolean,
    onNfcSuccess: () -> Unit = {},
    onQrScanned: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showQrFallback by remember { mutableStateOf(false) }
    var nfcWriteSuccess by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (nfcWriteSuccess) {
                    // Success State
                    NfcSuccessContent(onDismiss = onDismiss)
                } else if (showQrFallback) {
                    // QR Fallback
                    QrFallbackContent(
                        mode = mode,
                        bookingID = bookingID,
                        studentUID = studentUID,
                        onQrScanned = onQrScanned,
                        onBackToNfc = {
                            if (isNfcAvailable && isNfcEnabled) showQrFallback = false
                        },
                        onDismiss = onDismiss,
                        isNfcAvailable = isNfcAvailable && isNfcEnabled
                    )
                } else if (!isNfcAvailable) {
                    // NFC not available on device → go straight to QR
                    NfcUnavailableContent(
                        reason = "NFC is not available on this device.",
                        onUseQr = { showQrFallback = true },
                        onDismiss = onDismiss
                    )
                } else if (!isNfcEnabled) {
                    // NFC disabled → prompt to enable or use QR
                    NfcDisabledContent(
                        onOpenSettings = {
                            context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                        },
                        onUseQr = { showQrFallback = true },
                        onDismiss = onDismiss
                    )
                } else {
                    // NFC is available and enabled → show tap prompt
                    NfcTapPromptContent(
                        mode = mode,
                        onSwitchToQr = { showQrFallback = true },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

// ── Success Content ──────────────────────────────────────────

@Composable
private fun NfcSuccessContent(onDismiss: () -> Unit) {
    val scale by rememberInfiniteTransition(label = "success").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
                .background(Color(0xFF4CAF50).copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "NFC Broadcasting!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            "Your phone is now acting as an NFC tag.\nThe proctor can tap their phone to yours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Done", fontWeight = FontWeight.Bold)
        }
    }
}

// ── NFC Tap Prompt ───────────────────────────────────────────

@Composable
private fun NfcTapPromptContent(
    mode: NfcScanMode,
    onSwitchToQr: () -> Unit,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (mode == NfcScanMode.READ) "Scan Student NFC" else "NFC Broadcasting",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (mode == NfcScanMode.READ)
                "Tap the back of your phone against the student's phone to verify attendance."
            else
                "Your phone is now emitting NFC data.\nAsk the proctor to tap their phone against yours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // Animated NFC icon with pulse ring
        Box(contentAlignment = Alignment.Center) {
            // Pulse ring
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                        CircleShape
                    )
            )
            // Inner circle with icon
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (mode == NfcScanMode.READ) Icons.Default.Contactless else Icons.Default.Nfc,
                    contentDescription = "NFC",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            if (mode == NfcScanMode.READ) "Waiting for student's phone..." else "Ready — tap phones together",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(32.dp))

        // Divider with "or" label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                "  OR  ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onSwitchToQr,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (mode == NfcScanMode.READ) "Use QR Scanner Instead" else "Show QR Code Instead",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onDismiss) {
            Text("Cancel", color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        }
    }
}

// ── NFC Unavailable ──────────────────────────────────────────

@Composable
private fun NfcUnavailableContent(
    reason: String,
    onUseQr: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.NfcOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "NFC Unavailable",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onUseQr,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Use QR Code Instead", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
    }
}

// ── NFC Disabled ─────────────────────────────────────────────

@Composable
private fun NfcDisabledContent(
    onOpenSettings: () -> Unit,
    onUseQr: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.NfcOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "NFC is Disabled",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            "Please enable NFC in your device settings, or use QR code as an alternative.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open NFC Settings", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onUseQr,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Use QR Code Instead", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
    }
}

// ── QR Fallback Content ──────────────────────────────────────

@Composable
private fun QrFallbackContent(
    mode: NfcScanMode,
    bookingID: String,
    studentUID: String,
    onQrScanned: (String) -> Unit,
    onBackToNfc: () -> Unit,
    onDismiss: () -> Unit,
    isNfcAvailable: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (mode == NfcScanMode.READ) {
            // Proctor: show QR scanner
            Text(
                "Scan Student QR Code",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Point your camera at the student's QR code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                CameraPreview(onCodeScanned = onQrScanned)
            }
        } else {
            // Student: show QR code
            Text(
                "Check-in QR Code",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Present this QR code to the proctor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(8.dp)
            ) {
                QRCodeGenerator(
                    content = NfcAttendanceManager.buildAttendancePayload(bookingID, studentUID),
                    modifier = Modifier
                        .size(200.dp)
                        .padding(16.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isNfcAvailable) {
            OutlinedButton(
                onClick = onBackToNfc,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Nfc, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Switch Back to NFC", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))
        }

        TextButton(onClick = onDismiss) {
            Text("Cancel", color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        }
    }
}

/**
 * Icons.Default doesn't include NfcOff — we use SensorsOff as a visual proxy.
 */
private val Icons.Filled.NfcOff get() = Icons.Filled.SensorsOff





