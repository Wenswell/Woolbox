package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.TodoTask
import com.wswensw.woolbox.ui.AppLazyRouteScaffold
import com.wswensw.woolbox.ui.AppStyle

@Composable
fun ManageTasksScreen(
    state: RecordsUiState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups: List<Pair<Int, List<TodoTask>>> = listOf(
        R.string.open to state.tasks
            .filterNot(TodoTask::isCompleted)
            .sortedBy { it.title.lowercase() },
        R.string.completed to state.tasks
            .filter(TodoTask::isCompleted)
            .sortedByDescending(TodoTask::completedAt),
    ).filter { (_, tasks) -> tasks.isNotEmpty() }
    AppLazyRouteScaffold(
        title = stringResource(R.string.manage_tasks),
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(onClick = onCreate) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = stringResource(R.string.new_task))
            }
        },
    ) {
        when {
            state.isLoading -> item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.tasks.isEmpty() -> item {
                RecordsStatusCard(
                    message = state.error,
                    actionLabel = stringResource(R.string.navigation_back),
                    onAction = onBack,
                    modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal),
                )
            }
            state.tasks.isEmpty() -> item {
                RecordsStatusCard(
                    message = stringResource(R.string.no_tasks),
                    actionLabel = stringResource(R.string.create_task),
                    onAction = onCreate,
                    modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal),
                )
            }
            else -> groups.forEach { (titleRes, tasks) ->
                item(key = "group-$titleRes") {
                    Text(
                        text = "${stringResource(titleRes)} (${tasks.size})",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            start = AppStyle.spacing.pageHorizontal,
                            end = AppStyle.spacing.pageHorizontal,
                            top = 8.dp,
                            bottom = 6.dp,
                        ),
                    )
                }
                items(tasks, key = TodoTask::id) { task ->
                    TaskManageCard(
                        task = task,
                        onClick = { onEdit(task.id) },
                        modifier = Modifier
                            .padding(horizontal = AppStyle.spacing.pageHorizontal)
                            .padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskManageCard(
    task: TodoTask,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val completed = task.completedStepCount
    val total = task.steps.size
    val reduceMotion = prefersReducedMotion()
    val targetColors = recordProgressColors(
        progress = taskColorProgress(task),
        completed = task.isCompleted,
    )
    val unfinishedContainer = recordProgressColors(progress = 0f).container
    val container by animateColorAsState(
        targetValue = targetColors.container,
        label = "ManageTaskContainer",
    )
    val accent by animateColorAsState(
        targetValue = targetColors.accent,
        label = "ManageTaskAccent",
    )
    val contentColor by animateColorAsState(
        targetValue = targetColors.onContainer,
        label = "ManageTaskOnContainer",
    )
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = AppStyle.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
        ) {
            SegmentedProgressFill(
                stepFilled = task.steps.map { it.isCompleted },
                unfinishedBackgroundColor = unfinishedContainer,
                completedBackgroundColor = container,
                fillColor = accent.copy(alpha = 0.32f),
                reduceMotion = reduceMotion,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Text(
                        text = stringResource(R.string.subtasks_progress, completed, total),
                        color = contentColor.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$completed/$total",
                    color = accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.54f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
