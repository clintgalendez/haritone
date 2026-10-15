package io.github.clintgalendez.haritone.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.api.Goal;
import io.github.clintgalendez.haritone.api.Goals;
import io.github.clintgalendez.haritone.pathfinding.PathResult.Outcome;
import io.github.clintgalendez.haritone.pathfinding.PathResult.StopReason;
import io.github.clintgalendez.haritone.world.BlockView;
import io.github.clintgalendez.haritone.world.GridBlockView;
import io.github.clintgalendez.haritone.world.Terrain;
import java.util.List;
import org.junit.jupiter.api.Test;

class PathSearchTest {

  private static final PathSettings DEFAULTS = PathSettings.defaults();

  private static PathResult search(GridBlockView view, PathSettings settings) {
    return new PathSearch(view, view.start, new Goals.AtBlock(view.goal), settings).run();
  }

  private static PathResult search(GridBlockView view) {
    return search(view, DEFAULTS);
  }

  /** Every step must be standable and reachable from the previous one by its movement. */
  private static void assertWellFormed(BlockView view, Path path, PathSettings settings) {
    MoveEvaluator evaluator = new MoveEvaluator(view, settings);
    List<PathStep> steps = path.steps();
    assertEquals(Movement.START, steps.getFirst().movement());
    double total = 0;
    for (int i = 1; i < steps.size(); i++) {
      PathStep prev = steps.get(i - 1);
      PathStep next = steps.get(i);
      double[] found = {Double.NaN};
      evaluator.forEachMove(
          prev.pos().x(),
          prev.pos().y(),
          prev.pos().z(),
          (x, y, z, movement, cost, breaks) -> {
            if (x == next.pos().x()
                && y == next.pos().y()
                && z == next.pos().z()
                && movement == next.movement()) {
              found[0] = cost;
            }
          });
      assertFalse(Double.isNaN(found[0]), "step " + i + " is not a legal move: " + next);
      total += found[0];
    }
    assertEquals(path.cost(), total, 1e-6, "path cost must equal the sum of its moves");
  }

  @Test
  void startAtGoalNeedsNoMoves() {
    GridBlockView view = GridBlockView.flat(8);
    view.start = new BlockPos(3, 1, 3);
    view.goal = view.start;
    PathResult result = search(view);
    assertEquals(Outcome.FOUND, result.outcome());
    assertEquals(0, result.path().moves());
    assertEquals(0, result.path().cost(), 0);
  }

  @Test
  void straightLineCostsOnePerBlock() {
    GridBlockView view = GridBlockView.parse("S.........G");
    PathResult result = search(view);
    assertEquals(Outcome.FOUND, result.outcome());
    assertEquals(StopReason.GOAL_REACHED, result.reason());
    assertEquals(10, result.path().moves());
    assertEquals(10, result.path().cost(), 1e-9);
    assertTrue(result.path().reachesGoal());
    assertWellFormed(view, result.path(), DEFAULTS);
  }

  @Test
  void openFieldUsesDiagonals() {
    GridBlockView view = GridBlockView.flat(20);
    view.start = new BlockPos(0, 1, 0);
    view.goal = new BlockPos(10, 1, 4);
    PathResult result = search(view);
    assertEquals(Outcome.FOUND, result.outcome());
    assertEquals(Goal.octile(10, 4), result.path().cost(), 1e-9);
    assertWellFormed(view, result.path(), DEFAULTS);
  }

  @Test
  void detoursAroundWallThroughGap() {
    GridBlockView view =
        GridBlockView.parse(
            ".........", //
            "S...#...G", //
            "....#....", //
            "....#....", //
            ".........");
    view.fill(4, 1, 0, 4, 2, 0, Terrain.SOLID); // close the top so the only gap is at the bottom
    PathResult result = search(view);
    assertEquals(Outcome.FOUND, result.outcome());
    assertTrue(result.path().steps().stream().anyMatch(s -> s.pos().z() == 4));
    assertTrue(result.path().cost() > 8);
    assertWellFormed(view, result.path(), DEFAULTS);
  }

  @Test
  void neverCutsCorners() {
    GridBlockView view =
        GridBlockView.parse(
            "S#", //
            ".G");
    PathResult result = search(view);
    assertEquals(Outcome.FOUND, result.outcome());
    assertEquals(2, result.path().moves(), "must walk around the corner, not through it");
    assertTrue(result.path().steps().stream().noneMatch(s -> s.movement() == Movement.DIAGONAL));
  }

  @Test
  void enclosedGoalFailsWithoutPartialPaths() {
    GridBlockView view =
        GridBlockView.parse(
            ".......", //
            "S...###", //
            "....#G#", //
            "....###");
    PathResult result = search(view, DEFAULTS.withAllowPartial(false));
    assertEquals(Outcome.FAILED, result.outcome());
    assertEquals(StopReason.EXHAUSTED, result.reason());
    assertNull(result.path());
  }

  @Test
  void enclosedGoalGivesPartialPathTowardIt() {
    GridBlockView view =
        GridBlockView.parse(
            ".......", //
            "S...###", //
            "....#G#", //
            "....###");
    PathResult result = search(view);
    assertEquals(Outcome.PARTIAL, result.outcome());
    assertFalse(result.path().reachesGoal());
    BlockPos end = result.path().end();
    assertTrue(Math.abs(end.x() - 5) + Math.abs(end.z() - 2) < 5, "should end near the goal");
    assertWellFormed(view, result.path(), DEFAULTS);
  }

  @Test
  void stepsUpOneBlock() {
    GridBlockView view = GridBlockView.parse("S..=G");
    view.goal = new BlockPos(3, 2, 0);
    PathResult result = search(view);
    assertEquals(Outcome.FOUND, result.outcome());
    assertEquals(Movement.ASCEND, result.path().steps().getLast().movement());
    assertEquals(2 + 1 + DEFAULTS.costs().ascend(), result.path().cost(), 1e-9);
  }

  @Test
  void cannotJumpWithoutHeadroom() {
    GridBlockView view = GridBlockView.parse("S=");
    view.goal = new BlockPos(1, 2, 0);
    view.set(0, 3, 0, Terrain.SOLID); // ceiling right above the head
    PathResult result = search(view, DEFAULTS.withAllowPartial(false));
    assertEquals(Outcome.FAILED, result.outcome());
  }

  @Test
  void cannotClimbTwoBlockWall() {
    GridBlockView view = GridBlockView.parse("S#G");
    view.fill(1, 1, 0, 1, 2, 0, Terrain.SOLID);
    PathResult result = search(view, DEFAULTS.withAllowPartial(false));
    assertEquals(Outcome.FAILED, result.outcome());
  }

  @Test
  void fallsWithinTheLimitButNotBeyond() {
    GridBlockView view = GridBlockView.flat(4);
    view.fill(0, 1, 0, 0, 3, 0, Terrain.SOLID); // a 3-block pillar to stand on
    view.start = new BlockPos(0, 4, 0);
    view.goal = new BlockPos(1, 1, 0);

    PathResult ok = search(view, DEFAULTS.withMaxFallHeight(3));
    assertEquals(Outcome.FOUND, ok.outcome());
    PathStep fall = ok.path().steps().get(1);
    assertEquals(Movement.DESCEND, fall.movement());
    assertEquals(1 + 3 * DEFAULTS.costs().descendPerBlock(), ok.path().cost(), 1e-9);

    PathResult tooHigh = search(view, DEFAULTS.withMaxFallHeight(2).withAllowPartial(false));
    assertEquals(Outcome.FAILED, tooHigh.outcome());
  }

  @Test
  void avoidsHazardsAndKeepsAMargin() {
    GridBlockView view =
        GridBlockView.parse(
            "...........", //
            "...........", //
            "...........", //
            "S...LLL...G", //
            "....LLL....", //
            "....LLL....");
    PathResult result = search(view);
    assertEquals(Outcome.FOUND, result.outcome());
    for (PathStep step : result.path().steps()) {
      BlockPos p = step.pos();
      assertTrue(view.terrain(p.x(), p.y() - 1, p.z()) != Terrain.DANGER, "stood on lava " + p);
    }
    // With a margin cost the route should not hug the pit edge at z = 2.
    long hugging =
        result.path().steps().stream()
            .filter(s -> s.pos().z() == 2 && s.pos().x() >= 4 && s.pos().x() <= 6)
            .count();
    assertEquals(0, hugging);
    assertWellFormed(view, result.path(), DEFAULTS);
  }

  @Test
  void hazardOnlyRouteFails() {
    GridBlockView view = GridBlockView.parse("S.LL.G");
    PathResult result = search(view, DEFAULTS.withAllowPartial(false));
    assertEquals(Outcome.FAILED, result.outcome());
  }

  @Test
  void waterCostChangesTheRoute() {
    String[] map = {
      "...........", //
      ".WWWWWWWWW.", //
      "SWWWWWWWWWG", //
      ".WWWWWWWWW.", //
      "..........."
    };
    PathResult expensiveWater = search(GridBlockView.parse(map));
    assertTrue(
        expensiveWater.path().steps().stream()
            .noneMatch(s -> s.pos().z() == 2 && s.pos().x() == 5));

    CostModel c = CostModel.defaults();
    CostModel cheapWater =
        new CostModel(
            c.ascend(),
            c.descendPerBlock(),
            c.climbUp(),
            c.climbDown(),
            1.0,
            c.breakBlock(),
            c.hazardMargin(),
            c.unknown());
    PathResult swim = search(GridBlockView.parse(map), DEFAULTS.withCosts(cheapWater));
    assertEquals(10, swim.path().cost(), 1e-9, "with no water penalty the straight swim wins");
  }

  @Test
  void climbsLadders() {
    GridBlockView view = GridBlockView.parse("SH#");
    view.fill(2, 1, 0, 2, 5, 0, Terrain.SOLID); // tall wall with a ladder against it
    view.goal = new BlockPos(2, 6, 0);
    PathResult result = search(view);
    assertEquals(Outcome.FOUND, result.outcome());
    assertTrue(result.path().steps().stream().anyMatch(s -> s.movement() == Movement.CLIMB_UP));
    assertWellFormed(view, result.path(), DEFAULTS);
  }

  @Test
  void unknownTerrainIsAvoidedByDefault() {
    GridBlockView view = GridBlockView.parse("S....?????G");
    PathResult result = search(view);
    assertEquals(Outcome.PARTIAL, result.outcome());
    assertEquals(StopReason.EXHAUSTED, result.reason());
    assertEquals(new BlockPos(4, 1, 0), result.path().end(), "stops at the edge of known terrain");
  }

  @Test
  void unknownTerrainCanBeAllowed() {
    GridBlockView view = GridBlockView.parse("S....?????G");
    PathSettings allow = DEFAULTS.withUnknownTerrain(UnknownTerrainPolicy.ALLOW);
    PathResult result = search(view, allow);
    assertEquals(Outcome.FOUND, result.outcome());
    assertTrue(result.path().cost() > 10, "unknown steps carry a penalty");
  }

  @Test
  void nodeLimitBoundsMemory() {
    GridBlockView view = GridBlockView.flat(200);
    view.start = new BlockPos(0, 1, 0);
    view.goal = new BlockPos(199, 1, 199);
    view.fill(100, 1, 0, 100, 2, 198, Terrain.SOLID); // long wall forces wide exploration
    PathResult result = search(view, DEFAULTS.withMaxNodes(500));
    assertEquals(StopReason.NODE_LIMIT, result.reason());
    assertTrue(result.visited() <= 500);
    assertEquals(Outcome.PARTIAL, result.outcome());
  }

  @Test
  void timeoutStopsTheSearch() {
    GridBlockView view = GridBlockView.flat(2000);
    view.start = new BlockPos(0, 1, 0);
    view.goal = new BlockPos(1999, 1, 1999);
    view.fill(1000, 1, 0, 1000, 2, 1998, Terrain.SOLID);
    PathResult result =
        search(view, DEFAULTS.withTimeoutMillis(20).withMaxNodes(5_000_000).withHeuristicWeight(1));
    assertEquals(StopReason.TIMEOUT, result.reason());
    assertTrue(result.elapsedNanos() >= 20_000_000L);
  }

  @Test
  void cancellationStopsTheSearch() {
    GridBlockView view = GridBlockView.flat(100);
    view.start = new BlockPos(0, 1, 0);
    view.goal = new BlockPos(99, 1, 99);
    PathSearch search = new PathSearch(view, view.start, new Goals.AtBlock(view.goal), DEFAULTS);
    assertNull(search.advance(10, Long.MAX_VALUE), "not finished after 10 expansions");
    search.cancel();
    PathResult result = search.advance(10, Long.MAX_VALUE);
    assertEquals(Outcome.CANCELLED, result.outcome());
    assertNull(result.path());
    assertTrue(search.isDone());
  }

  @Test
  void slicedSearchMatchesOneShotSearch() {
    GridBlockView view =
        GridBlockView.parse(
            "S....#.......", //
            ".###.#.#####.", //
            "...#.#.....#.", //
            "##.#.#####.#.", //
            "...#.......#G");
    PathResult oneShot = search(view);
    PathSearch sliced = new PathSearch(view, view.start, new Goals.AtBlock(view.goal), DEFAULTS);
    PathResult result;
    int slices = 0;
    do {
      result = sliced.advance(3, Long.MAX_VALUE);
      slices++;
    } while (result == null);
    assertTrue(slices > 5);
    assertEquals(oneShot.path(), result.path());
    assertEquals(oneShot.expanded(), result.expanded());
  }

  @Test
  void invalidStartFailsImmediately() {
    GridBlockView view = GridBlockView.parse("S#G");
    view.start = new BlockPos(1, 1, 0); // inside the wall
    PathResult result = search(view);
    assertEquals(StopReason.INVALID_START, result.reason());
    assertEquals(Outcome.FAILED, result.outcome());
  }

  @Test
  void breakAwarePlanningTunnelsThroughWalls() {
    GridBlockView view =
        GridBlockView.parse(
            "S.#.G", //
            "..#..", //
            "..#..");
    view.fill(2, 1, 0, 2, 2, 2, Terrain.SOLID);
    assertEquals(Outcome.FAILED, search(view, DEFAULTS.withAllowPartial(false)).outcome());

    PathSettings breaking = DEFAULTS.withAllowBreak(true);
    PathResult result = search(view, breaking);
    assertEquals(Outcome.FOUND, result.outcome());
    // Cheapest: break the top wall block, hop over the bottom one, and drop down the far side.
    assertEquals(1, result.path().breakCount());
    for (PathStep step : result.path().steps()) {
      for (BlockPos broken : step.breaks()) {
        assertEquals(2, broken.x(), "only wall blocks are broken");
      }
    }
    assertWellFormed(view, result.path(), breaking);

    CostModel c = CostModel.defaults();
    CostModel cheapBreaks =
        new CostModel(
            c.ascend(),
            c.descendPerBlock(),
            c.climbUp(),
            c.climbDown(),
            c.water(),
            0.1,
            c.hazardMargin(),
            c.unknown());
    PathResult tunnel = search(view, breaking.withCosts(cheapBreaks));
    assertEquals(2, tunnel.path().breakCount(), "cheap breaking tunnels straight through");
  }

  @Test
  void unbreakableBlocksStayBlocking() {
    GridBlockView view = GridBlockView.parse("S#G");
    view.fill(1, 1, 0, 1, 2, 0, Terrain.SOLID).unbreakable(1, 1, 0).unbreakable(1, 2, 0);
    PathResult result = search(view, DEFAULTS.withAllowBreak(true).withAllowPartial(false));
    assertEquals(Outcome.FAILED, result.outcome());
  }

  @Test
  void weightedHeuristicStillProducesLegalPath() {
    // Scattered water makes many routes nearly equal, which is where weighting pays off.
    GridBlockView view = GridBlockView.flat(80);
    java.util.Random random = new java.util.Random(3);
    for (int x = 0; x < 80; x++) {
      for (int z = 0; z < 80; z++) {
        if (random.nextInt(100) < 30) {
          view.set(x, 1, z, Terrain.WATER);
        }
      }
    }
    view.start = new BlockPos(0, 1, 0);
    view.goal = new BlockPos(79, 1, 60);
    view.set(0, 1, 0, Terrain.OPEN).set(79, 1, 60, Terrain.OPEN);
    PathResult optimal = search(view);
    PathSettings fast = DEFAULTS.withHeuristicWeight(3);
    PathResult weighted = search(view, fast);
    assertEquals(Outcome.FOUND, weighted.outcome());
    assertTrue(weighted.path().cost() >= optimal.path().cost() - 1e-9);
    assertTrue(weighted.expanded() < optimal.expanded());
    assertWellFormed(view, weighted.path(), fast);
  }

  @Test
  void goalsOtherThanBlocksWork() {
    GridBlockView view = GridBlockView.flat(30);
    BlockPos start = new BlockPos(0, 1, 0);
    assertGoal(view, start, new Goals.Column(20, 5));
    assertGoal(view, start, new Goals.Near(new BlockPos(20, 1, 20), 3));
    assertGoal(view, start, new Goals.Adjacent(new BlockPos(10, 0, 10)));
    assertGoal(
        view,
        start,
        new Goals.AnyOf(
            List.of(
                new Goals.AtBlock(new BlockPos(25, 1, 25)),
                new Goals.AtBlock(new BlockPos(5, 1, 0)))));
  }

  private static void assertGoal(GridBlockView view, BlockPos start, Goal goal) {
    PathResult result = new PathSearch(view, start, goal, DEFAULTS).run();
    assertEquals(Outcome.FOUND, result.outcome(), goal.describe());
    BlockPos end = result.path().end();
    assertTrue(goal.isSatisfied(end.x(), end.y(), end.z()));
  }

  @Test
  void highestStandableFindsTheSurface() {
    GridBlockView view = GridBlockView.flat(4);
    view.fill(1, 1, 1, 1, 9, 1, Terrain.SOLID);
    MoveEvaluator evaluator = new MoveEvaluator(view, DEFAULTS);
    assertEquals(new BlockPos(1, 10, 1), evaluator.highestStandable(1, 1));
    assertEquals(new BlockPos(0, 1, 0), evaluator.highestStandable(0, 0));
    assertNull(evaluator.highestStandable(50, 50), "unknown columns have no surface");
  }

  @Test
  void snapFindsTheRealStandingPosition() {
    GridBlockView view = GridBlockView.flat(4);
    view.set(1, 1, 1, Terrain.SOLID); // a half block the player stands on top of
    MoveEvaluator evaluator = new MoveEvaluator(view, DEFAULTS);
    assertEquals(new BlockPos(1, 2, 1), evaluator.snap(new BlockPos(1, 1, 1)));
    assertEquals(new BlockPos(2, 1, 2), evaluator.snap(new BlockPos(2, 2, 2)), "mid-jump");
    assertNotNull(evaluator.snap(new BlockPos(2, 1, 2)));
  }
}
