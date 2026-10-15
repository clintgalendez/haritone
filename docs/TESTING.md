# Testing

Haritone is tested at three levels. Each level verifies different things, and this file says
exactly what each one covers.

| Level | Runs where | What it verifies |
|---|---|---|
| Unit and property tests (`core`) | everywhere, every CI run | search correctness and optimality, movement rules, limits, cancellation, sessions, guidance, settings, commands, persistence |
| Real-server checks (`plugin`) | a machine with the Hytale server | the mod loads, its config is written, commands register, and planning works on real generated terrain through the real block adapter |
| In-game manual checklist | a machine with the game client | what players experience: instructions, permissions, replanning |

Mocked tests are never reported as game integration tests.

## Running the tests

Requirements: a JDK 25 or newer as `JAVA_HOME` (the Gradle wrapper downloads Gradle itself).

```sh
./gradlew build                       # compile (-Werror, Error Prone), Spotless, tests, coverage gate, jar checks
./gradlew :core:test                  # unit tests only
./gradlew :core:test --tests '*Perf*' # performance budgets; numbers are printed to the test report
./gradlew spotlessApply               # fix formatting
```

The coverage reports are in `core/build/reports/jacoco/test/html/`. The build fails if `core`
line coverage drops below 90% or branch coverage below 75%. At the time of writing they are
94.7% and 83.8%.

## Unit test inventory (`core/src/test`)

| Area | Tests |
|---|---|
| `PathSearchTest` | start at goal, straight line, diagonals, wall detour, no corner cutting, enclosed goal (fails, or gives a partial path), step up, headroom, two-block wall, falls within and over the limit, hazard avoidance and margin, hazard-only route fails, water cost trade-off, ladders, unknown terrain (AVOID gives a partial path, ALLOW finds a route), node limit, timeout, cancellation, slicing gives the same result as one shot, invalid start, break-aware tunnelling and unbreakable blocks, weighted heuristic, every goal type, surface finding, start snapping |
| `OptimalityTest` | A* cost equals Dijkstra cost on 20 seeded random 3D terrains (pillars, pits, water, lava) |
| `NodeHeapTest` | ordering, deterministic tie-breaks, 5,000 random inserts and decreases checked against a sort |
| `BlockPosTest` | packing round trip across the full range, distinctness, range checks, flooring |
| `BoundedTerrainCacheTest` | size bound, hit counting, LRU eviction order |
| `NavigationSessionTest` | plan, guide and complete while walking; throttled guidance; plan-only; replanning on deviation; replan limit; replanning when the world changes; segment chaining as terrain loads; block find; block missing; cancel; already at goal; unstandable start; planning timeout; snapshot |
| `NavigatorTest` | session cap, ended sessions do not count, replacement, listeners, forget and cancel-all |
| `GuidanceAndBlockSearchTest` | leg merging and wording, breaks, partial-segment wording, compass, shell scan covers every block once in order, budgeted nearest-match search |
| `SettingsTest` | defaults are valid, every invalid field is reported, NaN rejected, break gating, fall-height cap, preference parsing and round trip |
| `CommandServiceTest` | goto, level and plan; coordinate and distance validation; busy cap; find validation; stop and status; waypoint lifecycle and persistence; break permission and server switch; set, get and reset; player leaving; unreadable data |
| `PlayerDataStoreTest` | round trip with no temp files left, invalid entries skipped and reported, oversized and malformed files ignored, name and world validation |

`GridBlockView` (in test sources) is the deterministic fixture world. It also parses small ASCII
maps.

## Performance budgets

`PathSearchPerfTest` (tag `perf`) runs each search twice and reports the second, warm run.
Budgets in the test are loose on purpose (2 to 5 s) so shared CI runners pass. They catch
large regressions. Measured on the development machine (Windows 11, JDK 26.0.2), 2026-10-15:

| Map | Moves | Nodes expanded | Time |
|---|---|---|---|
| Open field, 256×256, corner to corner | 255 | 256 | 1.6–2.4 ms |
| 20% random pillars, 256×256 | 338 | 19,215 | 210–350 ms |
| Serpentine maze, 129×129 | 8,194 | 8,320 | 38–51 ms |
| 3D sine hills, 200×200 | 281 | 24,547 | 209–267 ms |

In game, planning is spread across ticks (4 ms per world per tick by default), so a search
needing 200 ms of compute finishes in about 5 s of wall-clock time.

## Real-server checks

These need the Hytale server files from a game install, which cannot be redistributed or
downloaded in CI. On Windows they are in
`%APPDATA%\Hytale\install\release\package\game\latest\` (`Server/HytaleServer.jar` and
`Assets.zip`).

```sh
mkdir server-test && cd server-test && mkdir mods
cp ../plugin/build/libs/haritone-*.jar mods/
java -jar <game>/Server/HytaleServer.jar --assets <game>/Assets.zip \
  --auth-mode offline --bind 127.0.0.1:5521 --disable-sentry \
  --boot-command "help haritone" \
  --boot-command "haritone route 0 0 60 40" \
  --boot-command "haritone route -100 30 20 -90"
```

`--auth-mode offline` and a loopback bind keep the test server private. Stop it with Ctrl+C.

**Last run: 2026-10-15, server 0.7.2 (revision d769dc3d), fresh universe.**

- The plugin was discovered (`Haritone:Haritone from path haritone-0.1.0-SNAPSHOT.jar`) and
  enabled (`Enabled plugin Haritone:Haritone`). `mods/Haritone_Haritone/config.json` was written
  with every key.
- `help haritone` printed the full command tree (goto with its `<x> <z>` variant, level, find,
  plan, stop, status, help, wp add/remove/goto/list, set, get, reset, route).
- `route 0 0 60 40` loaded 16 chunks. Start was 0 114 0 (in water), goal 60 117 40 on
  `Soil_Grass`. Result: **FOUND**, 63 moves (23 straight, 37 diagonal, 3 step-ups), 699 nodes
  expanded.
- `route -100 30 20 -90` loaded 36 chunks. Start was on `Soil_Grass`, goal on `Soil_Pathway`.
  Result: **FOUND**, 141 moves (99 diagonal, 8 step-ups, 13 drops), 6,815 nodes in 242 ms.
- A span over 256 blocks was rejected with a clear message.

The `SEVERE … Took too long to run pre-load process hook` lines in the log come from the
server's own world generation. They are not from Haritone.

## In-game manual checklist (not yet run)

This needs a game client. Copy the jar into `%APPDATA%\Hytale\UserData\mods`, enable it for a
world, and check each item. Until someone does, every player-facing behaviour below is
**unverified in game**.

- [ ] `/haritone help` works for a normal (Adventure) player without extra permissions.
- [ ] `/haritone goto <x> <y> <z>` gives "Route found", then "Next: walk N blocks <dir> to …".
- [ ] The compass directions match the in-game compass (Haritone assumes −Z is north).
- [ ] Walking the route updates the instructions when a leg is finished, with no spam while
      standing still.
- [ ] Walking away more than 3 blocks gives "You left the route; replanning." and a new route.
- [ ] Placing a block on the route gives "The route is blocked at …" within about a second.
- [ ] Reaching the goal gives "Arrived at …".
- [ ] `/haritone goto <x> <z>` (column), `level <y>`, `find <blockId> --radius=20`, and `plan`
      all work.
- [ ] `/haritone wp add home`, `wp list`, `wp goto home`, and `wp remove home` work, and
      waypoints survive a restart.
- [ ] `/haritone set allowBreak true` is refused without `haritone.break`.
- [ ] Disconnecting or changing worlds cancels navigation.
- [ ] Ladders, water, and slabs or stairs are handled sensibly.

Record results, with the game version, in an issue or pull request, and update this section.
