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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.ItemStreak
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordMode
import com.wswensw.template.record.model.TodoTask
import com.wswensw.template.record.model.streakLabel
import com.wswensw.template.ui.TemplateStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RailCardWidth = 240.dp
private val RailCardHeight = 136.dp
private val RailCardSpacing = 12.dp

@Composable
fun RecordsScreen(
    state: RecordsUiState,
    onManageTasks: () -> Unit,
    onManageRecords: () -> Unit,
    onCreateAccumulate: () -> Unit,
    onCreateMeasurement: () -> Unit,
    onConfigureConnection: () -> Unit,
    onRetry: () -> Unit,
    onRecordItem: (RecordItem) -> Unit,
    onOpenMeasurement: (RecordItem) -> Unit,
    onCreateTask: () -> Unit,
    onOpenTask: (TodoTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    var completedTasksExpanded by rememberSaveable { mutableStateOf(false) }
    val openTasks = state.tasks.filterNot(TodoTask::isCompleted)
    val completedTasks = state.tasks
        .filter(TodoTask::isCompleted)
        .sortedByDescending(TodoTask::completedAt)
    val accumulateItems = orderedRecordItems(state.activeItems)
        .filter { it.mode == RecordMode.ACCUMULATE }
    val measurementItems = state.activeItems
        .filter { it.mode == RecordMode.MEASUREMENT }
        .sortedBy { it.name.lowercase(Locale.ROOT) }
    val todayValues = todayRecordValues(state.entries)

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
                item {
                    RecordsSectionHeader(
                        title = "Tasks",
                        onAdd = onCreateTask,
                        addContentDescription = "New task",
                        leadingActions = {
                            FilledTonalIconButton(onClick = onManageTasks) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings_24),
                                    contentDescription = "Manage tasks",
                                )
                            }
                        },
                    )
                }
                if (openTasks.isEmpty()) {
                    item {
                        RecordsStatusCard(
                            message = "No open tasks.",
                            actionLabel = "Create task",
                            onAction = onCreateTask,
                        )
                    }
                } else {
                    items(openTasks, key = { "open-${it.id}" }) { task ->
                        TaskProgressCard(
                            task = task,
                            onOpen = { onOpenTask(task) },
                        )
                    }
                }
                if (completedTasks.isNotEmpty()) {
                    item {
                        CompletedTasksHeader(
                            count = completedTasks.size,
                            expanded = completedTasksExpanded,
                            onToggle = { completedTasksExpanded = !completedTasksExpanded },
                        )
                    }
                    if (completedTasksExpanded) {
                        items(completedTasks, key = { "done-${it.id}" }) { task ->
                            TaskProgressCard(
                                task = task,
                                onOpen = { onOpenTask(task) },
                            )
                        }
                    }
                }

                item {
                    RecordsSectionHeader(
                        title = "Accumulate",
                        onAdd = onCreateAccumulate,
                        addContentDescription = "New accumulate",
                        leadingActions = {
                            FilledTonalIconButton(onClick = onManageRecords) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings_24),
                                    contentDescription = "Manage records",
                                )
                            }
                        },
                    )
                }
                item {
                    if (accumulateItems.isEmpty()) {
                        RecordsStatusCard(
                            message = "No accumulate items yet.",
                            actionLabel = "Create accumulate",
                            onAction = onCreateAccumulate,
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(RailCardSpacing),
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) {
                            items(accumulateItems, key = RecordItem::id) { item ->
                                AccumulateRailCard(
                                    item = item,
                                    todayValue = todayValues[item.id] ?: 0.0,
                                    goalCumulative = state.goalValues[item.id],
                                    streak = state.streaks[item.id],
                                    onClick = { onRecordItem(item) },
                                )
                            }
                        }
                    }
                }

                item {
                    RecordsSectionHeader(
                        title = "Measurement",
                        onAdd = onCreateMeasurement,
                        addContentDescription = "New measurement",
                        leadingActions = {
                            FilledTonalIconButton(onClick = onManageRecords) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings_24),
                                    contentDescription = "Manage records",
                                )
                            }
                        },
                    )
                }
                item {
                    if (measurementItems.isEmpty()) {
                        RecordsStatusCard(
                            message = "No measurement items yet.",
                            actionLabel = "Create measurement",
                            onAction = onCreateMeasurement,
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(RailCardSpacing),
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) {
                            items(measurementItems, key = RecordItem::id) { item ->
                                MeasurementRailCard(
                                    item = item,
                                    latest = state.latestEntries[item.id],
                                    onOpen = { onOpenMeasurement(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsSectionHeader(
    title: String,
    onAdd: () -> Unit,
    addContentDescription: String,
    leadingActions: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        leadingActions?.invoke()
        FilledTonalIconButton(onClick = onAdd) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = addContentDescription,
            )
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
private fun TaskProgressCard(
    task: TodoTask,
    onOpen: () -> Unit,
) {
    val completed = task.completedStepCount
    val progress = completed.toFloat() / task.steps.size.toFloat()
    val colors = recordItemColors(task.title)
    val cardContentColor = colors.onContainer.copy(
        alpha = if (task.isCompleted) 0.72f else 1f,
    )
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = TemplateStyle.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) {
                colors.container.copy(alpha = 0.72f)
            } else {
                colors.container
            },
            contentColor = cardContentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = colors.accent,
                    trackColor = colors.accent.copy(alpha = 0.18f),
                    strokeWidth = 5.dp,
                    gapSize = 0.dp,
                )
                Text(
                    text = "$completed",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    color = cardContentColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$completed / ${task.steps.size} subtasks",
                    color = cardContentColor.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (task.isCompleted) {
                    task.completedAt?.let { completedAt ->
                        Text(
                            text = taskCompletedAtLabel(completedAt),
                            color = cardContentColor.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccumulateRailCard(
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
        label = "AccumulateRailContainer",
    )
    val contentColor = if (goalReached) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        colors.onContainer
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(RailCardWidth)
            .height(RailCardHeight),
        shape = TemplateStyle.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DailyGoalMedallion(
                item = item,
                progress = progress,
                reached = goalReached,
                progressColor = progressColor,
                size = 76.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = displayRecordName(item.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatValue(todayValue)} ${item.unit}",
                    color = progressColor,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasGoal) {
                    Text(
                        text = quickRecordSupportingText(
                            item = item,
                            hasGoal = true,
                            goalReached = goalReached,
                        ),
                        color = if (goalReached) progressColor else contentColor.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        color = contentColor.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
private fun MeasurementRailCard(
    item: RecordItem,
    latest: RecordEntry?,
    onOpen: () -> Unit,
) {
    val colors = recordItemColors(item.name)
    Card(
        onClick = onOpen,
        modifier = Modifier
            .width(RailCardWidth)
            .height(RailCardHeight),
        shape = TemplateStyle.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = colors.container,
            contentColor = colors.onContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RecordItemInitial(name = item.name, size = 48.dp)
                Text(
                    text = displayRecordName(item.name),
                    color = colors.onContainer,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = latest?.let { "${formatValue(it.value)} ${item.unit}" } ?: "--",
                    color = colors.accent,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = latest?.let { measurementDateTimeLabel(it.occurredAt) } ?: "No measurements yet.",
                    color = colors.onContainer.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
    goalReached: Boolean,
): String = when {
    goalReached -> "Goal reached"
    hasGoal -> {
        val target = checkNotNull(item.goalValue)
        val period = if (item.goalPeriod == GoalPeriod.DAY) "Daily" else "Weekly"
        "$period target · ${formatValue(target)} ${item.unit}"
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
