package com.example.claudecounter.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User-tunable poll cadence — see [PollingSettings]. */
data class PollingConfig(
    val adaptiveEnabled: Boolean,
    val idleIntervalMs: Long,
    val activeIntervalMs: Long,
)

/**
 * How often [com.example.claudecounter.UsagePollingService] hits /usage.
 *
 * The endpoint is a quota-metadata read, so polling costs no tokens — but it
 * runs through a live WebView on claude.ai, so short intervals still cost
 * battery, data, and exposure to the bot detection that already 403'd this app
 * once. Hence two cadences: a slow one while nothing is happening, and a fast
 * one for the minutes right after usage is detected.
 *
 * Plain (unencrypted) prefs on purpose — no credentials live here, unlike
 * [com.example.claudecounter.SessionManager].
 */
class PollingSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<PollingConfig> = _config.asStateFlow()

    /** Current values without collecting the flow — used by the service when rescheduling. */
    val current: PollingConfig get() = _config.value

    fun setAdaptiveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADAPTIVE, enabled).apply()
        _config.value = _config.value.copy(adaptiveEnabled = enabled)
    }

    fun setIdleIntervalMs(ms: Long) {
        prefs.edit().putLong(KEY_IDLE_MS, ms).apply()
        _config.value = _config.value.copy(idleIntervalMs = ms)
    }

    fun setActiveIntervalMs(ms: Long) {
        prefs.edit().putLong(KEY_ACTIVE_MS, ms).apply()
        _config.value = _config.value.copy(activeIntervalMs = ms)
    }

    private fun load() = PollingConfig(
        adaptiveEnabled = prefs.getBoolean(KEY_ADAPTIVE, true),
        idleIntervalMs = prefs.getLong(KEY_IDLE_MS, DEFAULT_IDLE_MS),
        activeIntervalMs = prefs.getLong(KEY_ACTIVE_MS, DEFAULT_ACTIVE_MS),
    )

    companion object {
        private const val PREFS_NAME = "claude_counter_polling"
        private const val KEY_ADAPTIVE = "adaptive_enabled"
        private const val KEY_IDLE_MS = "idle_interval_ms"
        private const val KEY_ACTIVE_MS = "active_interval_ms"

        const val DEFAULT_IDLE_MS = 2L * 60_000L
        const val DEFAULT_ACTIVE_MS = 15_000L

        /** Selectable cadences, in the order the settings screen shows them. */
        val IDLE_OPTIONS_MS = listOf(60_000L, 2L * 60_000L, 5L * 60_000L, 10L * 60_000L)
        val ACTIVE_OPTIONS_MS = listOf(5_000L, 10_000L, 15_000L, 30_000L)

        /** Below this, the settings screen warns about battery / rate-limit cost. */
        const val AGGRESSIVE_THRESHOLD_MS = 10_000L

        /** "5s" / "2min" — the label used for every interval in the UI. */
        fun formatInterval(ms: Long): String =
            if (ms < 60_000L) "${ms / 1000}s" else "${ms / 60_000}min"

        @Volatile
        private var INSTANCE: PollingSettings? = null

        fun getInstance(context: Context): PollingSettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PollingSettings(context.applicationContext).also { INSTANCE = it }
            }
    }
}
