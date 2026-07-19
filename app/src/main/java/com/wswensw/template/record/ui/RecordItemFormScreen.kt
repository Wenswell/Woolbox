package com.wswensw.template.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.record.model.DEFAULT_DURATION_UNIT
import com.wswensw.template.record.model.DEFAULT_NUMBER_UNIT
import com.wswensw.template.record.model.DURATION_UNITS
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordItemDraft
import com.wswensw.template.record.model.RecordValueType
import com.wswensw.template.record.model.RecordWindow
import com.wswensw.template.ui.TemplateRouteScaffold
import com.wswensw.template.ui.TemplateStyle
import java.util.Locale

private data class TimeEditTarget(
    val index: Int,
    val start: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordItemFormScreen(
    item: RecordItem?,
    editing: Boolean,
    loading: Boolean,
    measurementLocked: Boolean,
    onBack: () -> Unit,
    onSave: (RecordItemDraft, (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (editing && item == null) {
        TemplateRouteScaffold(
            title = "Edit record item",
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
    var saving by rememberSaveable { mutableStateOf(false) }

    val valueType = RecordValueType.valueOf(valueTypeName)
    val goalPeriod = GoalPeriod.valueOf(goalPeriodName)
    val parsedWindows = windows.map(::decodeWindow)

    TemplateRouteScaffold(
        title = if (editing) "Edit record item" else "New record item",
        onBack = onBack,
        modifier = modifier,
    ) {
        FormHeading("Details")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.formField(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.formField(),
        )

        FormHeading("Measurement")
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
                    enabled = !measurementLocked,
                    shape = SegmentedButtonDefaults.itemShape(index, RecordValueType.entries.size),
                    label = { Text(if (type == RecordValueType.NUMBER) "Number" else "Duration") },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TemplateStyle.spacing.pageHorizontal),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = defaultValue,
                onValueChange = { defaultValue = it },
                label = { Text("Default") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = stepValue,
                onValueChange = { stepValue = it },
                label = { Text("Step") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (valueType == RecordValueType.NUMBER) {
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                enabled = !measurementLocked,
                label = { Text("Unit") },
                singleLine = true,
                modifier = Modifier.formField(),
            )
        } else {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.formField()) {
                DURATION_UNITS.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = unit == option,
                        onClick = { unit = option },
                        enabled = !measurementLocked,
                        shape = SegmentedButtonDefaults.itemShape(index, DURATION_UNITS.size),
                        label = { Text(option) },
                    )
                }
            }
        }

        FormHeading("Goal")
        Row(
            modifier = Modifier.formField(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Track a goal", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = goalEnabled,
                onCheckedChange = { goalEnabled = it },
            )
        }
        if (goalEnabled) {
            OutlinedTextField(
                value = goalValue,
                onValueChange = { goalValue = it },
                label = { Text("Goal value") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.formField(),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.formField()) {
                GoalPeriod.entries.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = goalPeriod == period,
                        onClick = { goalPeriodName = period.name },
                        shape = SegmentedButtonDefaults.itemShape(index, GoalPeriod.entries.size),
                        label = { Text(if (period == GoalPeriod.DAY) "Day" else "Week") },
                    )
                }
            }
        }

        FormHeading("Recommendation")
        parsedWindows.forEachIndexed { index, window ->
            Row(
                modifier = Modifier.formField(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { timeEditTarget = TimeEditTarget(index, start = true) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(formatMinute(window.startMinute))
                }
                Text("to", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { timeEditTarget = TimeEditTarget(index, start = false) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(formatMinute(window.endMinute))
                }
                IconButton(onClick = { windows = windows.toMutableList().also { it.removeAt(index) } }) {
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
            modifier = Modifier.formField(),
        ) {
            Text("Add suggested time")
        }
        OutlinedTextField(
            value = priority,
            onValueChange = { priority = it },
            label = { Text("Priority") },
            supportingText = { Text("Higher values appear first.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.formField(),
        )

        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.formField(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val draft = runCatching {
                    RecordItemDraft(
                        name = name,
                        description = description,
                        valueType = valueType,
                        defaultValue = parseOptionalPositive(defaultValue, "Default value"),
                        stepValue = parsePositive(stepValue, "Step"),
                        unit = unit,
                        goalValue = if (goalEnabled) parsePositive(goalValue, "Goal") else null,
                        goalPeriod = goalPeriod.takeIf { goalEnabled },
                        priority = priority.toIntOrNull() ?: error("Priority must be an integer."),
                        windows = parsedWindows,
                    )
                }.getOrElse { error ->
                    errorMessage = error.message ?: "Check the form values."
                    return@Button
                }
                saving = true
                errorMessage = null
                onSave(draft) { error ->
                    saving = false
                    errorMessage = error
                }
            },
            enabled = !saving,
            modifier = Modifier.formField(),
        ) {
            Text(if (saving) "Saving" else "Save")
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

@Composable
private fun FormHeading(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = TemplateStyle.spacing.pageHorizontal,
                end = TemplateStyle.spacing.pageHorizontal,
                top = 12.dp,
                bottom = 4.dp,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordTimePickerDialog(
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suggested time") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun Modifier.formField(): Modifier =
    fillMaxWidth().padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 6.dp)

private fun decodeWindow(value: String): RecordWindow {
    val parts = value.split(',', limit = 2)
    return RecordWindow(
        startMinute = parts[0].toInt(),
        endMinute = parts[1].toInt(),
    )
}

private fun formatMinute(minute: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    minute / 60,
    minute % 60,
)

private fun parseOptionalPositive(value: String, label: String): Double? =
    value.trim().takeIf(String::isNotEmpty)?.let { parsePositive(it, label) }

private fun parsePositive(value: String, label: String): Double {
    val parsed = value.trim().toDoubleOrNull()
    check(parsed != null && parsed.isFinite() && parsed > 0) { "$label must be a positive number." }
    return parsed
}
