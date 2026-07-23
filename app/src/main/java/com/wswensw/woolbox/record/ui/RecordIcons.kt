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
import androidx.compose.ui.graphics.luminance
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
import kotlin.math.pow

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
 * Progress colors: single primary ramp while in progress, fixed green when done.
 * Never uses secondary/tertiary (often pink/rose under dynamic color).
 *
 * - IDLE → LATE: gray → primary deepens (container wash strengthens with progress)
 * - DONE: success green, hue-distinct from LATE so "almost" ≠ "done"
 */
@Composable
internal fun recordProgressColors(
    progress: Float,
    completed: Boolean = false,
): RecordItemColors {
    val scheme = MaterialTheme.colorScheme
    val stage = progressColorStage(progress = progress, completed = completed)
    val unit = colorProgressUnit(progress = progress, completed = completed)
    val dark = scheme.background.luminance() < 0.5f
    val primary = scheme.primary
    val surface = scheme.surface
    val idleAccent = scheme.onSurfaceVariant

    if (stage == ProgressColorStage.DONE) {
        return recordsDoneColors(dark = dark)
    }

    // Continuous primary strength 0..1 for incomplete work only.
    val ramp = unit.pow(0.9f)
    // Accent: stay readable; move clearly from muted → full primary by LATE.
    val accent = when (stage) {
        ProgressColorStage.IDLE -> idleAccent
        else -> lerp(idleAccent, primary, 0.40f + 0.60f * ramp)
    }
    // Container wash: wide steps so deepening is obvious.
    val wash = when (stage) {
        ProgressColorStage.IDLE -> 0.06f
        ProgressColorStage.EARLY -> 0.12f + 0.08f * stageLocalUnit(unit, 0f, 1f / 3f)
        ProgressColorStage.MID -> 0.24f + 0.08f * stageLocalUnit(unit, 1f / 3f, 2f / 3f)
        ProgressColorStage.LATE -> 0.36f + 0.10f * stageLocalUnit(unit, 2f / 3f, 1f)
        ProgressColorStage.DONE -> 0f
    }
    val container = lerp(surface, primary, wash.coerceIn(0.06f, 0.48f))
    val onContainer = if (container.luminance() > 0.5f) {
        scheme.onSurface
    } else {
        scheme.surface
    }

    return RecordItemColors(
        accent = accent,
        container = container,
        onContainer = onContainer,
        stage = stage,
    )
}

/** Fixed success green — independent of dynamic secondary/tertiary. */
internal fun recordsDoneColors(dark: Boolean): RecordItemColors {
    return if (dark) {
        RecordItemColors(
            accent = Color(0xFF5EDF96),
            container = Color(0xFF143526),
            onContainer = Color(0xFFD4F5E2),
            stage = ProgressColorStage.DONE,
        )
    } else {
        RecordItemColors(
            accent = Color(0xFF0B7A43),
            container = Color(0xFFC8F0D8),
            onContainer = Color(0xFF0A3A22),
            stage = ProgressColorStage.DONE,
        )
    }
}

/** 0..1 progress inside [start, end). */
internal fun stageLocalUnit(unit: Float, start: Float, end: Float): Float {
    if (end <= start) return 0f
    return ((unit - start) / (end - start)).coerceIn(0f, 1f)
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

/** Maps progress into 0f..1f for continuous blending (incomplete path only uses this). */
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
