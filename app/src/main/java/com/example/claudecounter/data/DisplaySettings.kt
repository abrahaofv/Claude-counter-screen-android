package com.example.claudecounter.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User-tunable visual toggles — see [DisplaySettings]. */
data class DisplayConfig(
    val showMascots: Boolean = true,
    val showAgoraMascots: Boolean = true,
    /**
     * Off by default: once the mascot on the Agora cards got the V3 rework,
     * the dedicated Clawd tab ended up looking like a near-duplicate of the
     * first screen. Code stays in [com.example.claudecounter.ui.tiles.TileClawd]
     * either way — this only removes the page from the pager.
     */
    val showClawdTab: Boolean = false,
    /** Which page the pager opens on. Only takes effect while [showClawdTab] is
     * also on — if the Clawd tab is hidden, the app always opens on Agora. */
    val openClawdFirst: Boolean = false,
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

    fun setShowAgoraMascots(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_AGORA_MASCOTS, enabled).apply()
        _config.value = _config.value.copy(showAgoraMascots = enabled)
    }

    fun setShowClawdTab(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CLAWD_TAB, enabled).apply()
        _config.value = _config.value.copy(showClawdTab = enabled)
    }

    fun setOpenClawdFirst(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OPEN_CLAWD_FIRST, enabled).apply()
        _config.value = _config.value.copy(openClawdFirst = enabled)
    }

    private fun load() = DisplayConfig(
        showMascots = prefs.getBoolean(KEY_SHOW_MASCOTS, true),
        showAgoraMascots = prefs.getBoolean(KEY_SHOW_AGORA_MASCOTS, true),
        showClawdTab = prefs.getBoolean(KEY_SHOW_CLAWD_TAB, false),
        openClawdFirst = prefs.getBoolean(KEY_OPEN_CLAWD_FIRST, false),
    )

    companion object {
        private const val PREFS_NAME = "claude_counter_display"
        private const val KEY_SHOW_MASCOTS = "show_mascots"
        private const val KEY_SHOW_AGORA_MASCOTS = "show_agora_mascots"
        private const val KEY_SHOW_CLAWD_TAB = "show_clawd_tab"
        private const val KEY_OPEN_CLAWD_FIRST = "open_clawd_first"

        @Volatile
        private var INSTANCE: DisplaySettings? = null

        fun getInstance(context: Context): DisplaySettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DisplaySettings(context.applicationContext).also { INSTANCE = it }
            }
    }
}
