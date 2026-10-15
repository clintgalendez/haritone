package io.github.clintgalendez.haritone.api;

/**
 * A navigation destination. Implementations must be immutable and cheap to evaluate, because the
 * search calls both methods for every node it expands.
 *
 * <p>Third-party plugins may implement this interface to define custom goals.
 */
public interface Goal {

  /** Whether an entity standing with its feet at {@code (x, y, z)} has arrived. */
  boolean isSatisfied(int x, int y, int z);

  /**
   * Estimated remaining cost from {@code (x, y, z)} in walking-block units. To keep searches
   * optimal the estimate must never be too high: count horizontal distance at 1 per block
   * (diagonals at the square root of 2) and vertical distance at {@code verticalCost} per block.
   */
  double heuristic(int x, int y, int z, double verticalCost);

  /** Short human-readable description, for example {@code "block 10 64 -3"}. */
  String describe();

  /** Octile distance: the exact cost of an obstacle-free walk with diagonal steps. */
  static double octile(int dx, int dz) {
    int ax = Math.abs(dx);
    int az = Math.abs(dz);
    int min = Math.min(ax, az);
    int max = Math.max(ax, az);
    return (max - min) + min * Math.sqrt(2);
  }
}
