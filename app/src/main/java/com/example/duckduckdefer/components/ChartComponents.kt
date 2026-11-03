package com.example.duckduckdefer.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ──────────────────────────────────────────────────────
//  Animated Bar Chart
// ──────────────────────────────────────────────────────

data class BarChartEntry(
    val label: String,
    val value: Float,
    val color: Color
)

@Composable
fun AnimatedBarChart(
    entries: List<BarChartEntry>,
    modifier: Modifier = Modifier,
    barWidth: Dp = 28.dp,
    maxHeight: Dp = 160.dp,
    showValues: Boolean = true
) {
    val maxValue = entries.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f

    var animationPlayed by remember { mutableStateOf(false) }
    val animProgress = animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "barAnim"
    )
    LaunchedEffect(entries) { animationPlayed = true }

    Row(
        modifier = modifier.fillMaxWidth().height(maxHeight + 40.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        entries.forEach { entry ->
            val barHeight = (entry.value / maxValue) * maxHeight.value * animProgress.value
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                if (showValues && entry.value > 0) {
                    Text(
                        text = entry.value.toInt().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = entry.color,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(barHeight.dp.coerceAtLeast(2.dp))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(entry.color)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────
//  Animated Donut Chart
// ──────────────────────────────────────────────────────

data class DonutSlice(
    val label: String,
    val value: Float,
    val color: Color
)

@Composable
fun AnimatedDonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    strokeWidth: Dp = 24.dp,
    centerLabel: String? = null,
    centerValue: String? = null
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)

    var animationPlayed by remember { mutableStateOf(false) }
    val animProgress = animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "donutAnim"
    )
    LaunchedEffect(slices) { animationPlayed = true }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)

            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = (slice.value / total) * 360f * animProgress.value
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                startAngle += sweep
            }
        }

        // Center text
        if (centerLabel != null || centerValue != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                centerValue?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                centerLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────
//  Donut Legend
// ──────────────────────────────────────────────────────

@Composable
fun DonutLegend(slices: List<DonutSlice>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slices.forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(slice.color)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = slice.label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = slice.value.toInt().toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────
//  Animated Ring Stat  (for single-metric gauges)
// ──────────────────────────────────────────────────────

@Composable
fun AnimatedRingStat(
    label: String,
    value: Float,
    maxValue: Float = 100f,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    strokeWidth: Dp = 8.dp,
    displayText: String? = null
) {
    var animPlayed by remember { mutableStateOf(false) }
    val animProgress = animateFloatAsState(
        targetValue = if (animPlayed) (value / maxValue.coerceAtLeast(1f)).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "ringAnim"
    )
    LaunchedEffect(value) { animPlayed = true }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(size)) {
                val stroke = strokeWidth.toPx()
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                // Track
                drawArc(color = trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round))
                // Value
                drawArc(color = color, startAngle = -90f, sweepAngle = 360f * animProgress.value, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
            Text(
                text = displayText ?: "${(animProgress.value * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

// ──────────────────────────────────────────────────────
//  Mini Sparkline (horizontal trend line)
// ──────────────────────────────────────────────────────

@Composable
fun Sparkline(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = lineColor.copy(alpha = 0.15f),
    height: Dp = 40.dp
) {
    if (data.size < 2) return
    val maxVal = data.max().coerceAtLeast(1f)

    var animPlayed by remember { mutableStateOf(false) }
    val animProgress = animateFloatAsState(
        targetValue = if (animPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "sparkAnim"
    )
    LaunchedEffect(data) { animPlayed = true }

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val step = w / (data.size - 1)
        val points = data.mapIndexed { i, v ->
            Offset(i * step, h - (v / maxVal) * h * animProgress.value)
        }

        // Fill
        val fillPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(w, h)
            close()
        }
        drawPath(fillPath, fillColor)

        // Line
        for (i in 0 until points.size - 1) {
            drawLine(lineColor, points[i], points[i + 1], strokeWidth = 3f, cap = StrokeCap.Round)
        }

        // Dots
        points.forEach { p ->
            drawCircle(lineColor, radius = 3.5f, center = p)
        }
    }
}

// ──────────────────────────────────────────────────────
//  Horizontal Stacked Progress Bar
// ──────────────────────────────────────────────────────

data class StackSegment(
    val label: String,
    val value: Float,
    val color: Color
)

@Composable
fun StackedProgressBar(
    segments: List<StackSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp
) {
    val total = segments.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)

    var animPlayed by remember { mutableStateOf(false) }
    val animProgress = animateFloatAsState(
        targetValue = if (animPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "stackAnim"
    )
    LaunchedEffect(segments) { animPlayed = true }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
    ) {
        val w = size.width * animProgress.value
        val h = size.height
        var x = 0f
        segments.forEach { seg ->
            val segW = (seg.value / total) * w
            drawRoundRect(
                color = seg.color,
                topLeft = Offset(x, 0f),
                size = Size(segW, h),
                cornerRadius = CornerRadius(h / 2, h / 2)
            )
            x += segW
        }
    }
}

// ──────────────────────────────────────────────────────
//  Heat Map Grid  (7 cols x N rows)
// ──────────────────────────────────────────────────────

@Composable
fun HeatMapGrid(
    data: List<List<Float>>,
    rowLabels: List<String>,
    colLabels: List<String>,
    baseColor: Color = MaterialTheme.colorScheme.primary,
    emptyColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    modifier: Modifier = Modifier,
    cellSize: Dp = 28.dp
) {
    val maxVal = data.flatten().maxOrNull()?.coerceAtLeast(1f) ?: 1f

    Column(modifier = modifier) {
        // Column headers
        Row {
            Spacer(Modifier.width(48.dp))
            colLabels.forEach { label ->
                Box(Modifier.size(cellSize), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Rows
        data.forEachIndexed { rowIdx, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(48.dp)) {
                    Text(
                        text = if (rowIdx < rowLabels.size) rowLabels[rowIdx] else "",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                row.forEach { value ->
                    val alpha = if (value <= 0f) 0f else (value / maxVal).coerceIn(0.15f, 1f)
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .padding(1.5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (value <= 0f) emptyColor else baseColor.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}


