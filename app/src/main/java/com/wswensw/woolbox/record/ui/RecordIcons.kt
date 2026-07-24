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
 * In-progress: primary wash on surface container (readable in light/dark).
 * Done: fixed success green with theme-specific contrast.
 * Never uses secondary/tertiary for progress.
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
    val base = if (dark) scheme.surfaceContainerHigh else scheme.surface
    val idleAccent = scheme.onSurfaceVariant

    if (stage == ProgressColorStage.DONE) {
        return recordsDoneColors(dark = dark)
    }

    val ramp = unit.pow(0.9f)
    val accent = when (stage) {
        ProgressColorStage.IDLE -> idleAccent
        else -> lerp(idleAccent, primary, if (dark) 0.55f + 0.45f * ramp else 0.40f + 0.60f * ramp)
    }
    val wash = when (stage) {
        ProgressColorStage.IDLE -> if (dark) 0.10f else 0.07f
        ProgressColorStage.EARLY -> (if (dark) 0.14f else 0.12f) + 0.06f * stageLocalUnit(unit, 0f, 1f / 3f)
        ProgressColorStage.MID -> (if (dark) 0.20f else 0.22f) + 0.06f * stageLocalUnit(unit, 1f / 3f, 2f / 3f)
        ProgressColorStage.LATE -> (if (dark) 0.28f else 0.34f) + 0.08f * stageLocalUnit(unit, 2f / 3f, 1f)
        ProgressColorStage.DONE -> 0f
    }
    val maxWash = if (dark) 0.36f else 0.46f
    val container = lerp(base, primary, wash.coerceIn(0.07f, maxWash))
    // Always use onSurface for in-progress card text — primary-tinted dark cards stay readable.
    val onContainer = scheme.onSurface

    return RecordItemColors(
        accent = accent,
        container = container,
        onContainer = onContainer,
        stage = stage,
    )
}

internal fun recordsDoneColors(dark: Boolean): RecordItemColors {
    return if (dark) {
        RecordItemColors(
            accent = Color(0xFF7DDEA4),
            container = Color(0xFF1A3326),
            onContainer = Color(0xFFE3F7EB),
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
