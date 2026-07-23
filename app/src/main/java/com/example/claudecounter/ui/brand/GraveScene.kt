package com.example.claudecounter.ui.brand

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import com.example.claudecounter.ui.theme.StickColors
import kotlin.math.sin

private const val ViewBoxWidth = 40f
private const val ViewBoxHeight = 34f
const val GraveSceneAspectRatio = ViewBoxHeight / ViewBoxWidth // 0.85

/** translateY(-14%) in the preview is relative to the ghost path's own ~24.5-unit bbox height. */
private const val GhostBobUnits = 24.5f * 0.14f

/**
 * Tombstone + floating ghost, replacing [Mascot] wherever [MascotStage.KO] would
 * otherwise show the gray-with-X body — see fase 7 plan for the "lapide/fantasma"
 * mechanic. Geometry ported 1:1 from clawd-fase7-preview.html's graveSvg()
 * (viewBox 0 0 40 34, no crop).
 */
@Composable
fun GraveScene(width: Dp, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "grave")
    // One full 0..2pi lap per 3.2s float cycle, matching ghost-float's period.
    val floatPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "ghost-float"
    )

    val heightDp = width * GraveSceneAspectRatio

    Box(modifier.size(width, heightDp)) {
        Canvas(Modifier.size(width, heightDp)) {
            val scaleFactor = size.width / ViewBoxWidth
            fun s(v: Float) = v * scaleFactor

            drawOval(
                color = Color.Black.copy(alpha = 0.32f),
                topLeft = Offset(s(5f), s(28.5f)),
                size = Size(s(30f), s(6f))
            )

            // 0%/100% of the CSS keyframe sit at floatPhase 0 and 2pi, the single
            // peak (translateY -14%, opacity 1) sits at the midpoint — sin(phase/2)
            // traces exactly that zero -> one -> zero hump over one lap.
            val riseFrac = sin(floatPhase / 2f)
            val ghostAlpha = 0.6f * (0.85f + 0.15f * riseFrac)

            translate(top = -riseFrac * s(GhostBobUnits)) {
                val ghostPath = Path().apply {
                    moveTo(s(9f), s(20f))
                    quadraticTo(s(9f), s(8f), s(19f), s(8f))
                    quadraticTo(s(29f), s(8f), s(29f), s(20f))
                    lineTo(s(29f), s(29f))
                    quadraticTo(s(27f), s(32.5f), s(25f), s(29f))
                    quadraticTo(s(23f), s(32.5f), s(21f), s(29f))
                    quadraticTo(s(19f), s(32.5f), s(17f), s(29f))
                    quadraticTo(s(15f), s(32.5f), s(13f), s(29f))
                    quadraticTo(s(11f), s(32.5f), s(9f), s(29f))
                    close()
                }
                drawPath(ghostPath, color = StickColors.Accent, alpha = ghostAlpha)

                val eyeColor = Color.Black.copy(alpha = 0.55f)
                drawRoundRect(
                    color = eyeColor,
                    topLeft = Offset(s(14.4f), s(17f)),
                    size = Size(s(2.2f), s(3f)),
                    cornerRadius = CornerRadius(s(0.9f))
                )
                drawRoundRect(
                    color = eyeColor,
                    topLeft = Offset(s(20.4f), s(17f)),
                    size = Size(s(2.2f), s(3f)),
                    cornerRadius = CornerRadius(s(0.9f))
                )
            }

            // Tombstone: two quarter-circle arcs (r=6, centers at (16,17) and
            // (24,17)) round the top corners, matching the SVG's `A6,6 0 0 1` arcs.
            val stonePath = Path().apply {
                moveTo(s(10f), s(17f))
                arcTo(Rect(s(10f), s(11f), s(22f), s(23f)), 180f, 90f, false)
                lineTo(s(24f), s(11f))
                arcTo(Rect(s(18f), s(11f), s(30f), s(23f)), 270f, 90f, false)
                lineTo(s(30f), s(30f))
                lineTo(s(10f), s(30f))
                close()
            }
            drawPath(stonePath, color = StickColors.MascotKo)

            val crackColor = Color.Black.copy(alpha = 0.28f)
            val crackWidth = s(0.6f)
            drawLine(crackColor, Offset(s(14.5f), s(23f)), Offset(s(18f), s(28.5f)), crackWidth, StrokeCap.Round)
            drawLine(crackColor, Offset(s(18f), s(28.5f)), Offset(s(16.2f), s(30f)), crackWidth, StrokeCap.Round)

            drawContext.canvas.nativeCanvas.drawText(
                "RIP",
                s(20f),
                s(24.5f),
                Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.argb((0.34f * 255).toInt(), 0, 0, 0)
                    textAlign = Paint.Align.CENTER
                    textSize = s(6f)
                    letterSpacing = 0.08f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
            )
        }
    }
}
