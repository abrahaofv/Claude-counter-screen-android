package com.example.claudecounter.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User-tunable visual toggles — see [DisplaySettings]. */
data class DisplayConfig(
    val showMascots: Boolean = true,
)

/**
 * Whether Clawd (the animated mascot) is shown across the app — the dedicated
 * "Clawd" tile and the mini-mascot on the Agora cards and header. Kept separate
 * from [PollingSettings], which is documented as being only about poll cadence.
 *
 * Plain (unencrypted) prefs on purpose — no credentials live here, unlike
 * [com.example.claudecounter.SessionManager].
 */
class DisplaySettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<DisplayConfig> = _config.asStateFlow()

    /** Current value without collecting the flow. */
    val current: DisplayConfig get() = _config.value

    fun setShowMascots(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_MASCOTS, enabled).apply()
        _config.value = _config.value.copy(showMascots = enabled)
    }

    private fun load() = DisplayConfig(
        showMascots = prefs.getBoolean(KEY_SHOW_MASCOTS, true),
    )

    companion object {
        private const val PREFS_NAME = "claude_counter_display"
        private const val KEY_SHOW_MASCOTS = "show_mascots"

        @Volatile
        private var INSTANCE: DisplaySettings? = null

        fun getInstance(context: Context): DisplaySettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DisplaySettings(context.applicationContext).also { INSTANCE = it }
            }
    }
}
