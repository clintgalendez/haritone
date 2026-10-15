package io.github.clintgalendez.haritone.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.api.Goals;
import io.github.clintgalendez.haritone.pathfinding.PathResult.Outcome;
import io.github.clintgalendez.haritone.world.GridBlockView;
import io.github.clintgalendez.haritone.world.Terrain;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Performance budgets on synthetic maps. Budgets are deliberately loose (several times the measured
 * time on a laptop) so shared CI runners pass; they catch order-of-magnitude regressions, not small
 * ones. Measured numbers are recorded in docs/TESTING.md.
 */
@Tag("perf")
class PathSearchPerfTest {

  private static PathResult timed(String name, GridBlockView view, BlockPos start, BlockPos goal) {
    PathSettings settings =
        PathSettings.defaults().withMaxNodes(1_000_000).withTimeoutMillis(30_000);
    PathSearch search = new PathSearch(view, start, new Goals.AtBlock(goal), settings);
    PathResult warm = search.run(); // first run includes JIT warm-up
    PathResult result = new PathSearch(view, start, new Goals.AtBlock(goal), settings).run();
    System.out.printf(
        "[perf] %-22s outcome=%s moves=%d expanded=%d visited=%d time=%.1f ms (cold %.1f ms)%n",
        name,
        result.outcome(),
        result.hasPath() ? result.path().moves() : -1,
        result.expanded(),
        result.visited(),
        result.elapsedNanos() / 1e6,
        warm.elapsedNanos() / 1e6);
    return result;
  }

  @Test
  void openField256() {
    GridBlockView view = GridBlockView.flat(256);
    PathResult result =
        timed("open field 256x256", view, new BlockPos(0, 1, 0), new BlockPos(255, 1, 255));
    assertEquals(Outcome.FOUND, result.outcome());
    assertTrue(result.elapsedNanos() < 2_000_000_000L);
  }

  @Test
  void randomObstacles256() {
    GridBlockView view = GridBlockView.flat(256);
    Random random = new Random(11);
    for (int x = 0; x < 256; x++) {
      for (int z = 0; z < 256; z++) {
        if (random.nextInt(100) < 20 && x + z > 2 && x + z < 508) {
          view.fill(x, 1, z, x, 2, z, Terrain.SOLID);
        }
      }
    }
    PathResult result =
        timed("20% obstacles 256x256", view, new BlockPos(0, 1, 0), new BlockPos(255, 1, 255));
    assertEquals(Outcome.FOUND, result.outcome());
    assertTrue(result.elapsedNanos() < 5_000_000_000L);
  }

  @Test
  void maze128() {
    // Serpentine corridors: every other row is a wall with a gap at alternating ends.
    GridBlockView view = GridBlockView.flat(129);
    for (int z = 1; z < 128; z += 2) {
      int gap = (z / 2) % 2 == 0 ? 127 : 1;
      for (int x = 0; x < 129; x++) {
        if (x != gap) {
          view.fill(x, 1, z, x, 2, z, Terrain.SOLID);
        }
      }
    }
    PathResult result =
        timed("serpentine maze 129", view, new BlockPos(0, 1, 0), new BlockPos(0, 1, 128));
    assertEquals(Outcome.FOUND, result.outcome());
    assertTrue(result.path().moves() > 64 * 128, "must walk every corridor");
    assertTrue(result.elapsedNanos() < 5_000_000_000L);
  }

  @Test
  void hills3d() {
    GridBlockView view = GridBlockView.flat(200);
    Random random = new Random(5);
    int[][] height = new int[200][200];
    for (int x = 0; x < 200; x++) {
      for (int z = 0; z < 200; z++) {
        int h = (int) (6 + 4 * Math.sin(x / 9.0) + 4 * Math.cos(z / 7.0)) + random.nextInt(2);
        height[x][z] = h;
        view.fill(x, 1, z, x, h, z, Terrain.SOLID);
      }
    }
    BlockPos start = new BlockPos(0, height[0][0] + 1, 0);
    BlockPos goal = new BlockPos(199, height[199][199] + 1, 199);
    PathResult result = timed("3D hills 200x200", view, start, goal);
    assertEquals(Outcome.FOUND, result.outcome());
    assertTrue(result.elapsedNanos() < 5_000_000_000L);
  }
}
