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
import com.example.claudecounter.ui.brand.Mascot
import com.example.claudecounter.ui.brand.MascotMood
import com.example.claudecounter.ui.brand.MascotStage
import com.example.claudecounter.ui.brand.stageFor
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
    modifier: Modifier = Modifier,
    /** Portrait mode stacks the two cards vertically instead of side by side. */
    stacked: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
            .padding(top = 4.dp, bottom = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Clawd", style = StickType.heading, color = StickColors.Text)
            Text("como ele esta", style = StickType.caption, color = StickColors.Faint)
        }

        Spacer(Modifier.height(6.dp))

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

    Column(
        modifier = modifier
            .height(210.dp)
            .clip(RoundedCornerShape(StickDimens.CardRadius))
            .background(StickColors.Surface)
            .padding(StickDimens.CardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = StickType.label, color = StickColors.Muted, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(4.dp))

        Mascot(width = 96.dp, mood = MascotMood.Ok, stage = stage)

        Spacer(Modifier.height(6.dp))

        Text(
            text = "${pct.toInt()}% · ${stage.label}",
            style = StickType.label,
            color = usageColor(pct),
        )

        if (resetsAtMs > 0L) {
            Text(
                text = if (resetsAtMs > now) "reseta em ${formatCountdown(resetsAtMs - now)}" else "reseta ja",
                style = StickType.caption,
                color = StickColors.Faint,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
