package com.example.claudecounter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.claudecounter.MomentEvent
import com.example.claudecounter.MomentKind
import com.example.claudecounter.PollMode
import com.example.claudecounter.SessionManager
import com.example.claudecounter.UsageWindow
import com.example.claudecounter.data.DisplaySettings
import com.example.claudecounter.data.PollingSettings
import com.example.claudecounter.data.UsageHistoryRepository
import com.example.claudecounter.ui.brand.MascotStage
import com.example.claudecounter.ui.brand.stageFor
import com.example.claudecounter.ui.tiles.TileAgora
import com.example.claudecounter.ui.tiles.TileClawd
import com.example.claudecounter.ui.tiles.TileHeat
import com.example.claudecounter.ui.tiles.TileTrend
import kotlinx.coroutines.delay
import java.util.Locale

/** How long the mascot shows [MascotStage.REVIVING] after a window resets. */
private const val REVIVAL_DISPLAY_MS = 2000L

/** The pager's pages, in swipe order. CLAWD is only present when mascots are enabled. */
private enum class MonitorPage { AGORA, CLAWD, TREND, HEAT }

/**
 * The whole monitor experience once the user is logged in: header, the 3-tile
 * pager, the moment overlay, and settings — swapped between landscape
 * ("monitor mode", pixel-accurate 480x320 canvas) and portrait via
 * [MonitorScaffold].
 */
@Composable
fun MonitorRoot(
    usageState: SessionManager.UsageState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val historyRepo = remember { UsageHistoryRepository.getInstance(context) }
    val pollingSettings = remember { PollingSettings.getInstance(context) }
    val pollingConfig by pollingSettings.config.collectAsStateWithLifecycle()
    val displaySettings = remember { DisplaySettings.getInstance(context) }
    val displayConfig by displaySettings.config.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showMascotGallery by remember { mutableStateOf(false) }

    val historySamples = remember(usageState.lastFetchTime) { historyRepo.samples }

    var activeMoment by remember { mutableStateOf<MomentEvent?>(null) }
    // Revival is shown on the persistent mascots (header/cards/Clawd tab) for a
    // couple of seconds even after the momento overlay auto-dismisses — the
    // overlay is quick, but "he's alive again" deserves a beat longer than that.
    var sessionRevivalUntil by remember { mutableLongStateOf(0L) }
    var weeklyRevivalUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sessionManager) {
        sessionManager.momentEvents.collect { event ->
            activeMoment = event
            if (event.kind == MomentKind.REVIVAL) {
                val until = System.currentTimeMillis() + REVIVAL_DISPLAY_MS
                if (event.window == UsageWindow.SESSION) sessionRevivalUntil = until
                else weeklyRevivalUntil = until
            }
        }
    }

    // The countdown bar has to track whichever cadence the poller is actually
    // using right now, otherwise it drains at the wrong speed in ACTIVE mode.
    val isActiveMode = pollingConfig.adaptiveEnabled && usageState.pollMode == PollMode.ACTIVE
    val pollIntervalMs = if (isActiveMode) pollingConfig.activeIntervalMs else pollingConfig.idleIntervalMs

    val refreshFraction = remember(usageState.lastFetchTime, now, pollIntervalMs) {
        if (usageState.lastFetchTime <= 0L) 1f
        else (1f - (now - usageState.lastFetchTime).toFloat() / pollIntervalMs).coerceIn(0f, 1f)
    }
    val statusText = remember(usageState.lastFetchTime, now, isActiveMode) {
        if (usageState.lastFetchTime <= 0L) "sem dados"
        else {
            val elapsed = "atualizado ha ${formatElapsed(now - usageState.lastFetchTime)}"
            if (isActiveMode) "$elapsed · ATIVO" else elapsed
        }
    }

    val sessionPct = usageState.sessionUtilization.toFloat()
    val weeklyPct = usageState.weeklyUtilization.toFloat()
    val sessionReviving = now < sessionRevivalUntil
    val weeklyReviving = now < weeklyRevivalUntil

    val sessionStage = if (displayConfig.showMascots && displayConfig.showAgoraMascots) {
        if (sessionReviving) MascotStage.REVIVING else stageFor(sessionPct)
    } else null
    val weeklyStage = if (displayConfig.showMascots && displayConfig.showAgoraMascots) {
        if (weeklyReviving) MascotStage.REVIVING else stageFor(weeklyPct)
    } else null
    val headerStage = if (displayConfig.showMascots) {
        if (sessionReviving || weeklyReviving) MascotStage.REVIVING else stageFor(maxOf(sessionPct, weeklyPct))
    } else null

    val pages = remember(displayConfig.showMascots, displayConfig.showClawdTab) {
        if (displayConfig.showMascots && displayConfig.showClawdTab) {
            listOf(MonitorPage.AGORA, MonitorPage.CLAWD, MonitorPage.TREND, MonitorPage.HEAT)
        } else {
            listOf(MonitorPage.AGORA, MonitorPage.TREND, MonitorPage.HEAT)
        }
    }
    // Falls back to Agora (index 0) when Clawd isn't in pages — e.g. its tab is
    // off, so "open on Clawd" has nothing to point at.
    val initialPage = remember(pages, displayConfig.openClawdFirst) {
        if (displayConfig.openClawdFirst) pages.indexOf(MonitorPage.CLAWD).coerceAtLeast(0) else 0
    }

    Box(modifier = modifier.fillMaxSize()) {
        MonitorScaffold(
            portraitContent = {
                MonitorContent(
                    usageState = usageState, now = now, statusText = statusText, refreshFraction = refreshFraction,
                    onRefresh = onRefresh, onSettings = { showSettings = true },
                    historySamples = historySamples,
                    stackedAgora = true,
                    pages = pages,
                    initialPage = initialPage,
                    headerStage = headerStage, sessionStage = sessionStage, weeklyStage = weeklyStage,
                    sessionReviving = sessionReviving, weeklyReviving = weeklyReviving,
                )
            },
            landscapeContent = {
                MonitorContent(
                    usageState = usageState, now = now, statusText = statusText, refreshFraction = refreshFraction,
                    onRefresh = onRefresh, onSettings = { showSettings = true },
                    historySamples = historySamples,
                    stackedAgora = false,
                    pages = pages,
                    initialPage = initialPage,
                    headerStage = headerStage, sessionStage = sessionStage, weeklyStage = weeklyStage,
                    sessionReviving = sessionReviving, weeklyReviving = weeklyReviving,
                )
            }
        )

        activeMoment?.let { event ->
            MomentOverlay(event = event, now = now, onDismiss = { activeMoment = null })
        }

        if (showSettings) {
            SettingsScreen(
                isApiBlocked = usageState.isApiBlocked,
                displayConfig = displayConfig,
                onShowMascotsChange = displaySettings::setShowMascots,
                onShowAgoraMascotsChange = displaySettings::setShowAgoraMascots,
                onShowClawdTabChange = displaySettings::setShowClawdTab,
                onOpenClawdFirstChange = displaySettings::setOpenClawdFirst,
                onOpenMascotGallery = { showMascotGallery = true },
                pollingConfig = pollingConfig,
                pollMode = usageState.pollMode,
                onAdaptiveChange = pollingSettings::setAdaptiveEnabled,
                onIdleIntervalChange = pollingSettings::setIdleIntervalMs,
                onActiveIntervalChange = pollingSettings::setActiveIntervalMs,
                onLogout = {
                    showSettings = false
                    onLogout()
                },
                onClose = { showSettings = false },
            )
        }

        if (showMascotGallery) {
            MascotGalleryScreen(onClose = { showMascotGallery = false })
        }
    }
}

@Composable
private fun MonitorContent(
    usageState: SessionManager.UsageState,
    now: Long,
    statusText: String,
    refreshFraction: Float,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    historySamples: List<com.example.claudecounter.data.UsageSample>,
    stackedAgora: Boolean,
    pages: List<MonitorPage>,
    initialPage: Int,
    headerStage: MascotStage?,
    sessionStage: MascotStage?,
    weeklyStage: MascotStage?,
    sessionReviving: Boolean,
    weeklyReviving: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        StickHeader(
            statusText = statusText,
            refreshFraction = refreshFraction,
            onRefresh = onRefresh,
            onSettings = onSettings,
            mascotStage = headerStage,
        )
        // Keyed on the page set so the pager's remembered PagerState (and thus
        // its currentPage index) gets rebuilt when the Clawd tab is toggled on
        // or off in Settings — otherwise a stale index could point past the end
        // of a shrunk page list.
        key(pages.size) {
            MonitorPager(
                pageCount = pages.size,
                initialPage = initialPage,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { index ->
                when (pages[index]) {
                    MonitorPage.AGORA -> TileAgora(
                        sessionPct = usageState.sessionUtilization.toFloat(),
                        sessionResetsAtMs = usageState.sessionResetsAt,
                        weeklyPct = usageState.weeklyUtilization.toFloat(),
                        weeklyResetsAtMs = usageState.weeklyResetsAt,
                        now = now,
                        isApiBlocked = usageState.isApiBlocked,
                        stacked = stackedAgora,
                        sessionStage = sessionStage,
                        weeklyStage = weeklyStage,
                    )
                    MonitorPage.CLAWD -> TileClawd(
                        sessionPct = usageState.sessionUtilization.toFloat(),
                        sessionResetsAtMs = usageState.sessionResetsAt,
                        sessionReviving = sessionReviving,
                        weeklyPct = usageState.weeklyUtilization.toFloat(),
                        weeklyResetsAtMs = usageState.weeklyResetsAt,
                        weeklyReviving = weeklyReviving,
                        now = now,
                        isApiBlocked = usageState.isApiBlocked,
                        stacked = stackedAgora,
                    )
                    MonitorPage.TREND -> TileTrend(
                        sessionResetsAtMs = usageState.sessionResetsAt,
                        weeklyResetsAtMs = usageState.weeklyResetsAt,
                        now = now,
                        allSamples = historySamples,
                    )
                    MonitorPage.HEAT -> TileHeat(samples = historySamples, now = now)
                }
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return when {
        totalSeconds < 60 -> "${totalSeconds}s"
        totalSeconds < 3600 -> "${totalSeconds / 60}min"
        else -> String.format(Locale.getDefault(), "%dh%02dmin", totalSeconds / 3600, (totalSeconds % 3600) / 60)
    }
}
