package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.data.TodoStepDraft
import com.wswensw.woolbox.record.model.TaskRepeatMode
import com.wswensw.woolbox.record.model.validatedTaskTitle
import com.wswensw.woolbox.ui.AppRouteScaffold
import com.wswensw.woolbox.ui.AppSection
import com.wswensw.woolbox.ui.AppStyle

@Composable
fun TaskCreateScreen(
    onBack: () -> Unit,
    onCreate: (
        title: String,
        repeatMode: TaskRepeatMode,
        steps: List<TodoStepDraft>,
        onResult: (String?) -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var repeatModeName by rememberSaveable { mutableStateOf(TaskRepeatMode.ONCE.name) }
    var stepTitles by rememberSaveable { mutableStateOf(listOf("")) }
    var titleError by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val checkFieldsMessage = stringResource(R.string.error_check_fields)
    val eachSubtaskTitleMessage = stringResource(R.string.each_subtask_title)

    fun submit() {
        errorMessage = null
        val normalizedTitle = runCatching { validatedTaskTitle(title) }.getOrElse { error ->
            titleError = error.message
            errorMessage = checkFieldsMessage
            return
        }
        val normalizedSteps = runCatching {
            stepTitles.map { stepTitle -> TodoStepDraft(title = validatedTaskTitle(stepTitle)) }
        }.getOrElse { error ->
            errorMessage = error.message ?: eachSubtaskTitleMessage
            return
        }
        saving = true
        onCreate(normalizedTitle, TaskRepeatMode.valueOf(repeatModeName), normalizedSteps) { error ->
            saving = false
            errorMessage = error
        }
    }

    AppRouteScaffold(
        title = stringResource(R.string.new_task),
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(
                onClick = ::submit,
                enabled = !saving && title.isNotBlank() && stepTitles.all(String::isNotBlank),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(if (saving) R.string.saving else R.string.action_save),
                )
            }
        },
    ) {
        AppSection(title = stringResource(R.string.task_title)) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleError = null
                },
                enabled = !saving,
                label = { Text(stringResource(R.string.field_title)) },
                isError = titleError != null,
                supportingText = titleError?.let { message -> { Text(message) } },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp),
            )
        }
        AppSection(title = stringResource(R.string.task_repeat)) {
            TaskRepeatModeField(
                value = TaskRepeatMode.valueOf(repeatModeName),
                onChange = { repeatModeName = it.name },
                enabled = !saving,
            )
        }
        AppSection(title = stringResource(R.string.subtasks)) {
            stepTitles.forEachIndexed { index, stepTitle ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = stepTitle,
                        onValueChange = { updated ->
                            stepTitles = stepTitles.mapIndexed { itemIndex, value ->
                                if (itemIndex == index) updated else value
                            }
                        },
                        enabled = !saving,
                        label = { Text(stringResource(R.string.subtask_number, index + 1)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (stepTitles.size > 1) {
                        IconButton(
                            onClick = {
                                stepTitles = stepTitles.moveItem(index, index - 1)
                            },
                            enabled = !saving && index > 0,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.move_subtask_up, index + 1),
                            )
                        }
                        IconButton(
                            onClick = {
                                stepTitles = stepTitles.moveItem(index, index + 1)
                            },
                            enabled = !saving && index < stepTitles.lastIndex,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.move_subtask_down, index + 1),
                            )
                        }
                        FilledTonalIconButton(
                            onClick = {
                                stepTitles = stepTitles.filterIndexed { itemIndex, _ -> itemIndex != index }
                            },
                            enabled = !saving,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.remove_subtask, index + 1),
                            )
                        }
                    }
                }
            }
            FilledTonalIconButton(
                onClick = {
                    stepTitles = stepTitles + ""
                },
                enabled = !saving,
                modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.add_subtask),
                )
            }
        }
        errorMessage?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp),
            )
        }
    }
}
