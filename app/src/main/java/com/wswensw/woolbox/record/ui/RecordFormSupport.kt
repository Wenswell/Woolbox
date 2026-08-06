package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.ui.AppStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun Modifier.formField(): Modifier =
    fillMaxWidth().padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp)

/** The only task scheduling control: one optional planned date. */
@Composable
internal fun TaskScheduleFields(
    plannedDate: LocalDate?,
    today: LocalDate,
    enabled: Boolean,
    onPlannedDateChange: (LocalDate?) -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { pickingDate = true },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) {
            Icon(imageVector = Icons.Outlined.DateRange, contentDescription = null)
            Text(
                text = plannedDate?.let { taskDateLabel(it, today) }
                    ?: stringResource(R.string.choose_date),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (plannedDate != null) {
            IconButton(
                onClick = { onPlannedDateChange(null) },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.remove_date),
                )
            }
        }
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

@Composable
internal fun taskDateLabel(date: LocalDate, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (date) {
        today -> stringResource(R.string.today)
        else -> TaskDateFormatter.withLocale(locale).format(date)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialDate.utcStartMillis(),
    )
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
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

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
