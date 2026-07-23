package com.example.claudecounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the polling service on device boot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val session = SessionManager.getInstance(context)
            if (session.isLoggedIn) {
                val svc = Intent(context, UsagePollingService::class.java)
                context.startForegroundService(svc)
            }
        }
    }
}
