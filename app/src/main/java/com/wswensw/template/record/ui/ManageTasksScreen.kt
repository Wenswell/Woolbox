package com.wswensw.template.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.template.record.model.TodoTask
import com.wswensw.template.ui.TemplateLazyRouteScaffold
import com.wswensw.template.ui.TemplateStyle

@Composable
fun ManageTasksScreen(
    state: RecordsUiState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    TemplateLazyRouteScaffold(
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
            state.error != null -> item {
                RecordsStatusCard(
                    message = state.error,
                    actionLabel = "Back",
                    onAction = onBack,
                    modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal),
                )
            }
            state.tasks.isEmpty() -> item {
                RecordsStatusCard(
                    message = "No tasks yet.",
                    actionLabel = "Create task",
                    onAction = onCreate,
                    modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal),
                )
            }
            else -> items(state.tasks, key = TodoTask::id) { task ->
                TaskManageCard(
                    task = task,
                    onClick = { onEdit(task.id) },
                    modifier = Modifier.padding(
                        horizontal = TemplateStyle.spacing.pageHorizontal,
                        vertical = 4.dp,
                    ),
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
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = TemplateStyle.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${task.completedStepCount} / ${task.steps.size} subtasks",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
