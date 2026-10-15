package io.github.clintgalendez.haritone.navigation;

import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.world.BlockView;
import java.util.Locale;
import java.util.Objects;

/**
 * Finds the nearest block with a given key, scanning cube shells outward from a centre. Like {@link
 * io.github.clintgalendez.haritone.pathfinding.PathSearch} it runs in small budgeted slices.
 * "Nearest" means smallest Chebyshev distance (the largest of the axis distances), which is what
 * shell order gives for free. Unknown blocks never match.
 */
public final class BlockSearch {

  /** Progress of the scan. */
  public enum State {
    RUNNING,
    FOUND,
    NOT_FOUND
  }

  private final BlockView view;
  private final BlockPos center;
  private final String key;
  private final int radius;
  private int r = -1;
  private int x;
  private int y;
  private int z;
  private long scanned;
  private State state = State.RUNNING;
  private BlockPos found;

  public BlockSearch(BlockView view, BlockPos center, String blockKey, int radius) {
    this.view = Objects.requireNonNull(view, "view");
    this.center = Objects.requireNonNull(center, "center");
    this.key = Objects.requireNonNull(blockKey, "blockKey").toLowerCase(Locale.ROOT);
    if (radius < 0) {
      throw new IllegalArgumentException("radius must be >= 0");
    }
    this.radius = radius;
  }

  /** Scans at most {@code maxBlocks} blocks within {@code budgetNanos}, then reports progress. */
  public State advance(int maxBlocks, long budgetNanos) {
    long started = System.nanoTime();
    int done = 0;
    while (state == State.RUNNING) {
      if (done > 0 && (done >= maxBlocks || System.nanoTime() - started >= budgetNanos)) {
        break;
      }
      if (!nextOffset()) {
        state = State.NOT_FOUND;
        break;
      }
      done++;
      scanned++;
      int bx = center.x() + x;
      int by = center.y() + y;
      int bz = center.z() + z;
      if (by < view.minY() - 1 || by > view.maxY() + 1) {
        continue;
      }
      String blockKey = view.blockKey(bx, by, bz);
      if (blockKey != null && blockKey.toLowerCase(Locale.ROOT).equals(key)) {
        found = new BlockPos(bx, by, bz);
        state = State.FOUND;
      }
    }
    return state;
  }

  public State state() {
    return state;
  }

  /** The block found, or null. */
  public BlockPos found() {
    return found;
  }

  public long scanned() {
    return scanned;
  }

  /** Moves to the next offset in shell order; false once past the radius. */
  private boolean nextOffset() {
    if (r < 0) {
      r = 0;
      x = 0;
      y = 0;
      z = 0;
      return true;
    }
    if (Math.abs(x) == r || Math.abs(y) == r) {
      z++; // on a face of the shell: every z belongs to it
    } else {
      z = z == -r ? r : r + 1; // inside column: only the two caps belong to the shell
    }
    if (z > r) {
      z = -r;
      y++;
      if (y > r) {
        y = -r;
        x++;
        if (x > r) {
          r++;
          if (r > radius) {
            return false;
          }
          x = -r;
          y = -r;
          z = -r;
        }
      }
    }
    return true;
  }
}
