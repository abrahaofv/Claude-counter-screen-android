package com.example.claudecounter.ui.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.claudecounter.ui.SegmentMeter
import com.example.claudecounter.ui.StatusChip
import com.example.claudecounter.ui.brand.GraveScene
import com.example.claudecounter.ui.brand.Mascot
import com.example.claudecounter.ui.brand.MascotMood
import com.example.claudecounter.ui.brand.MascotStage
import com.example.claudecounter.ui.brand.stageFor
import com.example.claudecounter.ui.pctColorDiscrete
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickDimens
import com.example.claudecounter.ui.theme.StickType
import com.example.claudecounter.ui.usageColor
import com.example.claudecounter.util.formatCountdown

/**
 * Tile "Clawd" — the tamagotchi view: one large mascot per usage window, sized
 * and moody according to how close that window is to 100%. This is the same
 * [MascotStage] driving the mini mascots on the Agora cards and the header, just
 * given room to actually be seen (the overlay in [com.example.claudecounter.ui.MomentOverlay]
 * only shows for a few seconds).
 *
 * Sized to fit landscape's fixed 320dp-tall "monitor mode" canvas
 * ([StickDimens.CanvasHeight]) without scrolling — no title row (matches
 * [TileAgora]'s chrome-less style) and a compact [ClawdCard], same budget math
 * as [TileAgora]. `verticalScroll` stays on as a safety net, not the plan.
 */
@Composable
fun TileClawd(
    sessionPct: Float,
    sessionResetsAtMs: Long,
    sessionReviving: Boolean,
    weeklyPct: Float,
    weeklyResetsAtMs: Long,
    weeklyReviving: Boolean,
    now: Long,
    isApiBlocked: Boolean,
    modifier: Modifier = Modifier,
    /** Portrait mode stacks the two cards vertically instead of side by side. */
    stacked: Boolean = false,
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
                ClawdCard("JANELA 5H", sessionPct, sessionResetsAtMs, sessionReviving, now, Modifier.fillMaxWidth())
                ClawdCard("SEMANA", weeklyPct, weeklyResetsAtMs, weeklyReviving, now, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ClawdCard("JANELA 5H", sessionPct, sessionResetsAtMs, sessionReviving, now, Modifier.weight(1f))
                ClawdCard("SEMANA", weeklyPct, weeklyResetsAtMs, weeklyReviving, now, Modifier.weight(1f))
            }
        }

        // Same overall-status chip as TileAgora — "OK" / "ATENCAO" / "BLOQUEADO".
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
private fun ClawdCard(
    title: String,
    pct: Float,
    resetsAtMs: Long,
    reviving: Boolean,
    now: Long,
    modifier: Modifier = Modifier,
) {
    val stage = if (reviving) MascotStage.REVIVING else stageFor(pct)
    val isGrave = stage == MascotStage.KO

    Column(
        modifier = modifier
            .height(196.dp)
            .clip(RoundedCornerShape(StickDimens.CardRadius))
            .background(StickColors.Surface)
            .padding(StickDimens.CardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = StickType.label, color = StickColors.Muted, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(2.dp))

        if (isGrave) {
            GraveScene(width = 88.dp)
        } else {
            Mascot(width = 88.dp, mood = MascotMood.Ok, stage = stage, pct = pct)
        }

        Spacer(Modifier.height(4.dp))

        Text(
            // "Descansando" instead of stage.label's "Morto" once the grave scene
            // is showing — he's not just dead, he's waiting out the window reset.
            text = if (isGrave) "${pct.toInt()}% · DESCANSANDO" else "${pct.toInt()}% · ${stage.label}",
            style = StickType.label,
            color = if (isGrave) StickColors.MascotKo else usageColor(pct),
        )

        SegmentMeter(pct = pct, modifier = Modifier.padding(top = 6.dp))

        if (resetsAtMs > 0L) {
            val diffMs = resetsAtMs - now
            Text(
                text = if (diffMs > 0) formatCountdown(diffMs) else "ja",
                style = StickType.displayCountdown,
                color = StickColors.Text,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
