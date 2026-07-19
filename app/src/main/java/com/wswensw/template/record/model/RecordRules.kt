package com.wswensw.template.record.model

const val DEFAULT_NUMBER_UNIT = "次"
const val DEFAULT_DURATION_UNIT = "分"

val DURATION_UNITS = listOf("秒", "分", "小时")

class RecordValidationException(message: String) : IllegalArgumentException(message)

fun RecordItemDraft.validated(): RecordItemDraft {
    val normalizedName = name.trim()
    val normalizedDescription = description.trim()
    val normalizedUnit = unit.trim()

    requireRecord(normalizedName.isNotEmpty(), "Name is required.")
    requireRecord(normalizedUnit.isNotEmpty(), "Unit is required.")
    requirePositive(defaultValue, "Default value")
    requirePositive(stepValue, "Step")
    requirePositive(goalValue, "Goal")
    requireRecord(
        goalValue == null || goalPeriod != null,
        "Goal period is required when a goal is set.",
    )
    requireRecord(
        valueType != RecordValueType.DURATION || normalizedUnit in DURATION_UNITS,
        "Duration unit must be 秒, 分, or 小时.",
    )

    val normalizedWindows = windows.map { window ->
        requireRecord(window.startMinute in 0..1439, "Start time is invalid.")
        requireRecord(window.endMinute in 0..1439, "End time is invalid.")
        requireRecord(window.startMinute != window.endMinute, "Start and end times must differ.")
        window.copy(id = 0)
    }
    requireRecord(
        normalizedWindows.distinctBy { it.startMinute to it.endMinute }.size == normalizedWindows.size,
        "Suggested time windows must be unique.",
    )

    return copy(
        name = normalizedName,
        description = normalizedDescription,
        unit = normalizedUnit,
        goalPeriod = goalPeriod.takeIf { goalValue != null },
        windows = normalizedWindows,
    )
}

private fun requirePositive(value: Double?, label: String) {
    if (value != null) {
        requireRecord(value.isFinite() && value > 0, "$label must be a positive number.")
    }
}

private fun requireRecord(condition: Boolean, message: String) {
    if (!condition) {
        throw RecordValidationException(message)
    }
}
