package com.wswensw.woolbox.record.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayTaskTest {
    @Test
    fun nextStepIsFirstIncompleteSnapshotStep() {
        val task = TodayTask(
            id = 1,
            localDate = LocalDate.of(2026, 8, 18),
            templateId = 9,
            sourceTaskId = null,
            title = "Outcome",
            position = 0,
            completedAt = null,
            revision = 0,
            createdAt = 0,
            updatedAt = 0,
            steps = listOf(
                TodayTaskStep(1, 1, "Done", 0, 1, 0, 0),
                TodayTaskStep(2, 1, "Next", 1, null, 0, 0),
                TodayTaskStep(3, 1, "Later", 2, null, 0, 0),
            ),
        )

        assertEquals("Next", task.nextStep?.title)
        assertEquals(1, task.completedStepCount)
    }
}
