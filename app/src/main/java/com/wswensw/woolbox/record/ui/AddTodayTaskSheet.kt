package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.data.TemplateStepDraft
import com.wswensw.woolbox.record.model.TaskTemplate
import com.wswensw.woolbox.record.model.TodayTask
import com.wswensw.woolbox.record.model.validatedTemplateTitle
import com.wswensw.woolbox.ui.AppStyle

private enum class AddTaskTab { RECOMMENDED, TEMPLATES, NEW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTodayTaskSheet(
    dailySuggestions: List<TaskTemplate>,
    unfinishedTasks: List<TodayTask>,
    templates: List<TaskTemplate>,
    onDismiss: () -> Unit,
    onAddTemplate: (Long, (String?) -> Unit) -> Unit,
    onContinueTask: (Long, (String?) -> Unit) -> Unit,
    onCreateTask: (String, List<TemplateStepDraft>, (String?) -> Unit) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val tabs = AddTaskTab.entries

    fun finishAction(action: ((String?) -> Unit) -> Unit) {
        busy = true
        error = null
        action { result ->
            busy = false
            error = result
            if (result == null) onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Text(
                stringResource(R.string.add_task),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp),
            )
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index; error = null },
                        text = {
                            Text(
                                stringResource(
                                    when (tab) {
                                        AddTaskTab.RECOMMENDED -> R.string.recommended
                                        AddTaskTab.TEMPLATES -> R.string.records_templates
                                        AddTaskTab.NEW -> R.string.new_task
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            when (tabs[selectedTab]) {
                AddTaskTab.RECOMMENDED -> RecommendedTasks(
                    dailySuggestions = dailySuggestions,
                    unfinishedTasks = unfinishedTasks,
                    enabled = !busy,
                    onAddTemplate = { id -> finishAction { done -> onAddTemplate(id, done) } },
                    onContinueTask = { id -> finishAction { done -> onContinueTask(id, done) } },
                )
                AddTaskTab.TEMPLATES -> CandidateList(
                    values = templates,
                    key = TaskTemplate::id,
                    title = TaskTemplate::title,
                    enabled = !busy,
                    onClick = { finishAction { done -> onAddTemplate(it.id, done) } },
                )
                AddTaskTab.NEW -> NewTodayTaskForm(
                    enabled = !busy,
                    onCreate = { title, steps ->
                        finishAction { done -> onCreateTask(title, steps, done) }
                    },
                )
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RecommendedTasks(
    dailySuggestions: List<TaskTemplate>,
    unfinishedTasks: List<TodayTask>,
    enabled: Boolean,
    onAddTemplate: (Long) -> Unit,
    onContinueTask: (Long) -> Unit,
) {
    if (dailySuggestions.isEmpty() && unfinishedTasks.isEmpty()) {
        Text(
            stringResource(R.string.no_recommendations),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(AppStyle.spacing.pageHorizontal, 24.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
        if (unfinishedTasks.isNotEmpty()) {
            item { CandidateHeading(stringResource(R.string.unfinished_yesterday)) }
            items(unfinishedTasks, key = { "unfinished-${it.id}" }) { task ->
                CandidateRow(task.title, enabled) { onContinueTask(task.id) }
            }
        }
        if (dailySuggestions.isNotEmpty()) {
            item { CandidateHeading(stringResource(R.string.daily_recommendations)) }
            items(dailySuggestions, key = { "daily-${it.id}" }) { template ->
                CandidateRow(template.title, enabled) { onAddTemplate(template.id) }
            }
        }
    }
}

@Composable
private fun <T> CandidateList(
    values: List<T>,
    key: (T) -> Any,
    title: (T) -> String,
    enabled: Boolean,
    onClick: (T) -> Unit,
) {
    if (values.isEmpty()) {
        Text(
            stringResource(R.string.no_templates),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(AppStyle.spacing.pageHorizontal, 24.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
        items(values, key = key) { value -> CandidateRow(title(value), enabled) { onClick(value) } }
    }
}

@Composable
private fun CandidateHeading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 12.dp),
    )
}

@Composable
private fun CandidateRow(title: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .padding(horizontal = AppStyle.spacing.pageHorizontal),
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Add, contentDescription = null)
    }
}

@Composable
private fun NewTodayTaskForm(
    enabled: Boolean,
    onCreate: (String, List<TemplateStepDraft>) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf(listOf("")) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val stepError = stringResource(R.string.each_template_step_title)

    Column(
        modifier = Modifier.fillMaxWidth().padding(AppStyle.spacing.pageHorizontal),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; validationError = null },
            enabled = enabled,
            label = { Text(stringResource(R.string.task_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        steps.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { updated ->
                        steps = steps.mapIndexed { itemIndex, item -> if (itemIndex == index) updated else item }
                        validationError = null
                    },
                    enabled = enabled,
                    label = { Text(stringResource(R.string.template_step_number, index + 1)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { steps = steps.filterIndexed { itemIndex, _ -> itemIndex != index } },
                    enabled = enabled,
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.remove_template_step, index + 1),
                    )
                }
            }
        }
        TextButton(onClick = { steps = steps + "" }, enabled = enabled) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.add_checklist_step))
        }
        Button(
            onClick = {
                val normalizedTitle = runCatching { validatedTemplateTitle(title) }.getOrElse {
                    validationError = it.message
                    return@Button
                }
                val drafts = runCatching {
                    steps.map { TemplateStepDraft(validatedTemplateTitle(it)) }
                }.getOrElse {
                    validationError = stepError
                    return@Button
                }
                if (drafts.isEmpty()) {
                    validationError = stepError
                    return@Button
                }
                onCreate(normalizedTitle, drafts)
            },
            enabled = enabled && title.isNotBlank() && steps.isNotEmpty() && steps.all(String::isNotBlank),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_task))
        }
        validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
