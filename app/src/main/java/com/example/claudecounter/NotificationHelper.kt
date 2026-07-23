package com.example.claudecounter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object NotificationHelper {

    const val CHANNEL_FOREGROUND = "cc_foreground"
    const val CHANNEL_SESSION_RESET = "cc_session_reset"
    const val CHANNEL_WEEKLY_RESET = "cc_weekly_reset"

    private const val NOTIF_SESSION_RESET_ID = 2001
    private const val NOTIF_WEEKLY_RESET_ID = 2002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOREGROUND,
                "Background monitor",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "Keeps Claude Counter running in the background" }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SESSION_RESET,
                "Session reset alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifies you when your 5-hour Claude session resets" }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WEEKLY_RESET,
                "Weekly reset alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifies you when your 7-day Claude usage window resets" }
        )
    }

    fun notifySessionReset(context: Context) {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_SESSION_RESET)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🔄 Claude session reset!")
            .setContentText("Your 5-hour message window has reset. You have full capacity again.")
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_SESSION_RESET_ID, notif)
    }

    fun notifyWeeklyReset(context: Context) {
        val intent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_WEEKLY_RESET)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📅 Claude weekly usage reset!")
            .setContentText("Your 7-day usage window has reset. Fresh start!")
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_WEEKLY_RESET_ID, notif)
    }
}
