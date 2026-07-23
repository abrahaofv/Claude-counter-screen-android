package com.example.claudecounter.data

import java.util.Calendar

/**
 * Pure derivations over [UsageSample] history — feeds the trend graph (Fase 5)
 * and the hourly heatmap (Fase 6). Kept dependency-free (no Context, no Compose)
 * so it's trivial to reason about / unit test independently of the UI.
 */
object UsageAnalytics {

    /** Samples at or after [windowStartMs], oldest first. */
    fun samplesSince(samples: List<UsageSample>, windowStartMs: Long): List<UsageSample> =
        samples.filter { it.timestampMs >= windowStartMs }.sortedBy { it.timestampMs }

    /**
     * Naive linear projection: fit a rate from the first/last sample of the
     * current window and extrapolate to find when it would cross 100%.
     * Returns null when there isn't enough data yet, or the rate is flat/negative
     * (i.e. no plausible exhaustion before the window resets).
     *
     * [valueOf] selects which window's percentage to project — defaults to the
     * 5-hour session, but the same math projects the weekly window too (see the
     * "Janela de 5h" / "Janela da semana" toggle in
     * [com.example.claudecounter.ui.tiles.TileTrend]).
     */
    fun projectExhaustionMs(
        windowSamples: List<UsageSample>,
        valueOf: (UsageSample) -> Float = { it.sessionPct },
    ): Long? {
        if (windowSamples.size < 2) return null
        val first = windowSamples.first()
        val last = windowSamples.last()
        val elapsedMs = last.timestampMs - first.timestampMs
        if (elapsedMs <= 0) return null
        val deltaPct = valueOf(last) - valueOf(first)
        if (deltaPct <= 0f) return null
        if (valueOf(last) >= 100f) return last.timestampMs
        val ratePerMs = deltaPct / elapsedMs
        val remainingPct = 100f - valueOf(last)
        val msToExhaustion = remainingPct / ratePerMs
        return last.timestampMs + msToExhaustion.toLong()
    }

    /**
     * Splits [samples] into contiguous runs wherever the gap between two
     * consecutive samples exceeds [maxGapMs]. A sample is only recorded on a
     * successful fetch (see [com.example.claudecounter.SessionManager.updateUsage]),
     * so a stretch with the polling service dead — overnight, app force-stopped —
     * shows up as a long gap; drawing a straight line across it would fabricate
     * a trend that never happened. Each returned run is oldest-first, and the
     * runs themselves are in chronological order. Assumes [samples] is already
     * sorted oldest-first (as [samplesSince] returns it).
     */
    fun splitOnGaps(samples: List<UsageSample>, maxGapMs: Long): List<List<UsageSample>> {
        if (samples.isEmpty()) return emptyList()
        val runs = mutableListOf<MutableList<UsageSample>>(mutableListOf(samples.first()))
        for (i in 1 until samples.size) {
            val gap = samples[i].timestampMs - samples[i - 1].timestampMs
            if (gap > maxGapMs) runs.add(mutableListOf())
            runs.last().add(samples[i])
        }
        return runs
    }

    /**
     * Per-local-hour "quota burned" profile (24 buckets, index = hour-of-day),
     * normalized 0..1 against the busiest hour. [sinceMs] filters the source
     * samples (e.g. last 24h/7d/30d); pass null to use everything retained.
     * Resets (utilization dropping) are treated as noise and skipped.
     */
    fun hourlyBurnProfile(samples: List<UsageSample>, sinceMs: Long?): FloatArray {
        val buckets = FloatArray(24)
        val filtered = if (sinceMs != null) samples.filter { it.timestampMs >= sinceMs } else samples
        val sorted = filtered.sortedBy { it.timestampMs }
        if (sorted.size < 2) return buckets

        val cal = Calendar.getInstance()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val cur = sorted[i]
            val delta = cur.sessionPct - prev.sessionPct
            if (delta <= 0f) continue
            cal.timeInMillis = cur.timestampMs
            buckets[cal.get(Calendar.HOUR_OF_DAY)] += delta
        }

        val max = buckets.maxOrNull() ?: 0f
        if (max <= 0f) return buckets
        return FloatArray(24) { buckets[it] / max }
    }
}
