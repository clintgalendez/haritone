package io.github.clintgalendez.haritone.pathfinding;

/**
 * The outcome of a finished search.
 *
 * @param outcome whether a path was produced
 * @param reason why the search stopped
 * @param path the route for {@link Outcome#FOUND} and {@link Outcome#PARTIAL}, otherwise null
 * @param expanded nodes expanded
 * @param visited distinct positions tracked
 * @param elapsedNanos compute time used across all slices
 */
public record PathResult(
    Outcome outcome, StopReason reason, Path path, int expanded, int visited, long elapsedNanos) {

  /** Whether a path was produced. */
  public enum Outcome {
    /** The path reaches the goal. */
    FOUND,
    /** The path gets closer to the goal but does not reach it. */
    PARTIAL,
    /** No usable path. */
    FAILED,
    /** The search was cancelled. */
    CANCELLED
  }

  /** Why the search stopped. */
  public enum StopReason {
    GOAL_REACHED,
    /** Every reachable position was explored. */
    EXHAUSTED,
    NODE_LIMIT,
    TIMEOUT,
    CANCELLED,
    /** The start position is not somewhere an entity can stand. */
    INVALID_START
  }

  public boolean hasPath() {
    return path != null;
  }
}
