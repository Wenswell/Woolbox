package com.wswensw.woolbox.record.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.CommitmentRole
import com.wswensw.woolbox.record.model.DailyPlan
import com.wswensw.woolbox.record.model.DailyPlanPauseReason
import com.wswensw.woolbox.record.model.DailyPlanState
import com.wswensw.woolbox.record.model.RecordsClock
import com.wswensw.woolbox.record.model.TaskTemplate
import com.wswensw.woolbox.record.model.TaskTemplateStep
import com.wswensw.woolbox.record.model.TemplateCadence
import com.wswensw.woolbox.record.model.TodayCommitment
import com.wswensw.woolbox.record.model.TodayCommitmentStep
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
        val templates = listOf(template())
        val clock = mutableStateOf(RecordsClock(today, 12 * 60))
        var plan by mutableStateOf<DailyPlan?>(null)

        compose.setContent {
            AppTheme(useDynamicColor = false) {
                RecordsScreen(
                    state = RecordsUiState(
                        isLoading = false,
                        hasSnapshot = true,
                        templates = templates,
                        dailyPlan = plan,
                    ),
                    clock = clock,
                    onPlanToday = {},
                    onManageTemplates = {},
                    onManageAccumulates = {},
                    onManageMeasurements = {},
                    onConfigureConnection = {},
                    onRetry = {},
                    onRecordItem = {},
                    onCompleteStep = { _, _, _, complete -> complete(null) },
                    onOpenCommitment = {},
                    onOpenMeasurement = {},
                    notificationsEnabled = true,
                    onEnableNotifications = {},
                    onStartPlan = { _, complete -> complete(null) },
                    onReviewPlan = { _, _, complete -> complete(null) },
                    onContinuePlan = { _, _, complete -> complete(null) },
                    onAddCommitment = { _, _, complete -> complete(null) },
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
                currentCommitmentId = 1,
                nextReviewAt = 0,
            )
        }
        compose.onNodeWithText(context.getString(R.string.hourly_review)).assertIsDisplayed()

        compose.runOnIdle {
            plan = plan(today, DailyPlanState.PAUSED).copy(
                firstMoveCompletedAt = 1,
                pauseReason = DailyPlanPauseReason.TARGET_REACHED,
                completedRounds = 4,
            )
        }
        compose.onNodeWithText(context.getString(R.string.enough_for_today)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.continue_one_more_round)).assertIsDisplayed()

        compose.runOnIdle {
            plan = plan(today, DailyPlanState.PAUSED).copy(
                firstMoveCompletedAt = 1,
                pauseReason = DailyPlanPauseReason.COMMITMENTS_COMPLETED,
                commitments = listOf(commitment(completedAt = 1)),
            )
        }
        compose.onNodeWithText(context.getString(R.string.commitments_completed)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.completed_before_first_review)).assertIsDisplayed()
        compose.onAllNodesWithText(context.getString(R.string.continue_one_more_round)).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.add_commitment)).assertIsDisplayed()

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
        currentCommitmentId = null,
        state = state,
        pauseReason = null,
        completedRounds = 0,
        nextReviewAt = null,
        revision = 1,
        endedAt = null,
        createdAt = 0,
        updatedAt = 0,
        commitments = listOf(commitment()),
    )

    private fun template() = TaskTemplate(
        id = 1,
        title = "Primary template",
        cadence = TemplateCadence.MANUAL,
        archived = false,
        createdAt = 0,
        updatedAt = 0,
        steps = listOf(TaskTemplateStep(1, 1, "First step", 0, 0, 0)),
    )

    private fun commitment(completedAt: Long? = null) = TodayCommitment(
        id = 1,
        planId = 1,
        templateId = 1,
        role = CommitmentRole.PRIMARY,
        position = 0,
        title = "Primary commitment",
        cadence = TemplateCadence.MANUAL,
        completedAt = completedAt,
        createdAt = 0,
        updatedAt = 0,
        steps = listOf(TodayCommitmentStep(1, 1, "First step", 0, completedAt, 0, 0)),
    )
}
