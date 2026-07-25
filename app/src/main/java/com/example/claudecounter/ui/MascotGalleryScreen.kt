package com.example.claudecounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.claudecounter.ui.brand.GraveScene
import com.example.claudecounter.ui.brand.Mascot
import com.example.claudecounter.ui.brand.MascotAction
import com.example.claudecounter.ui.brand.MascotBehaviorState
import com.example.claudecounter.ui.brand.MascotMood
import com.example.claudecounter.ui.brand.MascotStage
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickDimens
import com.example.claudecounter.ui.theme.StickType

/**
 * "Ver animações do Clawd" — a gallery reached from Settings to preview every
 * [MascotStage], [MascotMood] and [MascotAction] on demand, without needing to
 * actually push real usage into any of those states. Renders full-screen like
 * [SettingsScreen] (outside [MonitorScaffold]), so it isn't bound by landscape's
 * 320dp canvas — just a normal scrollable page.
 */
@Composable
fun MascotGalleryScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val behaviorState = remember { MascotBehaviorState() }
    var stage by remember { mutableStateOf(MascotStage.RESTED) }
    var mood by remember { mutableStateOf(MascotMood.Ok) }
    val isGrave = stage == MascotStage.KO

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StickColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Animacoes do Clawd",
                style = StickType.heading.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                color = StickColors.Text
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = StickColors.Text)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(StickDimens.CardRadius))
                .background(StickColors.Surface)
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isGrave) {
                    GraveScene(width = 160.dp)
                } else {
                    Mascot(width = 160.dp, mood = mood, stage = stage, behaviorState = behaviorState)
                }
                Text(
                    text = "${mood.name} · ${stage.label}",
                    style = StickType.caption,
                    color = StickColors.Faint,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        GallerySection(title = "Estagio") {
            MascotStage.entries.forEach { s ->
                GalleryChip(text = s.label, active = stage == s, onClick = { stage = s })
            }
        }

        GallerySection(title = "Status de conexao") {
            MascotMood.entries.forEach { m ->
                GalleryChip(text = moodLabel(m), active = mood == m, onClick = { mood = m })
            }
        }

        GallerySection(
            title = "Microexpressoes",
            caption = "So reagem com status Ok/Limitado — nos outros o visual e estatico, igual no app de verdade.",
        ) {
            GalleryChip(text = "PISCAR", active = false, onClick = { behaviorState.forceBlink() })
            MascotAction.entries.forEach { action ->
                GalleryChip(text = actionLabel(action), active = false, onClick = { behaviorState.forceAction(action) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GallerySection(
    title: String,
    caption: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(title, style = StickType.heading, color = StickColors.Text)
        if (caption != null) {
            Text(
                caption,
                style = StickType.caption,
                color = StickColors.Muted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        FlowRow(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun GalleryChip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(StickDimens.PillRadius))
            .background(if (active) StickColors.Accent else StickColors.Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            style = StickType.label,
            color = if (active) StickColors.Bg else StickColors.Text
        )
    }
}

private fun moodLabel(mood: MascotMood): String = when (mood) {
    MascotMood.Ok -> "OK"
    MascotMood.Limited -> "LIMITADO (429)"
    MascotMood.Error -> "ERRO"
    MascotMood.Unavailable -> "INDISPONIVEL"
    MascotMood.NeverProbed -> "NUNCA SONDADO"
}

private fun actionLabel(action: MascotAction): String = when (action) {
    MascotAction.LOOK -> "OLHAR"
    MascotAction.LOOK_UP -> "OLHAR P/ CIMA"
    MascotAction.TILT -> "INCLINAR"
    MascotAction.JUMP -> "PULINHO"
    MascotAction.STRETCH -> "ESPREGUICAR"
    MascotAction.YAWN -> "BOCEJAR"
    MascotAction.WIPE -> "LIMPAR SUOR"
    MascotAction.SURPRISE -> "SURPRESA"
    MascotAction.SETTLE -> "AJUSTAR POSTURA"
}
