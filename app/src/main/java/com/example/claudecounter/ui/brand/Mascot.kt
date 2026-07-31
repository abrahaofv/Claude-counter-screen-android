package com.example.claudecounter.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import com.example.claudecounter.ui.theme.StickColors
import kotlin.math.sqrt

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
 *
 * The animation itself is driven by [MascotBehaviorState] (see MascotBehavior.kt)
 * — a continuous, procedural engine ported from `clawd-v3-preview.html` — instead
 * of the fixed-period loops this file used to own directly.
 */
private const val ViewBoxSize = 24f

/**
 * The artwork band grew from [5,20] to [3.5,22.5] to make room for the shadow
 * and the small jump hop — neither existed when this only had to crop tight
 * to the silhouette. Width is untouched (no extended anatomy in this pass),
 * so [MascotAspectRatio] moves from 0.625 to ~0.79: about 27% taller for the
 * same width. That's a real layout change, most visible at the 42dp header
 * size — check it visually after this lands, it's the size flagged as most
 * likely to turn extra detail into noise.
 */
private const val ArtworkTop = 3.5f
private const val ArtworkHeight = 19f // 22.5 - 3.5
const val MascotAspectRatio = ArtworkHeight / ViewBoxSize

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

/** Foot line in viewBox units — pivot for tilt, and where [MascotBehaviorState]'s postureY/jumpHeight land. */
private const val FootY = 20f

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

/**
 * Representative vitality (0..100) for a stage, used only when a caller doesn't
 * have the exact usage percentage handy (e.g. [StickHeader]'s login/no-data
 * fallback). These are the midpoint of each [stageFor] band — 88/62/40/20/5 for
 * RESTED..CRITICAL — the same presets validated in the `clawd-v3-preview.html`
 * lab. Callers that do have the real percentage should pass [Mascot]'s `pct`
 * instead, so vitality is continuous rather than snapping to a band midpoint.
 */
private fun stageMidpointVitality(stage: MascotStage): Float = when (stage) {
    MascotStage.RESTED -> 88f
    MascotStage.WORKING -> 62f
    MascotStage.STRAINING -> 40f
    MascotStage.EXHAUSTED -> 20f
    MascotStage.CRITICAL -> 5f
    MascotStage.KO -> 0f
    MascotStage.REVIVING -> 95f
}

/**
 * Renders Clawd at [width] (height follows [MascotAspectRatio] automatically).
 * [modifier] is applied to the outer Box so callers (e.g. the momento overlay)
 * can layer their own entrance/shake transforms without touching the mood/stage
 * animation defined here.
 *
 * [pct] is the exact usage percentage (0..100) when the caller has it — it
 * drives [MascotBehaviorState] continuously instead of snapping between
 * [stageMidpointVitality] bands. Optional and additive: existing call sites
 * that only pass [stage] keep compiling and working unchanged.
 *
 * [behaviorState] lets a caller hoist and hold on to the engine instance
 * instead of Mascot owning one internally — the mascot gallery needs this to
 * call `forceAction`/`forceBlink` on the exact instance being drawn. null
 * (every existing call site) keeps the old self-contained behavior.
 */
@Composable
fun Mascot(
    width: Dp,
    mood: MascotMood = MascotMood.Ok,
    stage: MascotStage = MascotStage.RESTED,
    pct: Float? = null,
    behaviorState: MascotBehaviorState? = null,
    modifier: Modifier = Modifier,
) {
    if (stage == MascotStage.KO && mood == MascotMood.Ok) {
        GraveScene(width = width, modifier = modifier)
        return
    }

    val behavior = behaviorState ?: remember { MascotBehaviorState() }

    // Error/Unavailable/NeverProbed mean there's no trustworthy usage number at
    // all, so they short-circuit to a static look instead of driving the
    // procedural engine — same precedence rule the old mascotVisuals() had.
    val isStatic = mood == MascotMood.Error || mood == MascotMood.Unavailable || mood == MascotMood.NeverProbed
    val rawVitalityTarget = pct?.let { (100f - it).coerceIn(0f, 100f) } ?: stageMidpointVitality(stage)
    // Limited (429): the server is throttling on top of whatever the quota says,
    // so nudge vitality down enough that the sweat-rate trait kicks in — same
    // intent as the old maxOf(sweatDrops, 1).
    val vitalityTarget = if (mood == MascotMood.Limited) rawVitalityTarget.coerceAtMost(45f) else rawVitalityTarget

    // LaunchedEffect(Unit) never restarts, so the long-running frame loop reads
    // the target through rememberUpdatedState instead of closing over a stale
    // value from whichever recomposition first launched it.
    val currentVitalityTarget = rememberUpdatedState(vitalityTarget)
    val currentIsStatic = rememberUpdatedState(isStatic)

    var frameMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val dt = ((now - last) / 1000f).coerceIn(0f, 0.1f)
            last = now
            if (!currentIsStatic.value) {
                behavior.setVitalityTarget(currentVitalityTarget.value)
                behavior.step(dt)
            }
            frameMillis = now
        }
    }

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

    val heightDp = width * MascotAspectRatio

    Box(modifier.size(width, heightDp)) {
        Canvas(Modifier.size(width, heightDp)) {
            frameMillis // subscribe this draw to the per-frame tick
            val scaleFactor = size.width / ViewBoxSize
            fun vx(v: Float) = v * scaleFactor
            fun vy(v: Float) = (v - ArtworkTop) * scaleFactor

            if (isStatic) {
                drawStaticMood(mood, ::vx, ::vy, scaleFactor)
            } else {
                drawBehaviorFrame(behavior.frame, behavior.particlesSnapshot(), popScale.value, ::vx, ::vy, scaleFactor)
            }
        }
    }
}

private fun DrawScope.drawBehaviorFrame(
    frame: MascotFrame,
    particles: List<MascotParticle>,
    popScale: Float,
    vx: (Float) -> Float,
    vy: (Float) -> Float,
    scaleFactor: Float,
) {
    // Drawn first (behind everything) and deliberately NOT nested inside the
    // root translate below — a shadow tracks the ground, not the body's own
    // droop/jump offset. Only its size/alpha react to how airborne he is.
    drawShadow(frame, vx, vy, scaleFactor)

    // Root layer: posture droop + tremor (translate) + lean (rotate), pivoted
    // at the feet. Squash & stretch is a second, inner layer scoped to just
    // the body — see the nested scale() below — never the whole canvas, which
    // is the "never move the whole SVG" rule the V3 proposal called out.
    translate(left = frame.tremorX * scaleFactor, top = frame.postureY * scaleFactor) {
        rotate(degrees = frame.tiltDeg, pivot = Offset(vx(12f), vy(FootY))) {
            val footPivot = Offset(vx(12f), vy(FootY))
            val squashScaleY = frame.squashY * popScale
            val squashScaleX = (1f / sqrt(frame.squashY)) * popScale // preserves volume
            scale(scaleX = squashScaleX, scaleY = squashScaleY, pivot = footPivot) {
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
                drawPath(path, color = frame.tint)
                drawFlatFaceShading(path, frame.tint, vx, vy)

                drawContinuousEye(EyeLeft, vx, vy, frame)
                drawContinuousEye(EyeRight, vx, vy, frame)
            }
        }
    }

    for (particle in particles) {
        val lifeFrac = (particle.life / particle.maxLife).coerceIn(0f, 1f)
        val color = if (particle.kind == ParticleKind.SWEAT) StickColors.Blue else StickColors.Warn
        val alpha = if (particle.kind == ParticleKind.SWEAT) (1f - lifeFrac) else kotlin.math.sin(Math.PI.toFloat() * lifeFrac)
        val s = particle.size * scaleFactor
        drawRoundRect(
            color = color.copy(alpha = alpha.coerceIn(0f, 1f) * 0.9f),
            topLeft = Offset(vx(particle.x), vy(particle.y)),
            size = Size(s, s),
            cornerRadius = CornerRadius(s * 0.3f),
        )
    }
}

/** Pupil position/size and eyelid coverage read continuously off [MascotFrame] instead of a discrete [EyeStyle]-style switch. */
private fun DrawScope.drawContinuousEye(
    socket: FloatArray,
    vx: (Float) -> Float,
    vy: (Float) -> Float,
    frame: MascotFrame,
) {
    val left = vx(socket[0])
    val top = vy(socket[1])
    val right = vx(socket[2])
    val bottom = vy(socket[3])
    val w = right - left
    val h = bottom - top
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f

    val pupilW = w * 0.64f * frame.pupilScale
    val pupilH = h * 0.76f * frame.pupilScale
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.8f),
        topLeft = Offset(cx - pupilW / 2f + frame.lookX * w, cy - pupilH / 2f + frame.lookY * h),
        size = Size(pupilW, pupilH),
        cornerRadius = CornerRadius(pupilW * 0.18f),
    )
    if (frame.eyelidFrac > 0.001f) {
        drawRoundRect(
            color = frame.tint,
            topLeft = Offset(left, top),
            size = Size(w, h * frame.eyelidFrac),
            cornerRadius = CornerRadius(w * 0.18f),
        )
    }
}

/**
 * Shadow ellipse, pinned to the ground line (not the body's own droop/jump
 * offset). Shrinks and fades as [MascotFrame.jumpHeight] approaches
 * [MascotJumpAmplitude] — "sombra encolhe quando pula, alarga quando pousa".
 */
private fun DrawScope.drawShadow(
    frame: MascotFrame,
    vx: (Float) -> Float,
    vy: (Float) -> Float,
    scaleFactor: Float,
) {
    val air = (frame.jumpHeight / MascotJumpAmplitude).coerceIn(0f, 1f)
    val rx = 8.4f * (1f - 0.42f * air) * scaleFactor
    val ry = 1.0f * (1f - 0.35f * air) * scaleFactor
    val cx = vx(12f + frame.tremorX * 0.6f)
    val cy = vy(FootY + 1.3f)
    drawOval(
        color = Color.Black.copy(alpha = 0.32f * (1f - 0.5f * air)),
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2f, ry * 2f),
    )
}

/** Fixed-size shadow for the static-mood branch — no jump/air to react to there. */
private fun DrawScope.drawStaticShadow(
    vx: (Float) -> Float,
    vy: (Float) -> Float,
    scaleFactor: Float,
) {
    val rx = 8.4f * scaleFactor
    val ry = 1.0f * scaleFactor
    drawOval(
        color = Color.Black.copy(alpha = 0.28f),
        topLeft = Offset(vx(12f) - rx, vy(FootY + 1.3f) - ry),
        size = Size(rx * 2f, ry * 2f),
    )
}

/**
 * Flat, solid-tone shading — a thin lighter strip along the top edge and a
 * thin darker strip along one side, both plain colors with no gradient/blend
 * in between. Reads as "this has volume" the way a Minecraft/low-poly block
 * does, without a specular highlight that would wash out the brand coral —
 * the front face (almost the whole silhouette) stays exactly [tint], untouched.
 */
private fun DrawScope.drawFlatFaceShading(
    bodyPath: Path,
    tint: Color,
    vx: (Float) -> Float,
    vy: (Float) -> Float,
) {
    val topTint = lerp(tint, Color.White, 0.16f)
    val sideTint = lerp(tint, Color.Black, 0.14f)
    clipPath(bodyPath) {
        drawRect(
            color = topTint,
            topLeft = Offset(vx(0f), vy(5f)),
            size = Size(vx(24f) - vx(0f), vy(6.2f) - vy(5f)),
        )
        drawRect(
            color = sideTint,
            topLeft = Offset(vx(19f), vy(5f)),
            size = Size(vx(24f) - vx(19f), vy(17.079f) - vy(5f)),
        )
    }
}

/** Error/Unavailable/NeverProbed — no trustworthy usage number, so this bypasses [MascotBehaviorState] entirely. */
private fun DrawScope.drawStaticMood(
    mood: MascotMood,
    vx: (Float) -> Float,
    vy: (Float) -> Float,
    scaleFactor: Float,
) {
    drawStaticShadow(vx, vy, scaleFactor)

    val bodyColor = if (mood == MascotMood.Error) StickColors.MascotKo else StickColors.Accent
    val bodyAlpha = if (mood == MascotMood.NeverProbed) 140f / 255f else 1f
    val slumpViewBoxUnits = if (mood == MascotMood.Error) 1.5f else 0f

    translate(top = slumpViewBoxUnits * scaleFactor) {
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
        drawPath(path, color = bodyColor, alpha = bodyAlpha)
        drawFlatFaceShading(path, bodyColor.copy(alpha = bodyAlpha), vx, vy)

        if (mood == MascotMood.Error) {
            drawDeadEye(EyeLeft, vx, vy)
            drawDeadEye(EyeRight, vx, vy)
        } else {
            drawSleepyEye(EyeLeft, vx, vy, bodyColor, bodyAlpha)
            drawSleepyEye(EyeRight, vx, vy, bodyColor, bodyAlpha)
        }
    }
}

private fun DrawScope.drawDeadEye(socket: FloatArray, vx: (Float) -> Float, vy: (Float) -> Float) {
    val left = vx(socket[0])
    val top = vy(socket[1])
    val right = vx(socket[2])
    val bottom = vy(socket[3])
    val strokeWidth = ((right - left) * 0.28f).coerceAtLeast(1f)
    drawLine(StickColors.Bad, Offset(left, top), Offset(right, bottom), strokeWidth, StrokeCap.Round)
    drawLine(StickColors.Bad, Offset(right, top), Offset(left, bottom), strokeWidth, StrokeCap.Round)
}

private fun DrawScope.drawSleepyEye(
    socket: FloatArray,
    vx: (Float) -> Float,
    vy: (Float) -> Float,
    tint: Color,
    alpha: Float,
) {
    val left = vx(socket[0])
    val top = vy(socket[1])
    val right = vx(socket[2])
    val bottom = vy(socket[3])
    val w = right - left
    val h = bottom - top
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.75f * alpha),
        topLeft = Offset(left + w * 0.15f, top + h * 0.25f),
        size = Size(w * 0.7f, h * 0.5f),
        cornerRadius = CornerRadius(w * 0.18f),
    )
    drawRoundRect(
        color = tint.copy(alpha = alpha),
        topLeft = Offset(left, top),
        size = Size(w, h * 0.4f),
        cornerRadius = CornerRadius(w * 0.15f),
    )
}
