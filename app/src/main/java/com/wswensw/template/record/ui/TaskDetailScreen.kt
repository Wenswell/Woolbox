package com.wswensw.template.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.template.record.model.TodoStep
import com.wswensw.template.record.model.TodoTask
import com.wswensw.template.record.model.validatedTaskTitle
import com.wswensw.template.ui.TemplateRouteScaffold
import com.wswensw.template.ui.TemplateSection
import com.wswensw.template.ui.TemplateStyle

@Composable
fun TaskDetailScreen(
    task: TodoTask?,
    loading: Boolean,
    onBack: () -> Unit,
    onSaveTitle: (String, (String?) -> Unit) -> Unit,
    onToggleTask: (Boolean) -> Unit,
    onCreateStep: (String, (String?) -> Unit) -> Unit,
    onUpdateStep: (TodoStep, String, (String?) -> Unit) -> Unit,
    onToggleStep: (TodoStep, Boolean) -> Unit,
    onDeleteStep: (TodoStep, (String?) -> Unit) -> Unit,
    onDeleteTask: ((String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (task == null) {
        TemplateRouteScaffold(title = "Edit task", onBack = onBack, modifier = modifier) {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Text(
                    text = "Task not found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(TemplateStyle.spacing.pageHorizontal),
                )
            }
        }
        return
    }

    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var titleError by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var savingTitle by rememberSaveable { mutableStateOf(false) }
    var creatingStep by rememberSaveable { mutableStateOf(false) }
    var editingStepId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingStepId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingTask by rememberSaveable { mutableStateOf(false) }

    fun saveTitle() {
        errorMessage = null
        val normalizedTitle = runCatching { validatedTaskTitle(title) }.getOrElse { error ->
            titleError = error.message
            errorMessage = "Check the highlighted field."
            return
        }
        if (normalizedTitle == task.title) return
        savingTitle = true
        onSaveTitle(normalizedTitle) { error ->
            savingTitle = false
            errorMessage = error
        }
    }

    val titleDirty = title.trim() != task.title
    TemplateRouteScaffold(
        title = "Edit task",
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(onClick = { deletingTask = true }) {
                Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Delete task")
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(
                onClick = ::saveTitle,
                enabled = !savingTitle && titleDirty && title.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = if (savingTitle) "Saving" else "Save title",
                )
            }
        },
    ) {
        TemplateSection(title = "Title") {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleError = null
                },
                enabled = !savingTitle,
                label = { Text("Title") },
                isError = titleError != null,
                supportingText = titleError?.let { message -> { Text(message) } },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = onToggleTask,
                )
                Text("Completed", style = MaterialTheme.typography.bodyLarge)
            }
        }

        TemplateSection(title = "Subtasks") {
            if (task.steps.isEmpty()) {
                Text(
                    text = "No subtasks.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
                )
            }
            task.steps.forEachIndexed { index, step ->
                TaskStepRow(
                    step = step,
                    onToggle = { completed -> onToggleStep(step, completed) },
                    onEdit = { editingStepId = step.id },
                    onDelete = { deletingStepId = step.id },
                )
                if (index < task.steps.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalIconButton(onClick = { creatingStep = true }) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add subtask")
                }
            }
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (creatingStep) {
        TaskTitleDialog(
            title = "New subtask",
            initialTitle = "",
            onDismiss = { creatingStep = false },
            onSave = { value, onResult ->
                onCreateStep(value) { error ->
                    onResult(error)
                    if (error == null) creatingStep = false
                }
            },
        )
    }
    val editingStep = task.steps.firstOrNull { it.id == editingStepId }
    if (editingStep != null) {
        TaskTitleDialog(
            title = "Edit subtask",
            initialTitle = editingStep.title,
            onDismiss = { editingStepId = null },
            onSave = { value, onResult ->
                onUpdateStep(editingStep, value) { error ->
                    onResult(error)
                    if (error == null) editingStepId = null
                }
            },
        )
    }
    val deletingStep = task.steps.firstOrNull { it.id == deletingStepId }
    if (deletingStep != null) {
        DeleteConfirmationDialog(
            title = "Delete subtask?",
            onDismiss = { deletingStepId = null },
            onConfirm = { onResult ->
                onDeleteStep(deletingStep) { error ->
                    onResult(error)
                    if (error == null) deletingStepId = null
                }
            },
        )
    }
    if (deletingTask) {
        DeleteConfirmationDialog(
            title = "Delete task?",
            onDismiss = { deletingTask = false },
            onConfirm = { onResult ->
                onDeleteTask { error ->
                    onResult(error)
                    if (error == null) {
                        deletingTask = false
                        onBack()
                    }
                }
            },
        )
    }
}

@Composable
private fun TaskStepRow(
    step: TodoStep,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = step.isCompleted, onCheckedChange = onToggle)
        Spacer(Modifier.width(8.dp))
        Text(
            text = step.title,
            color = if (step.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onEdit) {
            Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Edit subtask")
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Delete subtask")
        }
    }
}

@Composable
private fun TaskTitleDialog(
    title: String,
    initialTitle: String,
    onDismiss: () -> Unit,
    onSave: (String, (String?) -> Unit) -> Unit,
) {
    var value by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var valueError by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        valueError = null
                    },
                    enabled = !saving,
                    label = { Text("Title") },
                    isError = valueError != null,
                    supportingText = valueError?.let { message -> { Text(message) } },
                    singleLine = true,
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalized = runCatching { validatedTaskTitle(value) }.getOrElse { error ->
                        valueError = error.message
                        return@TextButton
                    }
                    saving = true
                    errorMessage = null
                    onSave(normalized) { error ->
                        saving = false
                        errorMessage = error
                    }
                },
                enabled = !saving,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: ((String?) -> Unit) -> Unit,
) {
    var saving by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        confirmButton = {
            TextButton(
                onClick = {
                    saving = true
                    errorMessage = null
                    onConfirm { error ->
                        saving = false
                        errorMessage = error
                    }
                },
                enabled = !saving,
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
        text = errorMessage?.let { message ->
            { Text(text = message, color = MaterialTheme.colorScheme.error) }
        },
    )
}
