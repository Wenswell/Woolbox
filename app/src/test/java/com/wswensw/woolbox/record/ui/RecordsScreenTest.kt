package com.wswensw.woolbox.record.ui

import com.wswensw.woolbox.record.model.GoalPeriod
import com.wswensw.woolbox.record.model.RecordEntry
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordMode
import com.wswensw.woolbox.record.model.RecordValueType
import com.wswensw.woolbox.record.model.TodoStep
import com.wswensw.woolbox.record.model.TodoTask
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordsScreenTest {
    @Test
    fun firstRecordCharacterKeepsTextCharacter() {
        assertEquals("阅", firstRecordCharacter("阅读"))
    }

    @Test
    fun firstRecordCharacterKeepsEmojiCharacter() {
        assertEquals("💧", firstRecordCharacter("💧 Water"))
    }

    @Test
    fun firstRecordCharacterKeepsCombiningCharacterSequence() {
        assertEquals("e\u0301", firstRecordCharacter("e\u0301 Accent"))
    }

    @Test
    fun firstRecordCharacterHandlesEmptyName() {
        assertEquals("", firstRecordCharacter(""))
    }

    @Test
    fun dropFirstRecordCharacterRemovesTextCharacter() {
        assertEquals("读", dropFirstRecordCharacter("阅读"))
    }

    @Test
    fun dropFirstRecordCharacterRemovesEmojiCharacter() {
        assertEquals(" Water", dropFirstRecordCharacter("💧 Water"))
    }

    @Test
    fun dropFirstRecordCharacterRemovesCombiningCharacterSequence() {
        assertEquals(" Accent", dropFirstRecordCharacter("e\u0301 Accent"))
    }

    @Test
    fun dropFirstRecordCharacterHandlesEmptyName() {
        assertEquals("", dropFirstRecordCharacter(""))
    }

    @Test
    fun displayRecordNameRemovesIconCharacterAndSeparator() {
        assertEquals("Water", displayRecordName("💧 Water"))
        assertEquals("阅", displayRecordName("阅"))
    }


    @Test
    fun orderedRecordItemsPreferGoalsFirst() {
        val ordered = orderedRecordItems(
            listOf(
                recordItem(id = 1, name = "A", priority = 100, period = null),
                recordItem(id = 2, name = "B", priority = 20, period = GoalPeriod.WEEK),
                recordItem(id = 3, name = "C", priority = 50, period = GoalPeriod.DAY),
                recordItem(id = 4, name = "D", priority = 90, period = null),
            ),
        )
        assertEquals(listOf(3L, 2L, 1L, 4L), ordered.map(RecordItem::id))
    }

    @Test
    fun managedRecordItemsOnlyContainTheRequestedMode() {
        val items = listOf(
            recordItem(id = 1, name = "Water", priority = 1, period = GoalPeriod.DAY),
            recordItem(id = 2, name = "Temperature", priority = 2, period = null)
                .copy(mode = RecordMode.MEASUREMENT),
            recordItem(id = 3, name = "Reading", priority = 3, period = GoalPeriod.WEEK),
        )

        assertEquals(
            listOf(3L, 1L),
            managedRecordItems(items, RecordMode.ACCUMULATE).map(RecordItem::id),
        )
        assertEquals(
            listOf(2L),
            managedRecordItems(items, RecordMode.MEASUREMENT).map(RecordItem::id),
        )
    }

    @Test
    fun focusGoalsUsePriorityAndIncludeAllConfiguredGoals() {
        val focus = goalItems(
            listOf(
                recordItem(id = 1, name = "A", priority = 100, period = null),
                recordItem(id = 2, name = "B", priority = 20, period = GoalPeriod.WEEK),
                recordItem(id = 3, name = "C", priority = 50, period = GoalPeriod.DAY),
                recordItem(id = 4, name = "D", priority = 40, period = GoalPeriod.DAY),
                recordItem(id = 5, name = "E", priority = 10, period = GoalPeriod.WEEK),
            ),
        )

        assertEquals(listOf(3L, 4L, 2L, 5L), focus.map(RecordItem::id))
    }

    @Test
    fun goalItemsSeparatePeriodsAndKeepPriorityOrder() {
        val items = listOf(
            recordItem(id = 1, name = "A", priority = 10, period = GoalPeriod.DAY),
            recordItem(id = 2, name = "B", priority = 30, period = GoalPeriod.WEEK),
            recordItem(id = 3, name = "C", priority = 20, period = GoalPeriod.DAY),
        )

        assertEquals(listOf(3L, 1L), goalItems(items, GoalPeriod.DAY).map(RecordItem::id))
        assertEquals(listOf(2L), goalItems(items, GoalPeriod.WEEK).map(RecordItem::id))
    }

    @Test
    fun goalItemsExcludeMeasurements() {
        val measurement = recordItem(
            id = 1,
            name = "Temperature",
            priority = 100,
            period = GoalPeriod.DAY,
        ).copy(mode = RecordMode.MEASUREMENT)
        val accumulation = recordItem(
            id = 2,
            name = "Water",
            priority = 1,
            period = GoalPeriod.DAY,
        )

        assertEquals(listOf(2L), goalItems(listOf(measurement, accumulation)).map(RecordItem::id))
    }

    @Test
    fun goalProgressFractionClampsAtBounds() {
        assertEquals(0f, goalProgressFraction(-2.0, 10.0), 0f)
        assertEquals(0.5f, goalProgressFraction(5.0, 10.0), 0f)
        assertEquals(1f, goalProgressFraction(15.0, 10.0), 0f)
    }

    @Test
    fun goalProgressRatioAndLapSupportOverflow() {
        assertEquals(0.0, goalProgressRatio(0.0, 10.0), 0.0)
        assertEquals(0.5, goalProgressRatio(5.0, 10.0), 0.0)
        assertEquals(1.5, goalProgressRatio(15.0, 10.0), 0.0)
        assertEquals(0.5f, goalProgressLapFraction(0.5), 0f)
        assertEquals(1f, goalProgressLapFraction(1.0), 0f)
        assertEquals(0.5f, goalProgressLapFraction(1.5), 0f)
        assertEquals(1f, goalProgressLapFraction(2.0), 0f)
        assertEquals(150, goalProgressPercent(1.5))
    }

    @Test
    fun todayRecordValuesSumEntriesByItem() {
        val values = todayRecordValues(
            listOf(
                recordEntry(id = 1, itemId = 7, value = 2.5),
                recordEntry(id = 2, itemId = 7, value = 1.5),
                recordEntry(id = 3, itemId = 8, value = 3.0),
            ),
        )

        assertEquals(4.0, values[7] ?: 0.0, 0.0)
        assertEquals(3.0, values[8] ?: 0.0, 0.0)
    }

    @Test
    fun nextTaskStepTitleReturnsFirstIncompleteStep() {
        val task = TodoTask(
            id = 1,
            title = "Plan",
            horizon = com.wswensw.woolbox.record.model.TaskHorizon.TODAY,
        completedAt = null,
            createdAt = 0,
            updatedAt = 0,
            steps = listOf(
                TodoStep(1, 1, "Done", completedAt = 1, createdAt = 0, updatedAt = 0),
                TodoStep(2, 1, "Review", completedAt = null, createdAt = 0, updatedAt = 0),
                TodoStep(3, 1, "Send", completedAt = null, createdAt = 0, updatedAt = 0),
            ),
        )

        assertEquals("Review", nextTaskStepTitle(task))
        assertEquals(
            null,
            nextTaskStepTitle(task.copy(steps = task.steps.map { it.copy(completedAt = 1) })),
        )
    }


    @Test
    fun focusGoalCompletionCountsReachedGoals() {
        val goals = listOf(
            recordItem(id = 1, name = "A", priority = 10, period = GoalPeriod.DAY, goalValue = 10.0),
            recordItem(id = 2, name = "B", priority = 9, period = GoalPeriod.WEEK, goalValue = 20.0),
            recordItem(id = 3, name = "C", priority = 8, period = GoalPeriod.DAY, goalValue = 5.0),
        )
        val completion = focusGoalCompletion(
            goals = goals,
            values = mapOf(1L to 10.0, 2L to 5.0, 3L to 8.0),
        )
        assertEquals(2, completion.done)
        assertEquals(3, completion.total)
        assertEquals(2f / 3f, completion.fraction, 0.0001f)
    }

    @Test
    fun dayStatusLabelCoversEmptyPartialAndComplete() {
        assertEquals("", dayStatusLabel(FocusGoalCompletion(0, 0)))
        assertEquals("0 of 2 goals done", dayStatusLabel(FocusGoalCompletion(0, 2)))
        assertEquals("1 of 2 goals done", dayStatusLabel(FocusGoalCompletion(1, 2)))
        assertEquals("All goals done", dayStatusLabel(FocusGoalCompletion(2, 2)))
    }

    @Test
    fun crossedGoalThresholdOnlyOnFirstCrossing() {
        assertEquals(true, crossedGoalThreshold(9.0, 10.0, 10.0))
        assertEquals(false, crossedGoalThreshold(10.0, 12.0, 10.0))
        assertEquals(false, crossedGoalThreshold(3.0, 4.0, 10.0))
        assertEquals(false, crossedGoalThreshold(0.0, 1.0, null))
    }


    private fun recordItem(
        id: Long,
        name: String,
        priority: Int,
        period: GoalPeriod?,
        goalValue: Double? = period?.let { 10.0 },
        description: String = "",
    ) = RecordItem(
        id = id,
        name = name,
        description = description,
        valueType = RecordValueType.NUMBER,
        defaultValue = 1.0,
        stepValue = 1.0,
        unit = "count",
        goalValue = goalValue,
        goalPeriod = period,
        priority = priority,
        archived = false,
        windows = emptyList(),
        createdAt = 0,
        updatedAt = 0,
    )

    private fun recordEntry(
        id: Long,
        itemId: Long,
        value: Double,
    ) = RecordEntry(
        id = id,
        itemId = itemId,
        occurredAt = 0,
        localDate = "2026-07-22",
        localMinute = 0,
        value = value,
        createdAt = 0,
        itemName = "Item",
        itemUnit = "count",
        itemValueType = RecordValueType.NUMBER,
    )

    @Test
    fun recordsDayOverviewHidesWhenEmpty() {
        val overview = recordsDayOverview(
            tasks = emptyList(),
            items = emptyList(),
            goalValues = emptyMap(),
        )
        assertEquals(false, overview.isVisible)
        assertEquals("", overview.label)
    }

    @Test
    fun recordsDayOverviewSummarizesTasksAndGoals() {
        val tasks = listOf(
            todoTask(id = 1, completedSteps = 2, totalSteps = 2),
            todoTask(id = 2, completedSteps = 0, totalSteps = 1),
        )
        val items = listOf(
            recordItem(id = 10, name = "Water", priority = 1, period = GoalPeriod.DAY)
                .copy(goalValue = 8.0),
            recordItem(id = 11, name = "Run", priority = 2, period = GoalPeriod.DAY)
                .copy(goalValue = 5.0),
            recordItem(id = 12, name = "Note", priority = 3, period = null),
        )
        val overview = recordsDayOverview(
            tasks = tasks,
            items = items,
            goalValues = mapOf(10L to 8.0, 11L to 1.0),
        )
        assertEquals(true, overview.isVisible)
        assertEquals(1, overview.taskDone)
        assertEquals(2, overview.taskTotal)
        assertEquals(1, overview.goalDone)
        assertEquals(2, overview.goalTotal)
        assertEquals("Tasks 1/2 · Goals 1/2", overview.label)
    }

    @Test
    fun crossedGoalThresholdOnlyOnRisingEdge() {
        assertEquals(true, crossedGoalThreshold(previous = 7.0, current = 8.0, target = 8.0))
        assertEquals(false, crossedGoalThreshold(previous = 8.0, current = 9.0, target = 8.0))
        assertEquals(false, crossedGoalThreshold(previous = 1.0, current = 2.0, target = null))
    }


    @Test
    fun progressColorStageMapsThresholds() {
        assertEquals(ProgressColorStage.IDLE, progressColorStage(0f))
        assertEquals(ProgressColorStage.EARLY, progressColorStage(0.2f))
        assertEquals(ProgressColorStage.MID, progressColorStage(0.5f))
        assertEquals(ProgressColorStage.LATE, progressColorStage(0.8f))
        assertEquals(ProgressColorStage.DONE, progressColorStage(1f))
        assertEquals(ProgressColorStage.DONE, progressColorStage(0.2f, completed = true))
    }

    @Test
    fun recentActivityProgressUsesActiveDayRatio() {
        assertEquals(0f, recentActivityProgress(listOf(false, false, false, false, false, false, false)), 0f)
        assertEquals(1f / 7f, recentActivityProgress(listOf(false, false, false, false, false, false, true)), 0f)
        assertEquals(3f / 7f, recentActivityProgress(listOf(true, false, true, false, false, false, true)), 0f)
        assertEquals(1f, recentActivityProgress(listOf(true, true, true, true, true, true, true)), 0f)
    }

    @Test
    fun measurementColorProgressUsesRange() {
        assertEquals(0f, measurementColorProgress(0.0, 100.0, null), 0f)
        assertEquals(0.5f, measurementColorProgress(0.0, 100.0, 50.0), 0f)
        assertEquals(1f, measurementColorProgress(0.0, 100.0, 150.0), 0f)
        assertEquals(1f / 3f, measurementColorProgress(null, null, 12.0), 0f)
    }

    @Test
    fun taskColorProgressUsesStepRatio() {
        val task = todoTask(id = 1, completedSteps = 1, totalSteps = 4)
        assertEquals(0.25f, taskColorProgress(task), 0f)
        assertEquals(1f, taskColorProgress(todoTask(id = 2, completedSteps = 2, totalSteps = 2)), 0f)
    }

}


private fun todoTask(id: Long, completedSteps: Int, totalSteps: Int): TodoTask {
    val steps = (0 until totalSteps).map { index ->
        TodoStep(
            id = id * 10 + index,
            taskId = id,
            title = "Step $index",
            completedAt = if (index < completedSteps) 1L else null,
            createdAt = 1L,
            updatedAt = 1L,
        )
    }
    return TodoTask(
        id = id,
        title = "Task $id",
        horizon = com.wswensw.woolbox.record.model.TaskHorizon.TODAY,
        completedAt = if (completedSteps >= totalSteps && totalSteps > 0) 1L else null,
        createdAt = 1L,
        updatedAt = 1L,
        steps = steps,
    )
}
