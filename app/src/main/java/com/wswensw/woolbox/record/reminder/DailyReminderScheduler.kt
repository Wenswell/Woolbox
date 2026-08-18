package com.wswensw.woolbox.record.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.wswensw.woolbox.record.model.DailyPlan
import com.wswensw.woolbox.record.model.DailyPlanState
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class DailyReminderSpec(
    val localDate: String,
    val revision: Int,
    val nextReviewAt: Long,
)

fun dailyReminderSpec(plan: DailyPlan?, today: LocalDate): DailyReminderSpec? {
    if (plan?.state != DailyPlanState.ACTIVE || plan.localDate != today) return null
    val nextReviewAt = plan.nextReviewAt ?: return null
    return DailyReminderSpec(plan.localDate.toString(), plan.revision, nextReviewAt)
}

class DailyReminderScheduler(context: Context) {
    private val applicationContext = context.applicationContext
    private val workManager = WorkManager.getInstance(applicationContext)

    fun reconcile(plan: DailyPlan?, now: Long = System.currentTimeMillis(), today: LocalDate = LocalDate.now()) {
        val spec = dailyReminderSpec(plan, today)
        if (spec == null) {
            workManager.cancelUniqueWork(UNIQUE_WORK)
            DailyReminderNotifications.cancelReview(applicationContext)
            return
        }
        if (spec.nextReviewAt > now) DailyReminderNotifications.cancelReview(applicationContext)
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay((spec.nextReviewAt - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(KEY_LOCAL_DATE, spec.localDate)
                    .putInt(KEY_REVISION, spec.revision)
                    .putLong(KEY_NEXT_REVIEW_AT, spec.nextReviewAt)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        internal const val KEY_LOCAL_DATE = "local_date"
        internal const val KEY_REVISION = "revision"
        internal const val KEY_NEXT_REVIEW_AT = "next_review_at"
        private const val UNIQUE_WORK = "records-daily-review"
    }
}
