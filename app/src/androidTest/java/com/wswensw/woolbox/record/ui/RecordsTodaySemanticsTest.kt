package com.wswensw.woolbox.record.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.RecordsClock
import com.wswensw.woolbox.record.model.TodayTask
import com.wswensw.woolbox.record.model.TodayTaskStep
import com.wswensw.woolbox.ui.AppTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordsTodaySemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun todayShowsHourlyCheckinAndTaskProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val date = LocalDate.of(2026, 8, 18)
        val clock = mutableStateOf(RecordsClock(date, 12 * 60))
        val task = TodayTask(
            id = 1,
            localDate = date,
            templateId = null,
            sourceTaskId = null,
            title = "Plan release",
            position = 0,
            completedAt = null,
            revision = 0,
            createdAt = 0,
            updatedAt = 0,
            steps = listOf(
                TodayTaskStep(1, 1, "Build", 0, 1, 0, 0),
                TodayTaskStep(2, 1, "Verify", 1, null, 0, 0),
            ),
        )

        compose.setContent {
            AppTheme(useDynamicColor = false) {
                RecordsScreen(
                    state = RecordsUiState(
                        isLoading = false,
                        hasSnapshot = true,
                        todayTasks = listOf(task),
                    ),
                    clock = clock,
                    onAddTask = {},
                    onOpenTask = {},
                    onManageTemplates = {},
                    onManageGoals = {},
                    onManageAccumulates = {},
                    onManageMeasurements = {},
                    onConfigureConnection = {},
                    onRetry = {},
                    onRecordItem = {},
                    onOpenGoal = {},
                    onMarkAwake = { complete -> complete(null) },
                    onOpenHourlyCheckin = {},
                    onOpenMeasurement = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.mark_awake)).assertIsDisplayed()
        compose.onNodeWithText("Plan release").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.task_steps_progress, 1, 2)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.next_subtask, "Verify")).assertIsDisplayed()
    }
}
