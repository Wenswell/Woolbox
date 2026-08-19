package com.wswensw.woolbox.record.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalModelsTest {
    @Test
    fun validatesSharedPoolDefinition() {
        val draft = GoalPoolDraft(
            title = "  Long goal  ",
            description = "  Context  ",
            unit = " points ",
            defaultValue = 1.0,
            stepValue = 1.0,
            startDate = LocalDate.parse("2026-08-19"),
            endDate = LocalDate.parse("2027-08-19"),
            subgoals = listOf(GoalSubgoalDraft(title = "  First  ", targetValue = 10.0)),
        ).validated()

        assertEquals("Long goal", draft.title)
        assertEquals("Context", draft.description)
        assertEquals("points", draft.unit)
        assertEquals("First", draft.subgoals.single().title)
    }

    @Test
    fun expiresOnlyWhenIncompleteAfterEndDate() {
        val goal = goal(completed = false)
        assertFalse(goal.expired(LocalDate.parse("2027-08-19")))
        assertTrue(goal.expired(LocalDate.parse("2027-08-20")))
        assertFalse(goal(completed = true).expired(LocalDate.parse("2027-08-20")))
        assertTrue(goal.pending(LocalDate.parse("2026-08-18")))
        assertFalse(goal.pending(LocalDate.parse("2026-08-19")))
    }

    @Test
    fun rejectsInvalidDateRange() {
        assertThrows(IllegalArgumentException::class.java) {
            GoalPoolDraft(
                title = "Goal",
                description = "",
                unit = "points",
                defaultValue = null,
                stepValue = 1.0,
                startDate = LocalDate.parse("2027-08-20"),
                endDate = LocalDate.parse("2027-08-19"),
                subgoals = listOf(GoalSubgoalDraft(title = "First", targetValue = 1.0)),
            ).validated()
        }
    }

    private fun goal(completed: Boolean) = GoalPool(
        id = 1,
        title = "Goal",
        description = "",
        unit = "points",
        defaultValue = null,
        stepValue = 1.0,
        startDate = LocalDate.parse("2026-08-19"),
        endDate = LocalDate.parse("2027-08-19"),
        archived = false,
        currentValue = 10.0,
        subgoals = listOf(
            GoalSubgoal(
                id = 1,
                goalId = 1,
                title = "First",
                targetValue = 10.0,
                position = 0,
                completedAt = 1L.takeIf { completed },
            ),
        ),
    )
}
