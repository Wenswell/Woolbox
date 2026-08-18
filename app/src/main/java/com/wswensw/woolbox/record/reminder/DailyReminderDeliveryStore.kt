package com.wswensw.woolbox.record.reminder

import android.content.Context

internal fun DailyReminderSpec.deliveryToken(): String =
    "$localDate|$revision|$nextReviewAt"

internal class DailyReminderDeliveryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun wasDelivered(spec: DailyReminderSpec): Boolean =
        preferences.getString(KEY_LAST_DELIVERY, null) == spec.deliveryToken()

    fun markDelivered(spec: DailyReminderSpec) {
        check(preferences.edit().putString(KEY_LAST_DELIVERY, spec.deliveryToken()).commit()) {
            "Unable to persist daily reminder delivery."
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "daily_reminder_delivery"
        const val KEY_LAST_DELIVERY = "last_delivery"
    }
}
