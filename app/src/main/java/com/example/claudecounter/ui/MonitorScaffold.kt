package com.example.claudecounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.example.claudecounter.ui.theme.StickColors
import com.example.claudecounter.ui.theme.StickDimens

/**
 * Root layout that picks between two presentations depending on device orientation:
 *
 * - **Landscape** ("monitor mode"): renders [landscapeContent] on a 320dp-tall
 *   logical canvas — matching the stick firmware's LCD height exactly — scaled to
 *   fill the screen's height, with the logical *width* stretched to match
 *   whatever's left over (bounded by [StickDimens.CanvasWidth]..[StickDimens.CanvasWidthMax]).
 *   On a screen close to the original 480x320 aspect this reduces to the same
 *   pixel-perfect fit as before; on a wider phone (most modern ones, propped up
 *   flat) the tiles get the full width instead of being letterboxed. This is what
 *   you get when the phone is propped up flat like the physical stick.
 * - **Portrait**: renders [portraitContent], a normal scrollable phone layout that
 *   adapts the same tiles into a stacked arrangement — already responsive since
 *   it has no fixed logical canvas.
 *
 * Callers author landscape content against a `fillMaxWidth()`/`weight()` model
 * (like the rest of the tiles already do), not a hard-coded 480dp — the logical
 * width they actually get varies with the device.
 */
@Composable
fun MonitorScaffold(
    modifier: Modifier = Modifier,
    portraitContent: @Composable () -> Unit,
    landscapeContent: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(StickColors.Bg)
    ) {
        val isLandscape = maxWidth >= maxHeight
        if (isLandscape) {
            // Fit height first — it's almost always the tighter constraint in
            // landscape — then see how much logical width that leaves.
            val heightFitScale = maxHeight / StickDimens.CanvasHeight
            val widthAtHeightFit = maxWidth / heightFitScale

            val scale: Float
            val logicalWidth: Dp
            if (widthAtHeightFit < StickDimens.CanvasWidth) {
                // Screen is narrower/more square than 480x320 (aspect < 1.5:1) —
                // fitting height alone would squeeze tiles below their authored
                // width, so fall back to the original fit-both-axes behavior.
                scale = minOf(
                    maxWidth / StickDimens.CanvasWidth,
                    maxHeight / StickDimens.CanvasHeight
                )
                logicalWidth = StickDimens.CanvasWidth
            } else {
                scale = heightFitScale
                logicalWidth = widthAtHeightFit.coerceAtMost(StickDimens.CanvasWidthMax)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(logicalWidth * scale, StickDimens.CanvasHeight * scale)
            ) {
                Box(
                    modifier = Modifier
                        .size(logicalWidth, StickDimens.CanvasHeight)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                ) {
                    landscapeContent()
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                portraitContent()
            }
        }
    }
}
