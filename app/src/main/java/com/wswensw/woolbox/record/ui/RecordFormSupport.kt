package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.MINUTES_PER_DAY
import com.wswensw.woolbox.record.model.RecordWindow
import com.wswensw.woolbox.record.model.validatedWindows
import com.wswensw.woolbox.record.model.windowCoverageMinutes
import com.wswensw.woolbox.ui.AppStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal data class TimeEditTarget(
    val index: Int,
    val start: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordTimePickerDialog(
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

internal fun Modifier.formField(): Modifier =
    fillMaxWidth().padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp)

/** Shared schedule controls for task forms. A date answers which day; windows answer what time. */
@Composable
internal fun TaskScheduleFields(
    plannedDate: LocalDate?,
    today: LocalDate,
    hasSuggestedTimes: Boolean,
    enabled: Boolean,
    onPlannedDateChange: (LocalDate?) -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = plannedDate == today,
            onClick = { onPlannedDateChange(today) },
            enabled = enabled,
            label = { Text("Today") },
        )
        FilterChip(
            selected = plannedDate == null,
            onClick = { onPlannedDateChange(null) },
            enabled = enabled && !hasSuggestedTimes,
            label = { Text("Later") },
        )
    }
    OutlinedButton(
        onClick = { pickingDate = true },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 4.dp),
    ) {
        Icon(imageVector = Icons.Outlined.DateRange, contentDescription = null)
        Text(
            text = plannedDate?.let { taskDateLabel(it, today) } ?: "Choose date",
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    if (hasSuggestedTimes) {
        Text(
            text = "Remove suggested times before moving this task to Later.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(
                start = AppStyle.spacing.pageHorizontal,
                end = AppStyle.spacing.pageHorizontal,
                bottom = 4.dp,
            ),
        )
    }
    if (pickingDate) {
        TaskDatePickerDialog(
            initialDate = plannedDate ?: today,
            onDismiss = { pickingDate = false },
            onConfirm = { selectedDate ->
                onPlannedDateChange(selectedDate)
                pickingDate = false
            },
        )
    }
}

internal fun taskDateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    else -> TaskDateFormatter.format(date)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate.utcStartMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { selectedMillis ->
                        onConfirm(selectedMillis.toUtcLocalDate())
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
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

private fun LocalDate.utcStartMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private val TaskDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

/**
 * Suggested time window rows plus their add action, shared by the accumulate
 * and task forms. Each row opens the time picker through [onEditTime]; the
 * caller owns the list and the picker dialog.
 */
@Composable
internal fun SuggestedTimeFields(
    windows: List<RecordWindow>,
    enabled: Boolean,
    onEditTime: (TimeEditTarget) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    horizontalPadding: Dp = AppStyle.spacing.pageHorizontal,
    summary: String = suggestedTimeSummary(windows),
) {
    val fieldModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = horizontalPadding, vertical = 8.dp)
    windows.forEachIndexed { index, window ->
        Row(
            modifier = fieldModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onEditTime(TimeEditTarget(index, start = true)) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(formatMinute(window.startMinute))
            }
            Text("to", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = { onEditTime(TimeEditTarget(index, start = false)) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(formatMinute(window.endMinute))
            }
            IconButton(
                enabled = enabled,
                onClick = { onRemove(index) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_24),
                    contentDescription = "Remove suggested time",
                )
            }
        }
    }
    OutlinedButton(
        onClick = onAdd,
        enabled = enabled,
        modifier = fieldModifier,
    ) {
        Text("Add suggested time")
    }
    Text(
        text = summary,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = horizontalPadding, end = horizontalPadding, bottom = 8.dp),
    )
}

/**
 * Standalone editor for one subtask's suggested times, used by `New task`
 * where the subtask has no id yet. Validation runs on confirm so the caller
 * only ever receives a legal list.
 */
@Composable
internal fun SuggestedTimeDialog(
    stepLabel: String,
    initialWindows: List<RecordWindow>,
    onDismiss: () -> Unit,
    onConfirm: (List<RecordWindow>) -> Unit,
) {
    var windows by remember { mutableStateOf(initialWindows.map { it.encode() }) }
    var timeEditTarget by remember { mutableStateOf<TimeEditTarget?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val parsedWindows = windows.map(::decodeWindow)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stepLabel) },
        text = {
            Column {
                SuggestedTimeFields(
                    windows = parsedWindows,
                    enabled = true,
                    onEditTime = { timeEditTarget = it },
                    onRemove = { index ->
                        windows = windows.toMutableList().also { it.removeAt(index) }
                        errorMessage = null
                    },
                    onAdd = {
                        windows = windows + nextSuggestedWindow(parsedWindows).encode()
                        errorMessage = null
                    },
                    horizontalPadding = 0.dp,
                    summary = taskSuggestedTimeSummary(parsedWindows),
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching { validatedWindows(parsedWindows) }.fold(
                        onSuccess = onConfirm,
                        onFailure = { error ->
                            errorMessage = error.message ?: "Suggested times are invalid."
                        },
                    )
                },
            ) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    timeEditTarget?.let { target ->
        val window = parsedWindows.getOrNull(target.index) ?: return@let
        RecordTimePickerDialog(
            initialMinute = if (target.start) window.startMinute else window.endMinute,
            onDismiss = { timeEditTarget = null },
            onConfirm = { minute ->
                windows = parsedWindows.withEditedTime(target, minute).map { it.encode() }
                errorMessage = null
                timeEditTarget = null
            },
        )
    }
}

/** Explains what the configured windows will do on the Records root page. */
internal fun suggestedTimeSummary(windows: List<RecordWindow>): String {
    if (windows.isEmpty()) return "No suggested times. Never appears in the Now band."
    return "Shown in the Now band for ${suggestedTimeSpan(windows)} of the day."
}

internal fun taskSuggestedTimeSummary(windows: List<RecordWindow>): String {
    if (windows.isEmpty()) return "No suggested times. Queued on the task date."
    return "Shown in the Now band on the task date for ${suggestedTimeSpan(windows)}."
}

private fun suggestedTimeSpan(windows: List<RecordWindow>): String {
    val coverage = windowCoverageMinutes(windows)
    val hours = coverage / 60
    val minutes = coverage % 60
    return if (hours == 0) "${minutes}m" else "${hours}h ${minutes}m"
}

/** Default window appended by the add action: one hour after the last end. */
internal fun nextSuggestedWindow(windows: List<RecordWindow>): RecordWindow {
    val start = windows.lastOrNull()?.endMinute ?: 9 * 60
    return RecordWindow(startMinute = start, endMinute = (start + 60) % MINUTES_PER_DAY)
}

/** Applies a picked minute to the window the [target] points at. */
internal fun List<RecordWindow>.withEditedTime(target: TimeEditTarget, minute: Int): List<RecordWindow> {
    if (target.index !in indices) return this
    return toMutableList().also { windows ->
        val window = windows[target.index]
        windows[target.index] = if (target.start) {
            window.copy(startMinute = minute)
        } else {
            window.copy(endMinute = minute)
        }
    }
}

internal fun decodeWindow(value: String): RecordWindow {
    val parts = value.split(',', limit = 2)
    return RecordWindow(
        startMinute = parts[0].toInt(),
        endMinute = parts[1].toInt(),
    )
}

/** Saveable form of a window, so form state survives configuration changes. */
internal fun RecordWindow.encode(): String = "$startMinute,$endMinute"

internal fun formatMinute(minute: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    minute / 60,
    minute % 60,
)

internal fun parseOptionalPositive(value: String, label: String): Double? =
    value.trim().takeIf(String::isNotEmpty)?.let { parsePositive(it, label) }

internal fun parseOptionalFinite(value: String, label: String): Double? =
    value.trim().takeIf(String::isNotEmpty)?.let { raw ->
        val parsed = raw.toDoubleOrNull()
        check(parsed != null && parsed.isFinite()) { "$label must be a finite number." }
        parsed
    }

internal fun parseRequiredFinite(value: String, label: String): Double =
    parseOptionalFinite(value, label) ?: error("$label is required.")

internal fun parsePositive(value: String, label: String): Double {
    val parsed = value.trim().toDoubleOrNull()
    check(parsed != null && parsed.isFinite() && parsed > 0) { "$label must be a positive number." }
    return parsed
}

internal fun <T> List<T>.moveItem(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable
}
