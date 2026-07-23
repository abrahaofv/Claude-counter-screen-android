package com.example.claudecounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickDimens

/**
 * The 18-segment usage meter used on every tile. Matches meter() in
 * tools/gen_mockups.py: lit segments are colored by [usageColor], unlit
 * segments fall back to the track color at reduced opacity.
 */
@Composable
fun SegmentMeter(
    pct: Float,
    modifier: Modifier = Modifier,
    count: Int = StickDimens.SegmentCount,
) {
    val filled = litSegments(pct, count)
    val litColor = usageColor(pct)
    Row(modifier = modifier) {
        for (i in 0 until count) {
            if (i > 0) {
                androidx.compose.foundation.layout.Spacer(
                    Modifier.size(StickDimens.SegmentStep - StickDimens.SegmentWidth)
                )
            }
            val lit = i < filled
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(StickDimens.SegmentWidth, StickDimens.SegmentHeight)
                    .clip(RoundedCornerShape(StickDimens.SegmentRadius))
                    .background(if (lit) litColor else StickColors.Track)
                    .alpha(if (lit) 1f else 0.63f)
            )
        }
    }
}
