package io.github.clintgalendez.haritone.pathfinding;

import java.util.Objects;

/**
 * Limits and rules for one search.
 *
 * @param maxNodes most distinct positions the search may track; bounds memory use
 * @param timeoutMillis most compute time the search may use in total, summed over every slice
 * @param heuristicWeight 1 gives optimal paths; larger values search faster but may be costlier
 * @param maxFallHeight most blocks the planner may drop in one move
 * @param allowBreak whether to plan through breakable solid blocks
 * @param unknownTerrain how to treat blocks that are not loaded
 * @param allowPartial whether to return a path toward the goal when the goal cannot be reached
 * @param cacheSize terrain lookups to memoize during the search
 * @param costs movement costs
 */
public record PathSettings(
    int maxNodes,
    long timeoutMillis,
    double heuristicWeight,
    int maxFallHeight,
    boolean allowBreak,
    UnknownTerrainPolicy unknownTerrain,
    boolean allowPartial,
    int cacheSize,
    CostModel costs) {

  public PathSettings {
    if (maxNodes < 1) {
      throw new IllegalArgumentException("maxNodes must be >= 1");
    }
    if (timeoutMillis < 1) {
      throw new IllegalArgumentException("timeoutMillis must be >= 1");
    }
    if (!(heuristicWeight >= 1 && heuristicWeight <= 10)) {
      throw new IllegalArgumentException("heuristicWeight must be between 1 and 10");
    }
    if (maxFallHeight < 0 || maxFallHeight > 256) {
      throw new IllegalArgumentException("maxFallHeight must be between 0 and 256");
    }
    if (cacheSize < 1) {
      throw new IllegalArgumentException("cacheSize must be >= 1");
    }
    Objects.requireNonNull(unknownTerrain, "unknownTerrain");
    Objects.requireNonNull(costs, "costs");
  }

  public static PathSettings defaults() {
    return new PathSettings(
        100_000,
        2_000,
        1.0,
        3,
        false,
        UnknownTerrainPolicy.AVOID,
        true,
        65_536,
        CostModel.defaults());
  }

  public PathSettings withMaxNodes(int value) {
    return new PathSettings(
        value,
        timeoutMillis,
        heuristicWeight,
        maxFallHeight,
        allowBreak,
        unknownTerrain,
        allowPartial,
        cacheSize,
        costs);
  }

  public PathSettings withTimeoutMillis(long value) {
    return new PathSettings(
        maxNodes,
        value,
        heuristicWeight,
        maxFallHeight,
        allowBreak,
        unknownTerrain,
        allowPartial,
        cacheSize,
        costs);
  }

  public PathSettings withHeuristicWeight(double value) {
    return new PathSettings(
        maxNodes,
        timeoutMillis,
        value,
        maxFallHeight,
        allowBreak,
        unknownTerrain,
        allowPartial,
        cacheSize,
        costs);
  }

  public PathSettings withMaxFallHeight(int value) {
    return new PathSettings(
        maxNodes,
        timeoutMillis,
        heuristicWeight,
        value,
        allowBreak,
        unknownTerrain,
        allowPartial,
        cacheSize,
        costs);
  }

  public PathSettings withAllowBreak(boolean value) {
    return new PathSettings(
        maxNodes,
        timeoutMillis,
        heuristicWeight,
        maxFallHeight,
        value,
        unknownTerrain,
        allowPartial,
        cacheSize,
        costs);
  }

  public PathSettings withUnknownTerrain(UnknownTerrainPolicy value) {
    return new PathSettings(
        maxNodes,
        timeoutMillis,
        heuristicWeight,
        maxFallHeight,
        allowBreak,
        value,
        allowPartial,
        cacheSize,
        costs);
  }

  public PathSettings withAllowPartial(boolean value) {
    return new PathSettings(
        maxNodes,
        timeoutMillis,
        heuristicWeight,
        maxFallHeight,
        allowBreak,
        unknownTerrain,
        value,
        cacheSize,
        costs);
  }

  public PathSettings withCosts(CostModel value) {
    return new PathSettings(
        maxNodes,
        timeoutMillis,
        heuristicWeight,
        maxFallHeight,
        allowBreak,
        unknownTerrain,
        allowPartial,
        cacheSize,
        value);
  }
}
