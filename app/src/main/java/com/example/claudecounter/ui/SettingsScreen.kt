package com.example.claudecounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.claudecounter.PollMode
import com.example.claudecounter.data.DisplayConfig
import com.example.claudecounter.data.PollingConfig
import com.example.claudecounter.data.PollingSettings
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickType

/**
 * Settings — appearance toggles, adaptive-polling controls, the "API Access
 * Restricted" banner (Fase 9), and account controls.
 */
@Composable
fun SettingsScreen(
    isApiBlocked: Boolean,
    displayConfig: DisplayConfig,
    onShowMascotsChange: (Boolean) -> Unit,
    pollingConfig: PollingConfig,
    pollMode: PollMode,
    onAdaptiveChange: (Boolean) -> Unit,
    onIdleIntervalChange: (Long) -> Unit,
    onActiveIntervalChange: (Long) -> Unit,
    onLogout: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                "Configuracoes",
                style = StickType.heading.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                color = StickColors.Text
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = StickColors.Text)
            }
        }

        AppearanceSection(
            config = displayConfig,
            onShowMascotsChange = onShowMascotsChange,
        )

        MonitoringSection(
            config = pollingConfig,
            pollMode = pollMode,
            onAdaptiveChange = onAdaptiveChange,
            onIdleIntervalChange = onIdleIntervalChange,
            onActiveIntervalChange = onActiveIntervalChange,
        )

        if (isApiBlocked) {
            Row(Modifier.padding(top = 16.dp)) {
                ApiBlockedBanner()
            }
        }

        Row(modifier = Modifier.padding(top = 32.dp)) {
            OutlinedButton(onClick = onLogout) {
                Text("Sair da conta", color = StickColors.Bad)
            }
        }
    }
}

/** Whether Clawd, the animated mascot, shows up around the app. */
@Composable
private fun AppearanceSection(
    config: DisplayConfig,
    onShowMascotsChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text("Aparencia", style = StickType.heading, color = StickColors.Text)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Mascote Clawd", style = StickType.label, color = StickColors.Text)
                Text(
                    "Mostra o Clawd reagindo ao seu consumo nos cards e em uma aba dedicada",
                    style = StickType.caption,
                    color = StickColors.Muted
                )
            }
            Switch(
                checked = config.showMascots,
                onCheckedChange = onShowMascotsChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = StickColors.Bg,
                    checkedTrackColor = StickColors.Accent,
                    uncheckedThumbColor = StickColors.Muted,
                    uncheckedTrackColor = StickColors.Surface2,
                )
            )
        }
    }
}

/**
 * Adaptive polling: the app idles at the slow interval and speeds up to the
 * active one for a few minutes whenever it sees usage rise.
 */
@Composable
private fun MonitoringSection(
    config: PollingConfig,
    pollMode: PollMode,
    onAdaptiveChange: (Boolean) -> Unit,
    onIdleIntervalChange: (Long) -> Unit,
    onActiveIntervalChange: (Long) -> Unit,
) {
    val activeInForce = config.adaptiveEnabled && pollMode == PollMode.ACTIVE
    val currentInterval = if (activeInForce) config.activeIntervalMs else config.idleIntervalMs

    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text("Monitoramento", style = StickType.heading, color = StickColors.Text)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Monitoramento inteligente", style = StickType.label, color = StickColors.Text)
                Text(
                    "Acelera a checagem quando detecta uso de tokens",
                    style = StickType.caption,
                    color = StickColors.Muted
                )
            }
            Switch(
                checked = config.adaptiveEnabled,
                onCheckedChange = onAdaptiveChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = StickColors.Bg,
                    checkedTrackColor = StickColors.Accent,
                    uncheckedThumbColor = StickColors.Muted,
                    uncheckedTrackColor = StickColors.Surface2,
                )
            )
        }

        // Current mode indicator
        Text(
            text = "Modo: ${if (activeInForce) "ATIVO" else "OCIOSO"} — atualizando a cada " +
                PollingSettings.formatInterval(currentInterval),
            style = StickType.caption,
            color = if (activeInForce) StickColors.Accent else StickColors.Muted,
            modifier = Modifier.padding(top = 12.dp)
        )

        IntervalRow(
            title = "Sem atividade",
            options = PollingSettings.IDLE_OPTIONS_MS,
            selected = config.idleIntervalMs,
            enabled = true,
            onSelect = onIdleIntervalChange,
        )

        IntervalRow(
            title = "Com atividade",
            options = PollingSettings.ACTIVE_OPTIONS_MS,
            selected = config.activeIntervalMs,
            enabled = config.adaptiveEnabled,
            onSelect = onActiveIntervalChange,
        )

        if (config.adaptiveEnabled && config.activeIntervalMs < PollingSettings.AGGRESSIVE_THRESHOLD_MS) {
            Text(
                "Intervalo muito curto: mais bateria e dados, e maior risco de a " +
                    "Anthropic bloquear as consultas.",
                style = StickType.caption,
                color = StickColors.Bad,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Text(
            "Consultar o uso nao gasta token — e apenas a leitura do seu medidor de " +
                "cota, nao uma conversa com o Claude. O custo de checar com frequencia " +
                "e bateria, dados moveis e o risco de bloqueio da API.",
            style = StickType.caption,
            color = StickColors.Muted,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

/** One labelled row of interval chips ("5s 10s 15s 30s"). */
@Composable
private fun IntervalRow(
    title: String,
    options: List<Long>,
    selected: Long,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            title,
            style = StickType.caption,
            color = if (enabled) StickColors.Muted else StickColors.Faint
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { ms ->
                IntervalChip(
                    label = PollingSettings.formatInterval(ms),
                    selected = ms == selected,
                    enabled = enabled,
                    onClick = { onSelect(ms) },
                )
            }
        }
    }
}

@Composable
private fun IntervalChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        selected && enabled -> StickColors.Accent
        selected -> StickColors.Surface2
        else -> StickColors.Surface
    }
    val textColor = when {
        !enabled -> StickColors.Faint
        selected -> StickColors.Bg
        else -> StickColors.Text
    }
    Text(
        text = label,
        style = StickType.caption,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/** "API Access Restricted" — carried over from the original MainActivity banner. */
@Composable
private fun ApiBlockedBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(StickColors.Bad.copy(alpha = 0.10f))
            .padding(16.dp)
    ) {
        Text(
            "API Access Restricted",
            color = StickColors.Bad,
            style = StickType.heading
        )
        Text(
            "Anthropic has blocked third-party API access as of April 2026. " +
                "Background usage tracking is no longer available.",
            color = StickColors.Muted,
            style = StickType.caption,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
