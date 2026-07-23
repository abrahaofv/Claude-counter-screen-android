package com.example.claudecounter

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches Claude usage data from the API.
 * Requires the session cookie obtained via the WebView login.
 */
object ClaudeApiService {

    private const val TAG = "ClaudeApiService"
    private const val BASE_URL = "https://claude.ai/api/organizations/%s/usage"

    // ── Data models ──────────────────────────────────────────────────────────

    data class UsageWindow(
        val utilization: Double,   // 0–100%
        val resetsAt: String       // ISO-8601 timestamp
    )

    data class UsageData(
        val fiveHour: UsageWindow?,
        val sevenDay: UsageWindow?
    )

    data class UsageResult(
        val success: Boolean,
        val data: UsageData? = null,
        val errorMessage: String? = null,
        val isApiBlocked: Boolean = false
    )

    // ── Fetch ────────────────────────────────────────────────────────────────

    /**
     * Synchronous fetch – call from a background thread.
     * Returns a [UsageResult] with parsed usage data or an error.
     */
    fun fetchUsage(orgId: String, sessionCookie: String): UsageResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(BASE_URL.format(orgId))
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Cookie", sessionCookie)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ClaudeCounter/1.0 Android")
                setRequestProperty("Referer", "https://claude.ai/")
                setRequestProperty("sec-fetch-site", "same-origin")
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val body = connection.inputStream
                    .bufferedReader()
                    .use(BufferedReader::readText)
                val json = JSONObject(body)
                val data = parseUsageData(json)
                UsageResult(success = true, data = data)
            } else {
                val errBody = connection.errorStream
                    ?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                Log.w(TAG, "HTTP $responseCode: $errBody")
                if (responseCode == 403) {
                    UsageResult(
                        success = false,
                        errorMessage = "API access restricted by Anthropic",
                        isApiBlocked = true
                    )
                } else {
                    UsageResult(success = false, errorMessage = "HTTP $responseCode")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchUsage error", e)
            UsageResult(success = false, errorMessage = e.message)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parses the raw JSON from the usage endpoint into [UsageData].
     * Handles various possible JSON structures from Claude's API.
     */
    private fun parseUsageData(json: JSONObject): UsageData {
        // API returns "five_hour" and "seven_day" objects
        val fiveHour = parseWindow(json, "five_hour")
        val sevenDay = parseWindow(json, "seven_day")
        return UsageData(fiveHour = fiveHour, sevenDay = sevenDay)
    }

    private fun parseWindow(json: JSONObject, key: String): UsageWindow? {
        val obj = json.optJSONObject(key) ?: return null
        // "utilization" might be a direct percentage field
        val utilization = obj.optDouble("utilization", -1.0).takeIf { it >= 0 }
        // Alternatively: compute from "used" / "limit"
            ?: run {
                val used = obj.optDouble("used", 0.0)
                val limit = obj.optDouble("limit", 1.0)
                if (limit > 0) (used / limit * 100.0) else 0.0
            }
        val resetsAt = obj.optString("resets_at", "")
        return if (resetsAt.isNotBlank() || utilization >= 0) {
            UsageWindow(utilization = utilization, resetsAt = resetsAt)
        } else null
    }
}
