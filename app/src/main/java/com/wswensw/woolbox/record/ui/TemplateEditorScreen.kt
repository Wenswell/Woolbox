package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.data.TemplateStepDraft
import com.wswensw.woolbox.record.model.TaskTemplate
import com.wswensw.woolbox.record.model.TemplateCadence
import com.wswensw.woolbox.record.model.validatedTemplateTitle
import com.wswensw.woolbox.ui.AppRouteScaffold
import com.wswensw.woolbox.ui.AppSection
import com.wswensw.woolbox.ui.AppStyle

@Composable
fun TemplateEditorScreen(
    template: TaskTemplate?,
    editing: Boolean,
    loading: Boolean,
    onBack: () -> Unit,
    onSave: (String, TemplateCadence, List<TemplateStepDraft>, (String?) -> Unit) -> Unit,
    onArchive: (((String?) -> Unit) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (editing && template == null) {
        AppRouteScaffold(title = stringResource(R.string.edit_template), onBack = onBack, modifier = modifier) {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Text(
                    stringResource(R.string.template_not_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(AppStyle.spacing.pageHorizontal),
                )
            }
        }
        return
    }

    var title by rememberSaveable(template?.id) { mutableStateOf(template?.title.orEmpty()) }
    var cadenceName by rememberSaveable(template?.id) {
        mutableStateOf(template?.cadence?.name ?: TemplateCadence.MANUAL.name)
    }
    var stepTitles by rememberSaveable(template?.id) {
        mutableStateOf(template?.steps?.map { it.title } ?: listOf(""))
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var confirmingArchive by rememberSaveable { mutableStateOf(false) }
    val invalidSteps = stringResource(R.string.each_template_step_title)

    fun submit() {
        val normalizedTitle = runCatching { validatedTemplateTitle(title) }.getOrElse {
            error = it.message
            return
        }
        val steps = runCatching {
            stepTitles.map { TemplateStepDraft(validatedTemplateTitle(it)) }
        }.getOrElse {
            error = invalidSteps
            return
        }
        saving = true
        error = null
        onSave(normalizedTitle, TemplateCadence.valueOf(cadenceName), steps) { result ->
            saving = false
            error = result
        }
    }

    AppRouteScaffold(
        title = stringResource(if (editing) R.string.edit_template else R.string.new_template),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (onArchive != null) {
                FilledTonalIconButton(onClick = { confirmingArchive = true }, enabled = !saving) {
                    Icon(Icons.Rounded.Archive, contentDescription = stringResource(R.string.archive_template))
                }
            }
            FilledTonalIconButton(
                onClick = ::submit,
                enabled = !saving && title.isNotBlank() && stepTitles.all(String::isNotBlank),
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = stringResource(if (saving) R.string.saving else R.string.action_save),
                )
            }
        },
    ) {
        AppSection(title = stringResource(R.string.template_title)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; error = null },
                enabled = !saving,
                label = { Text(stringResource(R.string.field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = AppStyle.spacing.pageHorizontal,
                    vertical = 8.dp,
                ),
            )
        }
        AppSection(title = stringResource(R.string.template_cadence)) {
            TemplateCadenceField(
                value = TemplateCadence.valueOf(cadenceName),
                onChange = { cadenceName = it.name },
                enabled = !saving,
            )
        }
        AppSection(title = stringResource(R.string.template_steps)) {
            stepTitles.forEachIndexed { index, stepTitle ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = AppStyle.spacing.pageHorizontal,
                        vertical = 8.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = stepTitle,
                        onValueChange = { updated ->
                            stepTitles = stepTitles.mapIndexed { itemIndex, value ->
                                if (itemIndex == index) updated else value
                            }
                            error = null
                        },
                        enabled = !saving,
                        label = { Text(stringResource(R.string.template_step_number, index + 1)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (stepTitles.size > 1) {
                        IconButton(
                            onClick = { stepTitles = stepTitles.moveItem(index, index - 1) },
                            enabled = !saving && index > 0,
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.move_template_step_up, index + 1),
                            )
                        }
                        IconButton(
                            onClick = { stepTitles = stepTitles.moveItem(index, index + 1) },
                            enabled = !saving && index < stepTitles.lastIndex,
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.move_template_step_down, index + 1),
                            )
                        }
                        FilledTonalIconButton(
                            onClick = {
                                stepTitles = stepTitles.filterIndexed { itemIndex, _ -> itemIndex != index }
                            },
                            enabled = !saving,
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.remove_template_step, index + 1),
                            )
                        }
                    }
                }
            }
            FilledTonalIconButton(
                onClick = { stepTitles = stepTitles + "" },
                enabled = !saving,
                modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_template_step))
            }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp),
            )
        }
    }

    if (confirmingArchive && onArchive != null) {
        var archiving by rememberSaveable { mutableStateOf(false) }
        var archiveError by rememberSaveable { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!archiving) confirmingArchive = false },
            title = { Text(stringResource(R.string.archive_template_question)) },
            text = {
                archiveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        archiving = true
                        archiveError = null
                        onArchive { result ->
                            archiving = false
                            archiveError = result
                            if (result == null) {
                                confirmingArchive = false
                                onBack()
                            }
                        }
                    },
                    enabled = !archiving,
                ) { Text(stringResource(R.string.archive_template)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingArchive = false }, enabled = !archiving) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
