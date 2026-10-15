package io.github.clintgalendez.haritone.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.api.Goal;
import io.github.clintgalendez.haritone.api.Goals;
import io.github.clintgalendez.haritone.api.NavigationStatus;
import io.github.clintgalendez.haritone.pathfinding.PathSettings;
import io.github.clintgalendez.haritone.pathfinding.PathStep;
import io.github.clintgalendez.haritone.world.GridBlockView;
import io.github.clintgalendez.haritone.world.Terrain;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NavigationSessionTest {

  private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final long BUDGET = 50_000_000L;

  private final List<String> transitions = new ArrayList<>();

  private NavigationSession session(Goal goal, PathSettings path, GuideSettings guide) {
    return new NavigationSession(
        new NavigationSession.Request(PLAYER, "world", goal, null, false, path, guide),
        (player, from, to, detail) -> transitions.add(from + "->" + to));
  }

  private NavigationSession session(Goal goal) {
    return session(goal, PathSettings.defaults(), GuideSettings.defaults());
  }

  /**
   * One tick at {@code pos}. A tick that starts while planning keeps ticking until planning ends,
   * so results never depend on how much a given machine can plan within one budget slice.
   */
  private static List<String> tickAt(NavigationSession s, GridBlockView view, BlockPos pos) {
    boolean planning = s.status() == NavigationStatus.PLANNING;
    List<String> messages = new ArrayList<>();
    int ticks = 0;
    do {
      messages.addAll(s.tick(pos.x() + 0.5, pos.y(), pos.z() + 0.5, view, BUDGET));
    } while (planning && s.status() == NavigationStatus.PLANNING && ++ticks < 10_000);
    return messages;
  }

  /** Walks the player along the current path, one step per tick, collecting messages. */
  private static List<String> walk(NavigationSession s, GridBlockView view) {
    List<String> messages = new ArrayList<>();
    for (PathStep step : s.path().steps()) {
      messages.addAll(tickAt(s, view, step.pos()));
      if (s.status() != NavigationStatus.NAVIGATING) {
        break;
      }
    }
    return messages;
  }

  @Test
  void plansGuidesAndCompletesAsThePlayerWalks() {
    GridBlockView view = GridBlockView.flat(30);
    BlockPos start = new BlockPos(0, 1, 0);
    NavigationSession s = session(new Goals.AtBlock(new BlockPos(20, 1, 0)));
    assertEquals(NavigationStatus.PLANNING, s.status());

    List<String> first = tickAt(s, view, start);
    assertEquals(NavigationStatus.NAVIGATING, s.status());
    assertTrue(first.getFirst().startsWith("Route found: 20 moves"), first.toString());
    assertTrue(first.get(1).startsWith("Next: walk 20 blocks E to 20 1 0"), first.toString());

    List<String> rest = walk(s, view);
    assertEquals(NavigationStatus.COMPLETED, s.status());
    assertEquals("Arrived at block 20 1 0.", rest.getLast());
    assertEquals(
        List.of("IDLE->PLANNING", "PLANNING->NAVIGATING", "NAVIGATING->COMPLETED"), transitions);
  }

  @Test
  void routeAheadStartsAtThePlayerAndEndsWithTheSession() {
    GridBlockView view = GridBlockView.flat(30);
    NavigationSession s = session(new Goals.AtBlock(new BlockPos(20, 1, 0)));
    assertEquals(List.of(), s.routeAhead(5), "nothing to draw while planning");

    tickAt(s, view, new BlockPos(0, 1, 0));
    List<BlockPos> line = s.routeAhead(5);
    assertEquals(6, line.size());
    assertEquals(new BlockPos(0, 1, 0), line.getFirst());
    assertEquals(new BlockPos(5, 1, 0), line.getLast());

    for (int x = 1; x <= 17; x++) {
      tickAt(s, view, new BlockPos(x, 1, 0));
    }
    assertEquals(
        List.of(
            new BlockPos(17, 1, 0),
            new BlockPos(18, 1, 0),
            new BlockPos(19, 1, 0),
            new BlockPos(20, 1, 0)),
        s.routeAhead(5),
        "follows the player and stops at the end of the route");

    s.cancel("test");
    assertEquals(List.of(), s.routeAhead(5));
  }

  @Test
  void pauseFreezesTheSessionUntilResumed() {
    GridBlockView view = GridBlockView.flat(30);
    BlockPos start = new BlockPos(0, 1, 0);
    NavigationSession s = session(new Goals.AtBlock(new BlockPos(20, 1, 0)));
    assertTrue(s.isSearching());
    assertTrue(s.pause());
    assertFalse(s.isSearching(), "a paused session takes no planning budget");
    assertTrue(s.tick(0.5, 1, 0.5, view, BUDGET).isEmpty());
    assertEquals(NavigationStatus.PLANNING, s.status(), "a paused session does not plan");
    assertTrue(s.resume());

    tickAt(s, view, start);
    assertEquals(NavigationStatus.NAVIGATING, s.status());
    assertTrue(s.pause());
    assertFalse(s.pause(), "already paused");
    long ticks = s.snapshot().ticks();
    for (int i = 0; i < 5; i++) {
      assertTrue(tickAt(s, view, new BlockPos(0, 1, 20)).isEmpty());
    }
    assertEquals(ticks, s.snapshot().ticks(), "timers stand still while paused");
    assertTrue(s.snapshot().paused());
    assertEquals(List.of(), s.routeAhead(5), "no route line while paused");

    assertTrue(s.resume());
    assertFalse(s.resume(), "not paused");
    List<String> messages = tickAt(s, view, start);
    assertTrue(
        messages.getFirst().startsWith("Next: walk 20 blocks E"),
        "resuming repeats the instruction: " + messages);
    assertEquals(6, s.routeAhead(5).size());
    assertEquals(NavigationStatus.NAVIGATING, s.status(), "the route was kept");
    assertEquals(0, s.snapshot().replans());

    s.cancel("test");
    assertFalse(s.pause());
    assertFalse(s.resume());
    assertEquals(
        List.of("IDLE->PLANNING", "PLANNING->NAVIGATING", "NAVIGATING->CANCELLED"),
        transitions,
        "pausing fires no transitions");
  }

  @Test
  void guidanceIsThrottledButAnnouncesNewLegs() {
    GridBlockView view =
        GridBlockView.parse(
            "S.........", //
            "#########.", //
            ".........G");
    view.goal = new BlockPos(9, 1, 2);
    NavigationSession s = session(new Goals.AtBlock(view.goal));
    tickAt(s, view, view.start);
    // Standing still repeats nothing until the reminder interval passes.
    for (int i = 0; i < 5; i++) {
      assertTrue(tickAt(s, view, view.start).isEmpty());
    }
    List<String> messages = walk(s, view);
    assertTrue(
        messages.stream().anyMatch(m -> m.startsWith("Next: walk 2 blocks S")),
        "turning the corner announces the new leg: " + messages);
  }

  @Test
  void planOnlyReportsAndCompletes() {
    GridBlockView view = GridBlockView.flat(10);
    NavigationSession s =
        new NavigationSession(
            new NavigationSession.Request(
                PLAYER,
                "world",
                new Goals.AtBlock(new BlockPos(5, 1, 5)),
                null,
                true,
                PathSettings.defaults(),
                GuideSettings.defaults()),
            null);
    List<String> messages = tickAt(s, view, new BlockPos(0, 1, 0));
    assertEquals(NavigationStatus.COMPLETED, s.status());
    assertTrue(messages.getLast().startsWith("Planned 5 moves to 5 1 5"));
  }

  @Test
  void replansWhenThePlayerLeavesTheRoute() {
    GridBlockView view = GridBlockView.flat(40);
    NavigationSession s = session(new Goals.AtBlock(new BlockPos(30, 1, 0)));
    tickAt(s, view, new BlockPos(0, 1, 0));
    BlockPos detour = new BlockPos(5, 1, 20);
    List<String> messages = new ArrayList<>();
    for (int i = 0; i < GuideSettings.defaults().offRouteGraceTicks(); i++) {
      messages.addAll(tickAt(s, view, detour));
    }
    assertTrue(messages.contains("You left the route; replanning."), messages.toString());
    assertEquals(NavigationStatus.PLANNING, s.status());
    tickAt(s, view, detour);
    assertEquals(NavigationStatus.NAVIGATING, s.status());
    assertEquals(detour, s.path().start(), "new route starts where the player is");
    assertEquals(1, s.snapshot().replans());
  }

  @Test
  void failsAfterTooManyReplans() {
    GridBlockView view = GridBlockView.flat(40);
    GuideSettings guide = new GuideSettings(30, 3, 1, 5, 12, 1, 10, 600);
    NavigationSession s =
        session(new Goals.AtBlock(new BlockPos(30, 1, 0)), PathSettings.defaults(), guide);
    tickAt(s, view, new BlockPos(0, 1, 0));
    tickAt(s, view, new BlockPos(0, 1, 20)); // leave: replan 1
    tickAt(s, view, new BlockPos(0, 1, 20)); // plan again from here
    List<String> messages = tickAt(s, view, new BlockPos(20, 1, 39)); // leave again
    assertEquals(NavigationStatus.FAILED, s.status());
    assertTrue(messages.getLast().contains("replan limit"), messages.toString());
  }

  @Test
  void replansWhenTheWorldBlocksTheRoute() {
    GridBlockView view = GridBlockView.flat(20);
    view.fill(0, 1, 5, 18, 2, 5, Terrain.SOLID); // wall with a gap at x = 19
    NavigationSession s = session(new Goals.AtBlock(new BlockPos(10, 1, 10)));
    tickAt(s, view, new BlockPos(10, 1, 0));
    assertEquals(NavigationStatus.NAVIGATING, s.status());
    // Someone builds a wall right in front of the player.
    view.fill(0, 1, 1, 19, 2, 1, Terrain.SOLID);
    List<String> messages = new ArrayList<>();
    for (int i = 0; i < GuideSettings.defaults().revalidateEveryTicks(); i++) {
      messages.addAll(tickAt(s, view, new BlockPos(10, 1, 0)));
    }
    assertTrue(
        messages.stream().anyMatch(m -> m.startsWith("The route is blocked at")),
        messages.toString());
  }

  @Test
  void chainsSegmentsAsUnknownTerrainLoads() {
    GridBlockView view = GridBlockView.flat(40);
    view.fill(16, 0, 0, 39, 319, 39, Terrain.UNKNOWN);
    NavigationSession s = session(new Goals.AtBlock(new BlockPos(30, 1, 0)));
    List<String> first = tickAt(s, view, new BlockPos(0, 1, 0));
    assertTrue(first.getFirst().contains("partial"), first.toString());
    assertEquals(new BlockPos(15, 1, 0), s.path().end());

    // The player walks; meanwhile the rest of the world loads.
    view.fill(16, 0, 0, 39, 0, 39, Terrain.SOLID).fill(16, 1, 0, 39, 319, 39, Terrain.OPEN);
    List<String> messages = walk(s, view);
    assertTrue(
        messages.stream().anyMatch(m -> m.startsWith("Next segment: 15 moves")),
        "the next segment was planned ahead, so it starts without a pause: " + messages);
    assertFalse(messages.contains("End of this segment; planning the next one."));
    assertEquals(NavigationStatus.NAVIGATING, s.status());
    assertTrue(s.path().reachesGoal());
    walk(s, view);
    assertEquals(NavigationStatus.COMPLETED, s.status());
    assertEquals(2, s.snapshot().segments());
  }

  @Test
  void aNextSegmentStillPlanningAtTheEndIsFinishedAfterwards() {
    GridBlockView view = GridBlockView.flat(80);
    view.fill(16, 0, 0, 79, 319, 79, Terrain.UNKNOWN);
    NavigationSession s = session(new Goals.AtBlock(new BlockPos(70, 1, 0)));
    tickAt(s, view, new BlockPos(0, 1, 0));
    assertFalse(s.path().reachesGoal());
    assertFalse(s.isSearching(), "planning ahead starts on the first tick of following");
    view.fill(16, 0, 0, 79, 0, 79, Terrain.SOLID).fill(16, 1, 0, 79, 319, 79, Terrain.OPEN);

    // A 1 ns budget allows one expansion per tick: not enough to finish while walking 15 steps.
    List<String> messages = new ArrayList<>();
    for (PathStep step : s.path().steps()) {
      messages.addAll(s.tick(step.pos().x() + 0.5, 1, step.pos().z() + 0.5, view, 1));
      if (step.pos().x() == 5) {
        assertTrue(s.isSearching(), "planning the next segment while walking");
      }
    }
    assertTrue(
        messages.contains("End of this segment; planning the next one."), messages.toString());
    assertEquals(NavigationStatus.PLANNING, s.status());
    long expandedAhead = s.snapshot().nodesExpanded();
    tickAt(s, view, new BlockPos(15, 1, 0));
    assertEquals(NavigationStatus.NAVIGATING, s.status());
    assertTrue(s.path().reachesGoal());
    assertEquals(new BlockPos(15, 1, 0), s.path().start());
    assertTrue(s.snapshot().nodesExpanded() > expandedAhead);
    assertEquals(2, s.snapshot().segments());
  }

  @Test
  void findsBlocksThenGuidesNextToThem() {
    GridBlockView view = GridBlockView.flat(30);
    view.key(12, 0, 7, "Ore_Gold");
    NavigationSession s =
        new NavigationSession(
            new NavigationSession.Request(
                PLAYER,
                "world",
                null,
                new NavigationSession.BlockTarget("ore_gold", 20),
                false,
                PathSettings.defaults(),
                GuideSettings.defaults()),
            null);
    List<String> messages = tickAt(s, view, new BlockPos(2, 1, 2));
    assertEquals("Found ore_gold at 12 0 7.", messages.getFirst());
    assertEquals(NavigationStatus.NAVIGATING, s.status());
    walk(s, view);
    assertEquals(NavigationStatus.COMPLETED, s.status());
  }

  @Test
  void missingBlockFails() {
    GridBlockView view = GridBlockView.flat(30);
    NavigationSession s =
        new NavigationSession(
            new NavigationSession.Request(
                PLAYER,
                "world",
                null,
                new NavigationSession.BlockTarget("Ore_Gold", 5),
                false,
                PathSettings.defaults(),
                GuideSettings.defaults()),
            null);
    List<String> messages = tickAt(s, view, new BlockPos(10, 1, 10));
    assertEquals(NavigationStatus.FAILED, s.status());
    assertEquals("Navigation failed: no Ore_Gold within 5 blocks.", messages.getLast());
  }

  @Test
  void cancellationIsFinal() {
    GridBlockView view = GridBlockView.flat(10);
    NavigationSession s = session(new Goals.AtBlock(new BlockPos(8, 1, 8)));
    assertTrue(s.cancel("stopped"));
    assertFalse(s.cancel("again"));
    assertTrue(tickAt(s, view, new BlockPos(0, 1, 0)).isEmpty());
    assertEquals(NavigationStatus.CANCELLED, s.status());
    assertEquals("stopped", s.snapshot().detail());
  }

  @Test
  void alreadyAtTheGoalCompletesImmediately() {
    GridBlockView view = GridBlockView.flat(10);
    NavigationSession s = session(new Goals.AtBlock(new BlockPos(3, 1, 3)));
    List<String> messages = tickAt(s, view, new BlockPos(3, 1, 3));
    assertEquals(NavigationStatus.COMPLETED, s.status());
    assertEquals("Already at block 3 1 3.", messages.getFirst());
  }

  @Test
  void unstandableStartFails() {
    GridBlockView view = GridBlockView.flat(10);
    view.fill(0, 1, 0, 2, 8, 2, Terrain.SOLID); // buried
    NavigationSession s = session(new Goals.AtBlock(new BlockPos(8, 1, 8)));
    tickAt(s, view, new BlockPos(1, 2, 1));
    assertEquals(NavigationStatus.FAILED, s.status());
  }

  @Test
  void slowPlanningTimesOutInTicks() {
    GridBlockView view = GridBlockView.flat(500);
    GuideSettings guide = new GuideSettings(30, 3, 3, 5, 12, 20, 500, 2);
    NavigationSession s =
        session(new Goals.AtBlock(new BlockPos(499, 1, 499)), PathSettings.defaults(), guide);
    List<String> messages = new ArrayList<>();
    for (int i = 0; i < 5 && s.status() == NavigationStatus.PLANNING; i++) {
      messages.addAll(s.tick(0.5, 1, 0.5, view, 1)); // 1 ns budget: one expansion per tick
    }
    assertEquals(NavigationStatus.FAILED, s.status());
    assertEquals("Navigation failed: planning took too long.", messages.getLast());
  }

  @Test
  void snapshotReportsProgress() {
    GridBlockView view = GridBlockView.flat(30);
    NavigationSession s = session(new Goals.AtBlock(new BlockPos(20, 1, 0)));
    tickAt(s, view, new BlockPos(0, 1, 0));
    tickAt(s, view, new BlockPos(5, 1, 0));
    NavigationSession.Snapshot snap = s.snapshot();
    assertEquals(5, snap.progress());
    assertEquals(20, snap.moves());
    assertEquals(15, snap.remaining(), 1e-9);
    assertNotNull(snap.goal());
    assertTrue(snap.nodesExpanded() > 0);
  }
}
