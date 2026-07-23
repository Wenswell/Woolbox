package com.wswensw.template.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wswensw.template.record.model.DEFAULT_NUMBER_UNIT
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordItemDraft
import com.wswensw.template.record.model.RecordMode
import com.wswensw.template.record.model.RecordValueType
import com.wswensw.template.ui.TemplateRouteScaffold
import com.wswensw.template.ui.TemplateSection
import com.wswensw.template.ui.TemplateStyle

@Composable
fun MeasurementItemFormScreen(
    item: RecordItem?,
    editing: Boolean,
    loading: Boolean,
    fieldsLocked: Boolean,
    onBack: () -> Unit,
    onSave: (RecordItemDraft, (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (editing && item == null) {
        TemplateRouteScaffold(
            title = "Edit measurement",
            onBack = onBack,
            modifier = modifier,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(TemplateStyle.spacing.pageHorizontal),
                )
            } else {
                Text(
                    text = "Record item not found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(TemplateStyle.spacing.pageHorizontal),
                )
            }
        }
        return
    }

    var name by rememberSaveable(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var description by rememberSaveable(item?.id) { mutableStateOf(item?.description.orEmpty()) }
    var defaultValue by rememberSaveable(item?.id) {
        mutableStateOf(
            if (item == null) "" else item.defaultValue?.let(::formatValue).orEmpty(),
        )
    }
    var stepValue by rememberSaveable(item?.id) {
        mutableStateOf(item?.stepValue?.let(::formatValue) ?: "1")
    }
    var unit by rememberSaveable(item?.id) { mutableStateOf(item?.unit ?: DEFAULT_NUMBER_UNIT) }
    var priority by rememberSaveable(item?.id) { mutableStateOf(item?.priority?.toString() ?: "0") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultError by rememberSaveable { mutableStateOf<String?>(null) }
    var stepError by rememberSaveable { mutableStateOf<String?>(null) }
    var unitError by rememberSaveable { mutableStateOf<String?>(null) }
    var priorityError by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

    fun clearFieldErrors() {
        nameError = null
        defaultError = null
        stepError = null
        unitError = null
        priorityError = null
    }

    fun validateFields(): Boolean {
        clearFieldErrors()
        var valid = true
        if (name.isBlank()) {
            nameError = "Name is required."
            valid = false
        }
        if (unit.isBlank()) {
            unitError = "Unit is required."
            valid = false
        }
        runCatching { parseOptionalFinite(defaultValue, "Default value") }
            .onFailure {
                defaultError = it.message
                valid = false
            }
        runCatching { parsePositive(stepValue, "Step") }
            .onFailure {
                stepError = it.message
                valid = false
            }
        if (priority.toIntOrNull() == null) {
            priorityError = "Priority must be an integer."
            valid = false
        }
        return valid
    }

    val formValid = name.isNotBlank() &&
        unit.isNotBlank() &&
        runCatching { parseOptionalFinite(defaultValue, "Default value") }.isSuccess &&
        runCatching { parsePositive(stepValue, "Step") }.isSuccess &&
        priority.toIntOrNull() != null

    fun submit() {
        errorMessage = null
        if (!validateFields()) {
            errorMessage = "Check the highlighted fields."
            return
        }
        val draft = RecordItemDraft(
            name = name,
            description = description,
            mode = RecordMode.MEASUREMENT,
            valueType = RecordValueType.NUMBER,
            defaultValue = parseOptionalFinite(defaultValue, "Default value"),
            stepValue = parsePositive(stepValue, "Step"),
            unit = unit,
            goalValue = null,
            goalPeriod = null,
            priority = checkNotNull(priority.toIntOrNull()),
            windows = emptyList(),
        )
        saving = true
        onSave(draft) { error ->
            saving = false
            errorMessage = error
        }
    }

    TemplateRouteScaffold(
        title = if (editing) "Edit measurement" else "New measurement",
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(
                onClick = ::submit,
                enabled = !saving && formValid,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = if (saving) "Saving" else "Save",
                )
            }
        },
    ) {
        if (name.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TemplateStyle.spacing.pageHorizontal)
                    .padding(bottom = 8.dp),
                shape = TemplateStyle.cardShape,
                color = recordItemColors(name).container,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RecordItemInitial(name = name, size = 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayRecordName(name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = if (editing) "Editing measurement" else "New measurement",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        TemplateSection(title = "Details") {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = null
                },
                enabled = !saving,
                label = { Text("Name") },
                isError = nameError != null,
                supportingText = nameError?.let { message ->
                    { Text(message) }
                },
                singleLine = true,
                modifier = Modifier.formField(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                enabled = !saving,
                label = { Text("Description") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.formField(),
            )
        }

        TemplateSection(title = "Measurement") {
            Row(
                modifier = Modifier.formField(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = {
                        defaultValue = it
                        defaultError = null
                    },
                    enabled = !saving,
                    label = { Text("Default (optional)") },
                    isError = defaultError != null,
                    supportingText = defaultError?.let { message ->
                        { Text(message) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = stepValue,
                    onValueChange = {
                        stepValue = it
                        stepError = null
                    },
                    enabled = !saving,
                    label = { Text("Step") },
                    isError = stepError != null,
                    supportingText = stepError?.let { message ->
                        { Text(message) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = unit,
                onValueChange = {
                    unit = it
                    unitError = null
                },
                enabled = !fieldsLocked && !saving,
                label = { Text("Unit") },
                isError = unitError != null,
                supportingText = unitError?.let { message ->
                    { Text(message) }
                },
                singleLine = true,
                modifier = Modifier.formField(),
            )
            OutlinedTextField(
                value = priority,
                onValueChange = {
                    priority = it
                    priorityError = null
                },
                enabled = !saving,
                label = { Text("Priority") },
                isError = priorityError != null,
                supportingText = {
                    Text(priorityError ?: "Higher values appear first.")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.formField(),
            )
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.formField(),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
