# ADR-0004: Unknown terrain is blocked by default

- Status: accepted
- Date: 2026-10-15

## Context

Blocks in unloaded chunks cannot be read. Treating them as open could route players into lava,
cliffs or walls.

## Decision

`UnknownTerrain=AVOID` (the default) treats unknown blocks as impassable. When the goal is
outside known terrain, the search returns a **partial** path to the explored position closest
to the goal. Navigation guides the player there, then plans the next segment from wherever the
player is, with whatever has loaded since. `UnknownTerrain=ALLOW` is an opt-in exploration mode:
unknown blocks count as open and supporting, at an extra cost per step. Routes are still
re-checked as terrain loads.

## Consequences

- Long trips work as a chain of short, verified segments, limited by `MaxSegments`.
- Partial plans say clearly that they are partial ("it ends at …, the closest point known so
  far").
