package io.github.clintgalendez.haritone.pathfinding;

/**
 * Movement costs in walking-block units, where one flat step costs 1 and a diagonal step costs the
 * square root of 2. Every value is an additive surcharge except {@code water}, which multiplies the
 * cost of horizontal moves that end in water and is charged per block when swimming up or down.
 *
 * @param ascend extra cost of stepping up one block
 * @param descendPerBlock extra cost per block dropped when stepping or falling down
 * @param climbUp cost per block climbing up a ladder or vine
 * @param climbDown cost per block climbing down
 * @param water multiplier for moves ending in water, at least 1
 * @param breakBlock extra cost per block broken (break-aware planning only)
 * @param hazardMargin extra cost for standing next to a dangerous block
 * @param unknown extra cost for relying on unknown terrain (only when that is allowed)
 */
public record CostModel(
    double ascend,
    double descendPerBlock,
    double climbUp,
    double climbDown,
    double water,
    double breakBlock,
    double hazardMargin,
    double unknown) {

  public static final double MAX_COST = 1000;

  public CostModel {
    requireRange("ascend", ascend, 0.01);
    requireRange("descendPerBlock", descendPerBlock, 0.01);
    requireRange("climbUp", climbUp, 0.01);
    requireRange("climbDown", climbDown, 0.01);
    requireRange("water", water, 1);
    requireRange("breakBlock", breakBlock, 0);
    requireRange("hazardMargin", hazardMargin, 0);
    requireRange("unknown", unknown, 0);
  }

  public static CostModel defaults() {
    return new CostModel(1.0, 0.5, 1.5, 1.0, 2.0, 4.0, 2.0, 1.0);
  }

  /**
   * The cheapest any move can make one block of vertical progress. Goal heuristics use it so they
   * never overestimate.
   */
  public double minVerticalCost() {
    return Math.min(
        Math.min(ascend, descendPerBlock), Math.min(Math.min(climbUp, climbDown), water));
  }

  private static void requireRange(String name, double value, double min) {
    if (!(value >= min && value <= MAX_COST)) {
      throw new IllegalArgumentException(
          name + " must be between " + min + " and " + MAX_COST + " but was " + value);
    }
  }
}
