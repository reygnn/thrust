# THRUST
### Fuel Pod Rescue Mission

A faithful recreation of the classic 1986 C64/Amiga game **Thrust**, built natively for Android using Kotlin, Jetpack Compose, and Material 3.

---

## Gameplay

You pilot a rocket through a series of underground cave systems. Your mission on each level is straightforward — but pulling it off is not:

1. **Fly** to the fuel pod hanging in the cave.
2. **Pick it up** by hovering directly above it. The pod attaches to your rocket on a swinging rope.
3. **Carry** the pod back to the landing pad without crashing, running out of fuel, or getting shot down.
4. **Land** gently — too fast or too steep and you'll explode.

Gravity is always working against you. Fuel is finite. Turrets don't miss.

---

## Controls

Two control schemes are available — pick the one that suits you in the Options menu.

### Buttons (default)

Hold-to-press buttons at the bottom of the screen.

| Button | Action |
|--------|--------|
| **◄** | Rotate left |
| **▲ THRUST** | Thrust — accelerates in the direction the nose is pointing |
| **►** | Rotate right |
| **🔥 FIRE** | Fire cannon from the rocket tip *(optional, see Options)* |
| **⏸** | Pause / Resume |

Rotation and thrust can be combined simultaneously. To aim the cannon at a turret, rotate your rocket until the nose points at the target, then fire.

### Wheel

A rotary wheel for steering and a thrust button. The wheel uses **relative drag** — touch anywhere on the wheel and rotate your finger; the rocket follows the rotation, not the absolute touch position.

- **Rotate finger on wheel** → ship rotates toward the target angle (subject to the same rotation-rate limit as button mode, so the skill element of rotational inertia is preserved).
- **Hold thrust button** → applies thrust.
- **Double-tap on the wheel** (release first, then two quick taps at the same spot) → fires the cannon.
- **Pause button** is always at the top right.

Wheel diameter is configurable (S / M / L / XL). Thrust button position can be set to the left or right side for left/right-handed players.

---

## Game Modes

Three modes are available from the main menu: Mission, Endless, and Practice.

### Mission (Story)

Four hand-crafted levels in fixed order. Score and lives carry over between levels. Complete all four to finish the mission. High scores are tracked per level.

| # | Name | World Size | Gravity | Turrets |
|---|------|-----------|---------|---------|
| 1 | Training Grounds | 2000 × 1600 | 0.030 | 0 |
| 2 | The Gauntlet | 3000 × 2000 | 0.040 | 1 |
| 3 | Deep Core | 4000 × 3000 | 0.055 | 3 |
| 4 | Fortress Omega | 5500 × 3500 | 0.080 | 5 |

### Endless

Procedurally generated levels at one of five difficulties. Pick your difficulty, run levels in series, accumulate score, see how far you can go. Lives reset to 3 each level; score carries.

| Difficulty | World Size | Gravity | Turrets | Barriers |
|-----------|-----------|---------|---------|----------|
| Rookie | 2000 × 1500 | 0.025 | 0 | 0–1 |
| Medium | 3000 × 2000 | 0.045 | 1–4 | 1–2 |
| Impossible | 4000 × 2800 | 0.065 | 5–10 | 2–3 |
| InstaDeath | 4500 × 3200 | 0.080 | 11–15 | 3–4 |
| Pure Chaos | 5500 × 3500 | 0.095 | 15–25 | 4–6 |

The level generator is deterministic in its seed and runtime-validated: each level is BFS-checked for reachability before being handed to the player. Pure Chaos guarantees the **fuel pod** is always reachable; the four lower difficulties additionally guarantee a **clear approach to the landing pad**. Pure Chaos also shows a brief "ABANDON HOPE" disclaimer on entry — fuel, pod or pad may legitimately be unreachable in the chaotic placement, and a level restart is part of the deal.

**Endless flow:**

- **Death with lives left** → full level reset (same seed, fresh state)
- **Game Over** → three buttons: Menu / Retry (same level) / Next (random level)
- **Pause overlay** in Endless adds **Skip Level** (fresh random, streak resets) and **Save Level** (persists this seed as a Favorite)

Persistent stats per difficulty: longest streak (highest number of consecutive levels completed in a single run).

### Favorites

You can save the current Endless level as a Favorite from the pause menu. Saved levels are stored as `(difficulty, seed)` pairs and replay identically. Manage favorites from the **FAVORITES** entry on the difficulty picker screen — tap to play, Remove to delete. Up to 20 entries (FIFO).

A Favorite playthrough does **not** count toward the streak (it would amount to level-hopping); when you complete or quit a Favorite, you return to the menu.

### Practice

Skill drills with no score, no lives, no end. Three modes accessible from a separate picker screen:

| Mode | What you practice |
|------|-------------------|
| **Tube** | Procedural snaking corridor (10000-wide) with stalactites, stalagmites, full pillar barriers and free-floating rocks. Ship starts with the pod on a rope; gravity is dialed up so you can't just hold thrust. Crash → instant reset to start. |
| **Delivery** | Open arena. Ship spawns top-left, pad sits bottom-right. Pod materializes at a random position each cycle (≥1000 from pad, ≥600 from ship spawn). Lift off → fetch pod → fly to pad → land. Successful landing kicks off the next cycle with a new pod position. |
| **Turrets** | Open arena, single turret, player cannon auto-enabled. Destroy the turret → it materializes at a new random position 2 seconds later. |

Lives are effectively infinite; on death the level resets cleanly. Pure muscle-memory training without scoring overhead.

### Scoring

| Event | Points |
|-------|--------|
| Pod delivered to landing pad | +500 |
| Successful landing after delivery | +1000 |

---

## Physics

The ship simulation runs at 60 fps (16 ms frame delay) and obeys the following rules:

- **Gravity** pulls the ship downward every frame. Each level has its own gravity constant — it increases with difficulty.
- **Thrust** applies force in the direction the nose is pointing. It consumes fuel and has no effect when the tank is empty.
- **Max speed** is capped at 7 units/frame in any direction.
- **The rope** is a pendulum — the pod swings and builds momentum. Sudden direction changes will throw it wide. If the pod brushes a wall lightly the rope holds and the pod bounces with damping; a hard impact (`|v · n| > 3.5`) snaps the rope and the pod drops. Detached pods fall under gravity, bounce 1–4 times depending on fall height (50% energy retained per bounce), and settle when their speed drops below the threshold. Falling pods cannot be picked up — the player has to wait for them to come to rest.
- **Landing** requires vertical speed ≤ 2.5 and nose angle ≤ 20° from vertical. Anything outside those tolerances is a crash.
- **Turrets** track the ship and fire on a per-turret cooldown. Bullets are filtered only by lifetime and world bounds — they pass through terrain (intentional; see `TODO.md`). Turrets sharing the same fire period are deterministically staggered so they don't lock-step fire on the same frame.

---

## Options

Open **Options** from the main menu.

**Control mode** — Buttons or Wheel (default: Buttons). See the Controls section above.

**Thrust position** *(wheel mode only)* — Left or Right side of the screen.

**Wheel size** *(wheel mode only)* — S (120 dp), M (144 dp, default), L (180 dp), or XL (220 dp).

**Thrust button size** — S (72 dp), M (88 dp, default), L (104 dp), or XL (128 dp). Affects both control modes.

**Player Cannon** — disabled by default.

When enabled, a **🔥 FIRE** button (or the wheel double-tap) becomes active during gameplay. Shots are fired from the rocket tip in the direction the nose is pointing, with the ship's current velocity added for momentum. There is a ~290 ms cooldown between shots. Direct hits destroy turrets permanently for the rest of the level. Recommended for Level 4.

All options are saved automatically and persist between sessions.

---

## Architecture

The project follows **MVVM** with a unidirectional data flow and no dependency injection framework.

```
com.github.reygnn.thrust
├── data/
│   ├── HighScoreRepository(.Impl)            # Story-mode: high score per level id
│   ├── EndlessHighScoreRepository(.Impl)     # Endless: longest streak per difficulty
│   ├── EndlessFavorite                       # (difficulty, seed, savedAt)
│   ├── EndlessFavoritesRepository(.Impl)     # Saved levels list (max 20, FIFO)
│   └── SettingsRepository(.Impl)             # Control mode, wheel size, cannon, …
├── domain/
│   ├── engine/
│   │   ├── PhysicsEngine           # Pure function: GameState + Input → GameState
│   │   ├── CollisionDetector       # Circle/segment geometry, landing logic
│   │   └── PhysicsConstants        # All tuning values in one place
│   ├── level/
│   │   ├── LevelRepository(.Impl)  # Story-mode level lookup
│   │   ├── Levels                  # The four story-mode level definitions
│   │   ├── Difficulty              # Endless difficulty + generation parameters
│   │   ├── LevelGenerator          # Seedable procedural level generator
│   │   ├── LevelPlayability        # BFS-based reachability checker
│   │   ├── PracticeKind            # Tube / Delivery / Turrets
│   │   └── PracticeLevels          # Hand-built configs for the practice drills
│   └── model/
│       ├── GameModels              # Ship, FuelPod, Bullet, Turret, GameState, …
│       └── Vector2                 # Lightweight 2D math type
└── ui/
    ├── game/
    │   ├── GameMode                # Story / Endless / EndlessFavorite / Practice
    │   ├── GameViewModel           # Game loop, input, nav events, mode switching
    │   ├── GameScreen              # Canvas + HUD + controls + overlays
    │   ├── GameCanvas              # DrawScope extensions for all game objects
    │   └── RotationWheel           # The rotary wheel composable
    ├── endless/
    │   ├── DifficultyPickerScreen  # 5 difficulty cards + best streak
    │   ├── EndlessPickerViewModel
    │   ├── FavoritesScreen         # Manage saved levels
    │   └── FavoritesViewModel
    ├── practice/
    │   └── PracticePickerScreen    # 3 practice cards (Tube / Delivery / Turrets)
    ├── menu/         MenuScreen + MenuViewModel
    ├── highscore/    HighScoreScreen + HighScoreViewModel
    ├── options/      OptionsScreen + OptionsViewModel
    ├── navigation/   ThrustNavGraph
    └── theme/        Color, Type, Theme
```

**Key design decisions:**

- `PhysicsEngine.update()` is a pure function — no Android dependencies, fully unit-testable.
- The game loop runs in `viewModelScope` using `delay(16L)`. No custom `Handler` or `Choreographer`.
- The engine supports two rotation modes: button mode (discrete rotate-left/right inputs) and slider mode (a target angle that the engine works toward at the same per-frame rate). Both modes share the same rotation-speed limit, so wheel control is more *precise*, not faster.
- The camera follows the ship with a vertical offset that keeps the rocket above the touch controls in both control modes.
- Manual DI via `ThrustApplication` — `HighScoreRepository` and `SettingsRepository` are lazily initialized and passed into ViewModels through their `Factory`.
- Navigation is handled by a single `NavHost`. The `GameViewModel` is scoped to its `NavBackStackEntry` so it survives recomposition but not back-stack popping.

---

## Tech Stack

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.2.21 | Language |
| Compose BOM | 2026.04.01 | UI framework |
| Material 3 | via BOM | Design system |
| Navigation Compose | 2.9.8 | Screen routing |
| DataStore Preferences | 1.2.1 | Persistent settings & high scores |
| Lifecycle / ViewModel | 2.10.0 | MVVM infrastructure |
| JUnit 4 | 4.13.2 | Test runner |
| MockK | 1.14.9 | Mocking |
| kotlinx-coroutines-test | 1.10.2 | Coroutine testing |
| Turbine | 1.2.1 | Flow testing |

Minimum SDK: **26** (Android 8.0). Target SDK: **36**.

---

## Building

```bash
# Clone and open in Android Studio, or build from the command line:
./gradlew assembleDebug

# Run unit tests:
./gradlew test
```

Requires **JDK 17** and Android SDK with API 36 build tools. Gradle 8.14 is bundled via the wrapper — no local Gradle installation needed.

---

## Testing

The test suite covers the core game logic without any Android dependencies.

| Suite | What it tests |
|-------|--------------|
| `PhysicsEngineTest` | Thrust direction, gravity, fuel depletion, respawn, turret cadence (per-turret cooldown), slider-mode rotation |
| `CollisionDetectorTest` | Circle/segment intersection, landing success/crash/none, bullet hits |
| `LevelGeneratorTest` | Determinism per seed, world bounds, parameter ranges, pad gap invariant, pod-x cap |
| `LevelPlayabilityTest` | BFS reachability — pod for all difficulties (incl. Pure Chaos), pad approach for non-Pure-Chaos |
| `GameViewModelTest` | Game loop, input handling, pause/resume, nav events, high score & streak saving, Endless modes (regular + favorite), seed propagation, DELIVERY pod-position regressions |
| `HighScoreRepositoryTest` | Score persistence, update-only-if-higher logic |

All tests use `MainDispatcherRule` with `UnconfinedTestDispatcher` and pass it explicitly to `runTest(mainDispatcherRule.dispatcher) { … }` — there is no separate `TestScope` or `StandardTestDispatcher` anywhere in the suite.

---

## Credits

Original game design by **Tony Crowther** (code) with music by **Rob Hubbard**, published by **Firebird Software**, 1986.

This is an unofficial fan remake for educational and personal use. No commercial use intended.
