package com.wswensw.woolbox.record.model

import java.time.LocalDate

data class TodayTask(
    val id: Long,
    val localDate: LocalDate,
    val templateId: Long?,
    val sourceTaskId: Long?,
    val title: String,
    val position: Int,
    val completedAt: Long?,
    val revision: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val steps: List<TodayTaskStep>,
) {
    val isCompleted: Boolean
        get() = completedAt != null

    val completedStepCount: Int
        get() = steps.count(TodayTaskStep::isCompleted)

    val nextStep: TodayTaskStep?
        get() = steps.firstOrNull { !it.isCompleted }
}

data class TodayTaskStep(
    val id: Long,
    val taskId: Long,
    val title: String,
    val position: Int,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isCompleted: Boolean
        get() = completedAt != null
}
