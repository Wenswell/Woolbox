package com.wswensw.woolbox.record.model

import java.time.LocalDate

enum class DailyFocusState {
    READY,
    ACTIVE,
    PAUSED,
    ENDED,
}

enum class DailyFocusPauseReason {
    FOCUS_CLEARED,
    TARGET_REACHED,
}

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

data class DailyFocus(
    val id: Long,
    val localDate: LocalDate,
    val firstMove: String,
    val firstMoveCompletedAt: Long?,
    val primaryTaskId: Long,
    val secondaryTaskId: Long?,
    val currentTaskId: Long?,
    val state: DailyFocusState,
    val pauseReason: DailyFocusPauseReason?,
    val completedRounds: Int,
    val nextReviewAt: Long?,
    val revision: Int,
    val endedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun currentTask(tasks: List<TodayTask>): TodayTask? =
        tasks.firstOrNull { it.id == currentTaskId }

    fun selectedOpenTasks(tasks: List<TodayTask>): List<TodayTask> =
        listOfNotNull(
            tasks.firstOrNull { it.id == primaryTaskId && !it.isCompleted },
            secondaryTaskId?.let { id -> tasks.firstOrNull { it.id == id && !it.isCompleted } },
        )

    fun isReviewDue(now: Long): Boolean =
        state == DailyFocusState.ACTIVE && nextReviewAt?.let { now >= it } == true
}

fun validatedFirstMove(value: String): String {
    val firstMove = value.trim()
    if (firstMove.isEmpty()) throw RecordValidationException("First move is required.")
    return firstMove
}
