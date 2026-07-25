package com.example.claudecounter.ui.tiles

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import com.example.claudecounter.data.UsageAnalytics
import com.example.claudecounter.data.UsageSample
import com.example.claudecounter.ui.PeriodPill
import com.example.claudecounter.ui.heatBarColor
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickDimens
import com.example.claudecounter.ui.theme.StickType
import java.util.Calendar

private enum class HeatPeriod(val label: String) {
    TODAY("Hoje"), SEVEN_DAYS("7d"), THIRTY_DAYS("30d"), ALL("Tudo")
}

/**
 * Tile 4 — "Ritmo por hora": 24-hour burn-rate heatmap with a period selector.
 * Mirrors mock_ritmo() in tools/gen_mockups.py, sourced from real recorded
 * samples (Fase 4) instead of the mockup's synthetic bell-curve profile.
 */
@Composable
fun TileHeat(
    samples: List<UsageSample>,
    now: Long,
    modifier: Modifier = Modifier,
) {
    var period by remember { mutableStateOf(HeatPeriod.ALL) }

    val sinceMs = remember(period, now) {
        when (period) {
            HeatPeriod.TODAY -> startOfDay(now)
            HeatPeriod.SEVEN_DAYS -> now - 7L * 24 * 3600 * 1000
            HeatPeriod.THIRTY_DAYS -> now - 30L * 24 * 3600 * 1000
            HeatPeriod.ALL -> null
        }
    }
    val profile = remember(samples, sinceMs) { UsageAnalytics.hourlyBurnProfile(samples, sinceMs) }
    val currentHour = remember(now) { Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .padding(top = 4.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ritmo por hora", style = StickType.heading, color = StickColors.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeatPeriod.entries.forEach { p ->
                    PeriodPill(text = p.label, active = p == period, onClick = { period = p })
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(StickDimens.HeatBarMaxHeight),
            // SpaceEvenly (not a fixed spacedBy step) so the 24 bars distribute
            // across whatever width the canvas actually has — on landscape
            // screens wider than the original 480dp stick display, a fixed step
            // would leave a dead gap after the last bar. See MonitorScaffold's
            // Fase 6 (responsive landscape canvas).
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            for (hour in 0 until 24) {
                val ratio = profile[hour]
                val barHeight = 4.dp + (StickDimens.HeatBarMaxHeight - 4.dp) * ratio
                val color = if (hour == currentHour) StickColors.Text else heatBarColor(ratio)
                Box(
                    modifier = Modifier
                        .width(StickDimens.HeatBarWidth)
                        .height(barHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (hour in intArrayOf(0, 6, 12, 18, 23)) {
                Text("${hour}h", style = StickType.caption, color = StickColors.Muted)
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            "quota da janela 5h queimada em cada hora local",
            style = StickType.caption,
            color = StickColors.Faint
        )
    }
}

private fun startOfDay(ms: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ms
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
