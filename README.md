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

## Levels

| # | Name | World Size | Gravity | Turrets |
|---|------|-----------|---------|---------|
| 1 | Training Grounds | 2000 × 1600 | 0.030 | 0 |
| 2 | The Gauntlet | 3000 × 2000 | 0.040 | 1 |
| 3 | Deep Core | 4000 × 3000 | 0.055 | 3 |
| 4 | Fortress Omega | 5500 × 3500 | 0.080 | 5 |

Score and lives carry over between levels. Complete all four to finish the mission.

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
- **The rope** is a pendulum — the pod swings and builds momentum. Sudden direction changes will throw it wide.
- **Landing** requires vertical speed ≤ 2.5 and nose angle ≤ 20° from vertical. Anything outside those tolerances is a crash.
- **Turrets** track the ship and fire on a per-turret cooldown. Their bullets are removed on impact with terrain or the ship. Turrets sharing the same fire period are deterministically staggered so they don't lock-step fire on the same frame.

---

## Options

Open **Options** from the main menu.

**Control mode** — Buttons or Wheel (default: Buttons). See the Controls section above.

**Thrust position** *(wheel mode only)* — Left or Right side of the screen.

**Wheel size** *(wheel mode only)* — S (120 dp), M (144 dp, default), L (180 dp), or XL (220 dp).

**Player Cannon** — disabled by default.

When enabled, a **🔥 FIRE** button (or the wheel double-tap) becomes active during gameplay. Shots are fired from the rocket tip in the direction the nose is pointing, with the ship's current velocity added for momentum. There is a ~290 ms cooldown between shots. Direct hits destroy turrets permanently for the rest of the level. Recommended for Level 4.

All options are saved automatically and persist between sessions.

---

## Architecture

The project follows **MVVM** with a unidirectional data flow and no dependency injection framework.

```
com.github.reygnn.thrust
├── data/
│   ├── HighScoreRepository         # Interface
│   ├── HighScoreRepositoryImpl     # DataStore-backed
│   ├── SettingsRepository          # Interface
│   └── SettingsRepositoryImpl      # DataStore-backed
├── domain/
│   ├── engine/
│   │   ├── PhysicsEngine           # Pure function: GameState + Input → GameState
│   │   ├── CollisionDetector       # Circle/segment geometry, landing logic
│   │   └── PhysicsConstants        # All tuning values in one place
│   ├── level/
│   │   ├── LevelRepository         # Interface
│   │   ├── LevelRepositoryImpl     # In-memory, delegates to Levels
│   │   └── Levels                  # All four level definitions
│   └── model/
│       ├── GameModels              # Ship, FuelPod, Bullet, Turret, GameState, …
│       └── Vector2                 # Lightweight 2D math type
└── ui/
    ├── game/
    │   ├── GameViewModel           # Game loop, input, nav events
    │   ├── GameScreen              # Canvas + HUD + controls + overlays
    │   ├── GameCanvas              # DrawScope extensions for all game objects
    │   └── RotationWheel           # The rotary wheel composable
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
| `GameViewModelTest` | Game loop, input handling, pause/resume, nav events, high score saving |
| `HighScoreRepositoryTest` | Score persistence, update-only-if-higher logic |

All tests use `MainDispatcherRule` with `UnconfinedTestDispatcher` and pass it explicitly to `runTest(mainDispatcherRule.dispatcher) { … }` — there is no separate `TestScope` or `StandardTestDispatcher` anywhere in the suite.

---

## Credits

Original game design by **Tony Crowther** (code) with music by **Rob Hubbard**, published by **Firebird Software**, 1986.

This is an unofficial fan remake for educational and personal use. No commercial use intended.
