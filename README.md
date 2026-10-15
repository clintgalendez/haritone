# Haritone

[![CI](https://github.com/clintgalendez/haritone/actions/workflows/ci.yml/badge.svg)](https://github.com/clintgalendez/haritone/actions/workflows/ci.yml)
[![CodeQL](https://github.com/clintgalendez/haritone/actions/workflows/codeql.yml/badge.svg)](https://github.com/clintgalendez/haritone/actions/workflows/codeql.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/clintgalendez/haritone/badge)](https://scorecard.dev/viewer/?uri=github.com/clintgalendez/haritone)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

An open-source route planner and navigation guide for Hytale servers, inspired by Baritone's
pathfinding.

> **What Haritone does today:** it plans routes through real Hytale terrain and **guides
> players along them** with turn-by-turn instructions. It tracks progress, replans when the
> player leaves the route or the world changes, and chains route segments for long trips.
>
> **What it does not do:** it **does not move players**. Hytale's server API has no supported
> way to drive a player's movement input (see [ADR-0003](docs/adr/0003-guide-mode-no-automatic-movement.md)).
> Automatic movement is on the [roadmap](docs/ROADMAP.md).

## Features

| Feature | Status |
|---|---|
| A* route planning: walk, diagonal (no corner cutting), step up, drop, ladders, swimming | ✅ Implemented, checked on a real 0.7.2 server |
| Goals: block, column, height, nearest block of a type, waypoint | ✅ Implemented |
| Hazard avoidance (damaging blocks and fluids, lava and fire) with a safety margin | ✅ Implemented |
| Unknown or unloaded terrain treated as blocked; long trips planned in segments | ✅ Implemented |
| Turn-by-turn guidance, progress tracking, replanning, live route re-checks | ✅ Implemented, ⚠️ not yet tested in game |
| Route line: particles along the next steps, shown only to the navigating player | ✅ Implemented, ⚠️ not yet tested in game |
| Waypoints and per-player preferences, saved on the server | ✅ Implemented, ⚠️ not yet tested in game |
| Limits on search time, memory, per-tick server load, and active sessions | ✅ Implemented |
| Break-aware planning (tells players which blocks to break; opt-in and permission-gated) | 🧪 Experimental |
| Admin/console route diagnostic | ✅ Implemented, checked on a real 0.7.2 server |
| Extension API (custom goals, status listeners) | ✅ Implemented, pre-1.0 |
| Automatic movement, sprinting, jumping, doors, mining, placing blocks | 📅 Planned |
| Client-side HUD or keybinds | ⛔ Unsupported: Hytale supports server-side mods only |

[docs/TESTING.md](docs/TESTING.md) lists exactly what has been verified and how.

## Compatibility

| Haritone | Hytale server | Java |
|---|---|---|
| 0.1.x | 0.7.2 (manifest range `>=0.7.2 <0.8.0`) | 25 (required by the server); CI also tests on 26 |

## Installation

1. Download `haritone-<version>.jar` from [Releases](https://github.com/clintgalendez/haritone/releases).
   To verify it:
   ```sh
   sha256sum -c SHA256SUMS
   gh attestation verify haritone-<version>.jar --repo clintgalendez/haritone
   ```
2. Copy it into your server's `mods/` folder (for single player, `%APPDATA%\Hytale\UserData\mods`).
3. Start the server. You should see `Enabled plugin Haritone:Haritone` in the log, and
   `mods/Haritone_Haritone/config.json` is created.

## Quick start

```text
/haritone goto 120 64 -30     guide me to that block (feet position)
/haritone status              where am I on the route?
/haritone wp add home         save this spot
/haritone wp goto home        guide me back later
/haritone stop                stop guiding
```

You will see messages like:

```text
[Haritone] Route found: 141 moves, about 191 blocks.
[Haritone] Next: step up N to -100 123 29, then walk 2 blocks N to -100 123 27. 191 blocks left.
```

Walk along the route. Instructions update as you finish each leg.

## Commands

The optional arguments use the server's `--name=value` form.

| Command | Permission node | What it does |
|---|---|---|
| `/haritone goto <x> <y> <z>` | `haritone.command.goto` | Guide to a block (feet position). |
| `/haritone goto <x> <z>` | `haritone.command.goto` | Guide to a column at any height. |
| `/haritone level <y>` | `haritone.command.level` | Guide to a height. |
| `/haritone find <blockId> [--radius=N]` | `haritone.command.find` | Find the nearest block of a type and guide you next to it. |
| `/haritone plan <x> <y> <z>` | `haritone.command.plan` | Plan and report a route without guiding. |
| `/haritone stop` | `haritone.command.stop` | Stop navigating. |
| `/haritone status` | `haritone.command.status` | Progress, segments, replans, and search statistics. |
| `/haritone wp add\|remove\|goto <name>`, `/haritone wp list` | `haritone.command.wp.*` | Waypoints in your current world (names: letters, digits, `_`, `-`; up to 32 characters). |
| `/haritone set <key> <value>`, `get [--key=k]`, `reset [--key=k]` | `haritone.command.set/get/reset` | Your settings: `allowBreak`, `maxFallHeight`, `guidanceInterval` (seconds). |
| `/haritone help` | `haritone.command.help` | Command summary. |
| `/haritone route <x1> <z1> <x2> <z2> [--world=name]` | `haritone.command.route` (admin) | Diagnostic: load chunks and plan between two columns. Works from the console. |

The player commands are granted to the server's Adventure and Creative player groups
(`hytale:Adventurer`, `hytale:WorldEditor`), because guidance only sends chat messages. `route`
and `haritone.break` (needed for `set allowBreak true`) are not granted by default.

## Configuration

`mods/Haritone_Haritone/config.json` is written with defaults on first start. If any value is
invalid, Haritone logs every problem and runs with **all defaults** until you fix the file.

| Key | Default | Range | Meaning |
|---|---|---|---|
| `MaxNodes` | 100000 | 1,000–5,000,000 | Positions one search may track; this bounds memory |
| `SearchTimeoutMs` | 2000 | 50–60,000 | Total compute time one search may use |
| `PartialRouteAfterMs` | 250 | 0–60,000 | After this much compute, a long search starts you on the best partial route (once it gets at least 16 blocks closer) and plans the rest in segments; 0 waits for the full route |
| `PlanningBudgetMs` | 8 | 1–50 | Planning time per world per tick, shared by the sessions that are planning |
| `TickIntervalMs` | 100 | 50–1,000 | How often sessions update |
| `MaxActiveSessions` | 32 | 1–1,000 | Players navigating at once |
| `MaxGoalDistance` | 10000 | 16–1,000,000 | Farthest horizontal goal |
| `MaxReplans` / `MaxSegments` | 20 / 500 | | Give-up limits per trip |
| `MaxWaypoints` | 64 | 0–1,000 | Waypoints per player |
| `BlockSearchRadius` | 48 | 1–128 | Largest `find` radius |
| `UnknownTerrain` | `AVOID` | `AVOID` or `ALLOW` | How unloaded blocks are treated |
| `AllowBreak` | `false` | | Server switch for break-aware planning |
| `MaxFallHeight` | 3 | 0–64 | Largest planned drop |
| `HeuristicWeight` | 1.0 | 1–10 | Above 1 is faster but gives less optimal routes |
| `DeviationTolerance` | 3.0 | 1–64 | Blocks off the route before replanning |
| `GuidanceIntervalSeconds` | 4 | 1–600 | How often an unchanged instruction repeats |
| `RouteLineLength` | 16 | 0–64 | Route steps ahead marked with particles; 0 turns the route line off |
| `RouteLineIntervalMs` | 1000 | 100–5,000 | How often the route line is redrawn; keep it below the particle's lifetime |
| `RouteLineParticle` | `Block_Top_Glow` | | Particle system id for the route line. The game client hides this default beyond about 10 blocks |
| `AscendCost`, `DescendCostPerBlock`, `ClimbUpCost`, `ClimbDownCost` | 1.0, 0.5, 1.5, 1.0 | > 0 | Movement costs (one flat block = 1) |
| `WaterCost` | 2.0 | ≥ 1 | Multiplier for moving through water |
| `BreakCost`, `HazardMarginCost`, `UnknownCost` | 4.0, 2.0, 1.0 | ≥ 0 | Surcharges |
| `HazardBlockKeys` | `["lava", "fire"]` | | Block or fluid ids containing these words are dangerous, in addition to anything that damages entities |
| `UnbreakableBlockKeys` | `[]` | | Never planned through, even with breaking on |

Data stored per player: waypoints and three preferences in
`mods/Haritone_Haritone/players/<uuid>.properties`. Nothing is sent off the server.

## Building from source

Requirements: JDK 25 or newer as `JAVA_HOME`. Gradle is downloaded by the wrapper, and the
Hytale API comes from Hypixel's Maven repository.

```sh
./gradlew build
# -> plugin/build/libs/haritone-<version>.jar
```

See [docs/TESTING.md](docs/TESTING.md) for tests, performance budgets, and how to check against
a real server.

## Troubleshooting

- **"you are not standing anywhere a route can start from":** you are mid-air or inside a
  block. Step onto solid ground and try again.
- **"partial: it ends at …":** the goal is outside loaded terrain, or the full route was taking
  longer than `PartialRouteAfterMs`. Walk the segment: the next one is planned while you walk
  it.
- **"Navigation is busy":** the server's `MaxActiveSessions` limit is reached.
- **Commands are unknown to players:** grant `haritone.command` (or the specific nodes) with the
  server's permission tools.
- **Planning is slow:** raise `PlanningBudgetMs` a little, or `HeuristicWeight` (for example to
  1.5). Configs written by Haritone 0.1.0-rc.4 or earlier keep `PlanningBudgetMs` at the old
  default of 4; set it to 8 to match the current default.

## Documentation

[Architecture](docs/ARCHITECTURE.md) · [Decisions](docs/adr) · [Technical research](docs/TECHNICAL_RESEARCH.md) ·
[Testing](docs/TESTING.md) · [Roadmap](docs/ROADMAP.md) · [Maintaining](docs/MAINTAINING.md) · [Changelog](CHANGELOG.md)

## Contributing, security, support

Contributions are welcome through forks and pull requests. See [CONTRIBUTING.md](CONTRIBUTING.md)
and the [Code of Conduct](CODE_OF_CONDUCT.md). Report vulnerabilities privately as described in
[SECURITY.md](SECURITY.md). For help, see [SUPPORT.md](SUPPORT.md).

## License and notices

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE). Haritone is an independent
project, not affiliated with Hypixel Studios or the Baritone project. Baritone (LGPL-3.0)
inspired the feature set only; none of its code or text is included. The Hytale server API is
used at compile time and is not redistributed.
