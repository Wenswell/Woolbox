package com.wswensw.template.record.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.ItemStreak
import com.wswensw.template.record.model.streakLabel
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordMode
import com.wswensw.template.record.model.TodoTask
import com.wswensw.template.ui.TemplateStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecordsScreen(
    state: RecordsUiState,
    onGoals: () -> Unit,
    onManage: () -> Unit,
    onCreateItem: () -> Unit,
    onConfigureConnection: () -> Unit,
    onRetry: () -> Unit,
    onRecordItem: (RecordItem) -> Unit,
    onOpenMeasurement: (RecordItem) -> Unit,
    onAddMeasurement: (RecordItem) -> Unit,
    onCreateTask: () -> Unit,
    onOpenTask: (TodoTask) -> Unit,
    onToggleTask: (TodoTask, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(RecordsRootTab.TASKS) }
    var completedTasksExpanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TemplateStyle.spacing.pageHorizontal,
            top = TemplateStyle.spacing.pageTop,
            end = TemplateStyle.spacing.pageHorizontal,
            bottom = TemplateStyle.spacing.pageBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(TemplateStyle.spacing.block),
    ) {
        item {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                RecordsRootTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                    )
                }
            }
        }

        if (selectedTab == RecordsRootTab.TASKS) {
            taskListItems(
                state = state,
                completedExpanded = completedTasksExpanded,
                onCompletedExpandedChange = { completedTasksExpanded = it },
                onCreateTask = onCreateTask,
                onOpenTask = onOpenTask,
                onToggleTask = onToggleTask,
                onConfigureConnection = onConfigureConnection,
                onRetry = onRetry,
            )
        } else {
            trackerListItems(
                state = state,
                onGoals = onGoals,
                onManage = onManage,
                onCreateItem = onCreateItem,
                onConfigureConnection = onConfigureConnection,
                onRetry = onRetry,
                onRecordItem = onRecordItem,
                onOpenMeasurement = onOpenMeasurement,
                onAddMeasurement = onAddMeasurement,
            )
        }
    }
}

private enum class RecordsRootTab(val label: String) {
    TASKS("Tasks"),
    TRACKERS("Trackers"),
}

private fun LazyListScope.taskListItems(
    state: RecordsUiState,
    completedExpanded: Boolean,
    onCompletedExpandedChange: (Boolean) -> Unit,
    onCreateTask: () -> Unit,
    onOpenTask: (TodoTask) -> Unit,
    onToggleTask: (TodoTask, Boolean) -> Unit,
    onConfigureConnection: () -> Unit,
    onRetry: () -> Unit,
) {
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tasks",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(onClick = onCreateTask) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = "New task")
            }
        }
    }
    when {
        state.isLoading -> item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        state.error != null -> item {
            RecordsStatusCard(
                message = state.error,
                actionLabel = if (state.connection.isConfigured) "Retry" else "Configure connection",
                onAction = if (state.connection.isConfigured) onRetry else onConfigureConnection,
            )
        }
        else -> {
            val openTasks = state.tasks.filterNot(TodoTask::isCompleted)
            val completedTasks = state.tasks
                .filter(TodoTask::isCompleted)
                .sortedByDescending(TodoTask::completedAt)
            if (openTasks.isEmpty()) {
                item {
                    RecordsStatusCard(
                        message = "No open tasks.",
                        actionLabel = "Create task",
                        onAction = onCreateTask,
                    )
                }
            } else {
                items(openTasks, key = TodoTask::id) { task ->
                    TaskCard(
                        task = task,
                        onOpen = { onOpenTask(task) },
                        onToggle = { completed -> onToggleTask(task, completed) },
                    )
                }
            }
            if (completedTasks.isNotEmpty()) {
                item {
                    CompletedTasksHeader(
                        count = completedTasks.size,
                        expanded = completedExpanded,
                        onToggle = { onCompletedExpandedChange(!completedExpanded) },
                    )
                }
                if (completedExpanded) {
                    items(completedTasks, key = TodoTask::id) { task ->
                        TaskCard(
                            task = task,
                            onOpen = { onOpenTask(task) },
                            onToggle = { completed -> onToggleTask(task, completed) },
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.trackerListItems(
    state: RecordsUiState,
    onGoals: () -> Unit,
    onManage: () -> Unit,
    onCreateItem: () -> Unit,
    onConfigureConnection: () -> Unit,
    onRetry: () -> Unit,
    onRecordItem: (RecordItem) -> Unit,
    onOpenMeasurement: (RecordItem) -> Unit,
    onAddMeasurement: (RecordItem) -> Unit,
) {
    item {
        val orderedItems = orderedRecordItems(state.activeItems)
        val focusGoals = goalItems(orderedItems)
        val completion = focusGoalCompletion(focusGoals, state.goalValues)
        RecordsHeader(
            date = state.selectedDate,
            completion = completion,
            onGoals = onGoals,
            onManage = onManage,
        )
    }
    when {
        state.isLoading -> item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        state.error != null -> item {
            RecordsStatusCard(
                message = state.error,
                actionLabel = if (state.connection.isConfigured) "Retry" else "Configure connection",
                onAction = if (state.connection.isConfigured) onRetry else onConfigureConnection,
            )
        }
        state.activeItems.isEmpty() -> item {
            RecordsStatusCard(
                message = "No record items yet.",
                actionLabel = "Create record item",
                onAction = onCreateItem,
            )
        }
        else -> {
            val accumulateItems = orderedRecordItems(state.activeItems)
                .filter { it.mode == RecordMode.ACCUMULATE }
            val measurementItems = state.activeItems
                .filter { it.mode == RecordMode.MEASUREMENT }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
            val todayValues = todayRecordValues(state.entries)
            items(accumulateItems, key = RecordItem::id) { item ->
                QuickRecordCard(
                    item = item,
                    todayValue = todayValues[item.id] ?: 0.0,
                    goalCumulative = state.goalValues[item.id],
                    streak = state.streaks[item.id],
                    onClick = { onRecordItem(item) },
                )
            }
            if (measurementItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Measurements",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(measurementItems, key = RecordItem::id) { item ->
                    MeasurementCard(
                        item = item,
                        latest = state.latestEntries[item.id],
                        onOpen = { onOpenMeasurement(item) },
                        onAdd = { onAddMeasurement(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedTasksHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Completed ($count)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = onToggle) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse completed tasks" else "Expand completed tasks",
            )
        }
    }
}

@Composable
private fun TaskCard(
    task: TodoTask,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = TemplateStyle.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { checked -> onToggle(checked) },
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.steps.isNotEmpty()) {
                    val completed = task.completedStepCount
                    Text(
                        text = "$completed / ${task.steps.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    LinearProgressIndicator(
                        progress = { completed.toFloat() / task.steps.size },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .height(3.dp)
                            .clip(CircleShape),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun MeasurementCard(
    item: RecordItem,
    latest: RecordEntry?,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = recordItemColors(item.name)
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = TemplateStyle.cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordItemInitial(name = item.name, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayRecordName(item.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = latest?.let { measurementDateTimeLabel(it.occurredAt) } ?: "No measurements yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = latest?.let { "${formatValue(it.value)} ${item.unit}" } ?: "--",
                    color = colors.accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                FilledTonalIconButton(onClick = onAdd) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add measurement",
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordsHeader(
    date: LocalDate,
    completion: FocusGoalCompletion,
    onGoals: () -> Unit,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = recordsDateLabel(date),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (completion.total > 0) {
                Text(
                    text = dayStatusLabel(completion),
                    color = if (completion.done >= completion.total) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        FilledTonalIconButton(onClick = onGoals) {
            Icon(
                imageVector = Icons.Outlined.Flag,
                contentDescription = "Goals",
            )
        }
        Spacer(Modifier.width(4.dp))
        FilledTonalIconButton(onClick = onManage) {
            Icon(
                painter = painterResource(R.drawable.ic_settings_24),
                contentDescription = "Manage records",
            )
        }
    }
}

@Composable
private fun QuickRecordCard(
    item: RecordItem,
    todayValue: Double,
    goalCumulative: Double?,
    streak: ItemStreak?,
    onClick: () -> Unit,
) {
    val colors = recordItemColors(item.name)
    val target = item.goalValue
    val period = item.goalPeriod
    val hasGoal = target != null && target > 0.0 && period != null
    val cumulative = goalCumulative ?: todayValue
    val progress = if (hasGoal) goalProgressRatio(cumulative, checkNotNull(target)).toFloat() else 0f
    val goalReached = hasGoal && cumulative >= checkNotNull(target)
    val progressColor = if (goalReached) {
        MaterialTheme.colorScheme.tertiary
    } else {
        colors.accent
    }
    val container by animateColorAsState(
        targetValue = if (goalReached) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            colors.container
        },
        label = "QuickRecordContainer",
    )

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = TemplateStyle.cardShape,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasGoal) {
                DailyGoalMedallion(
                    item = item,
                    progress = progress,
                    reached = goalReached,
                    progressColor = progressColor,
                    size = 48.dp,
                )
            } else {
                RecordItemInitial(name = item.name, size = 48.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayRecordName(item.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = quickRecordSupportingText(
                        item = item,
                        hasGoal = hasGoal,
                        cumulative = cumulative,
                        goalReached = goalReached,
                        progress = progress,
                    ),
                    color = if (goalReached) progressColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasGoal && period == GoalPeriod.WEEK && !goalReached) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = progressColor,
                        trackColor = progressColor.copy(alpha = 0.16f),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.widthIn(max = 140.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = "${formatValue(todayValue)} ${item.unit}",
                    color = progressColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val streakText = streak?.let(::streakLabel)
                if (streakText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = progressColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = streakText,
                            color = progressColor,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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
        shape = TemplateStyle.cardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_records_24),
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}

internal data class FocusGoalCompletion(
    val done: Int,
    val total: Int,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else done.toFloat() / total.toFloat()
}

internal fun focusGoalCompletion(
    goals: List<RecordItem>,
    values: Map<Long, Double>,
): FocusGoalCompletion {
    val done = goals.count { item ->
        val target = item.goalValue ?: return@count false
        (values[item.id] ?: 0.0) >= target
    }
    return FocusGoalCompletion(done = done, total = goals.size)
}

internal fun dayStatusLabel(completion: FocusGoalCompletion): String = when {
    completion.total <= 0 -> ""
    completion.done <= 0 -> "0 of ${completion.total} goals done"
    completion.done >= completion.total -> "All goals done"
    else -> "${completion.done} of ${completion.total} goals done"
}

internal fun crossedGoalThreshold(
    previous: Double,
    current: Double,
    target: Double?,
): Boolean {
    if (target == null || target <= 0.0) return false
    return previous < target && current >= target
}



internal fun quickRecordSupportingText(
    item: RecordItem,
    hasGoal: Boolean,
    cumulative: Double,
    goalReached: Boolean,
    progress: Float,
): String = when {
    goalReached -> "Goal reached"
    hasGoal -> {
        val target = checkNotNull(item.goalValue)
        val period = if (item.goalPeriod == GoalPeriod.DAY) "today" else "this week"
        "${goalValueLabel(cumulative, target, item.unit)} · ${goalProgressPercent(progress.toDouble())}% $period"
    }
    item.description.isNotEmpty() -> item.description
    else -> "Today"
}

internal fun orderedRecordItems(items: List<RecordItem>): List<RecordItem> =
    items.sortedWith(
        compareByDescending<RecordItem> { it.goalValue != null && it.goalPeriod != null }
            .thenByDescending { it.priority }
            .thenBy { it.name.lowercase(Locale.ROOT) },
    )

internal fun goalItems(items: List<RecordItem>, period: GoalPeriod? = null): List<RecordItem> =
    orderedRecordItems(items).filter { item ->
        item.mode == RecordMode.ACCUMULATE &&
            item.goalValue != null &&
            item.goalPeriod != null &&
            (period == null || item.goalPeriod == period)
    }

internal fun todayRecordValues(entries: List<RecordEntry>): Map<Long, Double> =
    entries.groupBy(RecordEntry::itemId).mapValues { (_, itemEntries) -> itemEntries.sumOf(RecordEntry::value) }

internal fun recordsDateLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))
