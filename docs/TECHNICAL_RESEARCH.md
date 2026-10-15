# Technical research

This file records what Haritone relies on, where each fact comes from, how it was checked, and
what is still open. It was written on 2026-10-15 against Hytale server **0.7.2** (release
channel). Re-check it whenever the server API version changes.

How the facts were checked:

- **[Javadoc]** The official API reference at <https://docs.hytale.com/api/> (headed "Hytale
  Server API 0.7.2").
- **[javap]** The official API jar `com.hypixel.hytale:Server:0.7.2` from
  <https://maven.hytale.com/release>, disassembled with `javap`. Its SHA-1 matches the
  repository's published `.sha1` file, and its SHA-256
  (`23f71f03…10aff`) is pinned in `gradle/verification-metadata.xml`. `javap` shows protected
  members, which the Javadoc leaves out.
- **[Server]** The real `HytaleServer.jar` 0.7.2 (revision `d769dc3d`) from a local game
  install, run headless with the Haritone jar in `mods/` (see [TESTING.md](TESTING.md)).
- **[Manual]** The official Hytale Server Manual,
  <https://support.hytale.com/hc/en-us/articles/45326769420827>, updated 2026-10-12.
- **[Community]** Community sources. They are used only as hints, and every API taken from
  them was re-checked with [javap] or [Server].

## Platform

| Fact | Source |
|---|---|
| Modding is server-side only. Hypixel does not plan to support client mods, and the client is compiled C#, not Java. | <https://hytale.com/news/2025/11/hytale-modding-strategy-and-status> |
| The server needs Java 25. The API jar's class files are major version 69 (Java 25). The game launcher ships a Temurin 26.0.2 runtime. | [Manual], [javap], local install |
| The API is published as `com.hypixel.hytale:Server` in `https://maven.hytale.com/release` (latest 0.7.2, 2026-10-14) and `/pre-release`. No sources or Javadoc jars are published. | <https://maven.hytale.com/release/com/hypixel/hytale/Server/maven-metadata.xml> |
| The API jar's license is the "Hypixel Studios Proprietary License". Haritone uses it `compileOnly` and never bundles or commits it. | POM at the URL above |
| Mods go in the server's `mods/` folder, or `UserData/mods` for single player. | [Manual], [Server] |
| Each mod jar has `manifest.json` at its root with `Group`, `Name`, `Version` (semver), `Main`, `ServerVersion` (a semver range), `Dependencies`, `OptionalDependencies`, `LoadBefore`, `DisabledByDefault`, `IncludesAssetPack`, and `SubPlugins`. | [javap] `PluginManifest`; installed mods; [Server] |

## Plugin lifecycle and services ([javap], confirmed by [Server])

- `com.hypixel.hytale.server.core.plugin.JavaPlugin(JavaPluginInit)`. Override the protected
  `setup()`, `start()`, and `shutdown()` methods.
- `PluginBase` provides `getLogger()` (a Flogger-based `HytaleLogger`), `getDataDirectory()`,
  `getCommandRegistry()`, `getEventRegistry()`, `getTaskRegistry()`, `getManifest()`, and
  `getBasePermission()`.
- `withConfig(String, BuilderCodec<T>)` must be called before setup. It returns a
  `server.core.util.Config<T>` with `get()` and `save()`, and creates
  `mods/<Group>_<Name>/config.json`. Checked with [Server], which wrote the file with every key
  at its default.
- `HytaleServer.SCHEDULED_EXECUTOR` is a shared `ScheduledExecutorService`.

## Commands ([javap]; registration confirmed by [Server])

- `AbstractCommandCollection(name, description)` groups subcommands (`addSubCommand`).
- `AbstractPlayerCommand` runs on the sending player's world thread. Implement
  `execute(CommandContext, Store<EntityStore>, Ref<EntityStore>, PlayerRef, World)`.
- `AbstractWorldCommand` runs on a world's thread and accepts an optional `--world` argument.
  Consoles can use it.
- Arguments: `withRequiredArg(name, description, ArgTypes.X)`, `withOptionalArg(...)` (typed on
  the command line as `--name=value`), `addUsageVariant(...)` (the same command with a
  different number of required arguments), and `ArgTypes.INTEGER`, `STRING`, and
  `BLOCK_TYPE_KEY`.
- Permissions: with `requirePermission(node)` on a parent, the server derives each
  subcommand's node as `<parent node>.<subcommand name>` (see `AbstractCommand.generatePermission`).
  `setPermissionGroups(...)` grants a node to groups. Adventure players map to the virtual group
  `hytale:Adventurer` and Creative players to `hytale:WorldEditor` (see
  `AbstractCommand.setPermissionGroup(GameMode)`). `PlayerRef.hasPermission(String)` checks a
  node.

## Events and threading ([javap])

- `EventRegistry.register(Class, Consumer)` handles unkeyed events, including
  `PlayerDisconnectEvent` (an `IEvent<Void>`, with `getPlayerRef()`).
- Each `World` is a `TickingThread` and an `Executor`. `world.execute(Runnable)` runs code on
  the world's thread. Entity and chunk stores are single-threaded; `Store.isInThread()` reports
  whether the caller is on the right thread.
- A player's world is `PlayerRef.getWorldUuid()`, compared against
  `world.getWorldConfig().getUuid()`.

## World access ([javap]; behaviour confirmed by [Server])

- `new SectionReader(world.getChunkStore())` provides `getBlock`, `getFluidId`,
  `hasStorageInMemory`, and `canHoldY`. It never loads chunks. Worlds are 320 blocks tall
  (`ChunkUtil.HEIGHT`) in 32×32 columns.
- `BlockType.getAssetMap().getAsset(id)` gives `getId()`, `getMaterial()` (`BlockMaterial` is
  only `Empty` or `Solid`), `getDamageToEntities()`, and `getMovementSettings().isClimbable()`.
  `isDoor()` is deprecated in 0.7.2. `Fluid.getAssetMap().getAsset(id)` gives `getId()` and
  `getDamageToEntities()`.
- `ChunkStore.getChunkReferenceAsync(ChunkUtil.indexChunkFromBlock(x, z))` loads a chunk. Only
  the admin `route` diagnostic uses it.
- Player position: `store.getComponent(ref, TransformComponent.getComponentType()).getPosition()`
  returns an `org.joml.Vector3d`.
- [Server] showed real block ids such as `Soil_Grass` and `Soil_Pathway` being classified, with
  routes planned across generated hills. See [TESTING.md](TESTING.md).

## Movement: the key limitation

- Players move by client-authoritative input. The client sends `ClientMovement` packets, which
  queue onto `PlayerInput` on the server. **No documented API lets a plugin drive a player's
  walk, jump, or sprint input.**
- The supported ways to change a player's position are the `Teleport` component (an instant
  reposition the client acknowledges) and `ChangeVelocity` packets (impulses). Built-in NPC
  navigation (`server.npc.navigation.AStarBase`, `PathFollower`, `MotionController`) belongs to
  NPC roles, not players.
- **Decision (owner, 2026-10-15):** this release plans and guides only. It never moves the
  player (see [ADR-0003](adr/0003-guide-mode-no-automatic-movement.md)). Teleport stepping and
  velocity impulses are candidates for a later milestone, once they have been tested in game.

## Breaking, placing, inventory ([javap]; not used yet)

- `BlockHarvestUtils.performBlockBreak(...)` fires a cancellable `BreakBlockEvent`.
  `BlockPlaceUtils.placeBlock(...)` places blocks.
- `LivingEntity.getInventory()` and `Inventory.getHotbar()`/`getItemInHand()` give inventory
  access.
- Haritone uses none of these yet. Break-aware **planning** only tells players which blocks
  they would have to break.

## Baritone (functional reference only)

- Repository: <https://github.com/cabaletta/baritone>. License: **LGPL-3.0**. The "anime
  exception" image in the README is not a legal exception.
- Ideas taken only from its public documentation (`README.md`, `FEATURES.md`, `USAGE.md`):
  - A* over block positions with movement types (traverse, diagonal, ascend, descend or fall,
    pillar, parkour).
  - Goal types (block, XZ, Y level, near, composite).
  - Partial paths when the goal cannot be reached, with segments chained for long trips.
  - Settings such as allow-break and maximum fall height, and per-move costs.
- Clean-room rule: no Baritone source, documentation text, setting descriptions, or constant
  tables were copied or translated. Haritone's movement rules, cost model, and defaults were
  derived independently and are documented in [ARCHITECTURE.md](ARCHITECTURE.md). Linking
  Baritone is not involved, so its LGPL obligations do not apply.

## Tooling versions (checked 2026-10-15)

Gradle 9.8.1 (the wrapper jar's SHA-256 matches services.gradle.org), Spotless 8.10.4 (with its
default google-java-format), Error Prone 2.50.0, JUnit 6.1.3, JaCoCo 0.8.15, and CycloneDX
Gradle plugin 3.5.1. Every GitHub Action used in a workflow is pinned to a commit SHA taken from
`git ls-remote` on that date.

## Open questions (need someone with a game client)

1. Does `hytale:Adventurer`/`hytale:WorldEditor` give ordinary players the command nodes by
   default on a real multiplayer server?
2. Compass wording assumes −Z is north. Check it against the in-game compass.
3. Water, ladder, and vine handling, and the partial-block assumption (slabs and stairs count
   as full blocks), need in-game confirmation.
4. Can teleport stepping or velocity impulses move players acceptably? This decides the
   auto-movement milestone.
