package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.DailyCheckin
import com.wswensw.woolbox.record.model.GoalPeriod
import com.wswensw.woolbox.record.model.GoalPool
import com.wswensw.woolbox.record.model.RecordEntry
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordMode
import com.wswensw.woolbox.record.model.RecordsClock
import com.wswensw.woolbox.record.model.TodayTask
import com.wswensw.woolbox.record.model.currentCheckinHour
import com.wswensw.woolbox.record.model.nextReminderAt
import com.wswensw.woolbox.ui.AppStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecordsScreen(
    state: RecordsUiState,
    clock: State<RecordsClock>,
    onAddTask: () -> Unit,
    onOpenTask: (TodayTask) -> Unit,
    onManageTemplates: () -> Unit,
    onManageGoals: () -> Unit,
    onManageAccumulates: () -> Unit,
    onManageMeasurements: () -> Unit,
    onConfigureConnection: () -> Unit,
    onRetry: () -> Unit,
    onRecordItem: (RecordItem) -> Unit,
    onOpenGoal: (GoalPool) -> Unit,
    onMarkAwake: ((String?) -> Unit) -> Unit,
    onOpenHourlyCheckin: () -> Unit,
    onOpenMeasurement: (RecordItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accumulateItems = orderedRecordItems(state.activeItems).filter { it.mode == RecordMode.ACCUMULATE }
    val goals = state.goals
        .filter { !it.completed && !it.expired() }
        .sortedWith(compareBy<GoalPool> { it.endDate }.thenBy { it.title })
    val measurementItems = state.activeItems
        .filter { it.mode == RecordMode.MEASUREMENT }
        .sortedBy { it.name.lowercase(Locale.ROOT) }
    val todayValues = todayRecordValues(state.entries)
    val now = System.currentTimeMillis()
    clock.value

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppStyle.spacing.pageHorizontal,
            top = AppStyle.spacing.pageTop,
            end = AppStyle.spacing.pageHorizontal,
            bottom = AppStyle.spacing.pageBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            state.isLoading -> item {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && !state.hasSnapshot -> item {
                RecordsStatusCard(
                    message = state.error,
                    actionLabel = stringResource(
                        if (state.connection.isConfigured) R.string.action_retry else R.string.configure_connection,
                    ),
                    onAction = if (state.connection.isConfigured) onRetry else onConfigureConnection,
                )
            }
            else -> {
                state.error?.let { message ->
                    item {
                        RecordsStatusCard(
                            message = message,
                            actionLabel = stringResource(R.string.action_retry),
                            onAction = onRetry,
                        )
                    }
                }
                item {
                    SectionTitle(stringResource(R.string.today))
                    HourlyCheckinCard(
                        checkin = state.dailyCheckin,
                        now = now,
                        onMarkAwake = onMarkAwake,
                        onOpenCheckin = onOpenHourlyCheckin,
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.today_tasks),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onAddTask) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.add_task))
                        }
                    }
                }
                if (state.todayTasks.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_tasks_today),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                } else {
                    items(state.todayTasks, key = { "task-${it.id}" }) { task ->
                        TodayTaskRow(
                            task = task,
                            onOpen = { onOpenTask(task) },
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionLink(
                        title = stringResource(R.string.records_templates),
                        subtitle = stringResource(R.string.templates_count, state.templates.size),
                        onClick = onManageTemplates,
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionLink(
                        title = stringResource(R.string.records_goals),
                        subtitle = stringResource(R.string.goals_count, goals.size),
                        onClick = onManageGoals,
                    )
                }
                items(goals, key = { "goal-${it.id}" }) { goal ->
                    val target = goal.subgoals.maxOf { it.targetValue }
                    RecordHomeProgressRow(
                        title = goal.title,
                        value = homeProgressValueLabel(goal.currentValue, target, goal.unit),
                        progress = homeProgressFraction(goal.currentValue, target),
                        onClick = { onOpenGoal(goal) },
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionLink(
                        title = stringResource(R.string.records_accumulate),
                        subtitle = stringResource(R.string.accumulate_count, accumulateItems.size),
                        onClick = onManageAccumulates,
                    )
                }
                items(accumulateItems, key = { "accumulate-${it.id}" }) { item ->
                    val current = accumulateHomeValue(item, state.goalValues, todayValues)
                    RecordHomeProgressRow(
                        title = displayRecordName(item.name),
                        value = homeProgressValueLabel(current, item.goalValue, item.unit),
                        progress = homeProgressFraction(current, item.goalValue),
                        onClick = { onRecordItem(item) },
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionLink(
                        title = stringResource(R.string.records_measurement),
                        subtitle = stringResource(R.string.measurements_count, measurementItems.size),
                        onClick = onManageMeasurements,
                    )
                }
                items(measurementItems, key = { "measurement-${it.id}" }) { item ->
                    val latest = state.latestEntries[item.id]
                    RecordHomeRow(
                        title = displayRecordName(item.name),
                        value = latest?.let { "${formatValue(it.value)} ${item.unit}" } ?: "--",
                        onClick = { onOpenMeasurement(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayTaskRow(
    task: TodayTask,
    onOpen: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RecordsCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.task_steps_progress, task.completedStepCount, task.steps.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = task.nextStep?.let { stringResource(R.string.next_subtask, it.title) }
                        ?: stringResource(R.string.all_subtasks_done),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun HourlyCheckinCard(
    checkin: DailyCheckin?,
    now: Long,
    onMarkAwake: ((String?) -> Unit) -> Unit,
    onOpenCheckin: () -> Unit,
) {
    var busy by remember(checkin?.wokeAt) { mutableStateOf(false) }
    var error by remember(checkin?.wokeAt) { mutableStateOf<String?>(null) }
    val dueHour = checkin?.currentCheckinHour(now)
    val nextReminder = checkin?.nextReminderAt(now)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RecordsCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.hourly_checkin),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            when {
                checkin == null -> Button(
                    onClick = {
                        busy = true
                        error = null
                        onMarkAwake { result -> busy = false; error = result }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.mark_awake)) }
                dueHour != null -> Button(
                    onClick = onOpenCheckin,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.check_in_now)) }
                nextReminder != null -> Text(
                    stringResource(R.string.next_checkin, hourlyCheckinTime(nextReminder)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun hourlyCheckinTime(epochMillis: Long): String = DateTimeFormatter.ofPattern("HH:mm")
    .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SectionLink(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun RecordHomeRow(title: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RecordsCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RecordHomeProgressRow(
    title: String,
    value: String,
    progress: Float,
    onClick: () -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow
    val stripeColor = MaterialTheme.colorScheme.primaryContainer
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RecordsCardShape,
        color = Color.Transparent,
    ) {
        Box {
            ZebraProgressFill(
                progress = progress,
                backgroundColor = backgroundColor,
                stripeColor = stripeColor,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 144.dp),
                )
            }
        }
    }
}

@Composable
internal fun RecordsStatusCard(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppStyle.cardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_records_24), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}

internal fun crossedGoalThreshold(previous: Double, current: Double, target: Double?): Boolean =
    target != null && target > 0.0 && previous < target && current >= target

internal fun nextTodayTaskStepTitle(task: TodayTask): String? = task.nextStep?.title

internal fun orderedRecordItems(items: List<RecordItem>): List<RecordItem> =
    items.sortedWith(
        compareByDescending<RecordItem> { it.goalValue != null && it.goalPeriod != null }
            .thenByDescending { it.priority }
            .thenBy { it.name.lowercase(Locale.ROOT) },
    )

internal fun goalItems(items: List<RecordItem>, period: GoalPeriod? = null): List<RecordItem> =
    orderedRecordItems(items).filter { item ->
        item.mode == RecordMode.ACCUMULATE && item.goalValue != null && item.goalPeriod != null &&
            (period == null || item.goalPeriod == period)
    }

internal fun todayRecordValues(entries: List<RecordEntry>): Map<Long, Double> =
    entries.groupBy(RecordEntry::itemId).mapValues { (_, values) -> values.sumOf(RecordEntry::value) }

internal fun todayRecordMinutes(entries: List<RecordEntry>): Map<Long, List<Int>> =
    entries.groupBy(RecordEntry::itemId).mapValues { (_, values) -> values.map(RecordEntry::localMinute) }

internal fun accumulateHomeValue(
    item: RecordItem,
    goalValues: Map<Long, Double>,
    todayValues: Map<Long, Double>,
): Double = if (item.goalValue != null) goalValues[item.id] ?: 0.0 else todayValues[item.id] ?: 0.0

internal fun homeProgressFraction(current: Double, target: Double?): Float =
    target?.let { goalProgressFraction(current, it) } ?: 0f

internal fun homeProgressValueLabel(current: Double, target: Double?, unit: String): String {
    val progress = "${formatValue(current)}/${target?.let(::formatValue) ?: "--"}"
    return if (unit.isBlank()) progress else "$progress $unit"
}
