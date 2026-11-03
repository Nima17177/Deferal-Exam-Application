package com.example.duckduckdefer.screens

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duckduckdefer.R
import com.example.duckduckdefer.components.AnimatedOrbs
import com.example.duckduckdefer.ui.theme.DuckGradients
import com.example.duckduckdefer.ui.theme.DuckYellowAccent

// ══════════════════════════════════════════════════════════
//  Splash Screen – branded gradient + animated orbs
// ══════════════════════════════════════════════════════════

@Composable
fun SplashScreen(modifier: Modifier = Modifier, onTimeout: () -> Unit) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showContent by remember { mutableStateOf(false) }
    val currentOnTimeout by rememberUpdatedState(onTimeout)

    LaunchedEffect(Unit) { showContent = true }

    val contentAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "splash_alpha"
    )
    val contentSlide by animateFloatAsState(
        targetValue = if (showContent) 0f else 40f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "splash_slide"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = DuckGradients.heroGradient())
    ) {
        // Animated floating orbs for depth
        AnimatedOrbs()

        // Content overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = contentAlpha, translationY = contentSlide)
        ) {
            if (isLandscape) {
                LandscapeLayout(onTimeout = currentOnTimeout)
            } else {
                PortraitLayout(onTimeout = currentOnTimeout)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  Portrait Layout
// ══════════════════════════════════════════════════════════

@Composable
private fun PortraitLayout(onTimeout: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "logo_scale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-40).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Duck logo with soft radial glow so it's visible on any gradient
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Soft light aura behind the logo
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.White.copy(alpha = 0.85f),
                                    0.35f to Color.White.copy(alpha = 0.55f),
                                    0.65f to Color.White.copy(alpha = 0.15f),
                                    1.0f to Color.Transparent
                                )
                            )
                        )
                )
                Image(
                    painter = painterResource(id = R.drawable.duck_logo2),
                    contentDescription = "DuckDuckDefer Logo",
                    modifier = Modifier
                        .size(105.dp)
                        .scale(logoScale)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Glass-style welcome pill
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Welcome to",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "DuckDuckDefer",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Unlock your potential",
                style = MaterialTheme.typography.bodyMedium,
                color = DuckGradients.heroContentColor().copy(alpha = 0.6f)
            )
        }

        // Bottom CTA
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 60.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            MotivationalMessage(
                modifier = Modifier
                    .weight(1f)
                    .offset(x = 30.dp)
            )
            SemiCircleGoButton(onTimeout = onTimeout)
        }
    }
}

// ══════════════════════════════════════════════════════════
//  Landscape Layout
// ══════════════════════════════════════════════════════════

@Composable
private fun LandscapeLayout(onTimeout: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse_land")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "logo_scale_land"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp)
    ) {
        // Center: Logo + title
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.White.copy(alpha = 0.85f),
                                    0.35f to Color.White.copy(alpha = 0.55f),
                                    0.65f to Color.White.copy(alpha = 0.15f),
                                    1.0f to Color.Transparent
                                )
                            )
                        )
                )
                Image(
                    painter = painterResource(id = R.drawable.duck_logo2),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .scale(logoScale)
                )
            }
            Spacer(Modifier.width(20.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)) {
                    Text(
                        text = "Welcome to",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "DuckDuckDefer",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Bottom left motivational text
        MotivationalMessage(
            modifier = Modifier.align(Alignment.BottomStart)
        )

        // Bottom right Go button
        GoButtonCircle(
            onTimeout = onTimeout,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

// ══════════════════════════════════════════════════════════
//  Shared Components
// ══════════════════════════════════════════════════════════

@Composable
fun MotivationalMessage(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        shape = RoundedCornerShape(50.dp),
        shadowElevation = 6.dp,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)) {
            Text(
                text = "Ready to get started?",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = "Sign in now!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun SemiCircleGoButton(onTimeout: () -> Unit) {
    Box(
        modifier = Modifier
            .offset(x = 50.dp)
            .size(100.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                ),
                shape = CircleShape
            )
            .clickable { onTimeout() },
        contentAlignment = Alignment.CenterStart
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Get Started",
            tint = Color.White,
            modifier = Modifier
                .padding(start = 15.dp)
                .size(32.dp)
        )
    }
}

@Composable
fun GoButtonCircle(onTimeout: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(64.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                ),
                shape = CircleShape
            )
            .clickable { onTimeout() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Get Started",
            tint = Color.White
        )
    }
}
