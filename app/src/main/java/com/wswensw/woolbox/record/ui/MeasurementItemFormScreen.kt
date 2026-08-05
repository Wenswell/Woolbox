package com.wswensw.woolbox.record.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.DEFAULT_NUMBER_UNIT
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordItemDraft
import com.wswensw.woolbox.record.model.RecordMode
import com.wswensw.woolbox.record.model.RecordValueType
import com.wswensw.woolbox.record.model.measurementStepCount
import com.wswensw.woolbox.ui.AppRouteScaffold
import com.wswensw.woolbox.ui.AppSection
import com.wswensw.woolbox.ui.AppStyle
import java.util.Locale

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
        AppRouteScaffold(
            title = stringResource(R.string.edit_measurement),
            onBack = onBack,
            modifier = modifier,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(AppStyle.spacing.pageHorizontal),
                )
            } else {
                Text(
                    text = stringResource(R.string.record_item_not_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(AppStyle.spacing.pageHorizontal),
                )
            }
        }
        return
    }

    var name by rememberSaveable(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var description by rememberSaveable(item?.id) { mutableStateOf(item?.description.orEmpty()) }
    var minValue by rememberSaveable(item?.id) { mutableStateOf(item?.minValue?.let(::formatValue).orEmpty()) }
    var maxValue by rememberSaveable(item?.id) { mutableStateOf(item?.maxValue?.let(::formatValue).orEmpty()) }
    var stepValue by rememberSaveable(item?.id) {
        mutableStateOf(item?.stepValue?.let(::formatValue) ?: "1")
    }
    var unit by rememberSaveable(item?.id) { mutableStateOf(item?.unit ?: DEFAULT_NUMBER_UNIT) }
    var priority by rememberSaveable(item?.id) { mutableStateOf(item?.priority?.toString() ?: "0") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var minError by rememberSaveable { mutableStateOf<String?>(null) }
    var maxError by rememberSaveable { mutableStateOf<String?>(null) }
    var stepError by rememberSaveable { mutableStateOf<String?>(null) }
    var unitError by rememberSaveable { mutableStateOf<String?>(null) }
    var priorityError by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val nameRequiredMessage = stringResource(R.string.error_name_required)
    val unitRequiredMessage = stringResource(R.string.error_unit_required)
    val priorityIntegerMessage = stringResource(R.string.error_priority_integer)
    val checkFieldsMessage = stringResource(R.string.error_check_fields)
    val finiteFieldTemplate = stringResource(R.string.error_finite_field)
    val requiredFieldTemplate = stringResource(R.string.error_required_field)
    val stepPositiveTemplate = stringResource(R.string.error_positive_field)
    val minimumFieldLabel = stringResource(R.string.field_minimum)
    val maximumFieldLabel = stringResource(R.string.field_maximum)
    val stepFieldLabel = stringResource(R.string.field_step)

    fun clearFieldErrors() {
        nameError = null
        minError = null
        maxError = null
        stepError = null
        unitError = null
        priorityError = null
    }

    fun validateFields(): Boolean {
        clearFieldErrors()
        var valid = true
        if (name.isBlank()) {
            nameError = nameRequiredMessage
            valid = false
        }
        if (unit.isBlank()) {
            unitError = unitRequiredMessage
            valid = false
        }
        runCatching { parseRequiredFinite(minValue, "Minimum") }
            .onFailure {
                minError = String.format(
                    Locale.getDefault(),
                    if (minValue.isBlank()) requiredFieldTemplate else finiteFieldTemplate,
                    minimumFieldLabel,
                )
                valid = false
            }
        runCatching { parseRequiredFinite(maxValue, "Maximum") }
            .onFailure {
                maxError = String.format(
                    Locale.getDefault(),
                    if (maxValue.isBlank()) requiredFieldTemplate else finiteFieldTemplate,
                    maximumFieldLabel,
                )
                valid = false
            }
        runCatching { parsePositive(stepValue, "Step") }
            .onFailure {
                stepError = String.format(Locale.getDefault(), stepPositiveTemplate, stepFieldLabel)
                valid = false
            }
        runCatching {
            measurementStepCount(
                minValue = parseRequiredFinite(minValue, "Minimum"),
                maxValue = parseRequiredFinite(maxValue, "Maximum"),
                stepValue = parsePositive(stepValue, "Step"),
            )
        }.onFailure {
            val message = it.message
            if (minError == null && maxError == null && stepError == null) {
                maxError = message
            }
            valid = false
        }
        if (priority.toIntOrNull() == null) {
            priorityError = priorityIntegerMessage
            valid = false
        }
        return valid
    }

    val formValid = name.isNotBlank() &&
        unit.isNotBlank() &&
        runCatching {
            measurementStepCount(
                minValue = parseRequiredFinite(minValue, "Minimum"),
                maxValue = parseRequiredFinite(maxValue, "Maximum"),
                stepValue = parsePositive(stepValue, "Step"),
            )
        }.isSuccess &&
        priority.toIntOrNull() != null

    fun submit() {
        errorMessage = null
        if (!validateFields()) {
            errorMessage = checkFieldsMessage
            return
        }
        val draft = RecordItemDraft(
            name = name,
            description = description,
            mode = RecordMode.MEASUREMENT,
            valueType = RecordValueType.NUMBER,
            defaultValue = null,
            stepValue = parsePositive(stepValue, "Step"),
            unit = unit,
            goalValue = null,
            goalPeriod = null,
            priority = checkNotNull(priority.toIntOrNull()),
            windows = emptyList(),
            minValue = parseRequiredFinite(minValue, "Minimum"),
            maxValue = parseRequiredFinite(maxValue, "Maximum"),
        )
        saving = true
        onSave(draft) { error ->
            saving = false
            errorMessage = error
        }
    }

    AppRouteScaffold(
        title = stringResource(if (editing) R.string.edit_measurement else R.string.new_measurement),
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(
                onClick = ::submit,
                enabled = !saving && formValid,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(if (saving) R.string.saving else R.string.action_save),
                )
            }
        },
    ) {
        if (name.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppStyle.spacing.pageHorizontal)
                    .padding(bottom = 8.dp),
                shape = AppStyle.cardShape,
                color = recordProgressColors(progress = 0f).container,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RecordItemInitial(name = name, size = 48.dp, colors = recordProgressColors(progress = 0f))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayRecordName(name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(
                                if (editing) R.string.editing_measurement else R.string.new_measurement,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        AppSection(title = stringResource(R.string.record_details)) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = null
                },
                enabled = !saving,
                label = { Text(stringResource(R.string.field_name)) },
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
                label = { Text(stringResource(R.string.field_description)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.formField(),
            )
        }

        AppSection(title = stringResource(R.string.record_measurement)) {
            Row(
                modifier = Modifier.formField(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = minValue,
                    onValueChange = {
                        minValue = it
                        minError = null
                    },
                    enabled = !fieldsLocked && !saving,
                    label = { Text(stringResource(R.string.field_minimum)) },
                    isError = minError != null,
                    supportingText = minError?.let { message ->
                        { Text(message) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = maxValue,
                    onValueChange = {
                        maxValue = it
                        maxError = null
                    },
                    enabled = !fieldsLocked && !saving,
                    label = { Text(stringResource(R.string.field_maximum)) },
                    isError = maxError != null,
                    supportingText = maxError?.let { message ->
                        { Text(message) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = stepValue,
                onValueChange = {
                    stepValue = it
                    stepError = null
                },
                enabled = !fieldsLocked && !saving,
                label = { Text(stringResource(R.string.field_step)) },
                isError = stepError != null,
                supportingText = stepError?.let { message ->
                    { Text(message) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.formField(),
            )
            OutlinedTextField(
                value = unit,
                onValueChange = {
                    unit = it
                    unitError = null
                },
                enabled = !fieldsLocked && !saving,
                label = { Text(stringResource(R.string.field_unit)) },
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
                label = { Text(stringResource(R.string.field_priority)) },
                isError = priorityError != null,
                supportingText = {
                    Text(priorityError ?: stringResource(R.string.higher_values_first))
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
