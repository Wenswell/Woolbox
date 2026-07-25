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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
            title = "Edit accumulate",
            onBack = onBack,
            modifier = modifier,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(AppStyle.spacing.pageHorizontal),
                )
            } else {
                Text(
                    text = "Record item not found.",
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
    var windows by rememberSaveable(item?.id) {
        mutableStateOf(item?.windows?.map { "${it.startMinute},${it.endMinute}" }.orEmpty())
    }
    var timeEditTarget by remember { mutableStateOf<TimeEditTarget?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultError by rememberSaveable { mutableStateOf<String?>(null) }
    var stepError by rememberSaveable { mutableStateOf<String?>(null) }
    var unitError by rememberSaveable { mutableStateOf<String?>(null) }
    var goalError by rememberSaveable { mutableStateOf<String?>(null) }
    var priorityError by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

    val valueType = RecordValueType.valueOf(valueTypeName)
    val goalPeriod = GoalPeriod.valueOf(goalPeriodName)
    val parsedWindows = windows.map(::decodeWindow)

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
            nameError = "Name is required."
            valid = false
        }
        if (unit.isBlank()) {
            unitError = "Unit is required."
            valid = false
        }
        runCatching { parseOptionalPositive(defaultValue, "Default value") }
            .onFailure {
                defaultError = it.message
                valid = false
            }
        runCatching { parsePositive(stepValue, "Step") }
            .onFailure {
                stepError = it.message
                valid = false
            }
        if (goalEnabled) {
            runCatching { parsePositive(goalValue, "Goal") }
                .onFailure {
                    goalError = it.message
                    valid = false
                }
        }
        if (priority.toIntOrNull() == null) {
            priorityError = "Priority must be an integer."
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
            errorMessage = "Check the highlighted fields."
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
            windows = parsedWindows,
        )
        saving = true
        onSave(draft) { error ->
            saving = false
            errorMessage = error
        }
    }

    AppRouteScaffold(
        title = if (editing) "Edit accumulate" else "New accumulate",
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
                            text = if (editing) "Editing accumulate" else "New accumulate",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        AppSection(title = "Details") {
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

        AppSection(title = "Measurement") {
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
                        label = { Text(if (type == RecordValueType.NUMBER) "Number" else "Duration") },
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
                    label = { Text("Default") },
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
            if (valueType == RecordValueType.NUMBER) {
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

        AppSection(title = "Goal") {
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
                Text("Track a goal", style = MaterialTheme.typography.bodyLarge)
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
                    label = { Text("Goal value") },
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
                            label = { Text(if (period == GoalPeriod.DAY) "Day" else "7 days") },
                        )
                    }
                }
            }
        }

        AppSection(title = "Recommendation") {
            parsedWindows.forEachIndexed { index, window ->
                Row(
                    modifier = Modifier.formField(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { timeEditTarget = TimeEditTarget(index, start = true) },
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(formatMinute(window.startMinute))
                    }
                    Text("to", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { timeEditTarget = TimeEditTarget(index, start = false) },
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(formatMinute(window.endMinute))
                    }
                    IconButton(
                        enabled = !saving,
                        onClick = { windows = windows.toMutableList().also { it.removeAt(index) } },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close_24),
                            contentDescription = "Remove suggested time",
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    val start = parsedWindows.lastOrNull()?.endMinute ?: 9 * 60
                    val end = (start + 60) % (24 * 60)
                    windows = windows + "$start,$end"
                },
                enabled = !saving,
                modifier = Modifier.formField(),
            ) {
                Text("Add suggested time")
            }
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

    timeEditTarget?.let { target ->
        val window = parsedWindows[target.index]
        RecordTimePickerDialog(
            initialMinute = if (target.start) window.startMinute else window.endMinute,
            onDismiss = { timeEditTarget = null },
            onConfirm = { minute ->
                val updated = parsedWindows.toMutableList()
                updated[target.index] = if (target.start) {
                    window.copy(startMinute = minute)
                } else {
                    window.copy(endMinute = minute)
                }
                windows = updated.map { "${it.startMinute},${it.endMinute}" }
                timeEditTarget = null
            },
        )
    }
}
