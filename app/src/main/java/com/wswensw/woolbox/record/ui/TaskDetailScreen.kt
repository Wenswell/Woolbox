package com.wswensw.woolbox.record.ui

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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.RecordWindow
import com.wswensw.woolbox.record.model.TodoStep
import com.wswensw.woolbox.record.model.TodoTask
import com.wswensw.woolbox.record.model.validatedTaskTitle
import com.wswensw.woolbox.record.model.validatedWindows
import com.wswensw.woolbox.ui.AppRouteScaffold
import com.wswensw.woolbox.ui.AppSection
import com.wswensw.woolbox.ui.AppStyle
import java.time.LocalDate

@Composable
fun TaskDetailScreen(
    task: TodoTask?,
    loading: Boolean,
    today: LocalDate,
    onBack: () -> Unit,
    onSave: (String, LocalDate?, (String?) -> Unit) -> Unit,
    onCreateStep: (String, List<RecordWindow>, (String?) -> Unit) -> Unit,
    onUpdateStep: (TodoStep, String, List<RecordWindow>, (String?) -> Unit) -> Unit,
    onToggleStep: (TodoStep, Boolean) -> Unit,
    onDeleteStep: (TodoStep, (String?) -> Unit) -> Unit,
    onReorderSteps: (List<Long>, (String?) -> Unit) -> Unit,
    onDeleteTask: ((String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (task == null) {
        AppRouteScaffold(title = stringResource(R.string.edit_task), onBack = onBack, modifier = modifier) {
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
                    text = stringResource(R.string.task_not_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(AppStyle.spacing.pageHorizontal),
                )
            }
        }
        return
    }

    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var plannedDateValue by rememberSaveable(task.id) { mutableStateOf(task.plannedDate?.toString()) }
    val plannedDate = plannedDateValue?.let(LocalDate::parse)
    var titleError by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var savingTitle by rememberSaveable { mutableStateOf(false) }
    var creatingStep by rememberSaveable { mutableStateOf(false) }
    var editingStepId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingStepId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingTask by rememberSaveable { mutableStateOf(false) }
    var reordering by rememberSaveable { mutableStateOf(false) }
    val checkFieldsMessage = stringResource(R.string.error_check_fields)

    fun moveStep(fromIndex: Int, toIndex: Int) {
        if (reordering || toIndex !in task.steps.indices) return
        val stepIds = task.steps.moveItem(fromIndex, toIndex).map(TodoStep::id)
        reordering = true
        errorMessage = null
        onReorderSteps(stepIds) { error ->
            reordering = false
            errorMessage = error
        }
    }

    fun saveTask() {
        errorMessage = null
        val normalizedTitle = runCatching { validatedTaskTitle(title) }.getOrElse { error ->
            titleError = error.message
            errorMessage = checkFieldsMessage
            return
        }
        if (normalizedTitle == task.title && plannedDate == task.plannedDate) return
        savingTitle = true
        onSave(normalizedTitle, plannedDate) { error ->
            savingTitle = false
            errorMessage = error
        }
    }

    val titleDirty = title.trim() != task.title || plannedDate != task.plannedDate
    val hasSuggestedTimes = task.steps.any { it.windows.isNotEmpty() }
    // Task date and subtask windows are separate writes. A new date must be
    // saved before the server can accept a window for a previously Later task.
    val canEditSuggestedTimes = task.plannedDate != null && plannedDate != null
    AppRouteScaffold(
        title = stringResource(R.string.edit_task),
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(onClick = { deletingTask = true }) {
                Icon(imageVector = Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete_task))
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(
                onClick = ::saveTask,
                enabled = !savingTitle && titleDirty && title.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(if (savingTitle) R.string.saving else R.string.save_title),
                )
            }
        },
    ) {
        AppSection(title = stringResource(R.string.field_title)) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleError = null
                },
                enabled = !savingTitle,
                label = { Text(stringResource(R.string.field_title)) },
                isError = titleError != null,
                supportingText = titleError?.let { message -> { Text(message) } },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp),
            )
            TaskScheduleFields(
                plannedDate = plannedDate,
                today = today,
                hasSuggestedTimes = hasSuggestedTimes,
                enabled = !savingTitle,
                onPlannedDateChange = { selectedDate -> plannedDateValue = selectedDate?.toString() },
            )
        }

        AppSection(title = stringResource(R.string.subtasks)) {
            if (!canEditSuggestedTimes) {
                Text(
                    text = stringResource(R.string.save_task_date_first),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp),
                )
            }
            task.steps.forEachIndexed { index, step ->
                TaskStepRow(
                    step = step,
                    onToggle = { completed -> onToggleStep(step, completed) },
                    onMoveUp = { moveStep(index, index - 1) },
                    onMoveDown = { moveStep(index, index + 1) },
                    onEdit = { editingStepId = step.id },
                    onDelete = { deletingStepId = step.id },
                    canMoveUp = !reordering && index > 0,
                    canMoveDown = !reordering && index < task.steps.lastIndex,
                    canDelete = !reordering && task.steps.size > 1,
                )
                if (index < task.steps.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalIconButton(onClick = { creatingStep = true }) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = stringResource(R.string.add_subtask))
                }
            }
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (creatingStep) {
        TaskStepDialog(
            title = stringResource(R.string.new_subtask),
            initialTitle = "",
            initialWindows = emptyList(),
            showSuggestedTimes = canEditSuggestedTimes,
            onDismiss = { creatingStep = false },
            onSave = { value, stepWindows, onResult ->
                onCreateStep(value, stepWindows) { error ->
                    onResult(error)
                    if (error == null) creatingStep = false
                }
            },
        )
    }
    val editingStep = task.steps.firstOrNull { it.id == editingStepId }
    if (editingStep != null) {
        TaskStepDialog(
            title = stringResource(R.string.edit_subtask),
            initialTitle = editingStep.title,
            initialWindows = editingStep.windows,
            showSuggestedTimes = canEditSuggestedTimes,
            onDismiss = { editingStepId = null },
            onSave = { value, stepWindows, onResult ->
                onUpdateStep(editingStep, value, stepWindows) { error ->
                    onResult(error)
                    if (error == null) editingStepId = null
                }
            },
        )
    }
    val deletingStep = task.steps.firstOrNull { it.id == deletingStepId }
    if (deletingStep != null) {
        DeleteConfirmationDialog(
            title = stringResource(R.string.delete_subtask_question),
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
            title = stringResource(R.string.delete_task_question),
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
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp),
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
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(imageVector = Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.move_subtask_up_generic))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(imageVector = Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.move_subtask_down_generic))
        }
        IconButton(onClick = onEdit) {
            Icon(imageVector = Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit_subtask))
        }
        IconButton(onClick = onDelete, enabled = canDelete) {
            Icon(imageVector = Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete_subtask))
        }
    }
}

/**
 * Title plus suggested times for one subtask. Windows live on the subtask
 * because that is the thing scheduled and completed.
 */
@Composable
private fun TaskStepDialog(
    title: String,
    initialTitle: String,
    initialWindows: List<RecordWindow>,
    showSuggestedTimes: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, List<RecordWindow>, (String?) -> Unit) -> Unit,
) {
    var value by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var windows by rememberSaveable(initialTitle) {
        mutableStateOf(initialWindows.map { "${it.startMinute},${it.endMinute}" })
    }
    var timeEditTarget by remember { mutableStateOf<TimeEditTarget?>(null) }
    val parsedWindows = windows.map(::decodeWindow)
    var valueError by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val invalidSuggestedTimesMessage = stringResource(R.string.invalid_suggested_times)
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
                    label = { Text(stringResource(R.string.field_title)) },
                    isError = valueError != null,
                    supportingText = valueError?.let { message -> { Text(message) } },
                    singleLine = true,
                )
                if (showSuggestedTimes) {
                    Text(
                        text = stringResource(R.string.suggested_time),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    SuggestedTimeFields(
                        windows = parsedWindows,
                        enabled = !saving,
                        onEditTime = { timeEditTarget = it },
                        onRemove = { index ->
                            windows = windows.toMutableList().also { it.removeAt(index) }
                        },
                        onAdd = { windows = windows + nextSuggestedWindow(parsedWindows).encode() },
                        horizontalPadding = 0.dp,
                        summary = taskSuggestedTimeSummary(parsedWindows),
                    )
                }
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
                    val normalizedWindows = runCatching {
                        validatedWindows(if (showSuggestedTimes) parsedWindows else emptyList())
                    }.getOrElse { error ->
                        errorMessage = error.message ?: invalidSuggestedTimesMessage
                        return@TextButton
                    }
                    saving = true
                    errorMessage = null
                    onSave(normalized, normalizedWindows) { error ->
                        saving = false
                        errorMessage = error
                    }
                },
                enabled = !saving,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    timeEditTarget?.let { target ->
        val window = parsedWindows.getOrNull(target.index) ?: return@let
        RecordTimePickerDialog(
            initialMinute = if (target.start) window.startMinute else window.endMinute,
            onDismiss = { timeEditTarget = null },
            onConfirm = { minute ->
                windows = parsedWindows.withEditedTime(target, minute).map { it.encode() }
                timeEditTarget = null
            },
        )
    }
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
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.action_cancel)) }
        },
        text = errorMessage?.let { message ->
            { Text(text = message, color = MaterialTheme.colorScheme.error) }
        },
    )
}
