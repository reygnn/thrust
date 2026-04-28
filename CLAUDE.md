# CLAUDE.md

Project conventions for **Thrust** (Android 2D game). This file is read automatically
by Claude Code at session start. Keep it focused and actionable — not a marketing
description (that's `README.md`'s job).

---

## Stack

- Kotlin 2.2.21, Jetpack Compose, Material 3
- Min SDK 26 (Android 8.0), Target SDK 36, JDK 17
- MVVM, manual DI via `ThrustApplication.kt` (no Hilt, no Koin)
- Persistence via DataStore Preferences
- Testing: JUnit 4, MockK, kotlinx-coroutines-test, Turbine

## Build & test

```bash
./gradlew assembleDebug    # debug APK
./gradlew test             # unit tests (no instrumentation needed)
```

Gradle 8.14 is bundled via wrapper. No local Gradle install required.

---

## Architecture

```
domain/engine/PhysicsEngine.kt          pure function: GameState + Input → GameState
domain/engine/CollisionDetector.kt      circle/segment geometry, landing logic
domain/engine/PhysicsConstants.kt       all tuning values in one place
domain/level/Levels.kt                  hand-built level data (id 1–4)
domain/model/GameModels.kt              Ship, FuelPod, Bullet, Turret, GameState, …
domain/model/Vector2.kt                 lightweight 2D math
data/SettingsRepository.kt              interface
data/SettingsRepositoryImpl.kt          DataStore-backed
data/HighScoreRepository.kt             interface
data/HighScoreRepositoryImpl.kt         DataStore-backed, dynamic per-level keys
ui/game/GameViewModel.kt                game loop, input, nav events
ui/game/GameScreen.kt                   Canvas + HUD + controls + overlays
ui/game/GameCanvas.kt                   DrawScope extensions for all game objects
ui/game/RotationWheel.kt                rotary wheel composable for wheel mode
```

## Hard architectural rules

1. **`PhysicsEngine.update()` is a pure function.** No Android dependencies, no
   `System.currentTimeMillis()`, no random number generation outside what's already
   handled. Engine input is `(GameState, InputState, Boolean) → GameState`. Tests
   depend on this purity.

2. **Game loop runs in `viewModelScope` via `delay(16L)`.** No custom `Handler`,
   no `Choreographer`. The simplicity is intentional.

3. **Manual DI through `ThrustApplication`.** Repositories are `by lazy` properties
   on the Application class, passed into ViewModels via the `Factory` pattern. Don't
   add a DI framework; the indirection it would buy doesn't justify the cost.

4. **`HighScoreRepositoryImpl` keys are generated dynamically** from
   `Levels.totalLevels`. Don't hardcode `hs_level_1`, `hs_level_2` etc. — when a new
   level is added, scoring must scale automatically. There was a real bug fixed in
   v0.2 where Level 4 scores silently overwrote Level 3 due to a hardcoded key range.

---

## Input handling — the wholesale-replace trap

Each input setter in `GameViewModel` updates **only its own field** of `_input`:

```kotlin
fun onRotateLeft(pressed: Boolean)  { _input.update { it.copy(rotateLeft  = pressed) } }
fun onRotateRight(pressed: Boolean) { _input.update { it.copy(rotateRight = pressed) } }
fun onThrust(pressed: Boolean)      { _input.update { it.copy(thrust      = pressed) } }
fun onFire(pressed: Boolean)        { _input.update { it.copy(shoot       = pressed) } }
fun onTargetAngleChange(a: Float?)  { _input.update { it.copy(targetAngle = a) } }
```

**Do not introduce** a `setInput(InputState)` method that replaces the whole input
state at once. There was a wholesale-replace pattern in v0.1 that silently
overwrote concurrent inputs — pressing thrust while firing reset `shoot` to false
because the thrust button sent a fresh `InputState` without it. Each control
manages its own field; nothing else.

## Two rotation modes share one engine path

`InputState.targetAngle: Float?` controls which rotation path the engine uses:

- `null` → button mode: `rotateLeft`/`rotateRight` apply ±`ROTATION_SPEED` per frame.
- non-null → wheel/slider mode: engine rotates toward the target, capped at
  `ROTATION_SPEED` per frame, using the shortest path around the 360° circle.

Both paths share the same speed limit. **Wheel mode is more precise (you specify
exact angles), not faster.** Don't introduce a wheel-mode bypass that lets the
ship snap to angles instantly — that loses Thrust's core skill element of
rotational inertia.

---

## UI layout rules

- **Pause button lives on the top-level `Box` of `GameScreen`**, NOT inside the
  controls Row. In v0.1 it was inside the controls Row and overlapped the rightmost
  button (FIRE when player gun was enabled). Keep it in its own corner.

- **Camera has a vertical offset** (`CAMERA_VERTICAL_OFFSET = 0.18f` in `GameCanvas`)
  that keeps the rocket above the touch controls. Active in both Buttons and Wheel
  modes. Engine is unaware — this is purely a render-time transform.

- **Landscape only.** Manifest sets `android:screenOrientation="sensorLandscape"`.
  Don't design for portrait; there's nothing meaningful to test there.

- **`GameScreen` switches between `GameControls` and `WheelControls`** based on
  `controlMode` from settings. They are sibling composables, not a single
  composable with branching internals.

---

## Wheel control: the closure-capture trap

`RotationWheel` holds the current angle as **internal `remember` state**, NOT as
a parameter from outside:

```kotlin
@Composable
fun RotationWheel(initialAngle: Float, ...) {
    var currentAngle by remember { mutableFloatStateOf(initialAngle) }
    Box(modifier = Modifier.pointerInput(Unit) {
        // Inside here, read `currentAngle`, not the parameter.
        // The pointerInput lambda captures parameters once at setup;
        // later parameter values are NOT visible inside the lambda.
        ...
    })
}
```

If you read the angle from a parameter inside `pointerInput`, the gesture handler
sees a stale value (the one captured when the lambda first ran), so each drag
delta is added to the same starting angle. Result: the rotation visibly
"stutters" around the initial value instead of accumulating. Lost an evening to
this in v0.2; don't repeat.

---

## Test conventions

### The runTest dispatcher rule (non-negotiable)

```kotlin
@get:Rule val mainDispatcherRule = MainDispatcherRule()

@Test fun whatever() = runTest(mainDispatcherRule.dispatcher) {
    // ...
}
```

**Always pass the dispatcher.** Plain `runTest { }` creates its own
`StandardTestDispatcher` with a separate `TestCoroutineScheduler`. Code under test
runs on `Dispatchers.Main` (the rule's `UnconfinedTestDispatcher`), so
`advanceTimeBy()` won't drive it. Tests fail or pass non-deterministically. This
caused real bugs in v0.1.

### Mock every flow the ViewModel reads at construction time

Every flow that the ViewModel collects via `stateIn(...)` in its constructor or
`init` block must be stubbed before instantiating the VM. If you extend
`SettingsRepository` with a new flow and forget to stub it in test setup, MockK
throws during VM construction and **all VM tests fail simultaneously** — a
distinctive symptom (13 reds at once, all with the same generic error). This
happened twice during v0.2 development.

```kotlin
private fun stubRepo() {
    every { highScoreRepo.getHighScores() } returns flowOf(emptyMap())
    coEvery { highScoreRepo.updateHighScore(any(), any()) } just Runs
    every { settingsRepo.playerGunEnabled } returns flowOf(false)
    every { settingsRepo.controlMode }     returns flowOf(ControlMode.BUTTONS)
    every { settingsRepo.thrustSide }      returns flowOf(ThrustSide.RIGHT)
    every { settingsRepo.wheelSize }       returns flowOf(WheelSize.MEDIUM)
    // Add new SettingsRepository flows here when extending the interface.
}
```

### Practice-mode VMs must be paused immediately in tests

Practice-mode (`GameMode.Practice`) runs with `lives = 999_999` and has no
`GamePhase.GameOver` path — the game loop is infinite by design. In tests, the
`UnconfinedTestDispatcher` runs the loop coroutine hot, and `runTest` will hang
forever trying to drain virtual time at the end of the test body.

**Fix: pause the VM right after construction.**

```kotlin
private fun buildDeliveryVmPaused(): GameViewModel {
    val vm = buildVm(savedStateHandle = deliveryHandle())
    vm.pauseGame()           // cancels gameLoopJob, sets phase = Paused
    return vm
}
```

This caused a 30+ minute hang during v0.4 development. A test executor at 102%
CPU for several minutes is the smoking-gun symptom — kill the daemons with
`./gradlew --stop` and look for a Practice-mode test missing the pause.

### Other conventions

- New tests are MockK only. Do not introduce Mockito.
- Pure-function physics tests use plain JUnit + `assertEquals`. No coroutine or
  flow infrastructure needed for these.
- Keep tests for `PhysicsEngine` deterministic — engine state is fully captured
  by `GameState`, given a state and an input the next state is exactly determined.

---

## High-score persistence: exactly one write per outcome

There are two pathways for high scores:

- **Game over** (lives reach zero): `PhysicsEngine` returns a state with
  `phase = GameOver`, the game loop in `GameViewModel.startLoop()` calls
  `highScoreRepository.updateHighScore(...)` once, then exits.
- **Level complete** (pad delivered + landed safely):
  `GameViewModel.advanceToNextLevel()` calls `updateHighScore(...)` once for the
  just-finished level before starting the next, regardless of whether it was the
  final level.

`onGameOverConfirmed()` does **not** persist. It only emits `BackToMenu`. The
loop already saved.

If you find yourself adding a third write site, you're probably introducing a
double-write bug. v0.1 had `onGameOverConfirmed()` writing again — was harmless
because the repo's `if (score > current)` made it idempotent, but redundant and
confusing.

---

## Localization

Two locales, both maintained:

- `app/src/main/res/values/strings.xml` (English, default + fallback)
- `app/src/main/res/values-de/strings.xml` (German)

**Convention:** arcade words stay English in both locales (SCORE, FUEL, FIRE,
PAUSE, RETRY, GAME OVER, MISSION COMPLETE, BEST SCORES). Prose translates
(Resume↔Weiter, Menu↔Menü, Final Score↔Finaler Score, Next Level↔Nächstes Level).

Level names ("Training Grounds", "Fortress Omega", etc.) are proper nouns —
they live as string literals in `Levels.kt` and are NOT translated.

App label is `@string/app_name`, not hardcoded in the manifest.

---

## Versioning

`versionName` in `app/build.gradle.kts` matches the GitHub release tag exactly.
v0.1 had a mismatch — `versionName = "1.0"` while the tag was `v0.1`. Fixed in
v0.2 development. Keep them aligned going forward.

---

## Git workflow: branch before non-trivial work

Larger changes — **bigger bugfixes, refactorings, new features, anything that
touches multiple files or could plausibly be reverted as a unit** — must happen
on a dedicated Git branch, never directly on `main`. Trivial edits (typo fix,
single-line tweak, doc nit) can stay on the current branch.

When in doubt, **stop and ask the user before starting**. It is always cheaper
to confirm than to realise mid-implementation that the work is on the wrong
branch.

**Workflow:**

1. Before writing code, propose a branch name that fits the topic and ask for
   confirmation. Suggested prefixes:
   - `fix/<slug>` — bugfix (e.g. `fix/pod-delivery-target`)
   - `refactor/<slug>` — refactoring (e.g. `refactor/input-state-flow`)
   - `feature/<slug>` — new feature (e.g. `feature/endless-leaderboard`)
   - `chore/<slug>` — tooling, build, dependencies
   - `test/<slug>` — test-only changes
2. Create the branch from an up-to-date `main` (or the appropriate base) and
   switch to it before the first edit.
3. If you notice mid-task that you are still on `main`, stop and remind the
   user — do not silently keep working.

The branch name proposal is a suggestion; the user gets the final say.

**After a fast-forward merge into `main`:** switch back to `main` and ask the
user whether the merged branch should be deleted both locally and on the
remote. Do not delete it silently — even though the commits live on in `main`,
the user may want to keep the branch around (open PR, ongoing review,
historical reference). Always confirm before `git branch -d` and especially
before `git push origin --delete`.

---

## What this file is NOT

- Not a description of the game (see `README.md`).
- Not a changelog (see `RELEASE_v*.md`).
- Not the place for transient notes about ongoing refactors. Add those to
  short-lived feature branches' commit messages, not here.

Update this file when an architectural rule changes or a new hard-won lesson
deserves to be future-proof. Do not bloat it with details that are obvious from
reading the code.
