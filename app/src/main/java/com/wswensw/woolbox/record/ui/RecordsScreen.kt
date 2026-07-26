package com.wswensw.woolbox.record.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.GoalPeriod
import com.wswensw.woolbox.record.model.dayRing
import com.wswensw.woolbox.record.model.emptyRecentDayValues
import com.wswensw.woolbox.record.model.hasFocusCommitments
import com.wswensw.woolbox.record.model.isGoalSatisfied
import com.wswensw.woolbox.record.model.NowTarget
import com.wswensw.woolbox.record.model.recentDayPieProgress
import com.wswensw.woolbox.record.model.RecordEntry
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordMode
import com.wswensw.woolbox.record.model.RecordsClock
import com.wswensw.woolbox.record.data.RootTaskPreviewLimit
import com.wswensw.woolbox.record.model.TaskHorizon
import com.wswensw.woolbox.record.model.TodoStep
import com.wswensw.woolbox.record.model.TodoTask
import com.wswensw.woolbox.ui.AppStyle
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private val GridCardHeight = 120.dp
private val GridCardSpacing = 8.dp

@Composable
fun RecordsScreen(
    state: RecordsUiState,
    clock: State<RecordsClock>,
    rootTaskPreviewLimit: RootTaskPreviewLimit,
    onManageTasks: () -> Unit,
    onManageAccumulates: () -> Unit,
    onManageMeasurements: () -> Unit,
    onConfigureConnection: () -> Unit,
    onRetry: () -> Unit,
    onRecordItem: (RecordItem) -> Unit,
    onQuickRecord: (RecordItem, Double) -> Unit,
    onCompleteStep: (TodoTask, TodoStep) -> Unit,
    onDeferTarget: (NowTarget) -> Unit,
    onOpenMeasurement: (RecordItem) -> Unit,
    onOpenTask: (TodoTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    var completedTasksExpanded by rememberSaveable { mutableStateOf(false) }
    var laterTasksExpanded by rememberSaveable { mutableStateOf(false) }
    val openTasks = state.tasks.filterNot(TodoTask::isCompleted)
    val todayTasks = openTasks.filter { it.horizon == TaskHorizon.TODAY }
    val laterTasks = openTasks.filter { it.horizon == TaskHorizon.LATER }
    val previewLimit = rootTaskPreviewLimit.count
    val completedTasks = state.tasks
        .filter(TodoTask::isCompleted)
        .sortedByDescending(TodoTask::completedAt)
    val accumulateItems = orderedRecordItems(state.activeItems)
        .filter { it.mode == RecordMode.ACCUMULATE }
    val measurementItems = state.activeItems
        .filter { it.mode == RecordMode.MEASUREMENT }
        .sortedBy { it.name.lowercase(Locale.ROOT) }
    val todayValues = todayRecordValues(state.entries)
    val recordMinutes = todayRecordMinutes(state.entries)
    val ring = dayRing(items = accumulateItems, tasks = openTasks, goalValues = state.goalValues)
    // Decided without the clock, so the card never appears or disappears on a
    // minute tick; only its contents follow time.
    val hasFocus = ring.isVisible || hasFocusCommitments(
        items = accumulateItems,
        tasks = openTasks,
        goalValues = state.goalValues,
        todayValues = todayValues,
        habitMinutes = state.habitMinutes,
    )
    val listState = rememberLazyListState()
    var pauseDecorativeMotion by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { scrolling ->
                if (scrolling) {
                    pauseDecorativeMotion = true
                } else {
                    delay(300)
                    pauseDecorativeMotion = false
                }
            }
    }

    CompositionLocalProvider(LocalPauseDecorativeMotion provides pauseDecorativeMotion) {
    LazyColumn(
        state = listState,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && !state.hasSnapshot -> item {
                RecordsStatusCard(
                    message = state.error,
                    actionLabel = if (state.connection.isConfigured) "Retry" else "Configure connection",
                    onAction = if (state.connection.isConfigured) onRetry else onConfigureConnection,
                )
            }
            else -> {
                if (state.error != null) {
                    item {
                        RecordsStatusCard(
                            message = state.error,
                            actionLabel = "Retry",
                            onAction = onRetry,
                        )
                    }
                }
                if (hasFocus) {
                    item {
                        RecordsFocusCard(
                            clock = clock,
                            items = accumulateItems,
                            tasks = openTasks,
                            ring = ring,
                            streak = state.goalStreak,
                            goalValues = state.goalValues,
                            todayValues = todayValues,
                            todayMinutes = recordMinutes,
                            habitMinutes = state.habitMinutes,
                            itemStreaks = state.itemStreaks,
                            deferred = state.deferredTargets,
                            colorProgressOf = { target ->
                                when (target) {
                                    is NowTarget.Accumulate -> accumulateColorProgress(
                                        item = target.item,
                                        cumulative = state.goalValues[target.item.id]
                                            ?: todayValues[target.item.id]
                                            ?: 0.0,
                                        recentDayValues = state.recentDayValues[target.item.id],
                                    )
                                    is NowTarget.Task -> taskColorProgress(target.task)
                                }
                            },
                            quickValueOf = { item ->
                                item.defaultValue?.takeIf { it.isFinite() && it > 0.0 }
                            },
                            onQuickRecord = onQuickRecord,
                            onOpenItem = onRecordItem,
                            onCompleteStep = onCompleteStep,
                            onDefer = onDeferTarget,
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                item {
                    RecordsSectionHeader(
                        title = "Tasks",
                        manageContentDescription = "Manage tasks",
                        onManage = onManageTasks,
                    )
                }
                if (openTasks.isEmpty()) {
                    item {
                        RecordsStatusCard(
                            message = "No open tasks.",
                            actionLabel = "Manage tasks",
                            onAction = onManageTasks,
                        )
                    }
                } else {
                    item { TaskHorizonHeader(title = "Today", count = todayTasks.size) }
                    if (todayTasks.isEmpty()) {
                        item {
                            Text(
                                text = "No today tasks.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        val visibleToday = previewLimit?.let { todayTasks.take(it) } ?: todayTasks
                        items(visibleToday, key = { "today-${it.id}" }) { task ->
                            TaskProgressCard(task = task, onOpen = { onOpenTask(task) })
                        }
                        if (previewLimit != null && todayTasks.size > previewLimit) {
                            item {
                                TextButton(onClick = onManageTasks) {
                                    Text("+${todayTasks.size - previewLimit} more in Manage")
                                }
                            }
                        }
                    }
                    if (laterTasks.isNotEmpty()) {
                        item {
                            TaskHorizonHeader(
                                title = "Later",
                                count = laterTasks.size,
                                expanded = laterTasksExpanded,
                                onToggle = { laterTasksExpanded = !laterTasksExpanded },
                            )
                        }
                        if (laterTasksExpanded) {
                            val visibleLater = previewLimit?.let { laterTasks.take(it) } ?: laterTasks
                            items(visibleLater, key = { "later-${it.id}" }) { task ->
                                TaskProgressCard(task = task, onOpen = { onOpenTask(task) })
                            }
                            if (previewLimit != null && laterTasks.size > previewLimit) {
                                item {
                                    TextButton(onClick = onManageTasks) {
                                        Text("+${laterTasks.size - previewLimit} more in Manage")
                                    }
                                }
                            }
                        }
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

                item { Spacer(Modifier.height(8.dp)) }
                item {
                    RecordsSectionHeader(
                        title = "Accumulate",
                        manageContentDescription = "Manage accumulates",
                        onManage = onManageAccumulates,
                    )
                }
                if (accumulateItems.isEmpty()) {
                    item {
                        RecordsStatusCard(
                            message = "No accumulate items yet.",
                            actionLabel = "Manage accumulates",
                            onAction = onManageAccumulates,
                        )
                    }
                } else {
                    items(
                        accumulateItems.chunked(2),
                        key = { row -> "acc-" + row.joinToString("-") { it.id.toString() } },
                    ) { row ->
                        RecordGridRow {
                            row.forEach { item ->
                                AccumulateRailCard(
                                    item = item,
                                    todayValue = todayValues[item.id] ?: 0.0,
                                    goalCumulative = state.goalValues[item.id],
                                    recentDayValues = state.recentDayValues[item.id],
                                    lastRecordMinute = recordMinutes[item.id]?.maxOrNull(),
                                    onClick = { onRecordItem(item) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
                item {
                    RecordsSectionHeader(
                        title = "Measurement",
                        manageContentDescription = "Manage measurements",
                        onManage = onManageMeasurements,
                    )
                }
                if (measurementItems.isEmpty()) {
                    item {
                        RecordsStatusCard(
                            message = "No measurement items yet.",
                            actionLabel = "Manage measurements",
                            onAction = onManageMeasurements,
                        )
                    }
                } else {
                    items(
                        measurementItems.chunked(2),
                        key = { row -> "mes-" + row.joinToString("-") { it.id.toString() } },
                    ) { row ->
                        RecordGridRow {
                            row.forEach { item ->
                                MeasurementRailCard(
                                    item = item,
                                    latest = state.latestEntries[item.id],
                                    onOpen = { onOpenMeasurement(item) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
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
    manageContentDescription: String,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onManage,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings_24),
                contentDescription = manageContentDescription,
                modifier = Modifier.size(18.dp),
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
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse completed tasks" else "Expand completed tasks",
            )
        }
    }
}


@Composable
private fun TaskHorizonHeader(
    title: String,
    count: Int,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title ($count)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (expanded != null && onToggle != null) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                )
            }
        }
    }
}

@Composable
private fun RecordGridRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GridCardSpacing),
        content = content,
    )
}

@Composable
private fun TaskProgressCard(
    task: TodoTask,
    onOpen: () -> Unit,
) {
    val completed = task.completedStepCount
    val total = task.steps.size
    val reduceMotion = prefersReducedMotion()
    val nextStepTitle = nextTaskStepTitle(task)
    val targetColors = recordProgressColors(
        progress = taskColorProgress(task),
        completed = task.isCompleted,
    )
    val unfinishedContainer = recordProgressColors(progress = 0f).container
    val container by animateColorAsState(
        targetValue = targetColors.container,
        label = "TaskCardContainer",
    )
    val accent by animateColorAsState(
        targetValue = targetColors.accent,
        label = "TaskCardAccent",
    )
    val onContainer by animateColorAsState(
        targetValue = targetColors.onContainer,
        label = "TaskCardOnContainer",
    )
    val cardContentColor = onContainer
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = AppStyle.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = cardContentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            SegmentedProgressFill(
                stepFilled = task.steps.map { it.isCompleted },
                unfinishedBackgroundColor = unfinishedContainer,
                completedBackgroundColor = container,
                fillColor = accent.copy(alpha = 0.32f),
                reduceMotion = reduceMotion,
                modifier = Modifier.matchParentSize(),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (task.isCompleted) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                        )
                    }
                    Text(
                        text = task.title,
                        color = cardContentColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$completed/$total",
                        color = accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (nextStepTitle != null) {
                    Text(
                        text = "Next · $nextStepTitle",
                        color = cardContentColor.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (task.isCompleted) {
                    task.completedAt?.let { completedAt ->
                        Text(
                            text = taskCompletedAtLabel(completedAt),
                            color = cardContentColor.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
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
    recentDayValues: List<Double>?,
    lastRecordMinute: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = item.goalValue
    val period = item.goalPeriod
    val hasGoal = target != null && target > 0.0 && period != null
    val cumulative = goalCumulative ?: todayValue
    val resolvedDayValues = recentDayValues ?: emptyRecentDayValues()
    val colorProgress = accumulateColorProgress(
        item = item,
        cumulative = cumulative,
        recentDayValues = resolvedDayValues,
    )
    val fillProgress = if (hasGoal) {
        goalProgressFraction(cumulative, checkNotNull(target))
    } else {
        colorProgress.coerceIn(0f, 1f)
    }
    val goalReached = hasGoal && cumulative >= checkNotNull(target)
    val colors = recordProgressColors(
        progress = colorProgress,
        completed = goalReached,
    )
    val progressColor by animateColorAsState(
        targetValue = colors.accent,
        label = "AccumulateRailAccent",
    )
    val container by animateColorAsState(
        targetValue = colors.container,
        label = "AccumulateRailContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = colors.onContainer,
        label = "AccumulateRailOnContainer",
    )
    val reduceMotion = prefersReducedMotion()

    Card(
        onClick = onClick,
        modifier = modifier.height(GridCardHeight),
        shape = AppStyle.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LiquidProgressFill(
                progress = fillProgress,
                fillColor = progressColor,
                reduceMotion = reduceMotion,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = displayRecordName(item.name),
                        color = contentColor.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (lastRecordMinute != null) {
                        Text(
                            text = formatMinute(lastRecordMinute),
                            color = contentColor.copy(alpha = 0.62f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text = "${formatValue(todayValue)} ${item.unit}",
                    color = progressColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                if (hasGoal) {
                    val isDays7Goal = period == GoalPeriod.DAYS_7
                    val goalText = buildString {
                        append(formatValue(checkNotNull(target)))
                        if (item.unit.isNotBlank()) {
                            append(' ')
                            append(item.unit)
                        }
                        if (isDays7Goal) {
                            append(" · 7 days")
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (isDays7Goal) {
                                Icons.Outlined.DateRange
                            } else {
                                Icons.Outlined.Flag
                            },
                            contentDescription = if (isDays7Goal) "7-day goal" else "Daily goal",
                            tint = progressColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = goalText,
                            color = progressColor,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (hasGoal) {
                    if (period == GoalPeriod.DAYS_7) {
                        Days7GoalProgressBar(
                            progress = fillProgress,
                            fillColor = progressColor,
                            reduceMotion = reduceMotion,
                        )
                    } else {
                        RecentDayPies(
                            progresses = recentDayPieProgress(
                                dayValues = resolvedDayValues,
                                goalValue = requireNotNull(target),
                            ),
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
    modifier: Modifier = Modifier,
) {
    val targetColors = recordProgressColors(
        progress = measurementColorProgress(item.minValue, item.maxValue, latest?.value),
    )
    val container by animateColorAsState(
        targetValue = targetColors.container,
        label = "MeasurementCardContainer",
    )
    val accent by animateColorAsState(
        targetValue = targetColors.accent,
        label = "MeasurementCardAccent",
    )
    val onContainer by animateColorAsState(
        targetValue = targetColors.onContainer,
        label = "MeasurementCardOnContainer",
    )
    val colors = targetColors.copy(accent = accent, container = container, onContainer = onContainer)
    Card(
        onClick = onOpen,
        modifier = modifier.height(GridCardHeight),
        shape = AppStyle.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = onContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RecordItemInitial(name = item.name, size = 36.dp, colors = colors)
                Text(
                    text = displayRecordName(item.name),
                    color = colors.onContainer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = latest?.let { "${formatValue(it.value)} ${item.unit}" } ?: "--",
                    color = colors.accent,
                    style = MaterialTheme.typography.titleLarge,
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
        shape = AppStyle.cardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
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

internal fun crossedGoalThreshold(
    previous: Double,
    current: Double,
    target: Double?,
): Boolean {
    if (target == null || target <= 0.0) return false
    return previous < target && current >= target
}

internal fun nextTaskStepTitle(task: TodoTask): String? =
    task.steps.firstOrNull { !it.isCompleted }?.title

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

/** Local minutes each item was recorded at today. */
internal fun todayRecordMinutes(entries: List<RecordEntry>): Map<Long, List<Int>> =
    entries.groupBy(RecordEntry::itemId).mapValues { (_, itemEntries) ->
        itemEntries.map(RecordEntry::localMinute)
    }
