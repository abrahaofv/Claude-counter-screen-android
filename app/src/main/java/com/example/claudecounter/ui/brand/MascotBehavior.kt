package com.example.claudecounter.ui.brand

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.claudecounter.ui.theme.StickColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pure, framework-agnostic behavior engine for Clawd V3 — ported near 1:1 from
 * `clawd-v3-preview.html` (traits/spring/scheduler math, distribution-checked
 * against 600 simulated seconds before this port). No Canvas or Compose
 * `State` dependency: [Mascot] ticks a [MascotBehaviorState] every frame and
 * only reads [MascotBehaviorState.frame] to draw.
 */

private val TwoPi = (2.0 * PI).toFloat()

/** Peak height (viewBox units) of the JUMP action — small on purpose: Clawd
 * renders as small as 42dp, and this sets how much vertical headroom
 * [Mascot] needs to reserve above the body so a jump never clips. */
const val MascotJumpAmplitude = 1.8f

// ---------------------------------------------------------------------------
// Spring — second-order damped spring: acceleration, deceleration, overshoot,
// never an instant stop. Sub-stepped so a slow frame can't blow it up.
// ---------------------------------------------------------------------------

private class Spring(initial: Float, private val stiffness: Float, private val damping: Float) {
    var v: Float = initial
        private set
    var target: Float = initial
    private var vel = 0f

    fun step(dt: Float) {
        val steps = kotlin.math.ceil(dt / 0.016f).toInt().coerceAtLeast(1)
        val h = dt / steps
        repeat(steps) {
            vel += (stiffness * (target - v) - damping * vel) * h
            v += vel * h
        }
    }
}

// ---------------------------------------------------------------------------
// Actions Clawd can pick between one at a time (plus blinking, a separate
// reflex channel). Durations mirror the ranges verified in the HTML lab.
// ---------------------------------------------------------------------------

enum class MascotAction(val minDur: Float, val maxDur: Float) {
    LOOK(1.0f, 2.0f),
    LOOK_UP(0.9f, 1.5f),
    TILT(1.0f, 1.8f),
    JUMP(0.78f, 0.78f),
    STRETCH(1.7f, 1.7f),
    YAWN(1.5f, 1.5f),
    WIPE(0.95f, 0.95f),
    SURPRISE(0.8f, 0.8f),
    SETTLE(0.9f, 1.4f),
}

// ---------------------------------------------------------------------------
// Traits — continuous function of vitality (0..100), replacing the old
// motionFor()/bodyColorFor()/eyeStyleFor() discrete-per-stage switches.
// ---------------------------------------------------------------------------

data class MascotTraits(
    val breathPeriod: Float,
    val breathDepth: Float,
    val postureDrop: Float,
    val tremorAmp: Float,
    val eyeOpen: Float,
    val pupilScale: Float,
    val glow: Float,
    val sweatRate: Float,
    val sparkRate: Float,
    val actionLag: Float,
    val blinkMin: Float,
    val blinkMax: Float,
    val tint: Color,
    val weights: Map<MascotAction, Float>,
)

private data class Anchor(
    val vitality: Float,
    val breathPeriod: Float,
    val breathDepth: Float,
    val postureDrop: Float,
    val tremorAmp: Float,
    val eyeOpen: Float,
    val pupilScale: Float,
    val tint: Color,
    val glow: Float,
    val sweatRate: Float,
    val sparkRate: Float,
    val blinkMin: Float,
    val blinkMax: Float,
    val actionLag: Float,
    val weights: Map<MascotAction, Float>,
)

/** Same tint fractions [bodyColorFor] used to use — coral blended toward the
 * usage gradient, never a full recolor (KO is handled by [GraveScene], not here). */
private val TintRested = StickColors.Accent
private val TintStraining = lerp(StickColors.Accent, StickColors.Warn, 0.15f)
private val TintExhausted = lerp(StickColors.Accent, StickColors.Warn, 0.25f)
private val TintCritical = lerp(StickColors.Accent, StickColors.Bad, 0.35f)

/**
 * Six anchors at vitality 100/75/50/30/10/0 — exactly [stageFor]'s cuts
 * (usage 0/25/50/70/90/100) so each app stage owns a whole interpolation span
 * instead of two personalities splitting one band.
 */
private val ANCHORS = listOf(
    Anchor(
        vitality = 100f, breathPeriod = 3.4f, breathDepth = 0.016f, postureDrop = 0f, tremorAmp = 0f,
        eyeOpen = 1f, pupilScale = 1f, tint = TintRested, glow = 0.42f, sweatRate = 0f, sparkRate = 0.55f,
        blinkMin = 2.2f, blinkMax = 6.5f, actionLag = 1.0f,
        weights = mapOf(
            MascotAction.LOOK to 1.0f, MascotAction.LOOK_UP to 0.5f, MascotAction.TILT to 0.5f,
            MascotAction.JUMP to 1.0f, MascotAction.STRETCH to 0.18f, MascotAction.YAWN to 0.04f,
            MascotAction.WIPE to 0f, MascotAction.SURPRISE to 0.2f, MascotAction.SETTLE to 0.7f,
        ),
    ),
    Anchor(
        vitality = 75f, breathPeriod = 2.6f, breathDepth = 0.013f, postureDrop = 0.15f, tremorAmp = 0f,
        eyeOpen = 0.96f, pupilScale = 0.96f, tint = TintRested, glow = 0.16f, sweatRate = 0f, sparkRate = 0.10f,
        blinkMin = 1.8f, blinkMax = 5.0f, actionLag = 1.0f,
        weights = mapOf(
            MascotAction.LOOK to 1.4f, MascotAction.LOOK_UP to 0.2f, MascotAction.TILT to 0.6f,
            MascotAction.JUMP to 0.22f, MascotAction.STRETCH to 0.12f, MascotAction.YAWN to 0.05f,
            MascotAction.WIPE to 0f, MascotAction.SURPRISE to 0.15f, MascotAction.SETTLE to 1.1f,
        ),
    ),
    Anchor(
        vitality = 50f, breathPeriod = 1.9f, breathDepth = 0.019f, postureDrop = 0.42f, tremorAmp = 0.035f,
        eyeOpen = 0.83f, pupilScale = 0.88f, tint = TintStraining, glow = 0.05f, sweatRate = 0.38f, sparkRate = 0f,
        blinkMin = 1.2f, blinkMax = 3.4f, actionLag = 1.15f,
        weights = mapOf(
            MascotAction.LOOK to 1.1f, MascotAction.LOOK_UP to 0.15f, MascotAction.TILT to 0.5f,
            MascotAction.JUMP to 0.06f, MascotAction.STRETCH to 0.10f, MascotAction.YAWN to 0.16f,
            MascotAction.WIPE to 0.60f, MascotAction.SURPRISE to 0.10f, MascotAction.SETTLE to 1.0f,
        ),
    ),
    Anchor(
        vitality = 30f, breathPeriod = 1.5f, breathDepth = 0.030f, postureDrop = 1.05f, tremorAmp = 0.10f,
        eyeOpen = 0.55f, pupilScale = 0.76f, tint = TintExhausted, glow = 0f, sweatRate = 0.72f, sparkRate = 0f,
        blinkMin = 0.9f, blinkMax = 2.6f, actionLag = 1.6f,
        weights = mapOf(
            MascotAction.LOOK to 0.55f, MascotAction.LOOK_UP to 0.05f, MascotAction.TILT to 0.35f,
            MascotAction.JUMP to 0f, MascotAction.STRETCH to 0.22f, MascotAction.YAWN to 0.90f,
            MascotAction.WIPE to 0.50f, MascotAction.SURPRISE to 0.05f, MascotAction.SETTLE to 0.8f,
        ),
    ),
    Anchor(
        vitality = 10f, breathPeriod = 1.25f, breathDepth = 0.038f, postureDrop = 1.75f, tremorAmp = 0.27f,
        eyeOpen = 0.37f, pupilScale = 0.62f, tint = TintCritical, glow = 0f, sweatRate = 1.10f, sparkRate = 0f,
        blinkMin = 0.7f, blinkMax = 2.0f, actionLag = 2.3f,
        weights = mapOf(
            MascotAction.LOOK to 0.25f, MascotAction.LOOK_UP to 0.02f, MascotAction.TILT to 0.20f,
            MascotAction.JUMP to 0f, MascotAction.STRETCH to 0.04f, MascotAction.YAWN to 0.55f,
            MascotAction.WIPE to 0.22f, MascotAction.SURPRISE to 0.02f, MascotAction.SETTLE to 0.6f,
        ),
    ),
    Anchor(
        vitality = 0f, breathPeriod = 1.10f, breathDepth = 0.042f, postureDrop = 2.20f, tremorAmp = 0.34f,
        eyeOpen = 0.28f, pupilScale = 0.55f, tint = TintCritical, glow = 0f, sweatRate = 1.30f, sparkRate = 0f,
        blinkMin = 0.6f, blinkMax = 1.7f, actionLag = 2.8f,
        weights = mapOf(
            MascotAction.LOOK to 0.15f, MascotAction.LOOK_UP to 0.01f, MascotAction.TILT to 0.15f,
            MascotAction.JUMP to 0f, MascotAction.STRETCH to 0.02f, MascotAction.YAWN to 0.40f,
            MascotAction.WIPE to 0.15f, MascotAction.SURPRISE to 0.01f, MascotAction.SETTLE to 0.5f,
        ),
    ),
)

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

fun traitsFor(vitalityRaw: Float): MascotTraits {
    val vitality = vitalityRaw.coerceIn(0f, 100f)
    var hi = ANCHORS.first()
    var lo = ANCHORS.last()
    var t = 0f
    for (i in 0 until ANCHORS.size - 1) {
        val a = ANCHORS[i]
        val b = ANCHORS[i + 1]
        if (vitality <= a.vitality && vitality >= b.vitality) {
            hi = a; lo = b
            t = (a.vitality - vitality) / (a.vitality - b.vitality)
            break
        }
    }
    val weights = MascotAction.entries.associateWith { action ->
        lerpF(hi.weights.getValue(action), lo.weights.getValue(action), t)
    }
    return MascotTraits(
        breathPeriod = lerpF(hi.breathPeriod, lo.breathPeriod, t),
        breathDepth = lerpF(hi.breathDepth, lo.breathDepth, t),
        postureDrop = lerpF(hi.postureDrop, lo.postureDrop, t),
        tremorAmp = lerpF(hi.tremorAmp, lo.tremorAmp, t),
        eyeOpen = lerpF(hi.eyeOpen, lo.eyeOpen, t),
        pupilScale = lerpF(hi.pupilScale, lo.pupilScale, t),
        glow = lerpF(hi.glow, lo.glow, t),
        sweatRate = lerpF(hi.sweatRate, lo.sweatRate, t),
        sparkRate = lerpF(hi.sparkRate, lo.sparkRate, t),
        actionLag = lerpF(hi.actionLag, lo.actionLag, t),
        blinkMin = lerpF(hi.blinkMin, lo.blinkMin, t),
        blinkMax = lerpF(hi.blinkMax, lo.blinkMax, t),
        tint = lerp(hi.tint, lo.tint, t),
        weights = weights,
    )
}

private fun easeInOut(t: Float): Float =
    if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).let { it * it } / 2f

// ---------------------------------------------------------------------------
// Particles — fixed-size pool, no per-frame allocation.
// ---------------------------------------------------------------------------

enum class ParticleKind { SWEAT, SPARK }

class MascotParticle {
    var alive: Boolean = false
    var kind: ParticleKind = ParticleKind.SWEAT
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var life: Float = 0f
    var maxLife: Float = 1f
    var size: Float = 0.6f
}

private const val ParticlePoolSize = 6

// ---------------------------------------------------------------------------
// Frame — everything the Canvas needs to draw one frame, resolved once per
// step(). Mirrors the shape of the old MascotVisuals, just continuous.
// ---------------------------------------------------------------------------

data class MascotFrame(
    val tint: Color,
    val breathScale: Float,
    val postureY: Float,
    val tremorX: Float,
    val tiltDeg: Float,
    val lookX: Float,
    val lookY: Float,
    val eyelidFrac: Float,
    val pupilScale: Float,
    val jumpHeight: Float,
    val squashY: Float,
    val glow: Float,
    val armLDeg: Float,
    val armRDeg: Float,
    val armReach: Float,
    val mouthHeight: Float,
)

private val InitialFrame = MascotFrame(
    tint = StickColors.Accent, breathScale = 1f, postureY = 0f, tremorX = 0f, tiltDeg = 0f,
    lookX = 0f, lookY = 0f, eyelidFrac = 0f, pupilScale = 1f, jumpHeight = 0f, squashY = 1f,
    glow = 0.42f, armLDeg = 0f, armRDeg = 0f, armReach = 0f, mouthHeight = 0f,
)

// ---------------------------------------------------------------------------
// Behavior state — one instance per on-screen Mascot. Own it with `remember`
// and call [step] once per animation frame; read [frame] to draw.
// ---------------------------------------------------------------------------

class MascotBehaviorState(private val random: Random = Random.Default) {
    private val vitality = Spring(100f, 42f, 13f)
    private var t = 0f
    private var breathPhase = random.nextFloat() * TwoPi

    private val posture = Spring(0f, 90f, 17f)
    private val lookXSpring = Spring(0f, 150f, 20f)
    private val lookYSpring = Spring(0f, 150f, 20f)
    private val tiltSpring = Spring(0f, 70f, 13f)
    private val armRSpring = Spring(0f, 130f, 17f)
    private val armLSpring = Spring(0f, 130f, 17f)
    private val reachSpring = Spring(0f, 130f, 18f)
    private val mouthSpring = Spring(0f, 160f, 22f)
    private val pupilBoost = Spring(1f, 120f, 18f)

    private var action: MascotAction? = null
    private var actionT = 0f
    private var actionDur = 0f
    private var actionDir = 1f
    private var nextActionAt = random.nextFloat() * 1.6f + 0.4f

    private var blinkAt = random.nextFloat() * 1.9f + 0.6f
    private var blinkT = -1f
    private var blinkDur = 0.14f

    private var jumpH = 0f
    private var squashY = 1f

    private var sweatAcc = 0f
    private var sparkAcc = 0f
    private val particles = Array(ParticlePoolSize) { MascotParticle() }

    /** Result of the last [step] — the only thing [Mascot] needs to draw a frame. */
    var frame: MascotFrame = InitialFrame
        private set

    /** Live particles, for the caller to draw — filtered fresh each call, pool stays fixed-size. */
    fun particlesSnapshot(): List<MascotParticle> = particles.filter { it.alive }

    fun setVitalityTarget(v: Float) {
        vitality.target = v
    }

    /**
     * Cuts in line ahead of the scheduler and plays [action] right now — for the
     * mascot gallery, so a tap can demo one microexpression on demand instead of
     * waiting for the weighted queue to sort it. Pushes [nextActionAt] out so the
     * scheduler doesn't immediately compete with (and cut off) what was just
     * forced, mirroring `Clawd.prototype.force()` in the V3 HTML lab.
     */
    fun forceAction(action: MascotAction) {
        startAction(action)
        nextActionAt = t + actionDur + (random.nextFloat() * 1.3f + 0.9f)
    }

    /** Blinking is a reflex channel, not a [MascotAction] — forced separately. */
    fun forceBlink() {
        blinkT = 0f
        blinkDur = random.nextFloat() * 0.07f + 0.10f
    }

    fun step(dt: Float) {
        t += dt
        vitality.step(dt)
        val traits = traitsFor(vitality.v)

        breathPhase += (dt / traits.breathPeriod) * TwoPi
        val breath = sin(breathPhase)

        // Reflex channel: blinking, independent of the action queue, suppressed mid-yawn/stretch.
        if (blinkT >= 0f) {
            blinkT += dt
            if (blinkT > blinkDur) blinkT = -1f
        } else if (t >= blinkAt) {
            if (action != MascotAction.YAWN && action != MascotAction.STRETCH) {
                blinkT = 0f
                blinkDur = random.nextFloat() * 0.07f + 0.10f
            }
            blinkAt = t + random.nextFloat() * (traits.blinkMax - traits.blinkMin) + traits.blinkMin
        }

        // Exclusive action queue — one behavior at a time, next one sorted with a lag scaled by strain.
        if (action != null) {
            actionT += dt
            if (actionT >= actionDur) {
                action = null
                nextActionAt = t + (random.nextFloat() * 2.5f + 1.1f) * traits.actionLag
            }
        } else if (t >= nextActionAt) {
            val picked = pickAction(traits)
            if (picked != null) {
                startAction(picked)
            } else {
                nextActionAt = t + (random.nextFloat() * 1.5f + 1.0f) * traits.actionLag
            }
        }

        // Default targets, overridden per-action below.
        posture.target = traits.postureDrop
        lookXSpring.target = 0f
        lookYSpring.target = 0f
        tiltSpring.target = 0f
        armRSpring.target = 0f
        armLSpring.target = 0f
        mouthSpring.target = 0f
        reachSpring.target = 0f
        pupilBoost.target = 1f
        var stretchScale = 1f
        var squashCmd = 1f
        var jumpTarget = 0f

        val p = if (action != null) (actionT / actionDur).coerceIn(0f, 1f) else 0f
        when (action) {
            MascotAction.LOOK -> {
                lookXSpring.target = if (p < 0.78f) actionDir * 0.30f else 0f
                tiltSpring.target = if (p < 0.78f) actionDir * 0.9f else 0f
            }
            MascotAction.LOOK_UP -> {
                lookYSpring.target = if (p < 0.72f) -0.34f else 0f
                posture.target = traits.postureDrop - if (p < 0.72f) 0.18f else 0f
            }
            MascotAction.TILT -> {
                tiltSpring.target = if (p < 0.72f) actionDir * 3.4f else 0f
            }
            MascotAction.JUMP -> {
                when {
                    p < 0.13f -> squashCmd = 1f - 0.11f * (p / 0.13f)
                    p < 0.86f -> {
                        val q = (p - 0.13f) / 0.73f
                        jumpTarget = sin(PI.toFloat() * q) * MascotJumpAmplitude
                        squashCmd = 1f + 0.15f * cos(PI.toFloat() * q)
                    }
                    else -> {
                        val s = (p - 0.86f) / 0.14f
                        squashCmd = 1f - 0.18f * sin(PI.toFloat() * s)
                    }
                }
            }
            MascotAction.STRETCH -> {
                val e = easeInOut(p)
                stretchScale = 1f + 0.14f * sin(PI.toFloat() * e)
                posture.target = traits.postureDrop - 0.5f * sin(PI.toFloat() * e)
                armRSpring.target = -68f * sin(PI.toFloat() * e)
                armLSpring.target = 68f * sin(PI.toFloat() * e)
            }
            MascotAction.YAWN -> {
                val e = easeInOut(p)
                stretchScale = 1f + 0.06f * sin(PI.toFloat() * e)
                mouthSpring.target = 2.6f * sin(PI.toFloat() * e)
            }
            MascotAction.WIPE -> {
                val e = easeInOut(p)
                val wp = sin(PI.toFloat() * e)
                armRSpring.target = -118f * wp
                reachSpring.target = wp
                if (p in 0.42f..0.52f) clearSweat()
            }
            MascotAction.SURPRISE -> {
                pupilBoost.target = if (p < 0.6f) 1.35f else 1f
                posture.target = traits.postureDrop + if (p < 0.25f) 0.5f else 0f
            }
            MascotAction.SETTLE -> {
                posture.target = traits.postureDrop + if (p < 0.6f) actionDir * 0.16f else 0f
                tiltSpring.target = if (p < 0.6f) actionDir * 1.1f else 0f
            }
            null -> Unit
        }

        posture.step(dt); lookXSpring.step(dt); lookYSpring.step(dt)
        tiltSpring.step(dt); armRSpring.step(dt); armLSpring.step(dt)
        mouthSpring.step(dt); pupilBoost.step(dt); reachSpring.step(dt)

        jumpH += (jumpTarget - jumpH) * (dt * 26f).coerceAtMost(1f)
        squashY += (squashCmd * stretchScale - squashY) * (dt * 24f).coerceAtMost(1f)

        val tremor = traits.tremorAmp * (sin(t * 37.1f) * 0.6f + sin(t * 23.7f) * 0.4f)

        sweatAcc += dt * traits.sweatRate
        while (sweatAcc >= 1f) { sweatAcc -= 1f; spawn(ParticleKind.SWEAT) }
        sparkAcc += dt * traits.sparkRate
        while (sparkAcc >= 1f) { sparkAcc -= 1f; spawn(ParticleKind.SPARK) }
        stepParticles(dt)

        val breathScale = 1f + traits.breathDepth * breath
        val eyelidBase = 1f - traits.eyeOpen
        val yawnClose = if (action == MascotAction.YAWN) sin(PI.toFloat() * easeInOut(p)) else 0f
        val stretchClose = if (action == MascotAction.STRETCH) sin(PI.toFloat() * easeInOut(p)) * 0.8f else 0f
        val blink = if (blinkT >= 0f) sin(PI.toFloat() * (blinkT / blinkDur)) else 0f
        val eyelidFrac = maxOf(eyelidBase, blink, yawnClose, stretchClose).coerceIn(0f, 1f)

        frame = MascotFrame(
            tint = traits.tint,
            breathScale = breathScale,
            postureY = posture.v - jumpH,
            tremorX = tremor,
            tiltDeg = tiltSpring.v,
            lookX = lookXSpring.v,
            lookY = lookYSpring.v,
            eyelidFrac = eyelidFrac,
            pupilScale = traits.pupilScale * pupilBoost.v,
            jumpHeight = jumpH,
            squashY = squashY * breathScale,
            glow = traits.glow * (0.72f + 0.28f * breath),
            armLDeg = armLSpring.v,
            armRDeg = armRSpring.v,
            armReach = reachSpring.v,
            mouthHeight = mouthSpring.v.coerceAtLeast(0f),
        )
    }

    private fun startAction(a: MascotAction) {
        action = a
        actionT = 0f
        actionDur = random.nextFloat() * (a.maxDur - a.minDur) + a.minDur
        actionDir = if (random.nextBoolean()) -1f else 1f
    }

    private fun pickAction(traits: MascotTraits): MascotAction? {
        val total = traits.weights.values.sum()
        if (total <= 0f) return null
        var r = random.nextFloat() * total
        for (candidate in MascotAction.entries) {
            r -= traits.weights.getValue(candidate)
            if (r <= 0f) return candidate
        }
        return MascotAction.SETTLE
    }

    /** Positions in the same 24-unit viewBox space as [OuterContour]/[EyeRight]. */
    private fun spawn(kind: ParticleKind) {
        val slot = particles.firstOrNull { !it.alive } ?: return
        slot.alive = true
        slot.kind = kind
        slot.life = 0f
        when (kind) {
            ParticleKind.SWEAT -> {
                slot.x = 16.51f + 1.49f * (1.1f + random.nextFloat() * 0.9f)
                slot.y = 8.102f + random.nextFloat() * 1.8f - 0.6f
                slot.vx = random.nextFloat() * 0.7f + 0.2f
                slot.vy = random.nextFloat() * 1.5f + 1.5f
                slot.maxLife = random.nextFloat() * 0.4f + 0.7f
                slot.size = random.nextFloat() * 0.3f + 0.55f
            }
            ParticleKind.SPARK -> {
                slot.x = random.nextFloat() * 22f + 1f
                slot.y = random.nextFloat() * 10f + 4f
                slot.vx = random.nextFloat() - 0.5f
                slot.vy = -(random.nextFloat() * 1.1f + 1.1f)
                slot.maxLife = random.nextFloat() * 0.7f + 0.9f
                slot.size = random.nextFloat() * 0.35f + 0.45f
            }
        }
    }

    private fun clearSweat() {
        for (p in particles) if (p.alive && p.kind == ParticleKind.SWEAT) p.alive = false
        sweatAcc = 0f
    }

    private fun stepParticles(dt: Float) {
        for (p in particles) {
            if (!p.alive) continue
            p.life += dt
            if (p.life >= p.maxLife) {
                p.alive = false
                continue
            }
            when (p.kind) {
                ParticleKind.SWEAT -> {
                    p.vy += dt * 9f
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                }
                ParticleKind.SPARK -> {
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                }
            }
        }
    }
}
