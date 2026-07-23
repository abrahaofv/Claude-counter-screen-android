package com.example.claudecounter.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.claudecounter.R
import com.example.claudecounter.ui.brand.Mascot
import com.example.claudecounter.ui.brand.MascotMood
import com.example.claudecounter.ui.brand.MascotStage
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickDimens
import com.example.claudecounter.ui.theme.StickType

/**
 * Header chrome shared by every tile: mascot + wordmark, "updated Xs ago" status,
 * refresh / settings buttons, and the thin refresh-countdown bar underneath.
 * Matches header()/gear() in tools/gen_mockups.py.
 *
 * [mascotStage] is null when the mascot is disabled (Settings -> Aparencia) or
 * when there's no trustworthy usage number yet — in both cases the header falls
 * back to a static, always-content Clawd instead of the reactive one.
 */
@Composable
fun StickHeader(
    statusText: String,
    refreshFraction: Float,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    mascotStage: MascotStage? = null,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(StickDimens.HeaderHeight)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Mascot(width = 42.dp, mood = MascotMood.Ok, stage = mascotStage ?: MascotStage.RESTED)
            Image(
                painter = painterResource(R.drawable.wordmark_claudecode),
                contentDescription = "Claude Code",
                modifier = Modifier.height(26.dp)
            )

            Box(modifier = Modifier.weight(1f))

            Text(text = statusText, style = StickType.caption, color = StickColors.Muted)

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(width = StickDimens.RefreshButtonSize, height = StickDimens.HeaderButtonHeight)
                    .clip(RoundedCornerShape(StickDimens.PillRadius))
                    .background(StickColors.Surface2)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = StickColors.Accent)
            }

            IconButton(
                onClick = onSettings,
                modifier = Modifier
                    .size(width = StickDimens.SettingsButtonWidth, height = StickDimens.HeaderButtonHeight)
                    .clip(RoundedCornerShape(StickDimens.PillRadius))
                    .background(StickColors.Surface2)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = StickColors.Text)
            }
        }

        // Thin refresh-countdown bar, pinned just under the header row.
        val animatedFraction by animateFloatAsState(
            targetValue = refreshFraction.coerceIn(0f, 1f),
            animationSpec = tween(250),
            label = "refreshBar"
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(StickDimens.RefreshBarHeight)
                .background(StickColors.Surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(StickDimens.RefreshBarHeight)
                    .background(StickColors.Accent)
            )
        }
    }
}
