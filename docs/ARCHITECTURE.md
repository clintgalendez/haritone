# Architecture

Haritone is two Gradle modules: a game-independent navigation engine (`core`) and a thin Hytale
adapter (`plugin`). The release jar bundles both. It never bundles any Hytale code.

```
core/  io.github.clintgalendez.haritone            (pure Java 25, no dependencies)
  api/          BlockPos, Goal + Goals.*, NavigationStatus, NavigationListener
  world/        BlockView (read-only world), Terrain, BoundedTerrainCache
  pathfinding/  PathSearch (resumable A*), MoveEvaluator (movement rules), NodeHeap,
                CostModel, PathSettings, Path, PathStep, PathResult, UnknownTerrainPolicy
  navigation/   Navigator (sessions), NavigationSession (state machine), Guidance,
                BlockSearch, GuideSettings
  config/       Settings (server, validated), PlayerPrefs (per player)
  command/      CommandService (all command logic), Permissions
  persist/      PlayerDataStore, Waypoint

plugin/ io.github.clintgalendez.haritone.plugin     (compileOnly com.hypixel.hytale:Server)
  HaritonePlugin     lifecycle: config, registry wiring, ticker start/stop
  HaritoneConfig     config.json via the server's BuilderCodec
  HytaleBlockView    SectionReader + BlockType/Fluid assets -> Terrain
  WorldTicker        fixed-rate scheduler -> world.execute(...) per world
  HaritoneCommand    /haritone subcommands -> CommandService
  RouteDiagnostic    /haritone route (admin/console): load chunks, plan, report
```

Runtime flow:

1. A player runs `/haritone goto 120 64 -30`. `AbstractPlayerCommand` executes on the player's
   world thread.
2. `HaritoneCommand` reads the arguments and the player's position and calls
   `CommandService.gotoBlock`. That method validates bounds and distance, loads the player's
   preferences, builds `PathSettings` and `GuideSettings`, and calls `Navigator.start`. The
   navigator enforces one session per player and a cap on active sessions server-wide.
3. Every `TickIntervalMs` (default 100 ms), `WorldTicker` runs on the scheduler thread. It
   groups active sessions by world and queues one task on each world's thread. A world whose
   previous task is still queued is skipped.
4. On the world thread, each session receives the live `HytaleBlockView`, the player's position,
   and a share of `PlanningBudgetMs` (default 4 ms per world per tick).
   - **PLANNING** advances a `PathSearch` within that budget. When the search finishes, the
     session moves to **NAVIGATING** and sends the first instruction.
   - **NAVIGATING** tracks the player's progress along the route, re-checks the upcoming steps
     against the live world, speaks when the next leg changes, and replans when the player
     leaves the route or the route is blocked. At the end of a partial route it plans the
     next segment. When the goal is reached it moves to **COMPLETED**.
5. Disconnecting, changing worlds, `/haritone stop`, a new goal, or plugin shutdown cancels the
   session (**CANCELLED**). Running out of replans, segments, or planning time, or having no
   route, fails it (**FAILED**).

## Movement model

`MoveEvaluator` defines where an entity may stand and how standing positions connect. The
rules are original to Haritone (see the clean-room note in
[TECHNICAL_RESEARCH.md](TECHNICAL_RESEARCH.md)).

- **Body:** two blocks tall (feet and head) and one wide. Partial blocks count as full blocks.
- **Standable:** both body blocks are passable (`OPEN`, `WATER`, `CLIMBABLE`) or, with
  break-aware planning, breakable `SOLID` blocks. Nothing in the body is `DANGER`. The entity
  is supported by `SOLID` or `CLIMBABLE` below, or its feet are in `WATER` or `CLIMBABLE`.
- **Moves:**

  | Move | Cost |
  |---|---|
  | Traverse (4 directions) | 1 |
  | Diagonal (4 directions, no corner cutting) | √2 |
  | Ascend one block (needs headroom) | 1 + `ascend` |
  | Descend or fall of up to `maxFallHeight` | 1 + `descendPerBlock` × drop |
  | Climb up or down | `climbUp` / `climbDown` |
  | Swim up or down | `water` |

- **Surcharges:** a move that ends in water is multiplied by `water`. Each block broken adds
  `breakBlock`. Standing next to `DANGER` adds `hazardMargin`. Relying on unknown terrain adds
  `unknown`.
- **Heuristic:** octile horizontal distance plus |dy| × `minVerticalCost`, where
  `minVerticalCost` is the cheapest vertical move. That makes the heuristic admissible at
  weight 1. `OptimalityTest` checks the A* cost against Dijkstra on random 3D terrain.
  `HeuristicWeight` above 1 trades optimality for speed.

## Bounds and safety

| Concern | Mechanism |
|---|---|
| Memory per search | `MaxNodes` (default 100k ≈ 12 MB) plus a terrain cache capped at 65,536 entries |
| CPU per search | `SearchTimeoutMs` (default 2 s of compute in total) |
| World-thread load | `PlanningBudgetMs` per world per tick, split between planning sessions |
| Runaway sessions | one session per player, `MaxActiveSessions`, `MaxReplans`, `MaxSegments`, planning-tick limit |
| Unknown terrain | blocked by default (`UnknownTerrain=AVOID`), giving partial paths and segmented trips |
| Destructive actions | none: Haritone never breaks, places or moves anything; break-aware planning is opt-in and permission-gated |
| Stored data | `mods/Haritone_Haritone/players/<uuid>.properties`: waypoints and three preferences; size-capped, validated on load, written atomically |
| Network | none: nothing leaves the server |

## Threading

- `core` takes no locks around world data. Callers must use a `BlockView` on the thread that
  owns the world.
- `Navigator` uses concurrent maps. `NavigationSession` methods are `synchronized`, so commands
  (on the world thread), the ticker, and disconnect events (on other threads) cannot corrupt a
  session.
- `PathSearch.cancel()` is the only `PathSearch` method that is safe to call from any thread.

## Extension API

Other plugins declare `"Haritone:Haritone"` as a dependency, then call
`HaritonePlugin.get().navigator().start(new NavigationSession.Request(...))` with any `Goal`
implementation, and can `addListener` for status changes. The `core` types (`Goal`,
`BlockView`, `PathSearch`, `MoveEvaluator`) are public, so other code can also use the planner
directly. The API is pre-1.0: breaking changes are allowed in minor releases and are listed in
the CHANGELOG.

## Decisions

See [docs/adr](adr):

- [0001](adr/0001-two-module-build.md): two-module build
- [0002](adr/0002-time-sliced-search-on-world-thread.md): time-sliced search on the world thread
- [0003](adr/0003-guide-mode-no-automatic-movement.md): guide mode with no automatic movement
- [0004](adr/0004-unknown-terrain-is-blocked.md): unknown terrain is blocked by default
- [0005](adr/0005-destructive-planning-is-opt-in.md): destructive planning is opt-in
