# ADR-0001: Two-module build

- Status: accepted
- Date: 2026-10-15

## Context

The Hytale server API is proprietary and only available from Hypixel's Maven repository. Most of
Haritone (search, rules, sessions, settings, persistence) does not need it. Tests that need a
running game server cannot run in CI.

## Decision

Split the code into `core` (pure Java, no dependencies, all logic and tests) and `plugin` (thin
adapters compiled against `com.hypixel.hytale:Server` as `compileOnly`). The plugin jar bundles
`core`'s classes. The `verifyPluginJar` task fails the build if any `com/hypixel/` class is
bundled.

## Consequences

- Core logic is tested quickly and deterministically, with fixtures instead of a server.
- The module boundary guarantees no Hytale types leak into the engine, so a future integration
  (another game or an NPC companion) can reuse it.
- Adapter code has no unit tests. It is covered by the real-server checks in docs/TESTING.md and
  kept as thin as possible.
