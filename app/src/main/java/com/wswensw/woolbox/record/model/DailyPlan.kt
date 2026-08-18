package com.wswensw.woolbox.record.model

import java.time.LocalDate

enum class DailyPlanState {
    READY,
    ACTIVE,
    PAUSED,
    ENDED,
}

enum class DailyPlanPauseReason {
    COMMITMENTS_COMPLETED,
    TARGET_REACHED,
}

enum class CommitmentRole {
    PRIMARY,
    SECONDARY,
    ADDITIONAL,
}

data class TodayCommitment(
    val id: Long,
    val planId: Long,
    val templateId: Long?,
    val role: CommitmentRole,
    val position: Int,
    val title: String,
    val cadence: TemplateCadence,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val steps: List<TodayCommitmentStep>,
) {
    val isCompleted: Boolean
        get() = completedAt != null

    val completedStepCount: Int
        get() = steps.count(TodayCommitmentStep::isCompleted)

    val nextStep: TodayCommitmentStep?
        get() = steps.firstOrNull { !it.isCompleted }
}

data class TodayCommitmentStep(
    val id: Long,
    val commitmentId: Long,
    val title: String,
    val position: Int,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isCompleted: Boolean
        get() = completedAt != null
}

data class DailyPlan(
    val id: Long,
    val localDate: LocalDate,
    val firstMove: String,
    val firstMoveCompletedAt: Long?,
    val currentCommitmentId: Long?,
    val state: DailyPlanState,
    val pauseReason: DailyPlanPauseReason?,
    val completedRounds: Int,
    val nextReviewAt: Long?,
    val revision: Int,
    val endedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val commitments: List<TodayCommitment>,
) {
    val currentCommitment: TodayCommitment?
        get() = commitments.firstOrNull { it.id == currentCommitmentId }

    val openCommitments: List<TodayCommitment>
        get() = commitments.filterNot(TodayCommitment::isCompleted)

    fun isReviewDue(now: Long): Boolean =
        state == DailyPlanState.ACTIVE && nextReviewAt?.let { now >= it } == true
}

fun validatedFirstMove(value: String): String {
    val firstMove = value.trim()
    if (firstMove.isEmpty()) throw RecordValidationException("First move is required.")
    return firstMove
}
