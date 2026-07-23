package com.wswensw.woolbox.record.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.record.model.GoalPeriod
import com.wswensw.woolbox.record.model.RecordItem
import kotlin.math.roundToInt

@Composable
internal fun DailyGoalMedallion(
    item: RecordItem,
    progress: Float,
    reached: Boolean,
    progressColor: Color,
    size: Dp,
) {
    val scale by animateFloatAsState(
        targetValue = if (reached) 1.06f else 1f,
        animationSpec = spring(),
        label = "DailyGoalMedallionScale",
    )
    val stroke = if (size > 72.dp) 9.dp else 7.5.dp
    val ratio = progress.coerceAtLeast(0f).toDouble()
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
    val colors = recordItemColors(item.name)
    val progressColor = if (reached) {
        MaterialTheme.colorScheme.tertiary
    } else {
        colors.accent
    }
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
