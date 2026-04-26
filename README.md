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

All controls are hold-to-press buttons at the bottom of the screen.

| Button | Action |
|--------|--------|
| **◄** | Rotate left |
| **▲ SCHUB** | Thrust — accelerates in the direction the nose is pointing |
| **►** | Rotate right |
| **🔥 FIRE** | Fire cannon from the rocket tip *(optional, see Options)* |
| **⏸** | Pause / Resume |

Rotation and thrust can be combined simultaneously. To aim the cannon at a turret, rotate your rocket until the nose points at the target, then fire.

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
- **Turrets** track the ship and fire every N frames depending on the level. Their bullets are removed on impact with terrain or the ship.

---

## Options

Open **Options** from the main menu.

**Player Cannon** — disabled by default.

When enabled, a **🔥 FIRE** button appears during gameplay. Shots are fired from the rocket tip in the direction the nose is pointing, with the ship's current velocity added for momentum. There is a ~290 ms cooldown between shots. Direct hits destroy turrets permanently for the rest of the level.

This option is saved automatically and persists between sessions.

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
    │   └── GameCanvas              # DrawScope extensions for all game objects
    ├── menu/         MenuScreen + MenuViewModel
    ├── highscore/    HighScoreScreen + HighScoreViewModel
    ├── options/      OptionsScreen + OptionsViewModel
    ├── navigation/   ThrustNavGraph
    └── theme/        Color, Type, Theme
```

**Key design decisions:**

- `PhysicsEngine.update()` is a pure function — no Android dependencies, fully unit-testable.
- The game loop runs in `viewModelScope` using `delay(16L)`. No custom `Handler` or `Choreographer`.
- Manual DI via `ThrustApplication` — `HighScoreRepository` and `SettingsRepository` are lazily initialized and passed into ViewModels through their `Factory`.
- Navigation is handled by a single `NavHost`. The `GameViewModel` is scoped to its `NavBackStackEntry` so it survives recomposition but not back-stack popping.

---

## Tech Stack

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.2.21 | Language |
| Compose BOM | 2026.03.01 | UI framework |
| Material 3 | via BOM | Design system |
| Navigation Compose | 2.9.7 | Screen routing |
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
| `PhysicsEngineTest` | Thrust direction, gravity, fuel depletion, respawn, turret fire |
| `CollisionDetectorTest` | Circle/segment intersection, landing success/crash/none, bullet hits |
| `GameViewModelTest` | Game loop, input handling, pause/resume, nav events, high score saving |
| `HighScoreRepositoryTest` | Score persistence, update-only-if-higher logic |

All tests use `MainDispatcherRule` with `UnconfinedTestDispatcher` — no separate `TestScope` or `StandardTestDispatcher` is created anywhere in the test suite.

---

## Credits

Original game design by **Rob Hubbard** (music) and **Tony Crowther** (code), published by **Firebird Software**, 1986.

This is an unofficial fan remake for educational and personal use. No commercial use intended.
