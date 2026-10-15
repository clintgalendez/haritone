package io.github.clintgalendez.haritone.pathfinding;

import io.github.clintgalendez.haritone.api.BlockPos;
import java.util.List;
import java.util.Objects;

/**
 * One position along a path.
 *
 * @param pos feet position after the move
 * @param movement how the position is reached from the previous step
 * @param breaks blocks that must be broken before making the move (empty unless break-aware
 *     planning is on)
 */
public record PathStep(BlockPos pos, Movement movement, List<BlockPos> breaks) {
  public PathStep {
    Objects.requireNonNull(pos, "pos");
    Objects.requireNonNull(movement, "movement");
    breaks = List.copyOf(breaks);
  }
}
