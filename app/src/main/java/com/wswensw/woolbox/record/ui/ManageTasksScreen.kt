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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    AppLazyRouteScaffold(
        title = "Manage tasks",
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(onClick = onCreate) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = "New task")
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
                    actionLabel = "Back",
                    onAction = onBack,
                    modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal),
                )
            }
            state.tasks.isEmpty() -> item {
                RecordsStatusCard(
                    message = "No tasks yet.",
                    actionLabel = "Create task",
                    onAction = onCreate,
                    modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal),
                )
            }
            else -> items(state.tasks, key = TodoTask::id) { task ->
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

@Composable
private fun TaskManageCard(
    task: TodoTask,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val completed = task.completedStepCount
    val total = task.steps.size
    val progress = if (total <= 0) 0f else completed.toFloat() / total.toFloat()
    val targetColors = recordProgressColors(
        progress = taskColorProgress(task),
        completed = task.isCompleted,
    )
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
    val colors = targetColors.copy(accent = accent, container = container, onContainer = contentColor)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = AppStyle.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = colors.accent,
                    trackColor = colors.accent.copy(alpha = 0.18f),
                    strokeWidth = 3.5.dp,
                    gapSize = 0.dp,
                )
                Text(
                    text = "$completed",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$completed / $total subtasks",
                    color = contentColor.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.54f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
