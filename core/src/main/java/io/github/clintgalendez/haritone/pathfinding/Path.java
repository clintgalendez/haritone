package io.github.clintgalendez.haritone.pathfinding;

import io.github.clintgalendez.haritone.api.BlockPos;
import java.util.List;

/**
 * A planned route. The first step is the start position ({@link Movement#START}).
 *
 * @param steps the positions to pass through, in order; never empty
 * @param cost total cost in walking-block units
 * @param reachesGoal false for a partial path that only gets closer to the goal
 */
public record Path(List<PathStep> steps, double cost, boolean reachesGoal) {
  public Path {
    steps = List.copyOf(steps);
    if (steps.isEmpty()) {
      throw new IllegalArgumentException("a path has at least its start step");
    }
  }

  public BlockPos start() {
    return steps.getFirst().pos();
  }

  public BlockPos end() {
    return steps.getLast().pos();
  }

  /** Number of moves (steps after the start). */
  public int moves() {
    return steps.size() - 1;
  }

  /** Total blocks that must be broken to follow this path. */
  public int breakCount() {
    int count = 0;
    for (PathStep step : steps) {
      count += step.breaks().size();
    }
    return count;
  }
}
