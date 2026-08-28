package com.example.claudecounter

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.claudecounter.data.SurvivalStatsRepository
import com.example.claudecounter.data.UsageHistoryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which usage window a [MomentEvent] is about. */
enum class UsageWindow { SESSION, WEEKLY }

/**
 * Poll cadence currently in force. [ACTIVE] means token usage was seen recently,
 * so the service polls at the short interval; [IDLE] is the economical default.
 */
enum class PollMode { IDLE, ACTIVE }

/** What kind of moment a [MomentEvent] represents. */
enum class MomentKind {
    /** Utilization crossed 25/50/70/100% going up. */
    THRESHOLD,
    /** Utilization dropped sharply — the window reset and Clawd is back. */
    REVIVAL,
}

/**
 * Fired once when a window's utilization crosses 25/50/70/100% going up
 * ([MomentKind.THRESHOLD]), or drops back down after a reset
 * ([MomentKind.REVIVAL]) — drives [com.example.claudecounter.ui.MomentOverlay].
 */
data class MomentEvent(
    val window: UsageWindow,
    val threshold: Int,
    val pct: Float,
    val resetsAtMs: Long,
    val kind: MomentKind = MomentKind.THRESHOLD,
)

/**
 * Single source of truth for usage data and authentication state.
 * Implemented as a singleton so that LoginWebViewActivity, UsageViewModel,
 * UsagePollingService, and BootReceiver all share one StateFlow.
 */
class SessionManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = openPrefs(appContext)

    // ---- StateFlows (observed by UI) ----

    private val _usageState = MutableStateFlow(loadUsageState())
    val usageState: StateFlow<UsageState> = _usageState.asStateFlow()

    private val _momentEvents = MutableSharedFlow<MomentEvent>(extraBufferCapacity = 1)
    /** One-shot threshold-crossing events — collect to show [com.example.claudecounter.ui.MomentOverlay]. */
    val momentEvents: SharedFlow<MomentEvent> = _momentEvents.asSharedFlow()

    private val momentThresholds = intArrayOf(25, 50, 70, 100)

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
        val lastError: String? = null,
        // Adaptive polling — in-memory only, deliberately not persisted: after a
        // restart we have no idea whether the user is still working, so IDLE is
        // the right cold-start assumption.
        val pollMode: PollMode = PollMode.IDLE,
        val lastActivityTime: Long = 0L
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
        val prevSessionUtil = _usageState.value.sessionUtilization
        val prevWeeklyUtil = _usageState.value.weeklyUtilization
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

        // Strictly *rising* utilization means tokens were just spent. A drop is a
        // window rollover, not activity, so it must not trigger fast polling.
        val sawActivity = sessionUtil > prevSessionUtil || weeklyUtil > prevWeeklyUtil

        _usageState.value = _usageState.value.copy(
            sessionUtilization = sessionUtil,
            sessionResetsAt = sessionResets,
            weeklyUtilization = weeklyUtil,
            weeklyResetsAt = weeklyResets,
            lastFetchTime = now,
            pollMode = if (sawActivity) PollMode.ACTIVE else _usageState.value.pollMode,
            lastActivityTime = if (sawActivity) now else _usageState.value.lastActivityTime
        )

        // Feed the local history used by the trend graph and hourly heatmap —
        // the API gives us a live snapshot only, so this app builds its own
        // timeline by sampling on every successful fetch.
        UsageHistoryRepository.getInstance(appContext)
            .recordSample(sessionUtil.toFloat(), weeklyUtil.toFloat(), now)

        // Revival takes precedence over a threshold crossing for the *same* window
        // in the same update: a reset that lands straight back up past 25% should
        // read as "he's alive again", not "uh oh, 25% already". The two windows
        // are otherwise independent — a weekly threshold crossing must still fire
        // even if the session window happened to revive in the same poll.
        if (!detectRevival(UsageWindow.SESSION, prevSessionUtil, sessionUtil, sessionResets)) {
            detectMomentCrossing(UsageWindow.SESSION, prevSessionUtil, sessionUtil, sessionResets)
        }
        if (!detectRevival(UsageWindow.WEEKLY, prevWeeklyUtil, weeklyUtil, weeklyResets)) {
            detectMomentCrossing(UsageWindow.WEEKLY, prevWeeklyUtil, weeklyUtil, weeklyResets)
        }
    }

    /**
     * Drops back to [PollMode.IDLE] once the activity burst has gone quiet for
     * [ACTIVE_LINGER_MS]. Called by the poller on every tick; returns the mode
     * now in force so the caller can schedule against it.
     */
    fun evaluatePollMode(now: Long = System.currentTimeMillis()): PollMode {
        val state = _usageState.value
        if (state.pollMode == PollMode.ACTIVE && now - state.lastActivityTime > ACTIVE_LINGER_MS) {
            _usageState.value = state.copy(pollMode = PollMode.IDLE)
            return PollMode.IDLE
        }
        return state.pollMode
    }

    /**
     * Emits a [MomentEvent] for the highest threshold newly crossed, if any.
     * Crossing 100% also fires a system notification — [detectMomentCrossing]
     * only runs on the rising transition, never on every subsequent poll while
     * still pinned at 100%, so this can't spam the user.
     */
    private fun detectMomentCrossing(window: UsageWindow, prevPct: Double, newPct: Double, resetsAtMs: Long) {
        val crossed = momentThresholds.filter { prevPct < it && newPct >= it }
        val top = crossed.maxOrNull() ?: return
        _momentEvents.tryEmit(MomentEvent(window, top, newPct.toFloat(), resetsAtMs))
        if (top == 100) {
            NotificationHelper.notifyClawdDown(appContext, window)
            SurvivalStatsRepository.getInstance(appContext).recordDeath(System.currentTimeMillis())
        }
    }

    /**
     * A window reset shows up here as utilization dropping sharply between two
     * polls — the API has no explicit "reset happened" signal, so a >=10 point
     * drop is used as the tell. 10 points is comfortably above the noise: since
     * `resets_at` is a sliding window, utilization can wobble a little between
     * polls, but a genuine reset drops it by tens of points at once (often to 0).
     * Emits [MomentKind.REVIVAL] and returns true if a revival was detected.
     */
    private fun detectRevival(window: UsageWindow, prevPct: Double, newPct: Double, resetsAtMs: Long): Boolean {
        if (prevPct - newPct < 10.0) return false
        _momentEvents.tryEmit(MomentEvent(window, 0, newPct.toFloat(), resetsAtMs, MomentKind.REVIVAL))
        return true
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
        private const val TAG = "SessionManager"
        private const val PREFS_NAME = "claude_counter_prefs_enc"
        private const val LEGACY_PREFS_NAME = "claude_counter_prefs"
        private const val KEY_ORG_ID = "org_id"
        private const val KEY_SESSION_COOKIE = "session_cookie"
        private const val KEY_SESSION_UTIL = "session_util"
        private const val KEY_SESSION_RESETS_AT = "session_resets_at"
        private const val KEY_WEEKLY_UTIL = "weekly_util"
        private const val KEY_WEEKLY_RESETS_AT = "weekly_resets_at"
        private const val KEY_LAST_FETCH_TIME = "last_fetch_time"
        private const val KEY_API_BLOCKED = "api_blocked"
        private const val KEY_LAST_ERROR = "last_error"

        /** How long [PollMode.ACTIVE] survives with no further usage detected. */
        const val ACTIVE_LINGER_MS = 3L * 60_000L

        /**
         * Cadence [UsagePollingService] retries the fetch while [UsageState.isApiBlocked]
         * is true. Infrequent enough to stay battery-conscious, but bounded — a stuck
         * 403 (which may just be a stale session cookie, not a permanent Anthropic
         * block) is retried instead of freezing the poll loop forever.
         */
        const val BLOCKED_RETRY_INTERVAL_MS = 45L * 60_000L

        /**
         * The session cookie is a full-account credential, so it is stored in
         * [EncryptedSharedPreferences] (AES-256-GCM, key held in the Android Keystore)
         * rather than a plaintext prefs file.
         *
         * Any pre-existing plaintext file is deleted on first run — the user simply
         * logs in again, which is preferable to leaving a readable cookie on disk.
         * If the Keystore is unavailable (rare OEM breakage) we fall back to the plain
         * prefs so the app still works instead of crashing on launch.
         */
        private fun openPrefs(context: Context): SharedPreferences {
            context.deleteSharedPreferences(LEGACY_PREFS_NAME)
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences unavailable, falling back", e)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }

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
