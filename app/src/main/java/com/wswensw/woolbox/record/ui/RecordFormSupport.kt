package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.ui.AppStyle
import java.util.Locale

internal fun Modifier.formField(): Modifier =
    fillMaxWidth().padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 8.dp)

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
