package com.example.claudecounter.ui.tiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.claudecounter.data.UsageAnalytics
import com.example.claudecounter.data.UsageSample
import com.example.claudecounter.ui.PeriodPill
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickDimens
import com.example.claudecounter.ui.theme.StickType
import com.example.claudecounter.util.WeekdayAbbrev
import com.example.claudecounter.util.formatCountdown
import com.example.claudecounter.util.formatDayTime
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Which window the trend line/projection is drawn for. [maxGapMs] is how long a
 * hole between two samples can be before [UsageAnalytics.splitOnGaps] treats it
 * as "the service was dead", not "usage was flat" — wider for the 7-day window
 * since an overnight gap is normal there but would eat most of a 5h window.
 */
private enum class TrendWindow(val label: String, val title: String, val spanMs: Long, val maxGapMs: Long) {
    FIVE_H("5h", "Janela de 5h", 5L * 3600_000L, 20L * 60_000L),
    SEVEN_D("7d", "Janela da semana", 7L * 24 * 3600_000L, 90L * 60_000L),
}

/**
 * Tile 3 — usage-over-time line for the current window plus a dashed linear
 * projection to the reset time, toggling between the 5-hour session window and
 * the 7-day weekly one. Mirrors mock_janela() in tools/gen_mockups.py, but the
 * line/projection are driven by real recorded samples (Fase 4) rather than the
 * mockup's illustrative synthetic curve.
 */
@Composable
fun TileTrend(
    sessionResetsAtMs: Long,
    weeklyResetsAtMs: Long,
    now: Long,
    allSamples: List<UsageSample>,
    modifier: Modifier = Modifier,
) {
    var window by remember { mutableStateOf(TrendWindow.FIVE_H) }

    val resetsAtMs = if (window == TrendWindow.FIVE_H) sessionResetsAtMs else weeklyResetsAtMs
    val windowResetMs = if (resetsAtMs > 0L) resetsAtMs else now + window.spanMs
    val windowStartMs = (windowResetMs - window.spanMs).coerceAtLeast(0L)
    val valueOf: (UsageSample) -> Float =
        if (window == TrendWindow.FIVE_H) { { it.sessionPct } } else { { it.weeklyPct } }

    val windowSamples = remember(allSamples, windowStartMs, now) {
        UsageAnalytics.samplesSince(allSamples, windowStartMs).filter { it.timestampMs <= now }
    }
    // Broken into contiguous runs so a dead-service gap (overnight, app killed)
    // draws as a gap in the line instead of a straight lie across it.
    val segments = remember(windowSamples, window) {
        UsageAnalytics.splitOnGaps(windowSamples, window.maxGapMs)
    }
    // Project from the *current* run only — bridging the rate calculation across
    // a multi-hour dead gap would produce a meaningless pace.
    val exhaustionMs = remember(segments, window) {
        UsageAnalytics.projectExhaustionMs(segments.lastOrNull().orEmpty(), valueOf)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .padding(top = 2.dp, bottom = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(window.title, style = StickType.heading, color = StickColors.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrendWindow.entries.forEach { w ->
                    PeriodPill(text = w.label, active = w == window, onClick = { window = w })
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(StickDimens.CardRadius))
                .background(StickColors.Surface)
        ) {
            TrendCanvas(
                window = window,
                windowStartMs = windowStartMs,
                windowResetMs = windowResetMs,
                segments = segments,
                valueOf = valueOf,
                exhaustionMs = exhaustionMs,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        VerdictText(
            window = window,
            now = now,
            windowResetMs = windowResetMs,
            exhaustionMs = exhaustionMs,
            lastPct = windowSamples.lastOrNull()?.let(valueOf),
        )
    }
}

@Composable
private fun TrendCanvas(
    window: TrendWindow,
    windowStartMs: Long,
    windowResetMs: Long,
    segments: List<List<UsageSample>>,
    valueOf: (UsageSample) -> Float,
    exhaustionMs: Long?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val axisLabel: (Long) -> String = remember(window) {
        if (window == TrendWindow.FIVE_H) {
            { ms -> timeFmt.format(Date(ms)) }
        } else {
            { ms -> WeekdayAbbrev[Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_WEEK) - 1] }
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val totalMs = (windowResetMs - windowStartMs).coerceAtLeast(1L)

        fun xOf(t: Long) = ((t - windowStartMs).toFloat() / totalMs).coerceIn(0f, 1f) * w
        fun yOf(pct: Float) = h - (pct.coerceIn(0f, 100f) / 100f) * h

        // Gridlines at 25/50/75%.
        for (p in intArrayOf(25, 50, 75)) {
            val y = yOf(p.toFloat())
            drawLine(StickColors.Grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }

        val labelStyle = StickType.caption.copy(color = StickColors.Faint)
        val topLabel = textMeasurer.measure("100", labelStyle)
        drawText(topLabel, topLeft = Offset(w - topLabel.size.width, 0f))
        val bottomLabel = textMeasurer.measure("0", labelStyle)
        drawText(bottomLabel, topLeft = Offset(w - bottomLabel.size.width, h - bottomLabel.size.height))

        for (seg in segments) {
            if (seg.size < 2) continue
            val segPath = Path().apply {
                val first = seg.first()
                moveTo(xOf(first.timestampMs), yOf(valueOf(first)))
                for (i in 1 until seg.size) lineTo(xOf(seg[i].timestampMs), yOf(valueOf(seg[i])))
            }
            drawPath(
                segPath,
                color = StickColors.Accent,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        val lastSample = segments.lastOrNull()?.lastOrNull()
        if (lastSample != null) {
            val last = Offset(xOf(lastSample.timestampMs), yOf(valueOf(lastSample)))
            val lastPct = valueOf(lastSample)

            // Dashed projection: from the last real point to either the exhaustion
            // point (if it falls inside the window) or the window's reset edge.
            val projectedEndT = exhaustionMs?.coerceAtMost(windowResetMs) ?: windowResetMs
            val projectedEndPct = if (exhaustionMs != null && exhaustionMs <= windowResetMs) 100f else lastPct
            val end = Offset(xOf(projectedEndT), yOf(projectedEndPct))
            if (exhaustionMs != null && (end.x != last.x || end.y != last.y)) {
                drawLine(
                    color = StickColors.Accent.copy(alpha = 170f / 255f),
                    start = last,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                )
            }

            drawCircle(StickColors.Text, radius = 4.dp.toPx(), center = last)
        }

        val startLabel = textMeasurer.measure(axisLabel(windowStartMs), labelStyle)
        drawText(startLabel, topLeft = Offset(0f, h - startLabel.size.height))
        val endLabel = textMeasurer.measure(axisLabel(windowResetMs), labelStyle)
        drawText(endLabel, topLeft = Offset(w - endLabel.size.width, h - endLabel.size.height))
    }
}

@Composable
private fun VerdictText(window: TrendWindow, now: Long, windowResetMs: Long, exhaustionMs: Long?, lastPct: Float?) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val (text, color) = when {
        exhaustionMs == null || lastPct == null -> "Coletando dados de uso..." to StickColors.Faint
        exhaustionMs >= windowResetMs -> {
            "NAO esgota antes do reset (~${lastPct.toInt()}%)" to StickColors.Ok
        }
        else -> {
            val remainingMs = exhaustionMs - now
            val whenLabel = if (window == TrendWindow.FIVE_H) timeFmt.format(Date(exhaustionMs)) else formatDayTime(exhaustionMs)
            val label = "No ritmo atual, esgota as $whenLabel (em ${formatCountdown(remainingMs)})"
            val urgentColor = if (remainingMs in 0..(60 * 60 * 1000L)) StickColors.Bad else StickColors.Warn
            label to urgentColor
        }
    }
    Text(text = text, style = StickType.heading, color = color, textAlign = TextAlign.Start)
}
