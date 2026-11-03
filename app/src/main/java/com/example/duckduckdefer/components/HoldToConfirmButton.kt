package com.example.duckduckdefer.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun HoldToConfirmButton(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 2000L,
    color: Color = MaterialTheme.colorScheme.error
) {
    var isPressing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (isPressing) tween(durationMillis = durationMs.toInt(), easing = LinearEasing) else tween(300),
        label = "HoldProgress"
    )

    LaunchedEffect(isPressing) {
        if (isPressing) {
            progress = 1f
            // Periodic haptics while holding
            while (isPressing && progress < 1f) {
                delay(durationMs / 5)
                if (isPressing) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            delay(durationMs)
            if (isPressing) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirm()
                isPressing = false
            }
        } else {
            progress = 0f
        }
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    isPressing = true
                    tryAwaitRelease()
                    isPressing = false
                })
            }
            .clip(ButtonDefaults.textShape)
            .background(if (isPressing) color.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                text = if (isPressing) "Release to Cancel" else text,
                color = color,
                fontWeight = FontWeight.Bold
            )
            if (isPressing) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .width(100.dp)
                        .height(4.dp)
                        .padding(top = 4.dp),
                    color = color,
                    trackColor = color.copy(alpha = 0.2f)
                )
            }
        }
    }
}
