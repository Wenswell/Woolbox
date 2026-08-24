package com.wswensw.woolbox.record.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wswensw.woolbox.MainActivity
import com.wswensw.woolbox.R

object HourlyCheckinNotifications {
    const val EXTRA_OPEN_HOURLY_CHECKIN = "open_hourly_checkin"

    fun areEnabled(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context): Boolean {
        if (!areEnabled(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.hourly_checkin_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_HOURLY_CHECKIN, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_records_24)
                .setContentTitle(context.getString(R.string.hourly_checkin))
                .setContentText(context.getString(R.string.hourly_checkin_notification))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build(),
        )
        return true
    }

    private const val CHANNEL_ID = "records_hourly_checkin"
    private const val NOTIFICATION_ID = 29_002
    private const val REQUEST_CODE = 29_002
}
