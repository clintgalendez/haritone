# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0-rc.5] - 2026-10-15

### Added

- The next route segment is planned while you walk the current one, starting 64 steps before
  its end, so long trips no longer stop at each segment end to plan.
- `PartialRouteAfterMs` (default 250): after this much compute, a long search starts you on
  its best partial route once that gets at least 16 blocks closer, and plans the rest in
  segments. Set it to 0 to always wait for the full route.

### Changed

- Route planning needs 3 to 6 times less compute on real terrain, with the same routes. The
  terrain cache stores one byte per block in 16×16×16 sections instead of a boxed LRU map, the
  search's node map no longer boxes keys, a repeated stand check per move was removed, and
  Hytale blocks are read through a section cursor.
- `PlanningBudgetMs` defaults to 8 instead of 4, and is shared by sessions planning a route or
  their next segment. Existing config files keep their value; set it to 8 to match.

## [0.1.0-rc.4] - 2026-10-15

### Added

- Route line: while navigating, particles mark the next steps of the route, so players can
  follow a line on the ground toward the target. Only the navigating player sees it. Set
  `RouteLineLength`, `RouteLineIntervalMs`, and `RouteLineParticle` in the config;
  `RouteLineLength: 0` turns it off. Not yet checked in game.

## [0.1.0-rc.3] - 2026-10-15

### Fixed

- `NavigationSessionTest` no longer assumes one planning slice finishes a plan, so it
  passes on slow CI runners.

## [0.1.0-rc.2] - 2026-10-15 [NOT RELEASED]

The release build failed on the flaky test fixed in rc.3.

### Fixed

- Release workflow: keep the SBOM when the reproducibility check rebuilds the jar. The
  `v0.1.0-rc.1` tag never produced a release because of this.

## [0.1.0-rc.1] - 2026-10-15 [NOT RELEASED]

First release candidate. Guide mode only: Haritone plans routes and guides players; it never
moves them. Player-facing behaviour has not yet been checked in game (see docs/TESTING.md).

### Added

- Navigation engine (`core`):
  - resumable, time-sliced A* with walking, diagonal (no corner cutting), step-up, drop,
    ladder, and swimming moves;
  - configurable costs and heuristic weight;
  - hazard avoidance with a safety margin;
  - limits on node count, compute time, and cache size;
  - an unknown-terrain policy and partial paths.
- Goals: block, near, column, level, next to a block, any-of.
- Guide mode:
  - turn-by-turn instructions with straight and diagonal runs merged into legs;
  - progress tracking and replanning when the player leaves the route;
  - live re-checks of the route against the world;
  - segment chaining for long trips;
  - plan-only mode and status reporting.
- Block targeting (`find`), waypoints, and per-player preferences, saved per player.
- Break-aware planning (experimental). It needs a server switch, a permission, and a player
  preference, and it never breaks blocks itself.
- Hytale 0.7.2 integration:
  - plugin lifecycle and `config.json` through the server's codec;
  - a block and fluid classifier;
  - a per-world ticker with a planning budget;
  - `/haritone` commands;
  - cancellation on disconnect, world change, and shutdown;
  - the admin `route` diagnostic.
- Build and CI:
  - Gradle 9.8.1 wrapper, compile with `-Werror` and Error Prone, Spotless, a JaCoCo gate;
  - dependency checksum verification, a CycloneDX SBOM, a reproducible jar, and jar integrity
    checks;
  - workflows for CI, CodeQL, OpenSSF Scorecard, dependency submission, and gated releases.
- Project documentation, governance files, and issue and PR templates.

[Unreleased]: https://github.com/clintgalendez/haritone/compare/v0.1.0-rc.5...HEAD
[0.1.0-rc.5]: https://github.com/clintgalendez/haritone/releases/tag/v0.1.0-rc.5
[0.1.0-rc.4]: https://github.com/clintgalendez/haritone/releases/tag/v0.1.0-rc.4
[0.1.0-rc.3]: https://github.com/clintgalendez/haritone/releases/tag/v0.1.0-rc.3
[0.1.0-rc.2]: https://github.com/clintgalendez/haritone/releases/tag/v0.1.0-rc.2
[0.1.0-rc.1]: https://github.com/clintgalendez/haritone/releases/tag/v0.1.0-rc.1
