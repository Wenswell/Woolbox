package com.wswensw.woolbox.record.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.DailyPlan
import com.wswensw.woolbox.record.model.DailyPlanState
import com.wswensw.woolbox.record.model.RecordsClock
import com.wswensw.woolbox.record.model.TodoStep
import com.wswensw.woolbox.record.model.TodoTask
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
    fun todayExposesEachDailyPlanState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 8, 18)
        val tasks = listOf(task())
        var plan by mutableStateOf<DailyPlan?>(null)

        compose.setContent {
            AppTheme(useDynamicColor = false) {
                RecordsScreen(
                    state = RecordsUiState(
                        isLoading = false,
                        hasSnapshot = true,
                        tasks = tasks,
                        dailyPlan = plan,
                    ),
                    clock = mutableStateOf(RecordsClock(today, 12 * 60)),
                    onPlanToday = {},
                    onManageTasks = {},
                    onManageAccumulates = {},
                    onManageMeasurements = {},
                    onConfigureConnection = {},
                    onRetry = {},
                    onRecordItem = {},
                    onCompleteStep = { _, _, complete -> complete(null) },
                    onOpenMeasurement = {},
                    notificationsEnabled = true,
                    onEnableNotifications = {},
                    onStartPlan = { _, complete -> complete(null) },
                    onReviewPlan = { _, _, complete -> complete(null) },
                    onExtraRound = { _, _, complete -> complete(null) },
                    onEndPlan = { _, complete -> complete(null) },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.no_plan_today)).assertIsDisplayed()

        compose.runOnIdle { plan = plan(today, DailyPlanState.READY) }
        compose.onNodeWithText(context.getString(R.string.first_move_done)).assertIsDisplayed()

        compose.runOnIdle {
            plan = plan(today, DailyPlanState.ACTIVE).copy(
                firstMoveCompletedAt = 1,
                currentTaskId = 1,
                nextReviewAt = 0,
            )
        }
        compose.onNodeWithText(context.getString(R.string.hourly_review)).assertIsDisplayed()

        compose.runOnIdle {
            plan = plan(today, DailyPlanState.ENOUGH).copy(
                firstMoveCompletedAt = 1,
                completedRounds = 4,
            )
        }
        compose.onNodeWithText(context.getString(R.string.enough_for_today)).assertIsDisplayed()

        compose.runOnIdle {
            plan = plan(today, DailyPlanState.ENDED).copy(
                firstMoveCompletedAt = 1,
                endedAt = 2,
            )
        }
        compose.onNodeWithText(context.getString(R.string.today_ended)).assertIsDisplayed()
    }

    private fun plan(date: LocalDate, state: DailyPlanState) = DailyPlan(
        id = 1,
        localDate = date,
        firstMove = "Go to the library",
        firstMoveCompletedAt = null,
        primaryTaskId = 1,
        secondaryTaskId = null,
        currentTaskId = null,
        state = state,
        completedRounds = 0,
        nextReviewAt = null,
        revision = 1,
        endedAt = null,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun task() = TodoTask(
        id = 1,
        title = "Primary task",
        completedAt = null,
        createdAt = 0,
        updatedAt = 0,
        steps = listOf(TodoStep(1, 1, "First step", null, 0, 0)),
    )
}
