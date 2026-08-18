package com.wswensw.woolbox.record.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wswensw.woolbox.record.data.RecordRepository
import java.time.LocalDate

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val localDate = inputData.getString(DailyReminderScheduler.KEY_LOCAL_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return Result.failure()
        val expected = DailyReminderSpec(
            localDate = localDate.toString(),
            revision = inputData.getInt(DailyReminderScheduler.KEY_REVISION, -1),
            nextReviewAt = inputData.getLong(DailyReminderScheduler.KEY_NEXT_REVIEW_AT, -1),
        )
        if (expected.revision < 0 || expected.nextReviewAt < 0) return Result.failure()
        if (localDate != LocalDate.now()) return Result.success()

        val plan = try {
            RecordRepository(applicationContext).getDailyPlan(localDate)
        } catch (_: Exception) {
            return Result.retry()
        }
        if (dailyReminderSpec(plan, localDate) != expected) return Result.success()
        if (!DailyReminderNotifications.areEnabled(applicationContext)) return Result.success()

        val deliveryStore = DailyReminderDeliveryStore(applicationContext)
        if (deliveryStore.wasDelivered(expected)) return Result.success()
        if (DailyReminderNotifications.showReview(applicationContext)) {
            deliveryStore.markDelivered(expected)
        }
        return Result.success()
    }
}
