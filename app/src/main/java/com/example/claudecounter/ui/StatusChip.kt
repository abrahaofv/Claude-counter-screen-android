package com.example.claudecounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.claudecounter.ui.theme.StickDimens
import com.example.claudecounter.ui.theme.StickType

/** Small pill with a tinted background, matching chip() in tools/gen_mockups.py. */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(StickDimens.ChipRadius))
            .background(chipBackground(color))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = text, style = StickType.caption, color = color)
    }
}
