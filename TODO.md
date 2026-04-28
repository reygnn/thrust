# TODO

Mögliche Änderungen, die bewusst aufgeschoben sind. Kein Versprechen, nur ein
Notizblock — wenn etwas davon im Spiel stört, hier nachschauen.

## checkBulletTerrain

Geschosse (Turret- und Spielergeschosse) fliegen aktuell durch Wände. In
`PhysicsEngine.update()` werden Bullets nur nach Lebensdauer und Welt-Bounds
gefiltert — keine Terrain-Kollision.

**Wann nachziehen:** wenn Türme aus der Deckung heraus zu unfair feuern oder
Spielergeschosse durch Wände ein Balance-Problem werden.

**Implementation:** Liniensegment-Schnitt-Test (Bullet-Vorgängerposition →
neue Position) gegen jedes Terrain-Segment. Performance unkritisch (~1500
Checks/Frame in Pure Chaos).

## Speedrun-Mode

Aus dem Tube-Generator ließe sich ein eigener vierter Practice-Mode
"Speedrun" bauen — gleicher Schlauch, aber mit Ziellinie statt Teleport-
zurück, Timer beim ersten Schub-Input, Best-Time pro Layout persistiert.

**Skizze der Mechanik:**
- Tube-Layout wird beim Start des Modus gewürfelt; bleibt persistent bis
  der Player den Modus verlässt
- Timer startet auf erstem `onThrust(true)`
- Stoppt sobald `ship.x > worldWidth - 100`
- Bei Tod: Attempt verworfen, Reset zum Start, Timer auf 0
- Bei Zielerreichung: Vergleich gegen Best-Time, "NEW BEST"-Anzeige bei
  Verbesserung, dann nächster Run mit gleichem Layout

**Persistenz:** `Map<Long, Float>` (Seed → bestTime) in eigenem DataStore.
Damit kann der Player Lieblings-Layouts durchspeedrunnen und sich
verbessern, statt zufällig leichte Strecken zu farmen.

**HUD:** aktueller Run-Timer, Best-Time darunter — beide compact.
