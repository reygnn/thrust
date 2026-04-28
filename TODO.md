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
