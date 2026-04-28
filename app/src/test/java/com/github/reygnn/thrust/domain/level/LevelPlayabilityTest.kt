package com.github.reygnn.thrust.domain.level

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validiert die Erreichbarkeits-Garantien des [LevelGenerator]:
 *
 * - Für **alle Difficulties außer Pure Chaos**: Pod **und** Pad-Anflug sind
 *   geometrisch erreichbar (volle Spielbarkeit by construction durch
 *   Pod-x-Cap und vernünftige Barrieren-Lücken).
 * - Für **Pure Chaos**: nur der Pod ist garantiert erreichbar (Generator
 *   permutiert dafür den Seed). Pad-Erreichbarkeit bleibt absichtlich
 *   chaotisch — der In-Game-Disclaimer weist darauf hin.
 */
class LevelPlayabilityTest {

    @Test
    fun `every non-Pure-Chaos seed produces a fully playable level`() {
        val nonChaos = Difficulty.values().filter { it != Difficulty.PURE_CHAOS }
        nonChaos.forEach { d ->
            (0L..49L).forEach { seed ->
                val cfg = LevelGenerator.generate(d, seed)
                assertTrue(
                    "Pod nicht erreichbar bei $d seed=$seed",
                    LevelPlayability.isPodReachableFromShip(cfg),
                )
                assertTrue(
                    "Pad-Anflug nicht erreichbar bei $d seed=$seed",
                    LevelPlayability.isPadApproachReachableFromShip(cfg),
                )
            }
        }
    }

    @Test
    fun `every Pure Chaos seed produces a level with a reachable pod`() {
        // Minimalanforderung an Pure Chaos: der Pod ist immer erreichbar,
        // selbst wenn der Generator dafür mehrere Seed-Permutationen probiert.
        // Pad-Erreichbarkeit absichtlich nicht garantiert.
        (0L..49L).forEach { seed ->
            val cfg = LevelGenerator.generate(Difficulty.PURE_CHAOS, seed)
            assertTrue(
                "Pod nicht erreichbar bei PURE_CHAOS seed=$seed",
                LevelPlayability.isPodReachableFromShip(cfg),
            )
        }
    }
}
