package io.github.clintgalendez.haritone.config;

import io.github.clintgalendez.haritone.navigation.GuideSettings;
import io.github.clintgalendez.haritone.pathfinding.CostModel;
import io.github.clintgalendez.haritone.pathfinding.PathSettings;
import io.github.clintgalendez.haritone.pathfinding.UnknownTerrainPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server-wide settings, loaded from the plugin's config file. Values are not trusted: call {@link
 * #validate()} and fall back to {@link #defaults()} when it reports problems.
 */
public record Settings(
    int maxNodes,
    int searchTimeoutMs,
    int planningBudgetMs,
    int tickIntervalMs,
    int maxActiveSessions,
    int maxGoalDistance,
    int maxReplans,
    int maxSegments,
    int maxWaypoints,
    int blockSearchRadius,
    String unknownTerrain,
    boolean allowBreak,
    int maxFallHeight,
    double heuristicWeight,
    double deviationTolerance,
    int guidanceIntervalSeconds,
    double ascendCost,
    double descendCostPerBlock,
    double climbUpCost,
    double climbDownCost,
    double waterCost,
    double breakCost,
    double hazardMarginCost,
    double unknownCost,
    List<String> hazardBlockKeys,
    List<String> unbreakableBlockKeys) {

  public Settings {
    hazardBlockKeys = hazardBlockKeys == null ? List.of() : List.copyOf(hazardBlockKeys);
    unbreakableBlockKeys =
        unbreakableBlockKeys == null ? List.of() : List.copyOf(unbreakableBlockKeys);
  }

  public static Settings defaults() {
    CostModel c = CostModel.defaults();
    return new Settings(
        100_000,
        2_000,
        4,
        100,
        32,
        10_000,
        20,
        500,
        64,
        48,
        UnknownTerrainPolicy.AVOID.name(),
        false,
        3,
        1.0,
        3.0,
        4,
        c.ascend(),
        c.descendPerBlock(),
        c.climbUp(),
        c.climbDown(),
        c.water(),
        c.breakBlock(),
        c.hazardMargin(),
        c.unknown(),
        List.of("lava", "fire"),
        List.of());
  }

  /** Every problem with these values, or an empty list when they are all usable. */
  public List<String> validate() {
    List<String> problems = new ArrayList<>();
    range(problems, "MaxNodes", maxNodes, 1_000, 5_000_000);
    range(problems, "SearchTimeoutMs", searchTimeoutMs, 50, 60_000);
    range(problems, "PlanningBudgetMs", planningBudgetMs, 1, 50);
    range(problems, "TickIntervalMs", tickIntervalMs, 50, 1_000);
    range(problems, "MaxActiveSessions", maxActiveSessions, 1, 1_000);
    range(problems, "MaxGoalDistance", maxGoalDistance, 16, 1_000_000);
    range(problems, "MaxReplans", maxReplans, 0, 1_000);
    range(problems, "MaxSegments", maxSegments, 1, 10_000);
    range(problems, "MaxWaypoints", maxWaypoints, 0, 1_000);
    range(problems, "BlockSearchRadius", blockSearchRadius, 1, 128);
    range(problems, "MaxFallHeight", maxFallHeight, 0, 64);
    range(problems, "HeuristicWeight", heuristicWeight, 1, 10);
    range(problems, "DeviationTolerance", deviationTolerance, 1, 64);
    range(problems, "GuidanceIntervalSeconds", guidanceIntervalSeconds, 1, 600);
    range(problems, "AscendCost", ascendCost, 0.01, CostModel.MAX_COST);
    range(problems, "DescendCostPerBlock", descendCostPerBlock, 0.01, CostModel.MAX_COST);
    range(problems, "ClimbUpCost", climbUpCost, 0.01, CostModel.MAX_COST);
    range(problems, "ClimbDownCost", climbDownCost, 0.01, CostModel.MAX_COST);
    range(problems, "WaterCost", waterCost, 1, CostModel.MAX_COST);
    range(problems, "BreakCost", breakCost, 0, CostModel.MAX_COST);
    range(problems, "HazardMarginCost", hazardMarginCost, 0, CostModel.MAX_COST);
    range(problems, "UnknownCost", unknownCost, 0, CostModel.MAX_COST);
    if (parsePolicy(unknownTerrain) == null) {
      problems.add("UnknownTerrain must be AVOID or ALLOW but was " + unknownTerrain);
    }
    keys(problems, "HazardBlockKeys", hazardBlockKeys);
    keys(problems, "UnbreakableBlockKeys", unbreakableBlockKeys);
    return problems;
  }

  /** Search settings for one player. Call only on validated settings. */
  public PathSettings pathSettings(PlayerPrefs prefs, boolean mayBreak) {
    return new PathSettings(
        maxNodes,
        searchTimeoutMs,
        heuristicWeight,
        Math.min(
            maxFallHeight, prefs.maxFallHeight() == null ? maxFallHeight : prefs.maxFallHeight()),
        allowBreak && mayBreak && prefs.allowBreak(),
        parsePolicy(unknownTerrain),
        true,
        Math.min(maxNodes * 4, 65_536),
        new CostModel(
            ascendCost,
            descendCostPerBlock,
            climbUpCost,
            climbDownCost,
            waterCost,
            breakCost,
            hazardMarginCost,
            unknownCost));
  }

  /** Guidance settings for one player. Call only on validated settings. */
  public GuideSettings guideSettings(PlayerPrefs prefs) {
    int seconds =
        prefs.guidanceIntervalSeconds() == null
            ? guidanceIntervalSeconds
            : prefs.guidanceIntervalSeconds();
    int ticksPerSecond = Math.max(1, 1000 / tickIntervalMs);
    // Allow wall-clock planning of several times the compute budget, since each tick only
    // spends planningBudgetMs on it.
    int planningTicks = Math.max(10, (searchTimeoutMs / planningBudgetMs) * 2);
    return new GuideSettings(
        Math.max(1, seconds * ticksPerSecond),
        deviationTolerance,
        Math.max(1, ticksPerSecond / 3),
        Math.max(1, ticksPerSecond / 2),
        12,
        maxReplans,
        maxSegments,
        planningTicks);
  }

  static UnknownTerrainPolicy parsePolicy(String value) {
    if (value == null) {
      return null;
    }
    try {
      return UnknownTerrainPolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static void range(
      List<String> problems, String name, double value, double min, double max) {
    if (!(value >= min && value <= max)) {
      problems.add(
          name + " must be between " + fmt(min) + " and " + fmt(max) + " but was " + fmt(value));
    }
  }

  private static void keys(List<String> problems, String name, List<String> keys) {
    if (keys.size() > 256) {
      problems.add(name + " may hold at most 256 entries");
    }
    for (String key : keys) {
      if (key == null || key.isBlank() || key.length() > 128) {
        problems.add(name + " entries must be 1 to 128 characters");
        return;
      }
    }
  }

  private static String fmt(double value) {
    return value == Math.rint(value) && Math.abs(value) < 1e15
        ? Long.toString((long) value)
        : Double.toString(value);
  }
}
