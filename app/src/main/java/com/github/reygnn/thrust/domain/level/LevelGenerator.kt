package com.github.reygnn.thrust.domain.level

import com.github.reygnn.thrust.domain.model.LandingPad
import com.github.reygnn.thrust.domain.model.LevelConfig
import com.github.reygnn.thrust.domain.model.TerrainSegment
import com.github.reygnn.thrust.domain.model.TurretConfig
import com.github.reygnn.thrust.domain.model.Vector2
import kotlin.random.Random

/**
 * Erzeugt Endless-Mode-Level prozedural und deterministisch.
 *
 * Die Generierung ist eine reine Funktion: gleicher (difficulty, seed) ergibt
 * exakt denselben [LevelConfig]. Damit lassen sich Levels reproduzieren
 * (Tests, "gleiches Level erneut spielen") ohne den fertigen `LevelConfig`
 * speichern zu müssen.
 *
 * Layout-Schema:
 *   ┌──────────────────────────────────────────────────────────┐
 *   │ ──────────────── (wavy ceiling) ───────────────────────  │
 *   │                                                          │
 *   │  ship                                                    │
 *   │              ⊓     ⊓     ⊓                               │
 *   │  pod                                                     │
 *   │              ⊔     ⊔     ⊔                               │
 *   │ ──────── (wavy floor) ─────  [pad]  ───────────────────  │
 *   └──────────────────────────────────────────────────────────┘
 */
object LevelGenerator {

    /** Sentinel-ID für Endless-Levels. Story-Mode beginnt bei 1, daher 0 sicher. */
    const val ENDLESS_LEVEL_ID: Int = 0

    /**
     * Maximale Versuchszahl für die Erreichbarkeitsschleife. Bei einer
     * pessimistisch geschätzten Trefferquote von ~50% pro Versuch ist die
     * Wahrscheinlichkeit, dass alle 30 Versuche fehlschlagen, < 10⁻⁹.
     */
    private const val MAX_REACH_ATTEMPTS = 30

    /**
     * Erzeugt einen Endless-Level mit Erreichbarkeits-Garantie:
     *
     * - **Pure Chaos**: der Pod ist vom Schiff aus geometrisch erreichbar.
     *   Pad-Erreichbarkeit ist absichtlich nicht garantiert — Disclaimer.
     * - **Alle anderen Difficulties**: Pod **und** Pad-Anflug erreichbar
     *   (volle Spielbarkeit).
     *
     * Wird die Erfüllung im ersten Anlauf nicht erreicht, wird der Seed
     * permutiert und neu generiert — bis zu [MAX_REACH_ATTEMPTS] Versuche.
     * Die Funktion bleibt deterministisch in (difficulty, seed).
     */
    fun generate(difficulty: Difficulty, seed: Long): LevelConfig {
        var s = seed
        var lastCfg = generateOnce(difficulty, s)
        if (passes(difficulty, lastCfg)) return lastCfg
        repeat(MAX_REACH_ATTEMPTS - 1) {
            // Einfache LCG-Permutation; bleibt deterministisch zu (difficulty, seed).
            s = s * 0x5DEECE66DL + 0xBL
            lastCfg = generateOnce(difficulty, s)
            if (passes(difficulty, lastCfg)) return lastCfg
        }
        return lastCfg
    }

    private fun passes(difficulty: Difficulty, cfg: LevelConfig): Boolean {
        if (!LevelPlayability.isPodReachableFromShip(cfg)) return false
        // Pad-Erreichbarkeit ist nur außerhalb von Pure Chaos verpflichtend.
        if (difficulty != Difficulty.PURE_CHAOS && !LevelPlayability.isPadApproachReachableFromShip(cfg)) return false
        return true
    }

    private fun generateOnce(difficulty: Difficulty, seed: Long): LevelConfig {
        val rng = Random(seed)
        val w = difficulty.worldWidth
        val h = difficulty.worldHeight

        val ceilingY = h * 0.13f
        val floorY   = h * 0.87f

        val shipStart = Vector2(x = w * 0.10f, y = ceilingY + h * 0.18f)

        val padCenterX   = w * (0.74f + rng.nextFloat() * 0.14f)
        val padHalfWidth = difficulty.padHalfWidth
        val pad          = LandingPad(Vector2(padCenterX, floorY), padHalfWidth)

        // Pod-x bleibt in allen Stufen außer Pure Chaos links von der Barrieren-Zone
        // (xMin = shipStart.x + 500). Mit max-Offset 400 bleibt ein Sicherheitspuffer
        // von ~100 Einheiten — ausreichend, damit der Pod nicht in einer Barriere
        // eingeschlossen sein kann. Pure Chaos darf chaotisch bleiben (s. Disclaimer).
        val podMaxOffset =
            if (difficulty == Difficulty.PURE_CHAOS) w * 0.18f
            else minOf(w * 0.18f, 400f)
        val pod = Vector2(
            x = w * 0.10f + rng.nextFloat() * podMaxOffset,
            y = floorY - h * (0.16f + rng.nextFloat() * 0.18f),
        )

        val terrain = buildList {
            addAll(outerWalls(w, h))
            addAll(wavyLine(rng, x0 = 0f, x1 = w, baseY = ceilingY, amplitude = h * 0.04f, segmentCount = ((w / 500f).toInt().coerceAtLeast(6))))
            addAll(wavyFloor(rng, w = w, baseY = floorY, amplitude = h * 0.03f, padCenterX = padCenterX, padHalfWidth = padHalfWidth))
            addAll(generateBarriers(
                rng        = rng,
                count      = difficulty.barrierCount.random(rng),
                xMin       = shipStart.x + 500f,
                xMax       = padCenterX - padHalfWidth - 250f,
                ceilingY   = ceilingY,
                floorY     = floorY,
            ))
        }

        val turrets = generateTurrets(
            rng        = rng,
            count      = difficulty.turretCount.random(rng),
            difficulty = difficulty,
            xMin       = shipStart.x + 700f,
            xMax       = w - 250f,
            yMin       = ceilingY + 200f,
            yMax       = floorY - 200f,
        )

        return LevelConfig(
            id              = ENDLESS_LEVEL_ID,
            name            = difficulty.displayName,
            worldWidth      = w,
            worldHeight     = h,
            gravity         = difficulty.gravity,
            shipStart       = shipStart,
            fuelPodPosition = pod,
            landingPad      = pad,
            terrain         = terrain,
            turrets         = turrets,
        )
    }

    private fun outerWalls(w: Float, h: Float): List<TerrainSegment> = listOf(
        seg(0f, 0f, w, 0f),
        seg(0f, 0f, 0f, h),
        seg(0f, h, w, h),
        seg(w, 0f, w, h),
    )

    private fun wavyLine(
        rng: Random,
        x0: Float, x1: Float,
        baseY: Float,
        amplitude: Float,
        segmentCount: Int,
    ): List<TerrainSegment> {
        if (x1 - x0 < 50f || segmentCount < 1) return emptyList()
        val xs = (0..segmentCount).map { x0 + (x1 - x0) * it / segmentCount }
        // Endpunkte exakt auf baseY, damit angrenzende Segmente sauber an Wänden/Pad-Lücken stehen.
        val ys = xs.indices.map { i ->
            if (i == 0 || i == xs.lastIndex) baseY
            else baseY + (rng.nextFloat() - 0.5f) * 2f * amplitude
        }
        return xs.zipWithNext().mapIndexed { i, (a, b) ->
            TerrainSegment(Vector2(a, ys[i]), Vector2(b, ys[i + 1]))
        }
    }

    private fun wavyFloor(
        rng: Random,
        w: Float,
        baseY: Float,
        amplitude: Float,
        padCenterX: Float,
        padHalfWidth: Float,
    ): List<TerrainSegment> {
        val gapLeft  = padCenterX - padHalfWidth
        val gapRight = padCenterX + padHalfWidth
        val left  = wavyLine(rng, 0f, gapLeft, baseY, amplitude, ((gapLeft / 500f).toInt().coerceAtLeast(2)))
        val right = wavyLine(rng, gapRight, w, baseY, amplitude, (((w - gapRight) / 500f).toInt().coerceAtLeast(2)))
        return left + right
    }

    /**
     * Form einer Barriere — bestimmt wie die beiden Caps (Stalaktit-Unterseite,
     * Stalagmit-Oberseite) zwischen xL und xR verlaufen. Pillars selbst bleiben
     * vertikal.
     *
     * - [CLASSIC]: beide Caps horizontal — der ursprüngliche ⊓⊔-Look.
     * - [SLANTED_DOWN] / [SLANTED_UP]: beide Caps neigen sich gleichsinnig,
     *   die Lücke kippt wie ein schräger Tunnel.
     * - [FUNNEL_RIGHT] / [FUNNEL_LEFT]: Caps neigen sich gegeneinander, die
     *   Lücke verengt sich zu einer Seite hin (Trichter).
     */
    private enum class BarrierShape { CLASSIC, SLANTED_DOWN, SLANTED_UP, FUNNEL_RIGHT, FUNNEL_LEFT }

    private fun generateBarriers(
        rng: Random,
        count: Int,
        xMin: Float, xMax: Float,
        ceilingY: Float, floorY: Float,
    ): List<TerrainSegment> {
        if (count <= 0 || xMax - xMin < 600f) return emptyList()
        val out = mutableListOf<TerrainSegment>()
        val slot = (xMax - xMin) / count
        val shapes = BarrierShape.values()
        for (i in 0 until count) {
            val centerX = xMin + (i + 0.5f) * slot + (rng.nextFloat() - 0.5f) * slot * 0.3f
            val halfW   = 80f + rng.nextFloat() * 50f
            val xL      = centerX - halfW
            val xR      = centerX + halfW
            val gapH    = 260f + rng.nextFloat() * 140f
            val gapMin  = ceilingY + 200f
            val gapMax  = (floorY - 200f - gapH).coerceAtLeast(gapMin)
            val gapTop  = gapMin + rng.nextFloat() * (gapMax - gapMin)
            val gapBot  = gapTop + gapH

            // Tilt-Stärke: 30 bis 90 Einheiten — genug um sichtbar zu sein, aber
            // nie so viel dass die Lücke an einer Seite zu eng wird (Min-Höhe ~80).
            val tilt   = 30f + rng.nextFloat() * 60f
            val shape  = shapes[rng.nextInt(shapes.size)]
            val (gtL, gtR, gbL, gbR) = capCorners(shape, gapTop, gapBot, tilt)

            // Stalaktit (Cap kann schräg sein)
            out += seg(xL, ceilingY, xL, gtL)
            out += seg(xL, gtL,      xR, gtR)
            out += seg(xR, gtR,      xR, ceilingY)
            // Stalagmit (Cap kann schräg sein)
            out += seg(xL, floorY,   xL, gbL)
            out += seg(xL, gbL,      xR, gbR)
            out += seg(xR, gbR,      xR, floorY)
        }
        return out
    }

    private fun generateTurrets(
        rng: Random,
        count: Int,
        difficulty: Difficulty,
        xMin: Float, xMax: Float,
        yMin: Float, yMax: Float,
    ): List<TurretConfig> {
        if (count <= 0 || xMax - xMin < 100f || yMax - yMin < 100f) return emptyList()
        return List(count) {
            val x = xMin + rng.nextFloat() * (xMax - xMin)
            val y = yMin + rng.nextFloat() * (yMax - yMin)
            val period = difficulty.turretFirePeriod.random(rng)
            val speed  = difficulty.turretBulletSpeed.let {
                it.start + rng.nextFloat() * (it.endInclusive - it.start)
            }
            TurretConfig(Vector2(x, y), period, speed)
        }
    }

    private fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
        TerrainSegment(Vector2(x1, y1), Vector2(x2, y2))

    /**
     * Liefert die vier y-Koordinaten der Cap-Endpunkte (Stalaktit links/rechts,
     * Stalagmit links/rechts) für die gewählte [BarrierShape]. Y wächst nach
     * unten — Stalaktit-Caps haben kleinere y, Stalagmit-Caps größere.
     *
     * Tilt wird in beide Richtungen halbiert um die durchschnittliche Lücken-
     * Position bei (gapTop, gapBot) zu halten. So wandert die Lücke nicht je
     * nach Shape nach oben/unten.
     */
    private fun capCorners(
        shape: BarrierShape,
        gapTop: Float, gapBot: Float,
        tilt: Float,
    ): CapCorners {
        val half = tilt / 2f
        return when (shape) {
            BarrierShape.CLASSIC       -> CapCorners(gapTop,        gapTop,        gapBot,        gapBot)
            BarrierShape.SLANTED_DOWN  -> CapCorners(gapTop - half, gapTop + half, gapBot - half, gapBot + half)
            BarrierShape.SLANTED_UP    -> CapCorners(gapTop + half, gapTop - half, gapBot + half, gapBot - half)
            // Funnel: Stalaktit-Cap und Stalagmit-Cap nähern sich zur engen Seite
            // einander an. Lücken-Höhe an der engen Seite ≈ originalGap - tilt.
            BarrierShape.FUNNEL_RIGHT  -> CapCorners(gapTop - half, gapTop + half, gapBot + half, gapBot - half)
            BarrierShape.FUNNEL_LEFT   -> CapCorners(gapTop + half, gapTop - half, gapBot - half, gapBot + half)
        }
    }

    private data class CapCorners(val gtL: Float, val gtR: Float, val gbL: Float, val gbR: Float)
}
