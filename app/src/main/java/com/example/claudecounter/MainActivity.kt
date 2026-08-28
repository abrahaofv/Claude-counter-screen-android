package com.example.claudecounter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.claudecounter.ui.MonitorRoot
import com.example.claudecounter.ui.brand.Mascot
import com.example.claudecounter.ui.brand.MascotMood
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickTheme
import com.example.claudecounter.ui.theme.StickType

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

        // Keep the screen on while the app is in the foreground, like a game —
        // this is what makes "prop the phone up as a monitor" viable. Automatically
        // cleared by the system once the activity leaves the foreground.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // True fullscreen, game-style: hide the status bar and nav bar entirely
        // instead of just drawing behind them (enableEdgeToEdge alone leaves them
        // visible and overlapping content). They only reappear on an edge swipe,
        // and auto-hide again — same behavior as a game in immersive mode.
        hideSystemBars()

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
            StickTheme {
                val usageState by viewModel.usageState.collectAsStateWithLifecycle()

                if (usageState.isLoggedIn) {
                    // Always start the service, even while isApiBlocked: it now
                    // self-throttles to a long retry cadence instead of hot-polling,
                    // so withholding it here would only prevent auto-recovery.
                    LaunchedEffect(Unit) {
                        startForegroundService(Intent(this@MainActivity, UsagePollingService::class.java))
                    }
                    MonitorRoot(
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

    // Android re-shows the system bars whenever the window regains focus (e.g.
    // after the notification-permission dialog, or switching back from another
    // app) — reassert immersive mode each time, exactly like a game would.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

// ─── Login Prompt ─────────────────────────────────────────────────────────

@Composable
private fun LoginPromptScreen(onLogin: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StickColors.Bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Mascot(width = 88.dp, mood = MascotMood.Ok)
            Text(
                "Claude Counter",
                style = StickType.heading.copy(fontSize = 28.sp),
                color = StickColors.Text
            )
            Text(
                "Acompanhe o uso das suas janelas de 5 horas e semanal em tempo real, " +
                    "com aviso quando uma nova janela comeca.",
                color = StickColors.Muted,
                textAlign = TextAlign.Center,
                style = StickType.label,
                lineHeight = 22.sp
            )
            Button(
                onClick = onLogin,
                colors = ButtonDefaults.buttonColors(containerColor = StickColors.Accent, contentColor = StickColors.Bg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Login with Claude", style = StickType.heading, color = StickColors.Bg)
            }
        }
    }
}
