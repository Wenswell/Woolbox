package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.data.TodoStepDraft
import com.wswensw.woolbox.record.model.validatedTaskTitle
import com.wswensw.woolbox.record.model.validatedWindows
import com.wswensw.woolbox.ui.AppRouteScaffold
import com.wswensw.woolbox.ui.AppSection
import com.wswensw.woolbox.ui.AppStyle
import java.time.LocalDate

@Composable
fun TaskCreateScreen(
    today: LocalDate,
    onBack: () -> Unit,
    onCreate: (
        title: String,
        steps: List<TodoStepDraft>,
        plannedDate: LocalDate?,
        onResult: (String?) -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var plannedDateValue by rememberSaveable { mutableStateOf<String?>(today.toString()) }
    val plannedDate = plannedDateValue?.let(LocalDate::parse)
    var stepTitles by rememberSaveable { mutableStateOf(listOf("")) }
    // One encoded window list per subtask position, moved and removed with it.
    var stepWindows by rememberSaveable { mutableStateOf(listOf(emptyList<String>())) }
    var windowEditStep by remember { mutableStateOf<Int?>(null) }
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
            stepTitles.mapIndexed { index, stepTitle ->
                TodoStepDraft(
                    title = validatedTaskTitle(stepTitle),
                    windows = validatedWindows(
                        stepWindows.getOrElse(index) { emptyList() }.map(::decodeWindow),
                    ),
                )
            }
        }.getOrElse { error ->
            errorMessage = error.message ?: eachSubtaskTitleMessage
            return
        }
        saving = true
        onCreate(normalizedTitle, normalizedSteps, plannedDate) { error ->
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
            TaskScheduleFields(
                plannedDate = plannedDate,
                today = today,
                hasSuggestedTimes = stepWindows.any { windows -> windows.isNotEmpty() },
                enabled = !saving,
                onPlannedDateChange = { selectedDate -> plannedDateValue = selectedDate?.toString() },
            )
        }
        AppSection(title = stringResource(R.string.subtasks)) {
            if (plannedDate == null) {
                Text(
                    text = stringResource(R.string.set_task_date_for_times),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp),
                )
            }
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
                    if (plannedDate != null) {
                        IconButton(
                            onClick = { windowEditStep = index },
                            enabled = !saving,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = stringResource(R.string.suggested_times_for_subtask, index + 1),
                                tint = if (stepWindows.getOrElse(index) { emptyList() }.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                    if (stepTitles.size > 1) {
                        IconButton(
                            onClick = {
                                stepTitles = stepTitles.moveItem(index, index - 1)
                                stepWindows = stepWindows.moveItem(index, index - 1)
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
                                stepWindows = stepWindows.moveItem(index, index + 1)
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
                                stepWindows = stepWindows.filterIndexed { itemIndex, _ -> itemIndex != index }
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
                    stepWindows = stepWindows + listOf(emptyList())
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

    windowEditStep?.takeIf { plannedDate != null }?.let { index ->
        val encoded = stepWindows.getOrElse(index) { emptyList() }
        SuggestedTimeDialog(
            stepLabel = stepTitles.getOrNull(index)?.trim()?.takeIf(String::isNotEmpty)
                ?: stringResource(R.string.subtask_number, index + 1),
            initialWindows = encoded.map(::decodeWindow),
            onDismiss = { windowEditStep = null },
            onConfirm = { updated ->
                stepWindows = stepWindows.mapIndexed { itemIndex, value ->
                    if (itemIndex == index) updated.map { it.encode() } else value
                }
                windowEditStep = null
            },
        )
    }
}
