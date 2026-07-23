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
import com.example.claudecounter.data.PollingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Foreground service that polls the claude.ai /usage endpoint on an adaptive
 * cadence: the slow idle interval while nothing is happening, and the short
 * active interval for the few minutes after token usage is detected (see
 * [PollingSettings] and [SessionManager.evaluatePollMode]).
 * Sends a notification when a session or weekly window resets.
 */
class UsagePollingService : Service() {

    private val tag = "UsagePollingService"
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var sessionManager: SessionManager
    private lateinit var pollingSettings: PollingSettings

    private val pollRunnable = object : Runnable {
        override fun run() {
            pollUsage()
            // pollUsage() is async; the mode it may promote is picked up either by
            // rescheduleNow() from its callback or by the next tick.
            sessionManager.evaluatePollMode()
            scheduleNext()
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager.getInstance(applicationContext)
        pollingSettings = PollingSettings.getInstance(applicationContext)
        NotificationHelper.createChannels(this)
        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification(null))

        // A settings change must take effect immediately, otherwise a long
        // postDelayed already in flight would keep the old cadence for minutes.
        scope.launch {
            pollingSettings.config.drop(1).collect {
                Log.d(tag, "Polling settings changed, rescheduling")
                rescheduleNow()
            }
        }
        Log.d(tag, "Service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** The cadence in force right now, honouring the adaptive on/off switch. */
    private fun currentIntervalMs(): Long {
        val config = pollingSettings.current
        val active = config.adaptiveEnabled &&
            sessionManager.usageState.value.pollMode == PollMode.ACTIVE
        return if (active) config.activeIntervalMs else config.idleIntervalMs
    }

    private fun scheduleNext() {
        val interval = currentIntervalMs()
        handler.removeCallbacks(pollRunnable)
        handler.postDelayed(pollRunnable, interval)
    }

    /**
     * Re-arms the timer against the interval in force *now*, cancelling whatever
     * delay is pending. Used when the mode or the settings change mid-cycle so
     * the new cadence doesn't wait out the old one.
     */
    private fun rescheduleNow() = scheduleNext()

    private fun pollUsage() {
        val orgId = sessionManager.orgId ?: return

        // Don't poll if we already know the API is blocked
        if (sessionManager.usageState.value.isApiBlocked) {
            Log.d(tag, "API blocked, skipping poll")
            return
        }

        WebViewUsageFetcher.getInstance().fetchUsage(this, orgId) { json, error ->
            val result = ClaudeApiService.parseUsageResponse(json, error)
            if (result.success && result.data != null) {
                val prevSessionReset = sessionManager.lastSessionResetsAt
                val prevWeeklyReset = sessionManager.lastWeeklyResetsAt
                val prevMode = sessionManager.usageState.value.pollMode

                sessionManager.clearError()
                sessionManager.updateUsage(result.data)

                // updateUsage flips to ACTIVE the moment it sees usage rise —
                // switch to the fast cadence now instead of after the slow tick.
                if (prevMode != PollMode.ACTIVE &&
                    sessionManager.usageState.value.pollMode == PollMode.ACTIVE
                ) {
                    Log.d(tag, "Usage detected — switching to ACTIVE polling")
                    rescheduleNow()
                }

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
            } else {
                Log.w(tag, "Usage fetch failed: ${result.errorMessage}")
            }
        }
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
        private const val FOREGROUND_NOTIFICATION_ID = 1001
    }
}
