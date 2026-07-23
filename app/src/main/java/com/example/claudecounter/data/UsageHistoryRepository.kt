package com.example.claudecounter.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One usage snapshot, taken every time a fetch succeeds. */
data class UsageSample(
    val timestampMs: Long,
    val sessionPct: Float,
    val weeklyPct: Float,
)

/**
 * Local, on-device history of usage samples — the raw material for the
 * "Janela de 5h" trend tile (Fase 5) and the "Ritmo por hora" heatmap (Fase 6).
 * The stick firmware gets this from onboard flash; this app has no equivalent
 * data source from the API, so it builds its own history starting from first run.
 *
 * Stored as a flat JSON array in a private app file (not SharedPreferences —
 * this can grow into the thousands of entries, which SharedPreferences handles
 * poorly). Retained for 31 days, matching the firmware's retention window.
 */
class UsageHistoryRepository private constructor(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    @Volatile
    private var cache: MutableList<UsageSample> = loadFromDisk().toMutableList()

    /** Snapshot of all retained samples, oldest first. */
    val samples: List<UsageSample>
        @Synchronized get() = cache.toList()

    @Synchronized
    fun recordSample(sessionPct: Float, weeklyPct: Float, timestampMs: Long = System.currentTimeMillis()) {
        cache.add(UsageSample(timestampMs, sessionPct, weeklyPct))
        val cutoff = timestampMs - RETENTION_MS
        cache.removeAll { it.timestampMs < cutoff }
        persist()
    }

    private fun persist() {
        try {
            val arr = JSONArray()
            for (s in cache) {
                arr.put(
                    JSONObject()
                        .put("t", s.timestampMs)
                        .put("s", s.sessionPct.toDouble())
                        .put("w", s.weeklyPct.toDouble())
                )
            }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist usage history", e)
        }
    }

    private fun loadFromDisk(): List<UsageSample> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                UsageSample(o.getLong("t"), o.getDouble("s").toFloat(), o.getDouble("w").toFloat())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load usage history, starting fresh", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "UsageHistoryRepo"
        private const val FILE_NAME = "usage_history.json"
        private const val RETENTION_MS = 31L * 24 * 3600 * 1000

        @Volatile
        private var INSTANCE: UsageHistoryRepository? = null

        fun getInstance(context: Context): UsageHistoryRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UsageHistoryRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
