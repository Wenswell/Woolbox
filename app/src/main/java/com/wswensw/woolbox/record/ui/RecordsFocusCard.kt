package com.wswensw.woolbox.record.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.record.model.DayRing
import com.wswensw.woolbox.record.model.FocusCandidate
import com.wswensw.woolbox.record.model.FocusReason
import com.wswensw.woolbox.record.model.MINUTES_PER_DAY
import com.wswensw.woolbox.record.model.NowTarget
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordWindow
import com.wswensw.woolbox.record.model.RecordsClock
import com.wswensw.woolbox.record.model.StreakDays
import com.wswensw.woolbox.record.model.TodoStep
import com.wswensw.woolbox.record.model.TodoTask
import com.wswensw.woolbox.record.model.forwardMinutes
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Queue entries shown under the lead action. */
private const val FOCUS_QUEUE_PREVIEW = 3

/**
 * The single answer to "what now": the strongest queue entry as one large
 * action, the next few behind it, and the day's own state as a ring, a timeline
 * and a streak. Acting on the lead entry promotes the one behind it, so the
 * card is a queue being worked down rather than a list being read.
 */
@Composable
internal fun RecordsFocusCard(
    clock: State<RecordsClock>,
    queue: List<FocusCandidate>,
    ring: DayRing,
    streak: StreakDays,
    windowStarts: List<Int>,
    colorProgressOf: (NowTarget) -> Float,
    quickValueOf: (RecordItem) -> Double?,
    onQuickRecord: (RecordItem, Double) -> Unit,
    onOpenItem: (RecordItem) -> Unit,
    onCompleteStep: (TodoTask, TodoStep) -> Unit,
) {
    val now = clock.value
    val lead = queue.firstOrNull()
    val rest = queue.drop(1).take(FOCUS_QUEUE_PREVIEW)
    val leadColors = lead
        ?.let { recordProgressColors(progress = colorProgressOf(it.target)) }
        ?: recordsDoneColors(dark = MaterialTheme.colorScheme.background.luminance() < 0.5f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DayRingBadge(ring = ring, size = 44.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recordsDayLabel(now),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = dayRingLabel(ring),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                if (streak.isVisible) StreakBadge(streak = streak)
            }
            DayTimeline(fraction = now.dayFraction, windowStarts = windowStarts, minuteOfDay = now.minuteOfDay)
            if (lead == null) {
                DayClearedRow(ring = ring)
            } else {
                FocusLeadAction(
                    candidate = lead,
                    colors = leadColors,
                    quickValueOf = quickValueOf,
                    onQuickRecord = onQuickRecord,
                    onOpenItem = onOpenItem,
                    onCompleteStep = onCompleteStep,
                )
                rest.forEach { candidate ->
                    FocusQueueRow(
                        candidate = candidate,
                        quickValueOf = quickValueOf,
                        onQuickRecord = onQuickRecord,
                        onOpenItem = onOpenItem,
                        onCompleteStep = onCompleteStep,
                    )
                }
            }
        }
    }
}

/** The lead entry, sized so the next action needs no reading to find. */
@Composable
private fun FocusLeadAction(
    candidate: FocusCandidate,
    colors: RecordItemColors,
    quickValueOf: (RecordItem) -> Double?,
    onQuickRecord: (RecordItem, Double) -> Unit,
    onOpenItem: (RecordItem) -> Unit,
    onCompleteStep: (TodoTask, TodoStep) -> Unit,
) {
    val quickValue = (candidate.target as? NowTarget.Accumulate)?.item?.let(quickValueOf)
    Surface(
        onClick = { candidate.act(quickValue, onQuickRecord, onOpenItem, onCompleteStep) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.container,
        contentColor = colors.onContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = focusTargetLabel(candidate.target),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = focusReasonLabel(candidate.reason),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (candidate.target is NowTarget.Task) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = focusActionDescription(candidate.target, quickValue),
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** A queued entry behind the lead: same one-tap action, quieter presentation. */
@Composable
private fun FocusQueueRow(
    candidate: FocusCandidate,
    quickValueOf: (RecordItem) -> Double?,
    onQuickRecord: (RecordItem, Double) -> Unit,
    onOpenItem: (RecordItem) -> Unit,
    onCompleteStep: (TodoTask, TodoStep) -> Unit,
) {
    val quickValue = (candidate.target as? NowTarget.Accumulate)?.item?.let(quickValueOf)
    Surface(
        onClick = { candidate.act(quickValue, onQuickRecord, onOpenItem, onCompleteStep) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = focusTargetLabel(candidate.target),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = focusReasonLabel(candidate.reason),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/** Shown when the queue is empty: the day's own result, not a blank space. */
@Composable
private fun DayClearedRow(ring: DayRing) {
    val colors = recordsDoneColors(dark = MaterialTheme.colorScheme.background.luminance() < 0.5f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.container,
        contentColor = colors.onContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = if (ring.isComplete) "Day closed" else "Nothing due right now",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/**
 * Closed share of today's commitments. Glows on the closing lap so the moment
 * the ring completes is visible without a message.
 */
@Composable
private fun DayRingBadge(ring: DayRing, size: Dp) {
    val reduceMotion = prefersReducedMotion()
    val animated by animateFloatAsState(
        targetValue = ring.fraction,
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.7f, stiffness = 260f),
        label = "DayRingFraction",
    )
    val scale by animateFloatAsState(
        targetValue = if (ring.isComplete && !reduceMotion) 1.06f else 1f,
        animationSpec = if (reduceMotion) tween(0) else spring(),
        label = "DayRingScale",
    )
    val colors = recordProgressColors(progress = animated, completed = ring.isComplete)
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
            val stroke = 5.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = colors.accent.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    color = colors.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        if (ring.isComplete) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(size / 2),
            )
        } else {
            Text(
                text = "${ring.closed}/${ring.total}",
                color = colors.accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** Consecutive closed days. Only shown once there is a run to lose. */
@Composable
private fun StreakBadge(streak: StreakDays) {
    val colors = recordsDoneColors(dark = MaterialTheme.colorScheme.background.luminance() < 0.5f)
    Surface(shape = CircleShape, color = colors.accent.copy(alpha = 0.16f)) {
        Text(
            text = if (streak.atHistoryLimit) "${streak.days}+ days" else "${streak.days} days",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = colors.accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * The local day as one bar: elapsed share filled, each suggested window start
 * marked, and now as a bright cap. Turns "when am I" into a position instead of
 * a number to read.
 */
@Composable
private fun DayTimeline(fraction: Float, windowStarts: List<Int>, minuteOfDay: Int) {
    val reduceMotion = prefersReducedMotion()
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260),
        label = "DayTimeline",
    )
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(base.copy(alpha = 0.14f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(CircleShape)
                .background(base.copy(alpha = 0.34f)),
        )
        // Marks sit on the same bar, so an upcoming window reads as a distance
        // rather than as a sentence about minutes.
        windowStarts.distinct().forEach { start ->
            val at = (start.toFloat() / MINUTES_PER_DAY).coerceIn(0f, 1f)
            val passed = forwardMinutes(start, minuteOfDay) < forwardMinutes(minuteOfDay, start)
            Box(
                modifier = Modifier
                    .fillMaxWidth(at)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(if (passed) base.copy(alpha = 0.5f) else primary.copy(alpha = 0.7f)),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(primary),
            )
        }
    }
}

private fun FocusCandidate.act(
    quickValue: Double?,
    onQuickRecord: (RecordItem, Double) -> Unit,
    onOpenItem: (RecordItem) -> Unit,
    onCompleteStep: (TodoTask, TodoStep) -> Unit,
) {
    when (val current = target) {
        is NowTarget.Accumulate -> if (quickValue != null) {
            onQuickRecord(current.item, quickValue)
        } else {
            onOpenItem(current.item)
        }
        is NowTarget.Task -> onCompleteStep(current.task, current.step)
    }
}

internal fun focusTargetLabel(target: NowTarget): String = when (target) {
    is NowTarget.Accumulate -> displayRecordName(target.item.name)
    is NowTarget.Task -> "${target.step.title} · ${target.task.title}"
}

/** Why this entry is here, in the fewest words that still say it. */
internal fun focusReasonLabel(reason: FocusReason): String = when (reason) {
    is FocusReason.DueNow -> "${formatMinuteSpan(reason.endsInMinutes)} left"
    is FocusReason.Habit -> "usually ${formatMinute(reason.habitMinute)}"
    is FocusReason.StreakRisk -> "${reason.days}-day streak"
    is FocusReason.BehindPace -> "behind · ${reason.percent}%"
    FocusReason.Queued -> "queued"
}

private fun focusActionDescription(target: NowTarget, quickValue: Double?): String = when (target) {
    is NowTarget.Accumulate -> if (quickValue != null) {
        "Record ${formatValue(quickValue)} ${target.item.unit} for ${displayRecordName(target.item.name)}"
    } else {
        "Open ${displayRecordName(target.item.name)}"
    }
    is NowTarget.Task -> "Complete ${target.step.title}"
}

internal fun dayRingLabel(ring: DayRing): String = when {
    !ring.isVisible -> "No commitments today"
    ring.isComplete -> "All ${ring.total} closed"
    else -> "${ring.total - ring.closed} left today"
}

internal fun recordsDayLabel(clock: RecordsClock): String =
    "${DayLabelFormatter.format(clock.date)} · ${formatMinute(clock.minuteOfDay)}"

/** Compact span for suggested windows, e.g. `45m` or `2h 05m`. */
internal fun formatMinuteSpan(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val rest = safe % 60
    return if (hours == 0) {
        "${rest}m"
    } else {
        String.format(Locale.ROOT, "%dh %02dm", hours, rest)
    }
}

/** Suggested window starts across every open commitment, for timeline marks. */
internal fun focusWindowStarts(items: List<RecordItem>, tasks: List<TodoTask>): List<Int> =
    items.flatMap(RecordItem::windows).map(RecordWindow::startMinute) +
        tasks.flatMap(TodoTask::steps)
            .filterNot(TodoStep::isCompleted)
            .flatMap(TodoStep::windows)
            .map(RecordWindow::startMinute)

private val DayLabelFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
