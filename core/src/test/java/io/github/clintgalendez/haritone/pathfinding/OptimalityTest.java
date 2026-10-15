package io.github.clintgalendez.haritone.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.api.Goals;
import io.github.clintgalendez.haritone.pathfinding.PathResult.Outcome;
import io.github.clintgalendez.haritone.world.GridBlockView;
import io.github.clintgalendez.haritone.world.Terrain;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Compares A* against a plain Dijkstra search over the same move rules on random 3D terrain. With
 * heuristic weight 1 the heuristic is admissible, so A* must find the same optimal cost.
 */
class OptimalityTest {

  private record Entry(long key, double cost) {}

  private static double dijkstra(GridBlockView view, BlockPos start, BlockPos goal) {
    MoveEvaluator evaluator = new MoveEvaluator(view, PathSettings.defaults());
    Map<Long, Double> dist = new HashMap<>();
    PriorityQueue<Entry> queue = new PriorityQueue<>((a, b) -> Double.compare(a.cost(), b.cost()));
    dist.put(start.pack(), 0.0);
    queue.add(new Entry(start.pack(), 0));
    while (!queue.isEmpty()) {
      Entry entry = queue.poll();
      if (entry.cost() > dist.get(entry.key())) {
        continue;
      }
      BlockPos pos = BlockPos.unpack(entry.key());
      if (pos.equals(goal)) {
        return entry.cost();
      }
      evaluator.forEachMove(
          pos.x(),
          pos.y(),
          pos.z(),
          (x, y, z, movement, cost, breaks) -> {
            long key = BlockPos.pack(x, y, z);
            double next = entry.cost() + cost;
            if (next < dist.getOrDefault(key, Double.POSITIVE_INFINITY) - 1e-12) {
              dist.put(key, next);
              queue.add(new Entry(key, next));
            }
          });
    }
    return Double.POSITIVE_INFINITY;
  }

  /** Hilly terrain with random pillars, pits, water and lava. */
  private static GridBlockView randomTerrain(long seed) {
    Random random = new Random(seed);
    int size = 24;
    GridBlockView view = GridBlockView.flat(size);
    for (int x = 0; x < size; x++) {
      for (int z = 0; z < size; z++) {
        int height = random.nextInt(3); // top of the ground between y = 0 and y = 2
        if (height > 0) {
          view.fill(x, 1, z, x, height, z, Terrain.SOLID);
        }
        int roll = random.nextInt(100);
        if (roll < 12) {
          view.fill(x, height + 1, z, x, height + 2, z, Terrain.SOLID);
        } else if (roll < 15) {
          view.set(x, height, z, Terrain.DANGER);
        } else if (roll < 22) {
          view.set(x, height + 1, z, Terrain.WATER);
        }
      }
    }
    return view;
  }

  private static BlockPos standable(GridBlockView view, int x, int z) {
    MoveEvaluator evaluator = new MoveEvaluator(view, PathSettings.defaults());
    for (int y = 1; y < 8; y++) {
      if (evaluator.canStand(x, y, z)) {
        return new BlockPos(x, y, z);
      }
    }
    return null;
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20})
  void aStarCostEqualsDijkstraCost(long seed) {
    GridBlockView view = randomTerrain(seed);
    BlockPos start = standable(view, 0, 0);
    BlockPos goal = standable(view, 23, 23);
    if (start == null || goal == null) {
      return; // this seed put a pillar or hazard on an endpoint
    }
    double expected = dijkstra(view, start, goal);
    PathSettings settings = PathSettings.defaults().withAllowPartial(false);
    PathResult result = new PathSearch(view, start, new Goals.AtBlock(goal), settings).run();
    if (Double.isInfinite(expected)) {
      assertEquals(Outcome.FAILED, result.outcome());
    } else {
      assertEquals(Outcome.FOUND, result.outcome());
      assertEquals(expected, result.path().cost(), 1e-6, "seed " + seed);
      assertTrue(result.path().steps().size() > 1);
    }
  }
}
