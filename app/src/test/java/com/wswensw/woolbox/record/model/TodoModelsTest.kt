package com.wswensw.woolbox.record.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TodoModelsTest {
    @Test
    fun nextStepIsFirstIncompleteStep() {
        val task = task(
            steps = listOf(
                TodoStep(1, 1, "Done", 1, 0, 0),
                TodoStep(2, 1, "Next", null, 0, 0),
                TodoStep(3, 1, "Later", null, 0, 0),
            ),
        )

        assertEquals("Next", task.nextStep?.title)
        assertEquals(1, task.completedStepCount)
    }

    private fun task(steps: List<TodoStep>) = TodoTask(
        id = 1,
        title = "Outcome",
        repeatMode = TaskRepeatMode.ONCE,
        completedAt = null,
        createdAt = 0,
        updatedAt = 0,
        steps = steps,
    )
}
