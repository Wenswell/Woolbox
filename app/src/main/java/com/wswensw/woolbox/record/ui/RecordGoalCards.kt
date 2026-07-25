package com.wswensw.woolbox.record.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.record.model.GoalPeriod
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RECENT_ACTIVE_DAYS
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin


/**
 * Bottom-up liquid progress fill for accumulate cards.
 * Dual sine waves animate only when motion is enabled and progress is partial.
 */
@Composable
internal fun LiquidProgressFill(
    progress: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val target = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260),
        label = "LiquidProgressFillLevel",
    )
    val transition = rememberInfiniteTransition(label = "LiquidProgressWave")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "LiquidProgressPhase",
    )
    val waveActive = !reduceMotion && target > 0.001f && target < 0.999f
    val phase = if (waveActive) animatedPhase else 0f
    val densityAmp = 3.dp
    val densityAmpBack = 2.dp
    Canvas(modifier = modifier.fillMaxSize()) {
        if (animatedProgress <= 0.001f) return@Canvas
        val frontAlpha = if (animatedProgress >= 0.999f) 0.34f else 0.26f
        val backAlpha = if (animatedProgress >= 0.999f) 0.18f else 0.14f
        val amp = if (!waveActive) 0f else densityAmp.toPx()
        val ampBack = if (!waveActive) 0f else densityAmpBack.toPx()
        drawLiquidLayer(
            progress = animatedProgress,
            phase = phase + 1.1f,
            amplitude = ampBack,
            waves = 1.15f,
            color = fillColor.copy(alpha = backAlpha),
        )
        drawLiquidLayer(
            progress = animatedProgress,
            phase = phase,
            amplitude = amp,
            waves = 1.0f,
            color = fillColor.copy(alpha = frontAlpha),
        )
    }
}

private fun DrawScope.drawLiquidLayer(
    progress: Float,
    phase: Float,
    amplitude: Float,
    waves: Float,
    color: Color,
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    val level = height * (1f - progress.coerceIn(0f, 1f))
    val path = Path()
    path.moveTo(0f, height)
    path.lineTo(0f, level)
    val steps = (width / 3f).toInt().coerceIn(24, 96)
    for (i in 0..steps) {
        val x = width * i / steps
        val y = level + amplitude * sin((2.0 * PI * waves * (x / width) + phase)).toFloat()
        path.lineTo(x, y)
    }
    path.lineTo(width, height)
    path.close()
    drawPath(path = path, color = color)
}

/**
 * Full-height segmented progress for task cards.
 * Unfinished tiles paint [unfinishedBackgroundColor], while gaps stay
 * transparent and expose the page surface. Completed tiles use
 * [completedBackgroundColor] with [fillColor] above it.
 */
@Composable
internal fun SegmentedProgressFill(
    completed: Int,
    total: Int,
    unfinishedBackgroundColor: Color,
    completedBackgroundColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    gap: Dp = 3.dp,
) {
    val safeTotal = total.coerceAtLeast(0)
    val safeCompleted = completed.coerceIn(0, safeTotal)
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        repeat(safeTotal) { index ->
            val filled = index < safeCompleted
            val pop by animateFloatAsState(
                targetValue = if (filled) 1f else 0f,
                animationSpec = if (reduceMotion) {
                    tween(0)
                } else {
                    spring(dampingRatio = 0.72f, stiffness = 420f)
                },
                label = "SegmentPop-$index",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (filled) completedBackgroundColor else unfinishedBackgroundColor,
                    ),
            ) {
                if (pop > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = 0.86f + 0.14f * pop
                                scaleX = s
                                scaleY = s
                                alpha = pop
                            }
                            .background(fillColor),
                    )
                }
            }
        }
    }
}


/** Width of the recent-day pie row: 6×day + today + 6×gap. */
internal fun recentDaysRowWidth(
    daySize: Dp = 12.dp,
    todaySize: Dp = 15.dp,
    gap: Dp = 3.dp,
    days: Int = RECENT_ACTIVE_DAYS,
): Dp {
    require(days > 0)
    return daySize * (days - 1) + todaySize + gap * (days - 1)
}

/**
 * Weekly goal track in the same footprint as the 7-day pies.
 * Height matches non-today pie (12dp). Bar ends A=6dp; cut sides and fill B=1.5dp; gap 2dp.
 * Seven independent stroked tiles with transparent gaps.
 */
@Composable
internal fun WeeklyGoalProgressBar(
    progress: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    segments: Int = RECENT_ACTIVE_DAYS,
    height: Dp = 12.dp,
    gap: Dp = 2.dp,
    trackRadius: Dp = 6.dp,
    fillRadius: Dp = 1.5.dp,
) {
    require(segments > 0)
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260),
        label = "WeeklyGoalProgressBar",
    )
    val borderColor = fillColor.copy(alpha = 0.16f)
    val units = animated * segments
    val fillShape = RoundedCornerShape(fillRadius)
    Row(
        modifier = modifier
            .width(recentDaysRowWidth())
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(segments) { index ->
            val segFill = (units - index).coerceIn(0f, 1f)
            // Outer ends keep trackRadius; cut sides use fillRadius (1dp).
            val start = if (index == 0) trackRadius else fillRadius
            val end = if (index == segments - 1) trackRadius else fillRadius
            val trackShape = RoundedCornerShape(
                topStart = start,
                topEnd = end,
                bottomEnd = end,
                bottomStart = start,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(width = 1.4.dp, color = borderColor, shape = trackShape)
                    .clip(trackShape),
            ) {
                if (segFill > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(segFill)
                            .clip(fillShape)
                            .background(fillColor),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RecentDayPies(
    progresses: List<Float>,
    modifier: Modifier = Modifier,
    daySize: Dp = 12.dp,
    todaySize: Dp = 15.dp,
    gap: Dp = 3.dp,
) {
    require(progresses.size == RECENT_ACTIVE_DAYS)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        progresses.forEachIndexed { index, raw ->
            val progress = raw.coerceIn(0f, 1f)
            val fill = recordProgressColors(
                progress = progress,
                completed = progress >= 1f,
            ).accent
            val isToday = index == progresses.lastIndex
            val diameter = if (isToday) todaySize else daySize
            Canvas(modifier = Modifier.size(diameter)) {
                val stroke = (if (isToday) 1.6.dp else 1.4.dp).toPx()
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(inset, inset)
                val track = fill.copy(alpha = if (isToday) 0.28f else 0.16f)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (progress > 0f) {
                    drawArc(
                        color = fill,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DailyGoalMedallion(
    item: RecordItem,
    progress: Float,
    reached: Boolean,
    progressColor: Color,
    size: Dp,
    colors: RecordItemColors? = null,
) {
    val resolvedColors = colors ?: recordProgressColors(progress = progress, completed = reached)
    val reduceMotion = prefersReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (reached && !reduceMotion) 1.06f else 1f,
        animationSpec = if (reduceMotion) tween(0) else spring(),
        label = "DailyGoalMedallionScale",
    )
    val stroke = if (size > 72.dp) 9.dp else 7.5.dp
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceAtLeast(0f),
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260),
        label = "DailyGoalMedallionProgress",
    )
    val ratio = animatedProgress.toDouble()
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = stroke.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.toPx() - strokeWidth, size.toPx() - strokeWidth)
            val topLeft = Offset(inset, inset)
            val track = progressColor.copy(alpha = 0.18f)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (ratio >= 1.0) {
                // Completed first lap (dimmer full ring under the overflow lap).
                drawArc(
                    color = progressColor.copy(alpha = 0.42f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
            val lap = goalProgressLapFraction(ratio)
            if (lap > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * lap,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        RecordItemInitial(
            name = item.name,
            size = size - if (size > 72.dp) 26.dp else 22.dp,
            colors = resolvedColors,
        )
        if (reached) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = progressColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(if (size > 72.dp) 28.dp else 22.dp),
            )
        }
    }
}

@Composable
private fun GoalStatus(
    progress: Float,
    reached: Boolean,
    periodLabel: String,
    color: Color,
) {
    val percent = goalProgressPercent(progress.toDouble())
    if (reached) {
        Surface(
            modifier = Modifier.padding(top = 6.dp),
            shape = CircleShape,
            color = color.copy(alpha = 0.16f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (percent > 100) {
                        "Goal reached · $percent%"
                    } else {
                        "Goal reached"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        return
    }
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = "$percent% $periodLabel",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}


@Composable
internal fun GoalProgressPanel(
    item: RecordItem,
    cumulative: Double,
    modifier: Modifier = Modifier,
) {
    val target = checkNotNull(item.goalValue)
    val progress by animateFloatAsState(
        targetValue = goalProgressRatio(cumulative, target).toFloat(),
        animationSpec = spring(),
        label = "SheetGoalProgress",
    )
    val reached = cumulative >= target
    val colors = recordProgressColors(progress = progress, completed = reached)
    val progressColor = colors.accent
    val periodLabel = when (checkNotNull(item.goalPeriod)) {
        GoalPeriod.DAY -> "today"
        GoalPeriod.WEEK -> "this week"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = goalValueLabel(cumulative, target, item.unit),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = progressColor,
        )
        if (item.goalPeriod == GoalPeriod.WEEK) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = progressColor,
                trackColor = progressColor.copy(alpha = 0.18f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        GoalStatus(
            progress = progress,
            reached = reached,
            periodLabel = periodLabel,
            color = progressColor,
        )
    }
}

/** Uncapped cumulative/target ratio (0+). Used for multi-lap rings and percent labels. */
internal fun goalProgressRatio(cumulative: Double, target: Double): Double {
    require(target > 0.0)
    if (cumulative <= 0.0) return 0.0
    return cumulative / target
}

/** 0..1 fill for the current lap; exact whole multiples count as a full lap. */
internal fun goalProgressLapFraction(ratio: Double): Float {
    if (ratio <= 0.0) return 0f
    val remainder = ratio % 1.0
    return if (remainder <= 1e-9) 1f else remainder.toFloat()
}

internal fun goalProgressPercent(ratio: Double): Int =
    (ratio.coerceAtLeast(0.0) * 100.0).roundToInt()

/** Clamped 0..1 for linear bars that cannot overflow visually. */
internal fun goalProgressFraction(cumulative: Double, target: Double): Float {
    require(target > 0.0)
    return goalProgressRatio(cumulative, target).coerceIn(0.0, 1.0).toFloat()
}

internal fun goalValueLabel(cumulative: Double, target: Double, unit: String): String =
    "${formatValue(cumulative)} / ${formatValue(target)} $unit"

internal fun displayRecordName(name: String): String =
    dropFirstRecordCharacter(name).trimStart().ifEmpty { firstRecordCharacter(name) }
