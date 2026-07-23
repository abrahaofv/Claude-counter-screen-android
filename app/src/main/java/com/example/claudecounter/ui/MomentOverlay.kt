package com.example.claudecounter.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.claudecounter.MomentEvent
import com.example.claudecounter.MomentKind
import com.example.claudecounter.UsageWindow
import com.example.claudecounter.ui.brand.Mascot
import com.example.claudecounter.ui.brand.MascotMood
import com.example.claudecounter.ui.brand.MascotStage
import com.example.claudecounter.ui.brand.stageFor
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickType
import com.example.claudecounter.util.formatCountdown
import kotlinx.coroutines.delay
import kotlin.math.sin

private const val AutoDismissMs = 4600L

/**
 * Full-screen "momento de limiar" — fires when a window crosses 25/50/70/100%.
 * Mirrors show_moment()/moment_tick() in claude_stick.ino: the mascot falls in,
 * the percentage counts up while the meter fills, and its mood escalates with
 * the threshold (calm bounce -> shake+sweat -> grey KO'd with a pulsing red ring).
 */
@Composable
fun MomentOverlay(
    event: MomentEvent,
    now: Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(event) {
        delay(AutoDismissMs)
        onDismiss()
    }

    val windowLabel = if (event.window == UsageWindow.SESSION) "JANELA DE 5 HORAS" else "JANELA SEMANAL"
    val isRevival = event.kind == MomentKind.REVIVAL
    val message = when {
        isRevival -> "Clawd renasceu! Janela resetada"
        event.threshold == 25 -> "Comecando • ritmo tranquilo"
        event.threshold == 50 -> "Metade da janela usada"
        event.threshold == 70 -> "Atencao • uso alto"
        else -> "Limite atingido • aguarde o reset"
    }
    // The mascot's mood stays Ok here — a mood other than Ok/Limited means "no
    // trustworthy usage number" (see MascotMood docs), which isn't the case for
    // a threshold/revival moment. Effort comes entirely from stage.
    val stage = if (isRevival) MascotStage.REVIVING else stageFor(event.pct)

    // Entrance: falls from above over 450ms, decelerating (quadratic ease-out).
    val entrance = remember(event) { Animatable(0f) }
    LaunchedEffect(event) {
        entrance.animateTo(1f, tween(450, easing = { t -> 1f - (1f - t) * (1f - t) }))
    }

    // Percentage counts up from 0 starting at t=200ms, finishing by t=1100ms —
    // the segment meter (driven by the same value) lights up in step.
    val displayPct = remember(event) { Animatable(0f) }
    LaunchedEffect(event) {
        delay(200)
        displayPct.animateTo(event.pct, tween(900, easing = LinearOutSlowInEasing))
    }

    val infinite = rememberInfiniteTransition(label = "moment-overlay")
    val shakePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing)),
        label = "moment-shake"
    )
    val ringAlpha by infinite.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "moment-ring"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StickColors.OverlayBg.copy(alpha = 248f / 255f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 36.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val fallOffsetY = (-60).dp * (1f - entrance.value)
            val shakeOffsetX = if (stage == MascotStage.EXHAUSTED || stage == MascotStage.CRITICAL) {
                (2f * sin(shakePhase)).dp
            } else 0.dp
            Mascot(
                width = 176.dp,
                mood = MascotMood.Ok,
                stage = stage,
                modifier = Modifier.offset(x = shakeOffsetX, y = fallOffsetY)
            )

            Spacer(Modifier.width(28.dp))

            Column {
                Text(text = windowLabel, style = StickType.overlayLabel, color = StickColors.Muted)
                Text(
                    text = "${displayPct.value.toInt()}%",
                    style = StickType.displayPercent,
                    color = usageColor(displayPct.value),
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    text = message,
                    style = StickType.heading,
                    color = StickColors.Text,
                    modifier = Modifier.padding(top = 10.dp)
                )
                SegmentMeter(pct = displayPct.value, modifier = Modifier.padding(top = 14.dp))
                if (event.resetsAtMs > now) {
                    Text(
                        text = "reseta em ${formatCountdown(event.resetsAtMs - now)}",
                        style = StickType.label,
                        color = StickColors.Faint,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }

        Text(
            text = "toque para fechar",
            style = StickType.caption,
            color = StickColors.Faint,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        if (stage == MascotStage.KO) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(4.dp, StickColors.Bad.copy(alpha = ringAlpha))
            )
        }
    }
}
