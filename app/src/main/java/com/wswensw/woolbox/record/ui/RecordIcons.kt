package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.record.model.ItemStreak
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.TodoTask
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.floor

/** Discrete progress stages used for Records card color. */
internal enum class ProgressColorStage {
    IDLE,
    EARLY,
    MID,
    LATE,
    DONE,
}

@Immutable
internal data class RecordItemColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val stage: ProgressColorStage = ProgressColorStage.IDLE,
)

/**
 * Colors from progress (0..1+, or completed), not item name.
 * Accent blends across stage stops; container follows the discrete stage.
 */
@Composable
internal fun recordProgressColors(
    progress: Float,
    completed: Boolean = false,
): RecordItemColors {
    val scheme = MaterialTheme.colorScheme
    val stage = progressColorStage(progress = progress, completed = completed)
    val t = colorProgressUnit(progress = progress, completed = completed)

    val accentStops = listOf(
        scheme.onSurfaceVariant,
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        scheme.tertiary,
    )
    val accent = if (stage == ProgressColorStage.DONE) {
        scheme.tertiary
    } else {
        lerpColorStops(accentStops, t)
    }

    val (materialContainer, onContainer) = when (stage) {
        ProgressColorStage.IDLE -> scheme.surfaceContainerHigh to scheme.onSurface
        ProgressColorStage.EARLY -> scheme.primaryContainer to scheme.onPrimaryContainer
        ProgressColorStage.MID -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ProgressColorStage.LATE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ProgressColorStage.DONE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }

    val container = when {
        stage == ProgressColorStage.DONE -> materialContainer
        isNearNeutralSurface(materialContainer, scheme.surface) -> {
            val tint = when (stage) {
                ProgressColorStage.IDLE -> 0.10f
                ProgressColorStage.EARLY -> 0.16f
                ProgressColorStage.MID -> 0.20f
                ProgressColorStage.LATE -> 0.24f
                ProgressColorStage.DONE -> 0.28f
            }
            lerp(scheme.surface, accent, tint)
        }
        else -> materialContainer
    }

    return RecordItemColors(
        accent = accent,
        container = container,
        onContainer = onContainer,
        stage = stage,
    )
}

internal fun progressColorStage(
    progress: Float,
    completed: Boolean = false,
): ProgressColorStage {
    if (completed || progress >= 1f) return ProgressColorStage.DONE
    if (progress <= 0f) return ProgressColorStage.IDLE
    if (progress < 1f / 3f) return ProgressColorStage.EARLY
    if (progress < 2f / 3f) return ProgressColorStage.MID
    return ProgressColorStage.LATE
}

/** Maps progress into 0f..1f for continuous accent blending. */
internal fun colorProgressUnit(
    progress: Float,
    completed: Boolean = false,
): Float {
    if (completed) return 1f
    if (progress.isNaN()) return 0f
    return progress.coerceIn(0f, 1f)
}

internal fun taskColorProgress(task: TodoTask): Float {
    if (task.steps.isEmpty()) return if (task.isCompleted) 1f else 0f
    return task.completedStepCount.toFloat() / task.steps.size.toFloat()
}

/**
 * Goal items use cumulative/target. Items without a goal use today's activity and streak length.
 */
internal fun accumulateColorProgress(
    item: RecordItem,
    cumulative: Double,
    todayValue: Double,
    streak: ItemStreak?,
): Float {
    val target = item.goalValue
    val period = item.goalPeriod
    if (target != null && target > 0.0 && period != null) {
        return goalProgressRatio(cumulative, target).toFloat()
    }
    return streakQuantityProgress(todayValue = todayValue, streakCurrent = streak?.current ?: 0)
}

/**
 * Measurement uses latest value within [min, max]. Missing range falls back to idle/early.
 */
internal fun measurementColorProgress(
    minValue: Double?,
    maxValue: Double?,
    latestValue: Double?,
): Float {
    if (latestValue == null) return 0f
    if (minValue == null || maxValue == null || maxValue <= minValue) return 1f / 3f
    return ((latestValue - minValue) / (maxValue - minValue)).toFloat().coerceIn(0f, 1f)
}

internal fun streakQuantityProgress(
    todayValue: Double,
    streakCurrent: Int,
): Float {
    if (streakCurrent >= 7) return 1f
    if (streakCurrent >= 3) return 2f / 3f
    if (streakCurrent >= 1) return 1f / 3f
    return if (todayValue > 0.0) 1f / 6f else 0f
}

internal fun lerpColorStops(stops: List<Color>, unit: Float): Color {
    require(stops.size >= 2)
    val t = unit.coerceIn(0f, 1f)
    if (t <= 0f) return stops.first()
    if (t >= 1f) return stops.last()
    val segments = stops.lastIndex
    val scaled = t * segments
    val index = floor(scaled.toDouble()).toInt().coerceIn(0, segments - 1)
    val fraction = (scaled - index).coerceIn(0f, 1f)
    return lerp(stops[index], stops[index + 1], fraction)
}

private fun isNearNeutralSurface(color: Color, surface: Color): Boolean {
    fun channelDelta(a: Float, b: Float) = kotlin.math.abs(a - b)
    val chroma =
        maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)
    val nearSurface =
        channelDelta(color.red, surface.red) < 0.04f &&
            channelDelta(color.green, surface.green) < 0.04f &&
            channelDelta(color.blue, surface.blue) < 0.04f
    return chroma < 0.04f || nearSurface
}

@Composable
internal fun RecordItemInitial(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    colors: RecordItemColors? = null,
) {
    val resolved = colors ?: recordProgressColors(progress = 0f)
    Box(
        modifier = modifier
            .size(size)
            .background(resolved.accent.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = firstRecordCharacter(name),
            color = resolved.accent,
            style = if (size > 40.dp) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

internal fun firstRecordCharacter(value: String): String {
    if (value.isEmpty()) return ""
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(value)
    val end = iterator.next()
    return if (end == BreakIterator.DONE) "" else value.substring(0, end)
}

internal fun dropFirstRecordCharacter(value: String): String {
    if (value.isEmpty()) return value
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(value)
    val end = iterator.next()
    return if (end == BreakIterator.DONE) value else value.substring(end)
}
