package com.wswensw.template.record.ui

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
import com.wswensw.template.record.model.validatedTaskTitle
import com.wswensw.template.ui.TemplateRouteScaffold
import com.wswensw.template.ui.TemplateSection
import com.wswensw.template.ui.TemplateStyle

@Composable
fun TaskCreateScreen(
    onBack: () -> Unit,
    onCreate: (title: String, stepTitles: List<String>, onResult: (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var stepTitles by rememberSaveable { mutableStateOf(listOf("")) }
    var titleError by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        errorMessage = null
        val normalizedTitle = runCatching { validatedTaskTitle(title) }.getOrElse { error ->
            titleError = error.message
            errorMessage = "Check the highlighted field."
            return
        }
        val normalizedSteps = runCatching { stepTitles.map(::validatedTaskTitle) }.getOrElse { error ->
            errorMessage = error.message ?: "Each subtask needs a title."
            return
        }
        saving = true
        onCreate(normalizedTitle, normalizedSteps) { error ->
            saving = false
            errorMessage = error
        }
    }

    TemplateRouteScaffold(
        title = "New task",
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(
                onClick = ::submit,
                enabled = !saving && title.isNotBlank() && stepTitles.all(String::isNotBlank),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = if (saving) "Saving" else "Save",
                )
            }
        },
    ) {
        TemplateSection(title = "Task") {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleError = null
                },
                enabled = !saving,
                label = { Text("Title") },
                isError = titleError != null,
                supportingText = titleError?.let { message -> { Text(message) } },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
            )
        }
        TemplateSection(title = "Subtasks") {
            stepTitles.forEachIndexed { index, stepTitle ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
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
                        label = { Text("Subtask ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (stepTitles.size > 1) {
                        IconButton(
                            onClick = { stepTitles = stepTitles.moveItem(index, index - 1) },
                            enabled = !saving && index > 0,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Move subtask ${index + 1} up",
                            )
                        }
                        IconButton(
                            onClick = { stepTitles = stepTitles.moveItem(index, index + 1) },
                            enabled = !saving && index < stepTitles.lastIndex,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Move subtask ${index + 1} down",
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
                                contentDescription = "Remove subtask ${index + 1}",
                            )
                        }
                    }
                }
            }
            FilledTonalIconButton(
                onClick = { stepTitles = stepTitles + "" },
                enabled = !saving,
                modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add subtask",
                )
            }
        }
        errorMessage?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
            )
        }
    }
}
