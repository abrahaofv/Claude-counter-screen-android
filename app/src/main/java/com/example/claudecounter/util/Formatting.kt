package com.example.claudecounter.util

import java.util.Calendar

/** "1h 40m", "5d 3h", "42s" — shared by the legacy cards and the stick-style tiles. */
fun formatCountdown(ms: Long): String {
    if (ms <= 0) return "now"
    val totalSeconds = ms / 1000
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/**
 * "4d 6h" / "3h 12min" / "42min" — the survival-streak banner's duration format.
 * Coarser than [formatCountdown] on purpose: streaks run for days, so seconds-level
 * precision would just churn the text on every 1s tick for no benefit.
 */
fun formatSurvivalDuration(ms: Long): String {
    val totalMinutes = ms.coerceAtLeast(0) / 60_000
    val days = totalMinutes / 1440
    val hours = (totalMinutes % 1440) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}min"
        else -> "${minutes}min"
    }
}

val WeekdayAbbrev = arrayOf("dom", "seg", "ter", "qua", "qui", "sex", "sab")

/** "sex 14:32" — weekday + time, used for the weekly reset chip and the 7d trend axis/verdict. */
fun formatDayTime(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    val weekday = WeekdayAbbrev[cal.get(Calendar.DAY_OF_WEEK) - 1]
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    return "%s %02d:%02d".format(weekday, hour, minute)
}
