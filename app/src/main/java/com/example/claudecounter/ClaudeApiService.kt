package com.example.claudecounter

import org.json.JSONObject

/**
 * Parses the JSON returned by claude.ai's /usage endpoint.
 *
 * The endpoint is fetched via [WebViewUsageFetcher], not a raw HTTP client: a plain
 * HttpURLConnection request gets a 403 from Anthropic's bot detection, so the network
 * call now happens inside a real claude.ai page context. This object is parsing-only.
 */
object ClaudeApiService {

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

    /**
     * Turns a (jsonText, errorMessage) pair from [WebViewUsageFetcher.fetchUsage] into a
     * [UsageResult]. Exactly one of the two arguments is expected to be non-null.
     */
    fun parseUsageResponse(jsonText: String?, errorMessage: String?): UsageResult {
        if (errorMessage != null) {
            val isBlocked = errorMessage.contains("403")
            return UsageResult(success = false, errorMessage = errorMessage, isApiBlocked = isBlocked)
        }
        return try {
            val json = JSONObject(jsonText ?: "")
            UsageResult(success = true, data = parseUsageData(json))
        } catch (e: Exception) {
            UsageResult(success = false, errorMessage = e.message)
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
