package com.example.claudecounter.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long Clawd has been alive since either window last hit 100%, plus the personal best. */
data class SurvivalState(
    val aliveSinceMs: Long,
    val recordMs: Long,
)

/**
 * Backs the "CLAWD VIVO HA Xd Yh · RECORDE Zd" banner in the Clawd tab (fase 7
 * plan). A single global streak, not one per window — either the 5-hour or the
 * weekly window hitting 100% resets it, matching the recommendation in
 * plans/fase7-lapide-fantasma-contador-sobrevivencia.md (simpler, and what the
 * approved preview showed). The record is kept forever, never reset.
 *
 * Plain (unencrypted) prefs on purpose — no credentials live here, unlike
 * [com.example.claudecounter.SessionManager].
 */
class SurvivalStatsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SurvivalState> = _state.asStateFlow()

    /**
     * Called on the rising edge of a window hitting 100% (see
     * [com.example.claudecounter.SessionManager.detectMomentCrossing], the same
     * spot that fires the "Clawd down" notification — so this can't spam on
     * every subsequent poll while a window stays pinned at 100%). Banks a new
     * record if this streak beat the old one, then starts the clock over.
     */
    fun recordDeath(now: Long) {
        val current = _state.value
        val lived = now - current.aliveSinceMs
        val newRecord = maxOf(current.recordMs, lived)
        prefs.edit()
            .putLong(KEY_ALIVE_SINCE, now)
            .putLong(KEY_RECORD_MS, newRecord)
            .apply()
        _state.value = SurvivalState(aliveSinceMs = now, recordMs = newRecord)
    }

    private fun load(): SurvivalState {
        val aliveSince = prefs.getLong(KEY_ALIVE_SINCE, -1L)
        if (aliveSince >= 0L) {
            return SurvivalState(aliveSinceMs = aliveSince, recordMs = prefs.getLong(KEY_RECORD_MS, 0L))
        }
        // First time this repo has ever loaded on this install: nothing has died
        // yet, so the streak starts counting from now rather than the epoch.
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_ALIVE_SINCE, now).apply()
        return SurvivalState(aliveSinceMs = now, recordMs = 0L)
    }

    companion object {
        private const val PREFS_NAME = "claude_counter_survival"
        private const val KEY_ALIVE_SINCE = "alive_since_ms"
        private const val KEY_RECORD_MS = "record_ms"

        @Volatile
        private var INSTANCE: SurvivalStatsRepository? = null

        fun getInstance(context: Context): SurvivalStatsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SurvivalStatsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
