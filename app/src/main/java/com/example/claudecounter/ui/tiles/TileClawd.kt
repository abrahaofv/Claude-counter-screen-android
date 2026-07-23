package com.example.claudecounter.ui.tiles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.claudecounter.data.SurvivalStatsRepository
import com.example.claudecounter.ui.brand.GraveScene
import com.example.claudecounter.ui.brand.Mascot
import com.example.claudecounter.ui.brand.MascotMood
import com.example.claudecounter.ui.brand.MascotStage
import com.example.claudecounter.ui.brand.stageFor
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickDimens
import com.example.claudecounter.ui.theme.StickType
import com.example.claudecounter.ui.usageColor
import com.example.claudecounter.util.formatCountdown
import com.example.claudecounter.util.formatSurvivalDuration

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

        Spacer(Modifier.height(8.dp))

        SurvivalBanner(now = now, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * "CLAWD VIVO HA Xd Yh · RECORDE Zd" — how long since either window last hit
 * 100%, with the all-time best alongside. Backed by [SurvivalStatsRepository];
 * ticks off [now], which the caller already refreshes once a second, so this
 * needs no timer of its own.
 */
@Composable
private fun SurvivalBanner(now: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { SurvivalStatsRepository.getInstance(context) }
    val survival by repo.state.collectAsStateWithLifecycle()

    // Flashes the border when a new record lands — mirrors the preview's
    // record-flash box-shadow pulse.
    val flash = remember { Animatable(0f) }
    var lastSeenRecord by remember { mutableLongStateOf(survival.recordMs) }
    LaunchedEffect(survival.recordMs) {
        if (survival.recordMs > lastSeenRecord) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(900, easing = LinearOutSlowInEasing))
        }
        lastSeenRecord = survival.recordMs
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(StickDimens.ChipRadius))
            .background(StickColors.Surface)
            .border(
                width = 1.dp,
                color = lerp(StickColors.Border, StickColors.Accent, flash.value),
                shape = RoundedCornerShape(StickDimens.ChipRadius)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = StickColors.Muted)) { append("CLAWD VIVO HA ") }
                withStyle(SpanStyle(color = StickColors.Text, fontWeight = FontWeight.Bold)) {
                    append(formatSurvivalDuration(now - survival.aliveSinceMs))
                }
                withStyle(SpanStyle(color = StickColors.Muted)) { append(" · RECORDE ") }
                withStyle(SpanStyle(color = StickColors.Accent)) {
                    append(formatSurvivalDuration(survival.recordMs))
                }
            },
            style = StickType.caption.copy(fontFamily = FontFamily.Monospace),
        )
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
            .height(210.dp)
            .clip(RoundedCornerShape(StickDimens.CardRadius))
            .background(StickColors.Surface)
            .padding(StickDimens.CardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = StickType.label, color = StickColors.Muted, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(4.dp))

        if (isGrave) {
            GraveScene(width = 96.dp)
        } else {
            Mascot(width = 96.dp, mood = MascotMood.Ok, stage = stage)
        }

        Spacer(Modifier.height(6.dp))

        Text(
            // "Descansando" instead of stage.label's "Morto" once the grave scene
            // is showing — he's not just dead, he's waiting out the window reset.
            text = if (isGrave) "${pct.toInt()}% · DESCANSANDO" else "${pct.toInt()}% · ${stage.label}",
            style = StickType.label,
            color = if (isGrave) StickColors.MascotKo else usageColor(pct),
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
