package com.example.claudecounter.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import com.example.claudecounter.ui.theme.StickColors
import kotlin.math.sin

/**
 * "Clawd" — the pixel-art mascot from the stick firmware, ported as a Compose
 * [Path] (not a static drawable) so its eye sockets stay pixel-aligned with the
 * body at any render size and can be filled with mood-specific overlays.
 *
 * Geometry is lifted directly from the outer contour + two eye-socket cutouts in
 * assets/brand/claudecode-color.svg (viewBox 0 0 24 24, evenodd fill). The artwork
 * only occupies y=[5,20] of that 24-unit box, so we crop to that band the same way
 * gen_mockups.py's `im.crop(im.getbbox())` does — otherwise the rendered mascot
 * would carry dead transparent padding above/below and throw off layout math that
 * assumes a ~0.625 aspect ratio (e.g. the 42x26 header icon).
 */
private const val ViewBoxSize = 24f
private const val ArtworkTop = 5f
private const val ArtworkHeight = 15f // 20 - 5
const val MascotAspectRatio = ArtworkHeight / ViewBoxSize // 0.625

private val OuterContour = listOf(
    20.998f to 10.949f, 24f to 10.949f, 24f to 14.051f, 21f to 14.051f, 21f to 17.079f,
    19.513f to 17.079f, 19.513f to 20f, 18f to 20f, 18f to 17.079f, 16.513f to 17.079f,
    16.513f to 20f, 15f to 20f, 15f to 17.079f, 9f to 17.079f, 9f to 20f, 7.488f to 20f,
    7.488f to 17.079f, 6f to 17.079f, 6f to 20f, 4.487f to 20f, 4.487f to 17.079f,
    3f to 17.079f, 3f to 14.05f, 0f to 14.05f, 0f to 10.95f, 3f to 10.95f, 3f to 5f,
    20.998f to 5f,
)

// left, top, right, bottom — in the same viewBox units as OuterContour.
private val EyeLeft = floatArrayOf(6f, 8.102f, 7.488f, 10.949f)
private val EyeRight = floatArrayOf(16.51f, 8.102f, 18f, 10.949f)

/**
 * Connection state: whether we could reach the endpoint at all. Deliberately
 * orthogonal to [MascotStage], which is about how hard Clawd is *working*.
 * A mood other than [Ok]/[Limited] means we have no trustworthy usage number,
 * so it takes precedence over the stage when rendering.
 */
enum class MascotMood {
    /** Never probed this cycle — dim/idle, no animation. */
    NeverProbed,
    /** Healthy — the stage decides how he looks. */
    Ok,
    /** Rate-limited / 429 — bobs less, sheds an intermittent sweat drop. */
    Limited,
    /** Auth/network/5xx error — recolored gray, eyes replaced with a red X. */
    Error,
    /** Endpoint not available (404) — sleepy half-closed eyes, static. */
    Unavailable,
}

/**
 * Vitality: how much of a usage window has been burned. Clawd strains harder the
 * closer the window gets to full and drops dead at 100%, which is the whole point
 * of the tamagotchi framing — the thing that "kills" him is your own token spend,
 * and the only cure is the window reset (see [MascotStage.REVIVING]).
 *
 * The 25/50/70/100 cuts are deliberately the same ones
 * [com.example.claudecounter.SessionManager] fires threshold moments on, so the
 * overlay and the persistent mascot never disagree about what state he's in.
 * The extra cut at 90 matches the bands in
 * [com.example.claudecounter.ui.pctColorDiscrete].
 */
enum class MascotStage(val label: String) {
    RESTED("DESCANSADO"),
    WORKING("TRABALHANDO"),
    STRAINING("SUANDO"),
    EXHAUSTED("EXAUSTO"),
    CRITICAL("NO LIMITE"),
    KO("MORTO"),
    /** Transient, ~2s after a window reset drops utilization back down. */
    REVIVING("RENASCEU"),
}

fun stageFor(pct: Float): MascotStage = when {
    pct >= 100f -> MascotStage.KO
    pct >= 90f -> MascotStage.CRITICAL
    pct >= 70f -> MascotStage.EXHAUSTED
    pct >= 50f -> MascotStage.STRAINING
    pct >= 25f -> MascotStage.WORKING
    else -> MascotStage.RESTED
}

private enum class EyeStyle { NORMAL, NARROW, HEAVY_LID, WOBBLE, WIDE, SLEEPY, DEAD_X }

/**
 * Everything the canvas needs for one frame, resolved from mood + stage + the
 * running animation phases. Kept as a plain value (no Compose types beyond
 * [Color]) so the "what does Clawd look like right now" decision is one pure
 * function instead of a `when (mood)` repeated in every draw call.
 */
private data class MascotVisuals(
    val bodyColor: Color,
    val bodyAlpha: Float,
    val bobPx: Float,
    val shakePx: Float,
    val slumpPx: Float,
    val breathScale: Float,
    val eyeStyle: EyeStyle,
    val blinking: Boolean,
    val sweatDrops: Int,
)

/** Bob amplitude (px) / period (ms), shake amplitude, sweat drops, breath depth. */
private data class StageMotion(
    val bobPx: Float,
    val bobPeriodMs: Int,
    val shakePx: Float,
    val sweatDrops: Int,
    val breathDepth: Float,
    val blinkPeriodMs: Int,
)

private fun motionFor(stage: MascotStage): StageMotion = when (stage) {
    MascotStage.RESTED -> StageMotion(1.5f, 2400, 0f, 0, 0f, 3000)
    MascotStage.WORKING -> StageMotion(2f, 1400, 0f, 0, 0f, 2200)
    MascotStage.STRAINING -> StageMotion(2.5f, 900, 0f, 1, 0f, 1800)
    MascotStage.EXHAUSTED -> StageMotion(3f, 600, 0.8f, 2, 0.012f, 1500)
    MascotStage.CRITICAL -> StageMotion(4f, 420, 2f, 3, 0.022f, 1200)
    MascotStage.KO -> StageMotion(0f, 2400, 0f, 0, 0f, 3000)
    MascotStage.REVIVING -> StageMotion(2f, 700, 0f, 0, 0f, 900)
}

/**
 * Body tint per stage. We do NOT recolor Clawd wholesale into the green/amber/red
 * usage gradient — the coral [StickColors.Accent] is the brand, and a green Clawd
 * reads as a different character. Mixing a *little* of the band color in reads as
 * "flushed from the effort" while staying recognisably him. The one full recolor
 * is death, which is supposed to be jarring.
 */
private fun bodyColorFor(stage: MascotStage): Color = when (stage) {
    MascotStage.RESTED, MascotStage.WORKING, MascotStage.REVIVING -> StickColors.Accent
    MascotStage.STRAINING -> lerp(StickColors.Accent, StickColors.Warn, 0.15f)
    MascotStage.EXHAUSTED -> lerp(StickColors.Accent, StickColors.Warn, 0.25f)
    MascotStage.CRITICAL -> lerp(StickColors.Accent, StickColors.Bad, 0.35f)
    MascotStage.KO -> StickColors.MascotKo
}

private fun eyeStyleFor(stage: MascotStage): EyeStyle = when (stage) {
    MascotStage.RESTED, MascotStage.WORKING -> EyeStyle.NORMAL
    MascotStage.STRAINING -> EyeStyle.NARROW
    MascotStage.EXHAUSTED -> EyeStyle.HEAVY_LID
    MascotStage.CRITICAL -> EyeStyle.WOBBLE
    MascotStage.KO -> EyeStyle.DEAD_X
    MascotStage.REVIVING -> EyeStyle.WIDE
}

private fun mascotVisuals(
    mood: MascotMood,
    stage: MascotStage,
    bobPhase: Float,
    blinkPhase: Float,
    shakePhase: Float,
): MascotVisuals {
    // A mood other than Ok/Limited means the usage number behind [stage] is stale
    // or missing, so connection state wins — showing a cheerfully bobbing Clawd
    // next to a dead endpoint would be a lie.
    if (mood == MascotMood.Error) {
        return MascotVisuals(
            bodyColor = StickColors.MascotKo, bodyAlpha = 1f, bobPx = 0f, shakePx = 0f,
            slumpPx = 6f, breathScale = 1f, eyeStyle = EyeStyle.DEAD_X, blinking = false, sweatDrops = 0
        )
    }
    if (mood == MascotMood.Unavailable || mood == MascotMood.NeverProbed) {
        return MascotVisuals(
            bodyColor = StickColors.Accent,
            bodyAlpha = if (mood == MascotMood.NeverProbed) 140f / 255f else 1f,
            bobPx = 0f, shakePx = 0f, slumpPx = 0f, breathScale = 1f,
            eyeStyle = EyeStyle.SLEEPY, blinking = false, sweatDrops = 0
        )
    }

    val motion = motionFor(stage)
    // A 429 means the *server* is throttling us on top of whatever the quota says,
    // so he sweats at least one drop even while the stage is otherwise relaxed.
    val sweat = if (mood == MascotMood.Limited) maxOf(motion.sweatDrops, 1) else motion.sweatDrops

    return MascotVisuals(
        bodyColor = bodyColorFor(stage),
        bodyAlpha = 1f,
        bobPx = motion.bobPx * sin(bobPhase),
        shakePx = motion.shakePx * sin(shakePhase),
        slumpPx = if (stage == MascotStage.KO) 6f else 0f,
        breathScale = 1f + motion.breathDepth * sin(bobPhase * 2f),
        eyeStyle = eyeStyleFor(stage),
        // Blink for the last ~150ms of each cycle. The dead don't blink.
        blinking = stage != MascotStage.KO && blinkPhase >= 1f - (150f / motion.blinkPeriodMs),
        sweatDrops = sweat,
    )
}

/**
 * Renders Clawd at [width] (height follows [MascotAspectRatio] automatically).
 * [modifier] is applied to the outer Box so callers (e.g. the momento overlay)
 * can layer their own entrance/shake transforms without touching the mood/stage
 * animation defined here.
 */
@Composable
fun Mascot(
    width: Dp,
    mood: MascotMood = MascotMood.Ok,
    stage: MascotStage = MascotStage.RESTED,
    modifier: Modifier = Modifier,
) {
    val motion = motionFor(stage)
    val infinite = rememberInfiniteTransition(label = "mascot")

    // Idle bob: firmware uses `2*sin(phase + i*0.9) - 1` px. The period shortens as
    // he strains, which is what sells "working harder" more than amplitude does.
    val bobPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(motion.bobPeriodMs, easing = LinearEasing)),
        label = "bob"
    )

    // Normalized 0..1 blink cycle so the eyelid timing is period-independent.
    val blinkPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(motion.blinkPeriodMs, easing = LinearEasing)),
        label = "blink"
    )

    // Fast phase driving the tremor at EXHAUSTED/CRITICAL.
    val shakePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(140, easing = LinearEasing)),
        label = "shake"
    )

    // Sweat drop cycle: falls and fades over 900ms, then loops.
    val tearPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "tear"
    )

    // One-shot bouncy pop when he comes back to life — the payoff for the reset.
    val popScale = remember { Animatable(1f) }
    LaunchedEffect(stage) {
        if (stage == MascotStage.REVIVING) {
            popScale.snapTo(0.55f)
            popScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow))
        } else {
            popScale.snapTo(1f)
        }
    }

    val visuals = mascotVisuals(mood, stage, bobPhase, blinkPhase, shakePhase)
    val heightDp = width * MascotAspectRatio

    Box(modifier.size(width, heightDp)) {
        Canvas(Modifier.size(width, heightDp)) {
            val scaleFactor = size.width / ViewBoxSize
            fun vx(v: Float) = v * scaleFactor
            fun vy(v: Float) = (v - ArtworkTop) * scaleFactor

            scale(popScale.value * visuals.breathScale) {
                translate(left = visuals.shakePx, top = visuals.bobPx + visuals.slumpPx) {
                    val path = Path().apply {
                        fillType = PathFillType.EvenOdd
                        moveTo(vx(OuterContour[0].first), vy(OuterContour[0].second))
                        for (i in 1 until OuterContour.size) {
                            lineTo(vx(OuterContour[i].first), vy(OuterContour[i].second))
                        }
                        close()
                        addRect(Rect(vx(EyeLeft[0]), vy(EyeLeft[1]), vx(EyeLeft[2]), vy(EyeLeft[3])))
                        addRect(Rect(vx(EyeRight[0]), vy(EyeRight[1]), vx(EyeRight[2]), vy(EyeRight[3])))
                    }
                    drawPath(path, color = visuals.bodyColor, alpha = visuals.bodyAlpha)

                    drawEye(EyeLeft, ::vx, ::vy, visuals, shakePhase)
                    drawEye(EyeRight, ::vx, ::vy, visuals, shakePhase)
                    drawSweat(EyeRight, ::vx, ::vy, visuals.sweatDrops, tearPhase)
                }
            }
        }
    }
}

private fun DrawScope.drawEye(
    socket: FloatArray,
    vx: (Float) -> Float,
    vy: (Float) -> Float,
    visuals: MascotVisuals,
    shakePhase: Float,
) {
    val left = vx(socket[0])
    val top = vy(socket[1])
    val right = vx(socket[2])
    val bottom = vy(socket[3])
    val w = right - left
    val h = bottom - top

    /** Pupil sized as a fraction of the socket, optionally nudged sideways. */
    fun pupil(widthFrac: Float, heightFrac: Float, dx: Float = 0f, alpha: Float = 0.8f) {
        drawRoundRect(
            color = Color.Black.copy(alpha = alpha * visuals.bodyAlpha),
            topLeft = Offset(left + w * (1f - widthFrac) / 2f + dx, top + h * (1f - heightFrac) / 2f),
            size = Size(w * widthFrac, h * heightFrac),
            cornerRadius = CornerRadius(w * 0.18f)
        )
    }

    /** Coral eyelid dropping [frac] of the way down the socket. */
    fun lid(frac: Float) {
        drawRoundRect(
            color = visuals.bodyColor.copy(alpha = visuals.bodyAlpha),
            topLeft = Offset(left, top),
            size = Size(w, h * frac),
            cornerRadius = CornerRadius(w * 0.15f)
        )
    }

    when (visuals.eyeStyle) {
        EyeStyle.DEAD_X -> {
            val strokeWidth = (w * 0.28f).coerceAtLeast(1f)
            drawLine(StickColors.Bad, Offset(left, top), Offset(right, bottom), strokeWidth, StrokeCap.Round)
            drawLine(StickColors.Bad, Offset(right, top), Offset(left, bottom), strokeWidth, StrokeCap.Round)
        }
        EyeStyle.SLEEPY -> {
            pupil(0.7f, 0.5f, alpha = 0.75f)
            lid(0.4f)
        }
        EyeStyle.NORMAL -> {
            pupil(0.64f, 0.76f)
            if (visuals.blinking) lid(1f)
        }
        EyeStyle.NARROW -> {
            // Squinting with effort — smaller pupil, slight lid.
            pupil(0.5f, 0.5f)
            lid(0.22f)
            if (visuals.blinking) lid(1f)
        }
        EyeStyle.HEAVY_LID -> {
            pupil(0.55f, 0.45f)
            lid(0.5f)
            if (visuals.blinking) lid(1f)
        }
        EyeStyle.WOBBLE -> {
            // Eyes darting: the pupil skitters inside the socket.
            pupil(0.42f, 0.42f, dx = w * 0.16f * sin(shakePhase))
            lid(0.3f)
        }
        EyeStyle.WIDE -> {
            pupil(0.8f, 0.9f, alpha = 0.85f)
        }
    }
}

/**
 * Falling, fading sweat drops beside the right eye. Drops are phase-staggered so
 * they trail each other instead of falling as one blob.
 */
private fun DrawScope.drawSweat(
    socket: FloatArray,
    vx: (Float) -> Float,
    vy: (Float) -> Float,
    count: Int,
    tearPhase: Float,
) {
    if (count <= 0) return
    val left = vx(socket[0])
    val top = vy(socket[1])
    val right = vx(socket[2])
    val bottom = vy(socket[3])
    val w = right - left
    val h = bottom - top
    val cy = top + h / 2f

    for (i in 0 until count) {
        val t = (tearPhase + i * 0.33f) % 1f
        drawRoundRect(
            color = StickColors.Blue.copy(alpha = (1f - t).coerceIn(0f, 1f)),
            topLeft = Offset(right + w * 0.15f, cy + t * h * 1.4f),
            size = Size(w * 0.5f, h * 0.55f),
            cornerRadius = CornerRadius(w * 0.2f)
        )
    }
}
