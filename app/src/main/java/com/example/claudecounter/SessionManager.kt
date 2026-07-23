package com.example.claudecounter

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for usage data and authentication state.
 * Implemented as a singleton so that LoginWebViewActivity, UsageViewModel,
 * UsagePollingService, and BootReceiver all share one StateFlow.
 */
class SessionManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- StateFlows (observed by UI) ----

    private val _usageState = MutableStateFlow(loadUsageState())
    val usageState: StateFlow<UsageState> = _usageState.asStateFlow()

    data class UsageState(
        val orgId: String? = null,
        val sessionCookie: String? = null,
        // 5-hour window
        val sessionUtilization: Double = 0.0,    // 0–100
        val sessionResetsAt: Long = 0L,           // epoch ms
        // 7-day window
        val weeklyUtilization: Double = 0.0,
        val weeklyResetsAt: Long = 0L,
        val lastFetchTime: Long = 0L,
        val isApiBlocked: Boolean = false,
        val lastError: String? = null
    ) {
        val isLoggedIn: Boolean get() = orgId != null && sessionCookie != null
    }

    // ---- Auth helpers ----

    val orgId: String? get() = _usageState.value.orgId
    val sessionCookie: String? get() = _usageState.value.sessionCookie
    val isLoggedIn: Boolean get() = _usageState.value.isLoggedIn

    fun saveAuth(orgId: String, cookie: String) {
        prefs.edit()
            .putString(KEY_ORG_ID, orgId)
            .putString(KEY_SESSION_COOKIE, cookie)
            .apply()
        _usageState.value = _usageState.value.copy(orgId = orgId, sessionCookie = cookie)
    }

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_ORG_ID)
            .remove(KEY_SESSION_COOKIE)
            .apply()
        _usageState.value = UsageState()
    }

    /**
     * Force-reload the in-memory StateFlow from SharedPreferences.
     * Call this when returning from another activity that may have written prefs.
     */
    fun reloadFromPrefs() {
        _usageState.value = loadUsageState()
    }

    // ---- Usage update ----

    /** Previous reset timestamps, used to detect a rollover (for notifications). */
    var lastSessionResetsAt: Long = prefs.getLong(KEY_SESSION_RESETS_AT, 0L)
        private set
    var lastWeeklyResetsAt: Long = prefs.getLong(KEY_WEEKLY_RESETS_AT, 0L)
        private set

    fun updateUsage(data: ClaudeApiService.UsageData) {
        val sessionUtil = data.fiveHour?.utilization ?: _usageState.value.sessionUtilization
        val sessionResets = data.fiveHour?.resetsAt?.let { parseIso8601(it) }
            ?: _usageState.value.sessionResetsAt
        val weeklyUtil = data.sevenDay?.utilization ?: _usageState.value.weeklyUtilization
        val weeklyResets = data.sevenDay?.resetsAt?.let { parseIso8601(it) }
            ?: _usageState.value.weeklyResetsAt
        val now = System.currentTimeMillis()

        // Remember previous for rollover detection
        lastSessionResetsAt = _usageState.value.sessionResetsAt
        lastWeeklyResetsAt = _usageState.value.weeklyResetsAt

        prefs.edit()
            .putFloat(KEY_SESSION_UTIL, sessionUtil.toFloat())
            .putLong(KEY_SESSION_RESETS_AT, sessionResets)
            .putFloat(KEY_WEEKLY_UTIL, weeklyUtil.toFloat())
            .putLong(KEY_WEEKLY_RESETS_AT, weeklyResets)
            .putLong(KEY_LAST_FETCH_TIME, now)
            .apply()

        _usageState.value = _usageState.value.copy(
            sessionUtilization = sessionUtil,
            sessionResetsAt = sessionResets,
            weeklyUtilization = weeklyUtil,
            weeklyResetsAt = weeklyResets,
            lastFetchTime = now
        )
    }

    // ---- Error state ----

    fun setApiBlocked(blocked: Boolean, errorMessage: String? = null) {
        prefs.edit()
            .putBoolean(KEY_API_BLOCKED, blocked)
            .putString(KEY_LAST_ERROR, errorMessage)
            .apply()
        _usageState.value = _usageState.value.copy(
            isApiBlocked = blocked,
            lastError = errorMessage
        )
    }

    fun clearError() {
        prefs.edit()
            .putBoolean(KEY_API_BLOCKED, false)
            .remove(KEY_LAST_ERROR)
            .apply()
        _usageState.value = _usageState.value.copy(
            isApiBlocked = false,
            lastError = null
        )
    }

    // ---- Internal helpers ----

    private fun loadUsageState(): UsageState = UsageState(
        orgId = prefs.getString(KEY_ORG_ID, null),
        sessionCookie = prefs.getString(KEY_SESSION_COOKIE, null),
        sessionUtilization = prefs.getFloat(KEY_SESSION_UTIL, 0f).toDouble(),
        sessionResetsAt = prefs.getLong(KEY_SESSION_RESETS_AT, 0L),
        weeklyUtilization = prefs.getFloat(KEY_WEEKLY_UTIL, 0f).toDouble(),
        weeklyResetsAt = prefs.getLong(KEY_WEEKLY_RESETS_AT, 0L),
        lastFetchTime = prefs.getLong(KEY_LAST_FETCH_TIME, 0L),
        isApiBlocked = prefs.getBoolean(KEY_API_BLOCKED, false),
        lastError = prefs.getString(KEY_LAST_ERROR, null)
    )

    companion object {
        private const val PREFS_NAME = "claude_counter_prefs"
        private const val KEY_ORG_ID = "org_id"
        private const val KEY_SESSION_COOKIE = "session_cookie"
        private const val KEY_SESSION_UTIL = "session_util"
        private const val KEY_SESSION_RESETS_AT = "session_resets_at"
        private const val KEY_WEEKLY_UTIL = "weekly_util"
        private const val KEY_WEEKLY_RESETS_AT = "weekly_resets_at"
        private const val KEY_LAST_FETCH_TIME = "last_fetch_time"
        private const val KEY_API_BLOCKED = "api_blocked"
        private const val KEY_LAST_ERROR = "last_error"

        fun parseIso8601(text: String): Long = try {
            java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (_: Exception) { 0L }

        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
