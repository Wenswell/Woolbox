package com.wswensw.woolbox.record.model

import java.time.LocalDate

enum class DailyPlanState {
    READY,
    ACTIVE,
    ENOUGH,
    ENDED,
}

enum class DailyPlanCompletionReason {
    ROUNDS,
    COMMITMENTS,
}

data class DailyPlan(
    val id: Long,
    val localDate: LocalDate,
    val firstMove: String,
    val firstMoveCompletedAt: Long?,
    val primaryTaskId: Long?,
    val secondaryTaskId: Long?,
    val currentTaskId: Long?,
    val state: DailyPlanState,
    val completionReason: DailyPlanCompletionReason?,
    val completedRounds: Int,
    val nextReviewAt: Long?,
    val revision: Int,
    val endedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun isReviewDue(now: Long): Boolean =
        state == DailyPlanState.ACTIVE && nextReviewAt?.let { now >= it } == true

    fun committedTasks(tasks: List<TodoTask>): List<TodoTask> =
        listOfNotNull(primaryTaskId, secondaryTaskId)
            .distinct()
            .mapNotNull { id -> tasks.firstOrNull { it.id == id } }
}

fun validatedFirstMove(value: String): String {
    val firstMove = value.trim()
    if (firstMove.isEmpty()) throw RecordValidationException("First move is required.")
    return firstMove
}
