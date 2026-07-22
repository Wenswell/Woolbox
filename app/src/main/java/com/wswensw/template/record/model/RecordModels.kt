package com.wswensw.template.record.model

enum class RecordValueType {
    NUMBER,
    DURATION,
}

enum class GoalPeriod {
    DAY,
    WEEK,
}

data class RecordWindow(
    val id: Long = 0,
    val startMinute: Int,
    val endMinute: Int,
)

data class RecordItem(
    val id: Long,
    val name: String,
    val description: String,
    val valueType: RecordValueType,
    val defaultValue: Double?,
    val stepValue: Double,
    val unit: String,
    val goalValue: Double?,
    val goalPeriod: GoalPeriod?,
    val priority: Int,
    val archived: Boolean,
    val windows: List<RecordWindow>,
    val createdAt: Long,
    val updatedAt: Long,
)

data class RecordItemDraft(
    val name: String,
    val description: String,
    val valueType: RecordValueType,
    val defaultValue: Double?,
    val stepValue: Double,
    val unit: String,
    val goalValue: Double?,
    val goalPeriod: GoalPeriod?,
    val priority: Int,
    val windows: List<RecordWindow>,
)

data class RecordEntry(
    val id: Long,
    val itemId: Long,
    val occurredAt: Long,
    val localDate: String,
    val localMinute: Int,
    val value: Double,
    val createdAt: Long,
    val itemName: String,
    val itemUnit: String,
    val itemValueType: RecordValueType,
)
