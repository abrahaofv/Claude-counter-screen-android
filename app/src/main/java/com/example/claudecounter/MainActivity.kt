package com.example.claudecounter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: UsageViewModel by viewModels()

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.refresh()
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, just move on */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        NotificationHelper.createChannels(this)

        setContent {
            ClaudeCounterTheme {
                val usageState by viewModel.usageState.collectAsStateWithLifecycle()

                if (usageState.isLoggedIn) {
                    // Only start polling service if API is not known to be blocked
                    if (!usageState.isApiBlocked) {
                        LaunchedEffect(Unit) {
                            startForegroundService(Intent(this@MainActivity, UsagePollingService::class.java))
                        }
                    }
                    MainScreen(
                        usageState = usageState,
                        onRefresh = { viewModel.refresh() },
                        onLogout = { viewModel.logout() }
                    )
                } else {
                    LoginPromptScreen {
                        loginLauncher.launch(LoginWebViewActivity.createIntent(this@MainActivity))
                    }
                }
            }
        }
    }
}

// ─── Theme ───────────────────────────────────────────────────────────────────

private val DarkBackground = Color(0xFF0D0C14)
private val CardBackground = Color(0xFF1A1825)
private val AccentPurple = Color(0xFF7C5CFC)
private val AccentBlue = Color(0xFF4A90D9)
private val SessionColor = Color(0xFF7C5CFC)
private val WeeklyColor = Color(0xFF4A90D9)
private val WarningColor = Color(0xFFFF6B6B)
private val TextPrimary = Color(0xFFF0EEF8)
private val TextSecondary = Color(0xFF9B99AE)

@Composable
private fun ClaudeCounterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DarkBackground,
            surface = CardBackground,
            primary = AccentPurple,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        ),
        content = content
    )
}

// ─── Login Prompt ─────────────────────────────────────────────────────────

@Composable
private fun LoginPromptScreen(onLogin: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .windowInsetsPadding(WindowInsets.statusBars),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("⚡", fontSize = 64.sp)
            Text(
                "Claude Counter",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "Track your Claude message usage in real‑time and get notified when your session or weekly window resets.",
                color = TextSecondary,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onLogin,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Login with Claude", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

// ─── Main Screen ─────────────────────────────────────────────────────────────

@Composable
private fun MainScreen(
    usageState: SessionManager.UsageState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    // Live countdown tick every second
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Gradient blob background
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-60).dp, y = (-40).dp)
                .background(
                    Brush.radialGradient(
                        listOf(AccentPurple.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Claude Counter", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Usage Tracker", fontSize = 13.sp, color = TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                    }
                }
            }

            // API Blocked Banner
            if (usageState.isApiBlocked) {
                ApiBlockedBanner()
            }

            Spacer(Modifier.height(4.dp))

            // Session Card
            UsageCard(
                title = "Session",
                subtitle = "5-hour window",
                utilization = usageState.sessionUtilization,
                resetsAtMs = usageState.sessionResetsAt,
                now = now,
                accentColor = SessionColor,
                isStale = usageState.isApiBlocked
            )

            // Weekly Card
            UsageCard(
                title = "Weekly",
                subtitle = "7-day window",
                utilization = usageState.weeklyUtilization,
                resetsAtMs = usageState.weeklyResetsAt,
                now = now,
                accentColor = WeeklyColor,
                isStale = usageState.isApiBlocked
            )

            Spacer(Modifier.weight(1f))

            // Last updated
            if (usageState.lastFetchTime > 0L) {
                val fmt = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
                Text(
                    text = if (usageState.isApiBlocked) "Last successful update: ${fmt.format(Date(usageState.lastFetchTime))}"
                           else "Last updated: ${fmt.format(Date(usageState.lastFetchTime))}",
                    color = if (usageState.isApiBlocked) WarningColor.copy(alpha = 0.7f) else TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
            }

            // Error message (non-blocked errors)
            if (!usageState.isApiBlocked && usageState.lastError != null) {
                Text(
                    text = "⚠ ${usageState.lastError}",
                    color = WarningColor.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
            }

            // Logout
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Text("Logout")
            }
        }
    }
}

// ─── API Blocked Banner ──────────────────────────────────────────────────────

@Composable
private fun ApiBlockedBanner() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = WarningColor.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⚠️", fontSize = 18.sp)
                Text(
                    "API Access Restricted",
                    color = WarningColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
            Text(
                "Anthropic has blocked third-party API access as of April 2026. " +
                "Background usage tracking is no longer available.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(2.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AccentPurple.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "💡 Use the Claude Counter browser extension instead for real-time tracking",
                    color = AccentPurple,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ─── Usage Card ─────────────────────────────────────────────────────────────

@Composable
private fun UsageCard(
    title: String,
    subtitle: String,
    utilization: Double,
    resetsAtMs: Long,
    now: Long,
    accentColor: Color,
    isStale: Boolean = false
) {
    val fraction = (utilization / 100.0).coerceIn(0.0, 1.0).toFloat()
    val isWarning = utilization >= 90.0
    val barColor = if (isWarning) WarningColor else accentColor
    val cardAlpha = if (isStale) 0.5f else 1.0f

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(700, easing = EaseInOut),
        label = "bar"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().alpha(cardAlpha)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(subtitle, fontSize = 12.sp, color = TextSecondary)
                }
                // Percentage badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = barColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${"%.1f".format(utilization)}%",
                        color = barColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.07f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(barColor.copy(alpha = 0.7f), barColor)
                            )
                        )
                )
            }

            Spacer(Modifier.height(10.dp))

            // Reset countdown
            if (resetsAtMs > 0L) {
                val diffMs = resetsAtMs - now
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (diffMs > 0) "Resets in ${formatCountdown(diffMs)}"
                               else "Reset imminent",
                        color = if (isWarning) WarningColor.copy(alpha = 0.85f) else TextSecondary,
                        fontSize = 13.sp
                    )
                    if (utilization == 0.0 && resetsAtMs > 0L && diffMs > 0L) {
                        Text("✓ Reset", color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                Text("No data yet", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun formatCountdown(ms: Long): String {
    if (ms <= 0) return "now"
    val totalSeconds = ms / 1000
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
