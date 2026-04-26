package com.github.reygnn.thrust.domain.level

import com.github.reygnn.thrust.domain.model.*

object Levels {

    val all: List<LevelConfig> by lazy { listOf(level1(), level2(), level3(), level4()) }

    fun getById(id: Int): LevelConfig = all.first { it.id == id }

    val totalLevels: Int get() = all.size

    private fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
        TerrainSegment(Vector2(x1, y1), Vector2(x2, y2))

    private fun level4() = buildLevel4()

    // ── Level 1 ───────────────────────────────────────────────────────────────
    private fun level1(): LevelConfig {
        val W = 2000f; val H = 1600f
        return LevelConfig(
            id              = 1,
            name            = "Training Grounds",
            worldWidth      = W,
            worldHeight     = H,
            gravity         = 0.030f,
            shipStart       = Vector2(380f, 500f),
            fuelPodPosition = Vector2(340f, 920f),
            landingPad      = LandingPad(center = Vector2(1500f, 1400f), halfWidth = 210f),
            terrain         = buildList {
                add(seg(0f, 0f, W, 0f)); add(seg(0f, 0f, 0f, H))
                add(seg(0f, H, W, H));   add(seg(W, 0f, W, H))
                // Decke
                add(seg(0f, 200f, 350f, 168f)); add(seg(350f, 168f, 650f, 235f))
                add(seg(650f, 235f, 950f, 178f)); add(seg(950f, 178f, 1250f, 215f))
                add(seg(1250f, 215f, 1550f, 175f)); add(seg(1550f, 175f, 1800f, 208f))
                add(seg(1800f, 208f, W, 190f))
                // Boden links (Spalt 1290..1710)
                add(seg(0f, 1390f, 400f, 1368f)); add(seg(400f, 1368f, 700f, 1418f))
                add(seg(700f, 1418f, 1050f, 1385f)); add(seg(1050f, 1385f, 1290f, 1402f))
                // Boden rechts
                add(seg(1710f, 1402f, 1900f, 1375f)); add(seg(1900f, 1375f, W, 1395f))
                // Stalaktit
                add(seg(750f, 235f, 750f, 700f)); add(seg(750f, 700f, 870f, 700f))
                add(seg(870f, 700f, 870f, 178f))
                // Stalagmit
                add(seg(940f, 1395f, 940f, 900f)); add(seg(940f, 900f, 1060f, 900f))
                add(seg(1060f, 900f, 1060f, 1385f))
            },
        )
    }

    // ── Level 2 ───────────────────────────────────────────────────────────────
    private fun level2(): LevelConfig {
        val W = 3000f; val H = 2000f
        return LevelConfig(
            id              = 2,
            name            = "The Gauntlet",
            worldWidth      = W,
            worldHeight     = H,
            gravity         = 0.040f,
            shipStart       = Vector2(480f, 580f),
            fuelPodPosition = Vector2(420f, 1100f),
            landingPad      = LandingPad(center = Vector2(2475f, 1760f), halfWidth = 275f),
            turrets         = listOf(
                TurretConfig(Vector2(1900f, 1450f), 150, 4.5f),
            ),
            terrain         = buildList {
                add(seg(0f, 0f, W, 0f)); add(seg(0f, 0f, 0f, H))
                add(seg(0f, H, W, H));   add(seg(W, 0f, W, H))
                // Decke
                add(seg(0f, 255f, 500f, 215f)); add(seg(500f, 215f, 900f, 282f))
                add(seg(900f, 282f, 1300f, 232f)); add(seg(1300f, 232f, 1700f, 262f))
                add(seg(1700f, 262f, 2100f, 222f)); add(seg(2100f, 222f, 2500f, 268f))
                add(seg(2500f, 268f, 2800f, 238f)); add(seg(2800f, 238f, W, 252f))
                // Boden links (Spalt 2200..2750)
                add(seg(0f, 1755f, 500f, 1722f)); add(seg(500f, 1722f, 900f, 1772f))
                add(seg(900f, 1772f, 1300f, 1742f)); add(seg(1300f, 1742f, 1700f, 1762f))
                add(seg(1700f, 1762f, 2100f, 1732f)); add(seg(2100f, 1732f, 2200f, 1760f))
                // Boden rechts
                add(seg(2750f, 1760f, 2900f, 1735f)); add(seg(2900f, 1735f, W, 1755f))
                // Stalaktit (Gap y 870..1120)
                add(seg(1380f, 232f, 1380f, 870f)); add(seg(1380f, 870f, 1570f, 870f))
                add(seg(1570f, 870f, 1570f, 262f))
                // Stalagmit
                add(seg(1380f, 1755f, 1380f, 1120f)); add(seg(1380f, 1120f, 1570f, 1120f))
                add(seg(1570f, 1120f, 1570f, 1742f))
                // Decken-Hindernis rechte Kammer
                add(seg(2020f, 222f, 2020f, 560f)); add(seg(2020f, 560f, 2150f, 560f))
                add(seg(2150f, 560f, 2150f, 222f))
                // Boden-Hindernis linke Kammer
                add(seg(700f, 1755f, 700f, 1350f)); add(seg(700f, 1350f, 830f, 1350f))
                add(seg(830f, 1350f, 830f, 1722f))
            },
        )
    }

    // ── Level 3 ───────────────────────────────────────────────────────────────
    private fun level3(): LevelConfig {
        val W = 4000f; val H = 3000f
        return LevelConfig(
            id              = 3,
            name            = "Deep Core",
            worldWidth      = W,
            worldHeight     = H,
            gravity         = 0.055f,
            shipStart       = Vector2(580f, 820f),
            fuelPodPosition = Vector2(680f, 1750f),
            landingPad      = LandingPad(center = Vector2(3500f, 2705f), halfWidth = 250f),
            turrets         = listOf(
                TurretConfig(Vector2(1700f, 1325f), 120, 5.0f),
                TurretConfig(Vector2(2950f, 1625f), 100, 5.0f),
                TurretConfig(Vector2(3780f, 2250f),  80, 5.5f),
            ),
            terrain         = buildList {
                add(seg(0f, 0f, W, 0f)); add(seg(0f, 0f, 0f, H))
                add(seg(0f, H, W, H));   add(seg(W, 0f, W, H))
                // Decke
                add(seg(0f, 305f, 600f, 262f)); add(seg(600f, 262f, 1200f, 335f))
                add(seg(1200f, 335f, 1800f, 288f)); add(seg(1800f, 288f, 2400f, 342f))
                add(seg(2400f, 342f, 3000f, 292f)); add(seg(3000f, 292f, 3600f, 328f))
                add(seg(3600f, 328f, W, 305f))
                // Boden (Spalt 3250..3750)
                add(seg(0f, 2705f, 600f, 2662f)); add(seg(600f, 2662f, 1200f, 2722f))
                add(seg(1200f, 2722f, 1800f, 2682f)); add(seg(1800f, 2682f, 2400f, 2722f))
                add(seg(2400f, 2722f, 3000f, 2682f)); add(seg(3000f, 2682f, 3250f, 2705f))
                add(seg(3750f, 2705f, 3900f, 2678f)); add(seg(3900f, 2678f, W, 2698f))
                // Durchlass 1 (Gap y 1050..1600)
                add(seg(1250f, 262f, 1250f, 1050f)); add(seg(1250f, 1050f, 1450f, 1050f))
                add(seg(1450f, 1050f, 1450f, 288f))
                add(seg(1250f, 2705f, 1250f, 1600f)); add(seg(1250f, 1600f, 1450f, 1600f))
                add(seg(1450f, 1600f, 1450f, 2682f))
                // Durchlass 2 (Gap y 1350..1900)
                add(seg(2500f, 288f, 2500f, 1350f)); add(seg(2500f, 1350f, 2700f, 1350f))
                add(seg(2700f, 1350f, 2700f, 292f))
                add(seg(2500f, 2705f, 2500f, 1900f)); add(seg(2500f, 1900f, 2700f, 1900f))
                add(seg(2700f, 1900f, 2700f, 2682f))
                // Extra-Hindernis letzte Kammer
                add(seg(3380f, 292f, 3380f, 820f)); add(seg(3380f, 820f, 3580f, 820f))
                add(seg(3580f, 820f, 3580f, 305f))
            },
        )
    }

    // ── Level 4 – Fortress Omega ──────────────────────────────────────────────
    // Welt 5500×3500, Schwerkraft 0.08 – schwerste Herausforderung.
    // 5 Türme, enge Schächte, drei Barrieren; Spieler-Bordkanone empfohlen.
    private fun buildLevel4(): LevelConfig {
        val W = 5500f; val H = 3500f
        return LevelConfig(
            id              = 4,
            name            = "Fortress Omega",
            worldWidth      = W,
            worldHeight     = H,
            gravity         = 0.08f,
            shipStart       = Vector2(700f, 900f),
            fuelPodPosition = Vector2(850f, 2100f),
            landingPad      = LandingPad(center = Vector2(4800f, 3180f), halfWidth = 240f),
            turrets         = listOf(
                TurretConfig(Vector2(1800f, 1580f), firePeriodFrames = 110),  // Wächter Durchlass 1
                TurretConfig(Vector2(2800f, 1900f), firePeriodFrames = 95),   // Wächter Durchlass 2
                TurretConfig(Vector2(3700f, 1300f), firePeriodFrames = 80),   // Mittlere Kammer
                TurretConfig(Vector2(4400f, 2600f), firePeriodFrames = 70),   // Vorkammer Pad
                TurretConfig(Vector2(5000f, 1100f), firePeriodFrames = 60,    // Schnellster Turm
                             bulletSpeed = 6f),
            ),
            terrain         = buildList {
                add(seg(0f, 0f, W, 0f)); add(seg(0f, 0f, 0f, H))
                add(seg(0f, H, W, H));   add(seg(W, 0f, W, H))
                // Decke (wellig)
                add(seg(0f, 340f, 700f, 295f));   add(seg(700f,  295f, 1400f, 360f))
                add(seg(1400f, 360f, 2100f, 305f)); add(seg(2100f, 305f, 2800f, 350f))
                add(seg(2800f, 350f, 3500f, 298f)); add(seg(3500f, 298f, 4200f, 340f))
                add(seg(4200f, 340f, 4900f, 302f)); add(seg(4900f, 302f, W,    330f))
                // Boden (Spalt 4560..5040 für Landing Pad)
                add(seg(0f, 3160f, 700f, 3125f));  add(seg(700f,  3125f, 1400f, 3170f))
                add(seg(1400f, 3170f, 2100f, 3130f)); add(seg(2100f, 3130f, 2800f, 3165f))
                add(seg(2800f, 3165f, 3500f, 3128f)); add(seg(3500f, 3128f, 4200f, 3160f))
                add(seg(4200f, 3160f, 4560f, 3180f))
                add(seg(5040f, 3180f, 5200f, 3150f)); add(seg(5200f, 3150f, W, 3165f))
                // Barriere 1 (Durchlass y 1150..1750, Breite 180)
                add(seg(1600f, 295f, 1600f, 1150f));  add(seg(1600f, 1150f, 1780f, 1150f))
                add(seg(1780f, 1150f, 1780f, 330f))
                add(seg(1600f, 3160f, 1600f, 1750f)); add(seg(1600f, 1750f, 1780f, 1750f))
                add(seg(1780f, 1750f, 1780f, 3125f))
                // Barriere 2 (Durchlass y 1400..1950, Breite 180)
                add(seg(2650f, 305f, 2650f, 1400f));  add(seg(2650f, 1400f, 2830f, 1400f))
                add(seg(2830f, 1400f, 2830f, 340f))
                add(seg(2650f, 3160f, 2650f, 1950f)); add(seg(2650f, 1950f, 2830f, 1950f))
                add(seg(2830f, 1950f, 2830f, 3130f))
                // Barriere 3 (Durchlass y 1000..1600, Breite 200)
                add(seg(3900f, 298f, 3900f, 1000f));  add(seg(3900f, 1000f, 4100f, 1000f))
                add(seg(4100f, 1000f, 4100f, 320f))
                add(seg(3900f, 3160f, 3900f, 1600f)); add(seg(3900f, 1600f, 4100f, 1600f))
                add(seg(4100f, 1600f, 4100f, 3128f))
                // Deckenstalagtit letzte Kammer (Turm-Schutz)
                add(seg(4700f, 302f, 4700f, 900f));   add(seg(4700f, 900f, 4900f, 900f))
                add(seg(4900f, 900f, 4900f, 330f))
            },
        )
    }
}
