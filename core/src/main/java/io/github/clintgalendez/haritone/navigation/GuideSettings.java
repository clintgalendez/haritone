package io.github.clintgalendez.haritone.navigation;

/**
 * How a session follows the player along a route. Durations are in navigation ticks.
 *
 * @param guidanceEveryTicks how often to repeat the next instruction while nothing changes
 * @param deviationTolerance blocks the player may stray from the route before it counts as left
 * @param offRouteGraceTicks consecutive ticks off the route before replanning
 * @param revalidateEveryTicks how often to re-check the upcoming steps against the live world
 * @param lookahead how many upcoming steps to track and re-check
 * @param maxReplans replans allowed before giving up
 * @param maxSegments route segments allowed before giving up (long trips need several)
 * @param planningTimeoutTicks ticks a single plan may take in total, however small the slices
 */
public record GuideSettings(
    int guidanceEveryTicks,
    double deviationTolerance,
    int offRouteGraceTicks,
    int revalidateEveryTicks,
    int lookahead,
    int maxReplans,
    int maxSegments,
    int planningTimeoutTicks) {

  public GuideSettings {
    requirePositive("guidanceEveryTicks", guidanceEveryTicks);
    requirePositive("offRouteGraceTicks", offRouteGraceTicks);
    requirePositive("revalidateEveryTicks", revalidateEveryTicks);
    requirePositive("lookahead", lookahead);
    requirePositive("maxSegments", maxSegments);
    requirePositive("planningTimeoutTicks", planningTimeoutTicks);
    if (maxReplans < 0) {
      throw new IllegalArgumentException("maxReplans must be >= 0");
    }
    if (!(deviationTolerance >= 1 && deviationTolerance <= 64)) {
      throw new IllegalArgumentException("deviationTolerance must be between 1 and 64");
    }
  }

  public static GuideSettings defaults() {
    return new GuideSettings(30, 3.0, 3, 5, 12, 20, 500, 600);
  }

  private static void requirePositive(String name, int value) {
    if (value < 1) {
      throw new IllegalArgumentException(name + " must be >= 1");
    }
  }
}
