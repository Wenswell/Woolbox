package com.wswensw.woolbox.record.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPlanTest {
    @Test
    fun reviewIsDueOnlyForActivePlanAtBoundary() {
        val plan = plan(nextReviewAt = 10_000)

        assertFalse(plan.isReviewDue(9_999))
        assertTrue(plan.isReviewDue(10_000))
        assertFalse(plan.copy(state = DailyPlanState.PAUSED).isReviewDue(10_000))
    }

    @Test
    fun currentAndOpenCommitmentsComeFromPlanSnapshots() {
        val completed = commitment(1, completedAt = 10)
        val current = commitment(2)
        val plan = plan(currentCommitmentId = 2, commitments = listOf(completed, current))

        assertEquals(current, plan.currentCommitment)
        assertEquals(listOf(2L), plan.openCommitments.map(TodayCommitment::id))
    }

    private fun plan(
        nextReviewAt: Long? = null,
        currentCommitmentId: Long? = 1,
        commitments: List<TodayCommitment> = listOf(commitment(1)),
    ) = DailyPlan(
        id = 1,
        localDate = LocalDate.of(2026, 8, 18),
        firstMove = "Go to the library",
        firstMoveCompletedAt = 1,
        currentCommitmentId = currentCommitmentId,
        state = DailyPlanState.ACTIVE,
        pauseReason = null,
        completedRounds = 0,
        nextReviewAt = nextReviewAt,
        revision = 0,
        endedAt = null,
        createdAt = 0,
        updatedAt = 0,
        commitments = commitments,
    )

    private fun commitment(id: Long, completedAt: Long? = null) = TodayCommitment(
        id = id,
        planId = 1,
        templateId = id,
        role = CommitmentRole.PRIMARY,
        position = id.toInt(),
        title = "Commitment $id",
        cadence = TemplateCadence.MANUAL,
        completedAt = completedAt,
        createdAt = 0,
        updatedAt = 0,
        steps = listOf(TodayCommitmentStep(id, id, "Step", 0, completedAt, 0, 0)),
    )
}
