package com.wswensw.woolbox.record.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.record.model.DayRing
import com.wswensw.woolbox.record.model.DeferredTargets
import com.wswensw.woolbox.record.model.FocusCandidate
import com.wswensw.woolbox.record.model.FocusReason
import com.wswensw.woolbox.record.model.GoalPeriod
import com.wswensw.woolbox.record.model.NowTarget
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordsClock
import com.wswensw.woolbox.record.model.StreakDays
import com.wswensw.woolbox.record.model.TodoStep
import com.wswensw.woolbox.record.model.TodoTask
import com.wswensw.woolbox.record.model.focusQueue
import com.wswensw.woolbox.record.model.focusTargetKey
import com.wswensw.woolbox.record.model.stepRecordValue
import com.wswensw.woolbox.ui.AppStyle
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val FOCUS_QUEUE_PREVIEW = 3

@Composable
internal fun RecordsFocusCard(
    clock: State<RecordsClock>,
    items: List<RecordItem>,
    tasks: List<TodoTask>,
    ring: DayRing,
    streak: StreakDays,
    goalValues: Map<Long, Double>,
    todayValues: Map<Long, Double>,
    todayMinutes: Map<Long, List<Int>>,
    habitMinutes: Map<Long, Int>,
    itemStreaks: Map<Long, Int>,
    deferred: DeferredTargets,
    colorProgressOf: (NowTarget) -> Float,
    cumulativeOf: (RecordItem) -> Double,
    onRecord: (RecordItem, Double, (String?) -> Unit) -> Unit,
    onOpenItem: (RecordItem) -> Unit,
    onOpenTask: (TodoTask) -> Unit,
    onCompleteStep: (TodoTask, TodoStep, (String?) -> Unit) -> Unit,
    onDefer: (NowTarget) -> Unit,
) {
    // This is the only scope that reads the minute clock, preserving list stability.
    val now = clock.value
    val queue = remember(now, items, tasks, goalValues, todayValues, todayMinutes, habitMinutes, itemStreaks, deferred) {
        focusQueue(
            items = items,
            tasks = tasks,
            clock = now,
            goalValues = goalValues,
            todayValues = todayValues,
            todayMinutes = todayMinutes,
            habitMinutes = habitMinutes,
            itemStreaks = itemStreaks,
            deferred = deferred,
        )
    }
    val lead = queue.firstOrNull()
    val rest = queue.drop(1).take(FOCUS_QUEUE_PREVIEW)
    val instant = prefersReducedMotion() || LocalPauseDecorativeMotion.current
    var changingStepId by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = focusDayLabel(now, ring, streak),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DayElapsedRule(fraction = now.dayFraction)
        AnimatedContent(
            targetState = lead,
            transitionSpec = {
                if (instant) {
                    (fadeIn(tween(0)) togetherWith fadeOut(tween(0))).using(SizeTransform(clip = false))
                } else {
                    val enter = slideInVertically(tween(220)) { height -> height / 2 } + fadeIn(tween(220))
                    val exit = slideOutVertically(tween(180)) { height -> -height / 2 } + fadeOut(tween(180))
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                }
            },
            label = "FocusLead",
        ) { candidate ->
            if (candidate == null) {
                DayClearedCard(ring = ring)
            } else {
                FocusHero(
                    candidate = candidate,
                    colorProgress = colorProgressOf(candidate.target),
                    cumulativeOf = cumulativeOf,
                    onRecord = onRecord,
                    onCompleteStep = { task, step, onResult ->
                        changingStepId = step.id
                        onCompleteStep(task, step) { error ->
                            changingStepId = null
                            onResult(error)
                        }
                    },
                    stepChanging = changingStepId != null,
                    onDefer = onDefer,
                )
            }
        }
        rest.forEach { candidate ->
            key(focusTargetKey(candidate.target)) {
                FocusQueueRow(
                    candidate = candidate,
                    instant = instant,
                    stepChanging = changingStepId != null,
                    onOpenItem = onOpenItem,
                    onOpenTask = onOpenTask,
                    onCompleteStep = { task, step ->
                        changingStepId = step.id
                        onCompleteStep(task, step) { changingStepId = null }
                    },
                )
            }
        }
    }
}

@Composable
private fun DayElapsedRule(fraction: Float) {
    val track = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(AppStyle.cardShape)
            .background(track.copy(alpha = 0.14f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(2.dp)
                .background(track.copy(alpha = 0.42f)),
        )
    }
}

@Composable
private fun FocusHero(
    candidate: FocusCandidate,
    colorProgress: Float,
    cumulativeOf: (RecordItem) -> Double,
    onRecord: (RecordItem, Double, (String?) -> Unit) -> Unit,
    onCompleteStep: (TodoTask, TodoStep, (String?) -> Unit) -> Unit,
    stepChanging: Boolean,
    onDefer: (NowTarget) -> Unit,
) {
    when (val target = candidate.target) {
        is NowTarget.Accumulate -> AccumulateFocusHero(
            candidate = candidate,
            item = target.item,
            cumulative = cumulativeOf(target.item),
            colorProgress = colorProgress,
            onRecord = onRecord,
            onDefer = onDefer,
        )
        is NowTarget.Task -> TaskFocusHero(
            candidate = candidate,
            task = target.task,
            step = target.step,
            colorProgress = colorProgress,
            saving = stepChanging,
            onComplete = onCompleteStep,
            onDefer = onDefer,
        )
    }
}

@Composable
private fun AccumulateFocusHero(
    candidate: FocusCandidate,
    item: RecordItem,
    cumulative: Double,
    colorProgress: Float,
    onRecord: (RecordItem, Double, (String?) -> Unit) -> Unit,
    onDefer: (NowTarget) -> Unit,
) {
    val colors = recordProgressColors(progress = colorProgress)
    var valueText by rememberSaveable(item.id) { mutableStateOf(focusRecordSeed(item)) }
    var saving by rememberSaveable(item.id) { mutableStateOf(false) }
    var errorMessage by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    val canSubmit = focusRecordCanSubmit(valueText, saving)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppStyle.cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.onContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LiquidProgressFill(
                progress = colorProgress,
                fillColor = colors.accent,
                reduceMotion = prefersReducedMotion(),
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroHeader(
                    title = displayRecordName(item.name),
                    reason = focusReasonLabel(candidate.reason),
                    colors = colors,
                    canDefer = !candidate.isDeferred,
                    onDefer = { onDefer(candidate.target) },
                )
                Text(
                    text = focusCumulativeLabel(item, cumulative),
                    color = colors.accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = { valueText = stepRecordValue(valueText, item.stepValue, -1) },
                        enabled = !saving,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Decrease by ${formatValue(item.stepValue)} ${item.unit}",
                        )
                    }
                    Text(
                        text = "${valueText.ifBlank { "0" }} ${item.unit}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(
                        onClick = { valueText = stepRecordValue(valueText, item.stepValue, 1) },
                        enabled = !saving,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Increase by ${formatValue(item.stepValue)} ${item.unit}",
                        )
                    }
                    FilledIconButton(
                        enabled = canSubmit,
                        onClick = {
                            val value = focusRecordValue(valueText) ?: return@FilledIconButton
                            saving = true
                            errorMessage = null
                            onRecord(item, value) { error ->
                                saving = false
                                if (error == null) {
                                    valueText = focusRecordSeed(item)
                                } else {
                                    errorMessage = error
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Record ${valueText.ifBlank { "0" }} ${item.unit} for ${displayRecordName(item.name)}",
                        )
                    }
                }
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskFocusHero(
    candidate: FocusCandidate,
    task: TodoTask,
    step: TodoStep,
    colorProgress: Float,
    saving: Boolean,
    onComplete: (TodoTask, TodoStep, (String?) -> Unit) -> Unit,
    onDefer: (NowTarget) -> Unit,
) {
    val colors = recordProgressColors(progress = colorProgress)
    var errorMessage by rememberSaveable(step.id) { mutableStateOf<String?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppStyle.cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.onContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SegmentedProgressFill(
                stepFilled = task.steps.map(TodoStep::isCompleted),
                unfinishedBackgroundColor = recordProgressColors(progress = 0f).container,
                completedBackgroundColor = colors.container,
                fillColor = colors.accent,
                reduceMotion = prefersReducedMotion(),
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroHeader(
                    title = step.title,
                    reason = focusReasonLabel(candidate.reason),
                    colors = colors,
                    canDefer = !candidate.isDeferred,
                    onDefer = { onDefer(candidate.target) },
                )
                Text(
                    text = task.title,
                    color = colors.onContainer.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${task.completedStepCount} / ${task.steps.size} subtasks",
                        color = colors.onContainer.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Checkbox(
                        checked = false,
                        enabled = !saving,
                        onCheckedChange = { checked ->
                            if (!checked) return@Checkbox
                            errorMessage = null
                            onComplete(task, step) { error -> errorMessage = error }
                        },
                    )
                }
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(
    title: String,
    reason: String,
    colors: RecordItemColors,
    canDefer: Boolean,
    onDefer: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (reason.isNotEmpty()) {
                Text(
                    text = reason,
                    color = colors.accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (canDefer) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More actions for $title",
                        tint = colors.onContainer.copy(alpha = 0.64f),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Not now") },
                        onClick = {
                            menuExpanded = false
                            onDefer()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusQueueRow(
    candidate: FocusCandidate,
    instant: Boolean,
    stepChanging: Boolean,
    onOpenItem: (RecordItem) -> Unit,
    onOpenTask: (TodoTask) -> Unit,
    onCompleteStep: (TodoTask, TodoStep) -> Unit,
) {
    val target = candidate.target
    Surface(
        onClick = {
            when (target) {
                is NowTarget.Accumulate -> onOpenItem(target.item)
                is NowTarget.Task -> onOpenTask(target.task)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (instant) Modifier else Modifier.animateContentSize()),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (target is NowTarget.Task) {
                Checkbox(
                    checked = false,
                    enabled = !stepChanging,
                    onCheckedChange = { checked ->
                        if (checked) onCompleteStep(target.task, target.step)
                    },
                )
            }
            Text(
                text = focusTargetLabel(target),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            focusReasonLabel(candidate.reason).takeIf { it.isNotEmpty() }?.let { reason ->
                Text(
                    text = reason,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DayClearedCard(ring: DayRing) {
    val colors = recordsDoneColors(dark = MaterialTheme.colorScheme.background.luminance() < 0.5f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppStyle.cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.onContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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

internal fun focusRecordValue(valueText: String): Double? = valueText.trim()
    .toDoubleOrNull()
    ?.takeIf { it.isFinite() && it > 0.0 }

internal fun focusRecordSeed(item: RecordItem): String = item.defaultValue?.let(::formatValue).orEmpty()

internal fun focusRecordCanSubmit(valueText: String, saving: Boolean): Boolean =
    !saving && focusRecordValue(valueText) != null

internal fun focusCumulativeLabel(item: RecordItem, cumulative: Double): String {
    val goal = item.goalValue
    val period = item.goalPeriod
    val prefix = if (period == GoalPeriod.DAYS_7) "7 days · " else ""
    val value = formatValue(cumulative)
    return if (goal != null && period != null) {
        "$prefix$value / ${formatValue(goal)} ${item.unit}"
    } else {
        "$value ${item.unit}"
    }
}

internal fun focusTargetLabel(target: NowTarget): String = when (target) {
    is NowTarget.Accumulate -> displayRecordName(target.item.name)
    is NowTarget.Task -> target.step.title
}

internal fun focusReasonLabel(reason: FocusReason): String = when (reason) {
    is FocusReason.DueNow -> "${formatMinuteSpan(reason.endsInMinutes)} left"
    is FocusReason.Habit -> "usually ${formatMinute(reason.habitMinute)}"
    is FocusReason.StreakRisk -> "${reason.days}-day streak"
    is FocusReason.BehindPace -> "behind · ${reason.percent}%"
    FocusReason.Queued -> ""
}

internal fun focusDayLabel(clock: RecordsClock, ring: DayRing, streak: StreakDays): String = buildString {
    append(DayLabelFormatter.format(clock.date))
    append(" · ")
    append(formatMinute(clock.minuteOfDay))
    append(" · ")
    if (ring.isVisible) {
        append("${ring.closed}/${ring.total} today")
    } else {
        append("No daily goals")
    }
    if (streak.isVisible) {
        append(" · ")
        append(if (streak.atHistoryLimit) "${streak.days}+ days" else "${streak.days} days")
    }
}

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

private val DayLabelFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
