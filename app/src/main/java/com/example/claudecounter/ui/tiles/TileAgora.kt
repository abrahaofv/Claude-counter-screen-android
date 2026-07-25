package com.example.claudecounter.ui.tiles

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.claudecounter.ui.SegmentMeter
import com.example.claudecounter.ui.StatusChip
import com.example.claudecounter.ui.brand.Mascot
import com.example.claudecounter.ui.brand.MascotMood
import com.example.claudecounter.ui.brand.MascotStage
import com.example.claudecounter.ui.pctColorDiscrete
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickDimens
import com.example.claudecounter.ui.theme.StickType
import com.example.claudecounter.ui.usageColor
import com.example.claudecounter.util.formatCountdown
import com.example.claudecounter.util.formatDayTime

/**
 * Tile 1 — "Agora": the two big usage cards (5-hour + weekly window).
 * Layout mirrors mock_agora() in tools/gen_mockups.py, minus the token-count
 * line (this app has no local token bridge to source that from).
 */
@Composable
fun TileAgora(
    sessionPct: Float,
    sessionResetsAtMs: Long,
    weeklyPct: Float,
    weeklyResetsAtMs: Long,
    now: Long,
    isApiBlocked: Boolean,
    modifier: Modifier = Modifier,
    /** Portrait mode stacks the two cards vertically instead of side by side. */
    stacked: Boolean = false,
    /** null hides the mini mascot — respects Settings -> Aparencia -> Mascote Clawd. */
    sessionStage: MascotStage? = null,
    weeklyStage: MascotStage? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
            .padding(top = 4.dp, bottom = 8.dp)
    ) {
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UsageCardStick("5 HORAS", sessionPct, sessionResetsAtMs, now, sessionStage, Modifier.fillMaxWidth())
                UsageCardStick("SEMANA", weeklyPct, weeklyResetsAtMs, now, weeklyStage, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UsageCardStick("5 HORAS", sessionPct, sessionResetsAtMs, now, sessionStage, Modifier.weight(1f))
                UsageCardStick("SEMANA", weeklyPct, weeklyResetsAtMs, now, weeklyStage, Modifier.weight(1f))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val overallPct = maxOf(sessionPct, weeklyPct)
            val (label, color) = when {
                isApiBlocked -> "BLOQUEADO" to StickColors.Bad
                else -> {
                    val c = pctColorDiscrete(overallPct)
                    val l = when (c) {
                        StickColors.Ok -> "OK"
                        StickColors.Warn -> "ATENCAO"
                        else -> "BLOQUEADO"
                    }
                    l to c
                }
            }
            StatusChip(text = label, color = color)
        }
    }
}

@Composable
private fun UsageCardStick(
    title: String,
    pct: Float,
    resetsAtMs: Long,
    now: Long,
    stage: MascotStage?,
    modifier: Modifier = Modifier,
) {
    val animatedPct by animateFloatAsState(
        targetValue = pct,
        animationSpec = tween(700, easing = EaseInOut),
        label = "cardPct"
    )

    Column(
        modifier = modifier
            .height(210.dp)
            .clip(RoundedCornerShape(StickDimens.CardRadius))
            .background(StickColors.Surface)
            .padding(StickDimens.CardPadding)
    ) {
        Text(text = title, style = StickType.label, color = StickColors.Muted)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${pct.toInt()}%",
                style = StickType.displayPercent,
                color = usageColor(animatedPct),
            )
            if (stage != null) {
                Mascot(width = 56.dp, mood = MascotMood.Ok, stage = stage, pct = pct)
            }
        }

        SegmentMeter(pct = animatedPct, modifier = Modifier.padding(top = 12.dp))

        if (resetsAtMs > 0L) {
            Text(
                text = "RESETA EM • ${formatDayTime(resetsAtMs)}",
                style = StickType.caption,
                color = StickColors.Faint,
                modifier = Modifier.padding(top = 6.dp)
            )

            val diffMs = resetsAtMs - now
            Text(
                text = if (diffMs > 0) formatCountdown(diffMs) else "ja",
                style = StickType.displayCountdown,
                color = StickColors.Text,
                modifier = Modifier.padding(top = 2.dp)
            )
        } else {
            Text(
                text = "sem dados",
                style = StickType.caption,
                color = StickColors.Faint,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
