package io.github.clintgalendez.haritone.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.pathfinding.Movement;
import io.github.clintgalendez.haritone.pathfinding.Path;
import io.github.clintgalendez.haritone.pathfinding.PathStep;
import io.github.clintgalendez.haritone.world.BlockView;
import io.github.clintgalendez.haritone.world.GridBlockView;
import io.github.clintgalendez.haritone.world.Terrain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuidanceAndBlockSearchTest {

  private static PathStep step(int x, int y, int z, Movement movement) {
    return new PathStep(new BlockPos(x, y, z), movement, List.of());
  }

  @Test
  void mergesRunsIntoLegs() {
    Path path =
        new Path(
            List.of(
                step(0, 1, 0, Movement.START),
                step(1, 1, 0, Movement.TRAVERSE),
                step(2, 1, 0, Movement.TRAVERSE),
                step(3, 1, -1, Movement.DIAGONAL),
                step(4, 1, -2, Movement.DIAGONAL),
                step(5, 2, -2, Movement.ASCEND),
                step(5, 3, -2, Movement.CLIMB_UP),
                step(5, 4, -2, Movement.CLIMB_UP)),
            10,
            true);
    List<Guidance.Leg> legs = Guidance.legs(path, 0);
    assertEquals(4, legs.size());
    assertEquals(2, legs.get(0).count());
    assertEquals(2, legs.get(1).count());
    assertEquals("walk 2 blocks NE to 4 1 -2", Guidance.describe(legs.get(1)));
    assertEquals("step up E to 5 2 -2", Guidance.describe(legs.get(2)));
    assertEquals("climb up 2 to 5 4 -2", Guidance.describe(legs.get(3)));
    assertEquals(
        "Next: walk 2 blocks E to 2 1 0, then walk 2 blocks NE to 4 1 -2. 8 blocks left.",
        Guidance.next(path, 0));
    assertNull(Guidance.next(path, 7));
  }

  @Test
  void mentionsBreaksAndPartialSegments() {
    Path path =
        new Path(
            List.of(
                step(0, 1, 0, Movement.START),
                new PathStep(
                    new BlockPos(0, 1, 1),
                    Movement.TRAVERSE,
                    List.of(new BlockPos(0, 1, 1), new BlockPos(0, 2, 1)))),
            9,
            false);
    assertEquals(
        "Next: walk 1 block S to 0 1 1 (break 2 blocks first). 1 block left in this segment.",
        Guidance.next(path, 0));
  }

  @Test
  void compassFollowsNegativeZNorth() {
    assertEquals("N", Guidance.compass(0, -1));
    assertEquals("SW", Guidance.compass(-1, 1));
    assertEquals("E", Guidance.compass(1, 0));
  }

  @Test
  void shellScanVisitsEveryOffsetOnceNearestFirst() {
    List<BlockPos> order = new ArrayList<>();
    BlockView recording =
        new BlockView() {
          @Override
          public Terrain terrain(int x, int y, int z) {
            return Terrain.OPEN;
          }

          @Override
          public String blockKey(int x, int y, int z) {
            order.add(new BlockPos(x, y, z));
            return null;
          }

          @Override
          public int minY() {
            return 0;
          }

          @Override
          public int maxY() {
            return 319;
          }
        };
    BlockSearch search = new BlockSearch(recording, new BlockPos(20, 100, 20), "nothing", 3);
    assertEquals(BlockSearch.State.NOT_FOUND, search.advance(Integer.MAX_VALUE, Long.MAX_VALUE));
    assertEquals(7 * 7 * 7, order.size());
    assertEquals(order.size(), new HashSet<>(order).size(), "each offset exactly once");
    int previous = 0;
    for (BlockPos p : order) {
      int d = Math.max(Math.abs(p.x() - 20), Math.max(Math.abs(p.y() - 100), Math.abs(p.z() - 20)));
      assertTrue(d >= previous, "shells never go back inward");
      previous = d;
    }
    assertNull(search.found());
  }

  @Test
  void findsTheNearestMatchInBudgetedSlices() {
    GridBlockView view = GridBlockView.flat(40);
    view.key(30, 0, 30, "Ore_Iron").key(23, 0, 21, "ore_iron");
    BlockSearch search = new BlockSearch(view, new BlockPos(20, 1, 20), "ORE_IRON", 15);
    int slices = 0;
    while (search.advance(50, Long.MAX_VALUE) == BlockSearch.State.RUNNING) {
      slices++;
    }
    assertEquals(BlockSearch.State.FOUND, search.state());
    assertEquals(new BlockPos(23, 0, 21), search.found());
    assertTrue(slices > 3);
  }
}
