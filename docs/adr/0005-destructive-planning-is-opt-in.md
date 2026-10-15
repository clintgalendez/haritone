# ADR-0005: Destructive planning is opt-in

- Status: accepted
- Date: 2026-10-15

## Context

Planning routes that go through blocks is useful (tunnels, short cuts), but on shared servers
breaking blocks can be griefing. Haritone does not break blocks itself, but its instructions
can tell players to.

## Decision

Break-aware planning (`allowBreak`) is off by default and requires all three of:

1. the server switch `AllowBreak=true` in config.json,
2. the `haritone.break` permission (not granted to player groups by default), and
3. the player's own preference (`/haritone set allowBreak true`).

Blocks whose id matches `UnbreakableBlockKeys` are never planned through. Dangerous blocks are
never broken, entered or stood on. Haritone itself never breaks or places blocks.

## Consequences

- Default behaviour cannot cause any world change.
- Server owners keep full control, enforced through the server's own permission system.
