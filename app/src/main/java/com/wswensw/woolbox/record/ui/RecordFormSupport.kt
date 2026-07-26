package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.MINUTES_PER_DAY
import com.wswensw.woolbox.record.model.RecordWindow
import com.wswensw.woolbox.record.model.windowCoverageMinutes
import com.wswensw.woolbox.ui.AppStyle
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
) {
    windows.forEachIndexed { index, window ->
        Row(
            modifier = Modifier.formField(),
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
        modifier = Modifier.formField(),
    ) {
        Text("Add suggested time")
    }
    Text(
        text = suggestedTimeSummary(windows),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(
            start = AppStyle.spacing.pageHorizontal,
            end = AppStyle.spacing.pageHorizontal,
            bottom = 8.dp,
        ),
    )
}

/** Explains what the configured windows will do on the Records root page. */
internal fun suggestedTimeSummary(windows: List<RecordWindow>): String {
    if (windows.isEmpty()) return "No suggested times. This never appears in the Now band."
    val coverage = windowCoverageMinutes(windows)
    val hours = coverage / 60
    val minutes = coverage % 60
    val span = if (hours == 0) "${minutes}m" else "${hours}h ${minutes}m"
    return "Shown in the Now band for $span of the day."
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
