package com.wswensw.woolbox.record.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TodayCommitmentTest {
    @Test
    fun nextStepIsFirstIncompleteSnapshotStep() {
        val commitment = TodayCommitment(
            id = 1,
            planId = 1,
            templateId = 9,
            role = CommitmentRole.PRIMARY,
            position = 0,
            title = "Outcome",
            cadence = TemplateCadence.DAILY,
            completedAt = null,
            createdAt = 0,
            updatedAt = 0,
            steps = listOf(
                TodayCommitmentStep(1, 1, "Done", 0, 1, 0, 0),
                TodayCommitmentStep(2, 1, "Next", 1, null, 0, 0),
                TodayCommitmentStep(3, 1, "Later", 2, null, 0, 0),
            ),
        )

        assertEquals("Next", commitment.nextStep?.title)
        assertEquals(1, commitment.completedStepCount)
    }
}
