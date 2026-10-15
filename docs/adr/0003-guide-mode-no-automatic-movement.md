# ADR-0003: Guide mode, no automatic movement

- Status: accepted (owner decision)
- Date: 2026-10-15

## Context

Hytale modding is server-side only, and player movement is driven by the client. The 0.7.2
API has no documented way for a plugin to drive a player's walk, jump or sprint input (see
docs/TECHNICAL_RESEARCH.md). The only supported ways to change a player's position are
`Teleport` components and `ChangeVelocity` impulses. Neither has been tested in game for smooth,
safe path following, and both carry risks: rubber-banding, fall damage, and looking like a
cheat on multiplayer servers.

## Decision

The first release plans routes and **guides** players: turn-by-turn chat instructions,
progress tracking, replanning, and segment chaining. **It never moves the player.**
Automatic movement is a roadmap item and will be prototyped behind a server switch once it has
been tested in game.

## Consequences

- Every documented feature is real and verifiable. Nothing is faked.
- Haritone works on any server without changing how players move, so it does not interfere
  with anti-cheat or server rules.
- Baritone-style "it walks for you" behaviour is explicitly not available yet. The README and
  the feature matrix say so.
