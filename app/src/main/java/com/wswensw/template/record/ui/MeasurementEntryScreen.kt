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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.stepMeasurementValue
import com.wswensw.template.ui.TemplateRouteScaffold
import com.wswensw.template.ui.TemplateSection
import com.wswensw.template.ui.TemplateStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementEntryScreen(
    item: RecordItem,
    entry: RecordEntry?,
    onBack: () -> Unit,
    onSave: (value: Double, occurredAt: Long, onResult: (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialTime = entry?.occurredAt ?: System.currentTimeMillis()
    val initialLocal = Instant.ofEpochMilli(initialTime).atZone(ZoneId.systemDefault())
    var value by rememberSaveable(entry?.id) {
        mutableStateOf(entry?.value?.let(::formatValue) ?: item.defaultValue?.let(::formatValue).orEmpty())
    }
    var date by rememberSaveable(entry?.id) { mutableStateOf(initialLocal.toLocalDate().toString()) }
    var minute by rememberSaveable(entry?.id) { mutableStateOf(initialLocal.hour * 60 + initialLocal.minute) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var valueError by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

    fun parsedValue(): Double {
        val parsed = value.trim().toDoubleOrNull()
        check(parsed != null && parsed.isFinite()) { "Value must be a finite number." }
        return parsed
    }

    fun occurredAt(): Long = LocalDate.parse(date)
        .atTime(minute / 60, minute % 60)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    fun submit() {
        errorMessage = null
        val parsed = runCatching(::parsedValue).getOrElse {
            valueError = it.message
            errorMessage = "Check the highlighted field."
            return
        }
        saving = true
        onSave(parsed, occurredAt()) { error ->
            saving = false
            errorMessage = error
        }
    }

    TemplateRouteScaffold(
        title = if (entry == null) "New measurement" else "Edit measurement",
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(onClick = ::submit, enabled = !saving) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = if (saving) "Saving" else "Save",
                )
            }
        },
    ) {
        TemplateSection(title = displayRecordName(item.name)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { value = stepMeasurementValue(value, item.stepValue, -1) },
                    enabled = !saving,
                ) {
                    Icon(imageVector = Icons.Rounded.Remove, contentDescription = "Decrease value")
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        valueError = null
                    },
                    enabled = !saving,
                    label = { Text(item.unit) },
                    isError = valueError != null,
                    supportingText = valueError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { value = stepMeasurementValue(value, item.stepValue, 1) },
                    enabled = !saving,
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = "Increase value")
                }
            }
        }

        TemplateSection(title = "Occurred") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    enabled = !saving,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(measurementDateLabel(date))
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    enabled = !saving,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60))
                }
            }
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showDatePicker) {
        MeasurementDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { selectedDate ->
                date = selectedDate
                showDatePicker = false
            },
        )
    }
    if (showTimePicker) {
        MeasurementTimePickerDialog(
            initialMinute = minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { selectedMinute ->
                minute = selectedMinute
                showTimePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementDatePickerDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.parse(initialDate)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli(),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Date") },
        text = { DatePicker(state = state) },
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { selected ->
                        onConfirm(
                            Instant.ofEpochMilli(selected)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString(),
                        )
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurementTimePickerDialog(
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
        title = { Text("Time") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun measurementDateLabel(date: String): String = LocalDate.parse(date)
    .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
