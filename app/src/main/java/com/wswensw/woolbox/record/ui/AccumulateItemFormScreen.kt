package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.DEFAULT_DURATION_UNIT
import com.wswensw.woolbox.record.model.DEFAULT_NUMBER_UNIT
import com.wswensw.woolbox.record.model.DURATION_UNITS
import com.wswensw.woolbox.record.model.GoalPeriod
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordItemDraft
import com.wswensw.woolbox.record.model.RecordMode
import com.wswensw.woolbox.record.model.RecordValueType
import com.wswensw.woolbox.ui.AppRouteScaffold
import com.wswensw.woolbox.ui.AppSection
import com.wswensw.woolbox.ui.AppStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccumulateItemFormScreen(
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
            title = stringResource(R.string.edit_accumulate),
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
    var valueTypeName by rememberSaveable(item?.id) {
        mutableStateOf((item?.valueType ?: RecordValueType.NUMBER).name)
    }
    var defaultValue by rememberSaveable(item?.id) {
        mutableStateOf(
            if (item == null) "1" else item.defaultValue?.let(::formatValue).orEmpty(),
        )
    }
    var stepValue by rememberSaveable(item?.id) {
        mutableStateOf(item?.stepValue?.let(::formatValue) ?: "1")
    }
    var unit by rememberSaveable(item?.id) { mutableStateOf(item?.unit ?: DEFAULT_NUMBER_UNIT) }
    var goalEnabled by rememberSaveable(item?.id) { mutableStateOf(item?.goalValue != null) }
    var goalValue by rememberSaveable(item?.id) {
        mutableStateOf(item?.goalValue?.let(::formatValue).orEmpty())
    }
    var goalPeriodName by rememberSaveable(item?.id) {
        mutableStateOf((item?.goalPeriod ?: GoalPeriod.DAY).name)
    }
    var priority by rememberSaveable(item?.id) { mutableStateOf(item?.priority?.toString() ?: "1") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultError by rememberSaveable { mutableStateOf<String?>(null) }
    var stepError by rememberSaveable { mutableStateOf<String?>(null) }
    var unitError by rememberSaveable { mutableStateOf<String?>(null) }
    var goalError by rememberSaveable { mutableStateOf<String?>(null) }
    var priorityError by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val nameRequiredMessage = stringResource(R.string.error_name_required)
    val unitRequiredMessage = stringResource(R.string.error_unit_required)
    val priorityIntegerMessage = stringResource(R.string.error_priority_integer)
    val checkFieldsMessage = stringResource(R.string.error_check_fields)
    val positiveFieldTemplate = stringResource(R.string.error_positive_field)
    val defaultFieldLabel = stringResource(R.string.field_default)
    val stepFieldLabel = stringResource(R.string.field_step)
    val goalFieldLabel = stringResource(R.string.field_goal_value)

    val valueType = RecordValueType.valueOf(valueTypeName)
    val goalPeriod = GoalPeriod.valueOf(goalPeriodName)

    fun clearFieldErrors() {
        nameError = null
        defaultError = null
        stepError = null
        unitError = null
        goalError = null
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
        runCatching { parseOptionalPositive(defaultValue, "Default value") }
            .onFailure {
                defaultError = String.format(Locale.getDefault(), positiveFieldTemplate, defaultFieldLabel)
                valid = false
            }
        runCatching { parsePositive(stepValue, "Step") }
            .onFailure {
                stepError = String.format(Locale.getDefault(), positiveFieldTemplate, stepFieldLabel)
                valid = false
            }
        if (goalEnabled) {
            runCatching { parsePositive(goalValue, "Goal") }
                .onFailure {
                    goalError = String.format(Locale.getDefault(), positiveFieldTemplate, goalFieldLabel)
                    valid = false
                }
        }
        if (priority.toIntOrNull() == null) {
            priorityError = priorityIntegerMessage
            valid = false
        }
        return valid
    }

    val formValid = name.isNotBlank() &&
        unit.isNotBlank() &&
        runCatching { parseOptionalPositive(defaultValue, "Default value") }.isSuccess &&
        runCatching { parsePositive(stepValue, "Step") }.isSuccess &&
        (!goalEnabled || runCatching { parsePositive(goalValue, "Goal") }.isSuccess) &&
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
            mode = RecordMode.ACCUMULATE,
            valueType = valueType,
            defaultValue = parseOptionalPositive(defaultValue, "Default value"),
            stepValue = parsePositive(stepValue, "Step"),
            unit = unit,
            goalValue = if (goalEnabled) parsePositive(goalValue, "Goal") else null,
            goalPeriod = goalPeriod.takeIf { goalEnabled },
            priority = checkNotNull(priority.toIntOrNull()),
        )
        saving = true
        onSave(draft) { error ->
            saving = false
            errorMessage = error
        }
    }

    AppRouteScaffold(
        title = stringResource(if (editing) R.string.edit_accumulate else R.string.new_accumulate),
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
                                if (editing) R.string.editing_accumulate else R.string.new_accumulate,
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
            SingleChoiceSegmentedButtonRow(modifier = Modifier.formField()) {
                RecordValueType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = valueType == type,
                        onClick = {
                            valueTypeName = type.name
                            if (type == RecordValueType.NUMBER) {
                                defaultValue = "1"
                                unit = DEFAULT_NUMBER_UNIT
                            } else {
                                defaultValue = "30"
                                unit = DEFAULT_DURATION_UNIT
                            }
                        },
                        enabled = !fieldsLocked && !saving,
                        shape = SegmentedButtonDefaults.itemShape(index, RecordValueType.entries.size),
                        label = {
                            Text(
                                stringResource(
                                    if (type == RecordValueType.NUMBER) R.string.record_number else R.string.record_duration,
                                ),
                            )
                        },
                    )
                }
            }
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
                    label = { Text(stringResource(R.string.field_default)) },
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
                    label = { Text(stringResource(R.string.field_step)) },
                    isError = stepError != null,
                    supportingText = stepError?.let { message ->
                        { Text(message) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            if (valueType == RecordValueType.NUMBER) {
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
            } else {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.formField()) {
                    DURATION_UNITS.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = unit == option,
                            onClick = { unit = option },
                            enabled = !fieldsLocked && !saving,
                            shape = SegmentedButtonDefaults.itemShape(index, DURATION_UNITS.size),
                            label = { Text(option) },
                        )
                    }
                }
            }
        }

        AppSection(title = stringResource(R.string.record_goal)) {
            Row(
                modifier = Modifier
                    .formField()
                    .toggleable(
                        value = goalEnabled,
                        enabled = !saving,
                        role = Role.Switch,
                        onValueChange = { goalEnabled = it },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.track_a_goal), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = goalEnabled,
                    onCheckedChange = null,
                    enabled = !saving,
                )
            }
            if (goalEnabled) {
                OutlinedTextField(
                    value = goalValue,
                    onValueChange = {
                        goalValue = it
                        goalError = null
                    },
                    enabled = !saving,
                    label = { Text(stringResource(R.string.field_goal_value)) },
                    isError = goalError != null,
                    supportingText = goalError?.let { message ->
                        { Text(message) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.formField(),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.formField()) {
                    GoalPeriod.entries.forEachIndexed { index, period ->
                        SegmentedButton(
                            selected = goalPeriod == period,
                            onClick = { goalPeriodName = period.name },
                            enabled = !saving,
                            shape = SegmentedButtonDefaults.itemShape(index, GoalPeriod.entries.size),
                            label = {
                                Text(
                                    stringResource(
                                        if (period == GoalPeriod.DAY) R.string.period_day_title else R.string.period_seven_days,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }

        AppSection(title = stringResource(R.string.field_priority)) {
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
