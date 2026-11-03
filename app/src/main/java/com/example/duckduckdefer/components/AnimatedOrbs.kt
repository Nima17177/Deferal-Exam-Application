package com.example.duckduckdefer.components
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.duckduckdefer.ui.theme.DuckYellowAccent
import com.example.duckduckdefer.ui.theme.LocalThemeId
import com.example.duckduckdefer.ui.theme.SpaceCyan
import kotlin.math.cos
import kotlin.math.sin
data class OrbSpec(
    val color: Color,
    val sizeDp: Dp,
    val xFraction: Float,
    val yFraction: Float,
    val driftRadius: Dp,
    val periodMs: Int,
    val phaseOffset: Float = 0f
)
val DefaultOrbs = listOf(
    OrbSpec(Color(0xFFFF8C2A).copy(alpha = 0.65f), 180.dp, 0.15f, 0.20f, 30.dp, 6000),
    OrbSpec(DuckYellowAccent.copy(alpha = 0.55f), 140.dp, 0.80f, 0.15f, 25.dp, 7500, 0.3f),
    OrbSpec(Color(0xFFFF8C2A).copy(alpha = 0.50f), 200.dp, 0.60f, 0.55f, 35.dp, 8000, 0.6f),
    OrbSpec(Color(0xFFFF8C2A).copy(alpha = 0.45f), 120.dp, 0.25f, 0.75f, 20.dp, 5500, 0.9f),
    OrbSpec(DuckYellowAccent.copy(alpha = 0.60f), 160.dp, 0.85f, 0.70f, 28.dp, 7000, 0.5f),
    OrbSpec(Color(0xFFFF8C2A).copy(alpha = 0.45f), 100.dp, 0.50f, 0.35f, 22.dp, 9000, 0.2f),
)
@Composable
fun defaultOrbs(): List<OrbSpec> {
    val primary = MaterialTheme.colorScheme.primary
    val accent = if (LocalThemeId.current == 6) SpaceCyan else DuckYellowAccent
    return listOf(
        OrbSpec(primary.copy(alpha = 0.65f), 180.dp, 0.15f, 0.20f, 30.dp, 6000),
        OrbSpec(accent.copy(alpha = 0.55f), 140.dp, 0.80f, 0.15f, 25.dp, 7500, 0.3f),
        OrbSpec(primary.copy(alpha = 0.50f), 200.dp, 0.60f, 0.55f, 35.dp, 8000, 0.6f),
        OrbSpec(primary.copy(alpha = 0.45f), 120.dp, 0.25f, 0.75f, 20.dp, 5500, 0.9f),
        OrbSpec(accent.copy(alpha = 0.60f), 160.dp, 0.85f, 0.70f, 28.dp, 7000, 0.5f),
        OrbSpec(primary.copy(alpha = 0.45f), 100.dp, 0.50f, 0.35f, 22.dp, 9000, 0.2f),
    )
}
@Composable
fun AnimatedOrbs(
    modifier: Modifier = Modifier,
    orbs: List<OrbSpec> = defaultOrbs()
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbs")
    orbs.forEach { orb ->
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(orb.periodMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orb_${orb.xFraction}"
        )
        val angle = (progress + orb.phaseOffset) * 2f * Math.PI.toFloat()
        val dxDp = orb.driftRadius * cos(angle)
        val dyDp = orb.driftRadius * sin(angle)
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val baseX = maxWidth * orb.xFraction - orb.sizeDp / 2
            val baseY = maxHeight * orb.yFraction - orb.sizeDp / 2
            Box(
                modifier = Modifier
                    .offset(x = baseX + dxDp, y = baseY + dyDp)
                    .size(orb.sizeDp)
                    .background(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to orb.color,
                                0.4f to orb.color.copy(alpha = orb.color.alpha * 0.7f),
                                0.7f to orb.color.copy(alpha = orb.color.alpha * 0.3f),
                                1.0f to Color.Transparent
                            ),
                            radius = Float.POSITIVE_INFINITY
                        )
                    )
            )
        }
    }
}
