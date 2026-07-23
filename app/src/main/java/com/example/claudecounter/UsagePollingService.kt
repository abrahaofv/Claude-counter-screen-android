package com.example.claudecounter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

/**
 * Foreground service that polls the claude.ai /usage endpoint every 2 minutes.
 * Sends a notification when a session or weekly window resets.
 */
class UsagePollingService : Service() {

    private val tag = "UsagePollingService"
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sessionManager: SessionManager

    private val pollRunnable = object : Runnable {
        override fun run() {
            pollUsage()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager.getInstance(applicationContext)
        NotificationHelper.createChannels(this)
        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification(null))
        Log.d(tag, "Service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun pollUsage() {
        val orgId = sessionManager.orgId ?: return
        val cookie = sessionManager.sessionCookie ?: return

        // Don't poll if we already know the API is blocked
        if (sessionManager.usageState.value.isApiBlocked) {
            Log.d(tag, "API blocked, skipping poll")
            return
        }

        Thread {
            val result = ClaudeApiService.fetchUsage(orgId, cookie)
            if (result.success && result.data != null) {
                val prevSessionReset = sessionManager.lastSessionResetsAt
                val prevWeeklyReset = sessionManager.lastWeeklyResetsAt

                sessionManager.clearError()
                sessionManager.updateUsage(result.data)

                val newSessionReset = sessionManager.usageState.value.sessionResetsAt
                val newWeeklyReset = sessionManager.usageState.value.weeklyResetsAt

                checkAndNotifyReset(prevSessionReset, newSessionReset, isWeekly = false)
                checkAndNotifyReset(prevWeeklyReset, newWeeklyReset, isWeekly = true)

                // Refresh persistent notification with latest usage
                updateForegroundNotification(result.data)
            } else if (result.isApiBlocked) {
                Log.w(tag, "API blocked (403) — stopping polling")
                sessionManager.setApiBlocked(true, result.errorMessage)
                handler.removeCallbacks(pollRunnable)
                updateForegroundNotificationBlocked()
            }
        }.start()
    }

    /**
     * Detects a genuine window reset. The API's resets_at is a sliding window that
     * moves forward with every new message, so we can't simply compare timestamps.
     * A real reset occurs when:
     *   1. We had a previous reset time recorded, AND
     *   2. That previous reset time is now in the past (the old window expired), AND
     *   3. The new reset time is different (a brand-new window has started).
     */
    private fun checkAndNotifyReset(prevResetsAt: Long, newResetsAt: Long, isWeekly: Boolean) {
        val now = System.currentTimeMillis()
        if (prevResetsAt > 0L && now > prevResetsAt && newResetsAt != prevResetsAt) {
            if (isWeekly) {
                NotificationHelper.notifyWeeklyReset(this)
                Log.d(tag, "Weekly window reset detected")
            } else {
                NotificationHelper.notifySessionReset(this)
                Log.d(tag, "Session window reset detected")
            }
        }
    }

    private fun buildForegroundNotification(data: ClaudeApiService.UsageData?): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)

        if (data != null) {
            // Build rich custom notification with progress bars
            val customView = RemoteViews(packageName, R.layout.notification_usage)

            // Session row
            val sessionPct = data.fiveHour?.utilization ?: 0.0
            customView.setProgressBar(R.id.session_progress, 1000, (sessionPct * 10).toInt(), false)
            customView.setTextViewText(R.id.session_pct, "${"%.0f".format(sessionPct)}%")
            val sessionTime = data.fiveHour?.resetsAt?.let { formatCountdown(it) } ?: ""
            customView.setTextViewText(R.id.session_time, sessionTime)

            // Weekly row
            val weeklyPct = data.sevenDay?.utilization ?: 0.0
            customView.setProgressBar(R.id.weekly_progress, 1000, (weeklyPct * 10).toInt(), false)
            customView.setTextViewText(R.id.weekly_pct, "${"%.0f".format(weeklyPct)}%")
            val weeklyTime = data.sevenDay?.resetsAt?.let { formatCountdown(it) } ?: ""
            customView.setTextViewText(R.id.weekly_time, weeklyTime)

            builder.setCustomContentView(customView)
                .setCustomBigContentView(customView)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setContentTitle("Claude Counter")
        } else {
            builder.setContentTitle("Claude Counter")
                .setContentText("Monitoring usage…")
        }

        return builder.build()
    }

    private fun updateForegroundNotification(data: ClaudeApiService.UsageData) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification(data))
    }

    private fun updateForegroundNotificationBlocked() {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setContentTitle("Claude Counter")
            .setContentText("API restricted — use browser extension instead")
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(FOREGROUND_NOTIFICATION_ID, notif)
    }

    private fun formatCountdown(isoResetTime: String): String {
        val resetMs = SessionManager.parseIso8601(isoResetTime)
        if (resetMs <= 0L) return ""
        val diff = resetMs - System.currentTimeMillis()
        if (diff <= 0) return "resetting"
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
        return when {
            hours >= 24 -> "${hours / 24}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2L * 60L * 1000L  // 2 minutes
        private const val FOREGROUND_NOTIFICATION_ID = 1001
    }
}
