# Roadmap

Status labels: **Done** (implemented, with automated tests), **Verified on server** (also
checked against the real Hytale server), **Unverified in game** (needs a game client),
**Planned** (not implemented), **Unsupported** (no supported API).

## 0.1.0: guide mode (this release candidate)

| Feature | Status |
|---|---|
| A* planner with the movement rules above (walk, diagonal, step up, drop, climb, swim) | Done, Verified on server |
| Goals: block, column, level, near, next to a block, any-of | Done |
| Configurable costs, heuristic weight, fall height, hazard margin | Done |
| Limits: node count, compute time, per-tick budget, active sessions, replans, segments | Done |
| Unknown terrain policy (avoid gives a partial path; allow for exploring) | Done |
| Partial paths and segmented long-distance trips | Done |
| Guide mode: turn-by-turn instructions, progress tracking, deviation replanning, live re-validation | Done, Unverified in game |
| Status reporting (`/haritone status`), plan-only (`/haritone plan`) | Done, Unverified in game |
| Block targeting (`/haritone find <block>`) | Done, Unverified in game |
| Waypoints (add, list, remove, goto) with persistence | Done, Unverified in game |
| Per-player preferences (`set`, `get`, `reset`) | Done, Unverified in game |
| Break-aware planning (instructions name the blocks to break; nothing is broken automatically) | Done (experimental), Unverified in game |
| Admin/console route diagnostic (`/haritone route`) | Done, Verified on server |
| Extension API: `Navigator`, custom `Goal`, status listeners | Done |
| Cancellation on stop, new goal, disconnect, world change, shutdown | Done (core tested; world-change and disconnect hooks Unverified in game) |

## Next milestone: 0.2.0, in-game hardening

- Run the in-game checklist in [TESTING.md](TESTING.md) and fix what it finds (compass,
  permission groups, partial blocks).
- Route line: particles along the upcoming route are implemented. Check them in game and tune the
  default particle (see TESTING.md).
- Performance: a primitive-keyed node map and section-level terrain reads in `HytaleBlockView`.
- A chunk-section snapshot cache so searches can run off the world thread (see ADR-0002).

## Later

| Feature | Status | Notes |
|---|---|---|
| Automatic movement (teleport stepping or velocity impulses) | Planned | Needs in-game experiments and a server switch; see ADR-0003 |
| Sprint and jump execution | Planned | Depends on automatic movement |
| Opening doors | Planned | The door API (`isDoor`) is deprecated in 0.7.2; needs the interaction API |
| Breaking blocks automatically (`BlockHarvestUtils`) | Planned | Must respect the cancellable `BreakBlockEvent` and tools |
| Placing blocks (pillar up, bridge) | Planned | Needs inventory integration |
| Mining and collection tasks, multi-step goals | Planned | |
| Follow a player or entity | Planned | |
| Persistent terrain cache | Planned | |
| Parkour and gap jumps | Planned | |
| Client-side features (HUD, keybinds) | Unsupported | Hytale supports server-side mods only |
