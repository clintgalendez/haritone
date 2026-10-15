package io.github.clintgalendez.haritone.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.clintgalendez.haritone.api.NavigationStatus;
import io.github.clintgalendez.haritone.command.CommandService.Player;
import io.github.clintgalendez.haritone.command.CommandService.Reply;
import io.github.clintgalendez.haritone.config.Settings;
import io.github.clintgalendez.haritone.navigation.Navigator;
import io.github.clintgalendez.haritone.persist.PlayerDataStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandServiceTest {

  @TempDir Path dir;

  private final List<String> warnings = new ArrayList<>();
  private Navigator navigator;
  private CommandService service;

  private static Player player(UUID id, String... permissions) {
    Set<String> granted = Set.of(permissions);
    return new Player(id, "default", 0.5, 64, 0.5, granted::contains);
  }

  private static Settings settings(boolean allowBreak, int maxWaypoints) {
    Settings d = Settings.defaults();
    return new Settings(
        d.maxNodes(),
        d.searchTimeoutMs(),
        d.partialRouteAfterMs(),
        d.planningBudgetMs(),
        d.tickIntervalMs(),
        2,
        1_000,
        d.maxReplans(),
        d.maxSegments(),
        maxWaypoints,
        32,
        d.unknownTerrain(),
        allowBreak,
        d.maxFallHeight(),
        d.heuristicWeight(),
        d.deviationTolerance(),
        d.guidanceIntervalSeconds(),
        d.routeLineLength(),
        d.routeLineIntervalMs(),
        d.routeLineParticle(),
        d.ascendCost(),
        d.descendCostPerBlock(),
        d.climbUpCost(),
        d.climbDownCost(),
        d.waterCost(),
        d.breakCost(),
        d.hazardMarginCost(),
        d.unknownCost(),
        d.hazardBlockKeys(),
        d.unbreakableBlockKeys());
  }

  @BeforeEach
  void setUp() {
    navigator = new Navigator(2);
    service =
        new CommandService(
            settings(true, 3), navigator, new PlayerDataStore(dir), 0, 319, warnings::add);
  }

  @Test
  void gotoStartsNavigation() {
    Player p = player(UUID.randomUUID());
    Reply reply = service.gotoBlock(p, 10, 64, 10);
    assertTrue(reply.ok());
    assertEquals("Planning a route to block 10 64 10.", reply.lines().getFirst());
    assertEquals(NavigationStatus.PLANNING, navigator.session(p.id()).orElseThrow().status());
    assertTrue(service.gotoColumn(p, 3, 3).ok());
    assertTrue(service.gotoLevel(p, 80).ok());
    assertTrue(service.plan(p, 1, 64, 1).ok());
  }

  @Test
  void rejectsBadCoordinates() {
    Player p = player(UUID.randomUUID());
    assertEquals(
        "y must be between 0 and 319.", service.gotoBlock(p, 0, 400, 0).lines().getFirst());
    assertFalse(service.gotoLevel(p, -1).ok());
    Reply far = service.gotoBlock(p, 5_000, 64, 0);
    assertFalse(far.ok());
    assertEquals("That is 5000 blocks away; the limit is 1000.", far.lines().getFirst());
    assertFalse(service.gotoColumn(p, Integer.MAX_VALUE, 0).ok());
    assertTrue(navigator.session(p.id()).isEmpty(), "nothing started");
  }

  @Test
  void reportsWhenBusy() {
    service.gotoBlock(player(UUID.randomUUID()), 1, 64, 1);
    service.gotoBlock(player(UUID.randomUUID()), 1, 64, 1);
    Reply busy = service.gotoBlock(player(UUID.randomUUID()), 1, 64, 1);
    assertFalse(busy.ok());
    assertTrue(busy.lines().getFirst().startsWith("Navigation is busy"));
  }

  @Test
  void findValidatesRadiusAndKey() {
    Player p = player(UUID.randomUUID());
    assertFalse(service.find(p, "Rock", 0).ok());
    assertFalse(service.find(p, "Rock", 33).ok());
    assertFalse(service.find(p, " ", null).ok());
    Reply ok = service.find(p, "Ore_Iron", null);
    assertTrue(ok.ok());
    assertEquals("Looking for the nearest Ore_Iron within 32 blocks.", ok.lines().getFirst());
  }

  @Test
  void stopAndStatus() {
    Player p = player(UUID.randomUUID());
    assertEquals("Idle: no navigation this session.", service.status(p).lines().getFirst());
    assertFalse(service.stop(p).ok());
    service.gotoBlock(p, 4, 64, 4);
    assertTrue(service.status(p).lines().getFirst().startsWith("Status: PLANNING"));
    assertTrue(service.stop(p).ok());
    assertTrue(service.status(p).lines().getFirst().startsWith("Status: CANCELLED"));
  }

  @Test
  void pauseAndResume() {
    Player p = player(UUID.randomUUID());
    assertEquals("Nothing to pause.", service.pause(p).lines().getFirst());
    assertFalse(service.resume(p).ok());
    service.gotoBlock(p, 4, 64, 4);
    assertTrue(service.pause(p).ok());
    assertTrue(service.status(p).lines().getFirst().startsWith("Status: PLANNING, paused ("));
    assertFalse(service.pause(p).ok());
    assertEquals("Navigation resumed.", service.resume(p).lines().getFirst());
    assertFalse(service.resume(p).ok());
    assertTrue(service.status(p).lines().getFirst().startsWith("Status: PLANNING ("));
    service.pause(p);
    assertTrue(service.stop(p).ok(), "a paused session can be stopped");
    assertFalse(service.resume(p).ok());
  }

  @Test
  void waypointLifecycle() throws Exception {
    UUID id = UUID.randomUUID();
    Player p = player(id);
    assertEquals(
        "No waypoints yet. Save one with /haritone wp add <name>.",
        service.waypointList(p).lines().getFirst());
    assertEquals(
        "Saved waypoint home at 0 64 0.", service.waypointAdd(p, "Home").lines().getFirst());
    assertEquals(
        "Updated waypoint home at 0 64 0.", service.waypointAdd(p, "home").lines().getFirst());
    assertFalse(service.waypointAdd(p, "bad name").ok());
    assertTrue(service.waypointAdd(p, "a").ok());
    assertTrue(service.waypointAdd(p, "b").ok());
    assertEquals(
        "You already have the maximum of 3 waypoints.",
        service.waypointAdd(p, "c").lines().getFirst());
    assertEquals(4, service.waypointList(p).lines().size());

    assertTrue(service.waypointGoto(p, "HOME").ok());
    Player elsewhere = new Player(id, "nether", 0, 64, 0, s -> false);
    assertEquals(
        "Waypoint home is in world default; travel there first.",
        service.waypointGoto(elsewhere, "home").lines().getFirst());
    assertFalse(service.waypointGoto(p, "missing").ok());

    assertTrue(service.waypointRemove(p, "a").ok());
    assertFalse(service.waypointRemove(p, "a").ok());

    // Saved to disk: a fresh service sees the same waypoints.
    assertTrue(Files.exists(dir.resolve(id + ".properties")));
    CommandService fresh =
        new CommandService(
            settings(true, 3), new Navigator(2), new PlayerDataStore(dir), 0, 319, warnings::add);
    assertEquals(3, fresh.waypointList(p).lines().size());
  }

  @Test
  void breakPreferenceNeedsPermissionAndServerSwitch() {
    UUID id = UUID.randomUUID();
    Reply denied = service.set(player(id), "allowBreak", "true");
    assertFalse(denied.ok());
    assertEquals(
        "You need the haritone.break permission to plan through blocks.",
        denied.lines().getFirst());

    Reply granted = service.set(player(id, Permissions.BREAK), "allowBreak", "true");
    assertTrue(granted.ok(), granted.toString());
    assertEquals("allowBreak = true", service.get(player(id), "allowbreak").lines().getFirst());

    CommandService serverOff =
        new CommandService(
            settings(false, 3),
            new Navigator(2),
            new PlayerDataStore(dir.resolve("off")),
            0,
            319,
            warnings::add);
    Reply off = serverOff.set(player(UUID.randomUUID(), Permissions.BREAK), "allowBreak", "on");
    assertEquals("Break-aware planning is disabled on this server.", off.lines().getFirst());
  }

  @Test
  void settingsCommands() {
    Player p = player(UUID.randomUUID());
    assertTrue(service.set(p, "maxFallHeight", "2").ok());
    assertFalse(service.set(p, "maxFallHeight", "99").ok());
    assertFalse(service.set(p, "warp", "1").ok());
    assertEquals(3, service.get(p, null).lines().size());
    assertFalse(service.get(p, "warp").ok());
    assertEquals("maxFallHeight reset.", service.reset(p, "maxfallheight").lines().getFirst());
    assertFalse(service.reset(p, "warp").ok());
    assertEquals("All settings reset.", service.reset(p, null).lines().getFirst());
  }

  @Test
  void playerLeavingCancelsAndForgets() {
    Player p = player(UUID.randomUUID());
    service.gotoBlock(p, 4, 64, 4);
    service.playerLeft(p.id());
    assertTrue(navigator.session(p.id()).isEmpty());
  }

  @Test
  void unreadableDataIsReportedNotThrown() throws Exception {
    UUID id = UUID.randomUUID();
    Files.createDirectories(dir.resolve(id + ".properties")); // a directory where a file belongs
    Reply reply = service.waypointList(player(id));
    assertFalse(reply.ok());
    assertEquals(1, warnings.size());
  }

  @Test
  void helpListsCommands() {
    assertTrue(service.help().lines().size() > 5);
    assertEquals("haritone.command.goto", Permissions.sub("goto"));
  }
}
