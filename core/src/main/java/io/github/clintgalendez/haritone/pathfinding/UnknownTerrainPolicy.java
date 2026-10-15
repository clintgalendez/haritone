package io.github.clintgalendez.haritone.pathfinding;

/** How the planner treats blocks it cannot see (usually unloaded chunks). */
public enum UnknownTerrainPolicy {
  /**
   * Treat unknown blocks as impassable. Plans stop at the edge of known terrain and return a
   * partial path, and navigation plans the next segment once more terrain is visible. This is the
   * safe default.
   */
  AVOID,
  /**
   * Assume unknown blocks are open and can be stood on, at {@link CostModel#unknown()} extra cost
   * per affected step. Useful for exploring; the route is re-checked as terrain loads.
   */
  ALLOW
}
