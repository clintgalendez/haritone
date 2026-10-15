package io.github.clintgalendez.haritone.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import io.github.clintgalendez.haritone.config.Settings;
import java.util.List;

/**
 * The plugin's {@code config.json}, read and written by the server's own config system. Fields
 * start at the defaults, so missing keys keep their default. Values are validated by {@link
 * Settings#validate()} before use.
 */
public final class HaritoneConfig {

  private static final Settings D = Settings.defaults();

  int maxNodes = D.maxNodes();
  int searchTimeoutMs = D.searchTimeoutMs();
  int planningBudgetMs = D.planningBudgetMs();
  int tickIntervalMs = D.tickIntervalMs();
  int maxActiveSessions = D.maxActiveSessions();
  int maxGoalDistance = D.maxGoalDistance();
  int maxReplans = D.maxReplans();
  int maxSegments = D.maxSegments();
  int maxWaypoints = D.maxWaypoints();
  int blockSearchRadius = D.blockSearchRadius();
  String unknownTerrain = D.unknownTerrain();
  boolean allowBreak = D.allowBreak();
  int maxFallHeight = D.maxFallHeight();
  double heuristicWeight = D.heuristicWeight();
  double deviationTolerance = D.deviationTolerance();
  int guidanceIntervalSeconds = D.guidanceIntervalSeconds();
  double ascendCost = D.ascendCost();
  double descendCostPerBlock = D.descendCostPerBlock();
  double climbUpCost = D.climbUpCost();
  double climbDownCost = D.climbDownCost();
  double waterCost = D.waterCost();
  double breakCost = D.breakCost();
  double hazardMarginCost = D.hazardMarginCost();
  double unknownCost = D.unknownCost();
  String[] hazardBlockKeys = D.hazardBlockKeys().toArray(String[]::new);
  String[] unbreakableBlockKeys = D.unbreakableBlockKeys().toArray(String[]::new);

  public static final BuilderCodec<HaritoneConfig> CODEC =
      BuilderCodec.builder(HaritoneConfig.class, HaritoneConfig::new)
          .append(
              new KeyedCodec<>("MaxNodes", Codec.INTEGER),
              (c, v) -> c.maxNodes = v,
              c -> c.maxNodes)
          .add()
          .append(
              new KeyedCodec<>("SearchTimeoutMs", Codec.INTEGER),
              (c, v) -> c.searchTimeoutMs = v,
              c -> c.searchTimeoutMs)
          .add()
          .append(
              new KeyedCodec<>("PlanningBudgetMs", Codec.INTEGER),
              (c, v) -> c.planningBudgetMs = v,
              c -> c.planningBudgetMs)
          .add()
          .append(
              new KeyedCodec<>("TickIntervalMs", Codec.INTEGER),
              (c, v) -> c.tickIntervalMs = v,
              c -> c.tickIntervalMs)
          .add()
          .append(
              new KeyedCodec<>("MaxActiveSessions", Codec.INTEGER),
              (c, v) -> c.maxActiveSessions = v,
              c -> c.maxActiveSessions)
          .add()
          .append(
              new KeyedCodec<>("MaxGoalDistance", Codec.INTEGER),
              (c, v) -> c.maxGoalDistance = v,
              c -> c.maxGoalDistance)
          .add()
          .append(
              new KeyedCodec<>("MaxReplans", Codec.INTEGER),
              (c, v) -> c.maxReplans = v,
              c -> c.maxReplans)
          .add()
          .append(
              new KeyedCodec<>("MaxSegments", Codec.INTEGER),
              (c, v) -> c.maxSegments = v,
              c -> c.maxSegments)
          .add()
          .append(
              new KeyedCodec<>("MaxWaypoints", Codec.INTEGER),
              (c, v) -> c.maxWaypoints = v,
              c -> c.maxWaypoints)
          .add()
          .append(
              new KeyedCodec<>("BlockSearchRadius", Codec.INTEGER),
              (c, v) -> c.blockSearchRadius = v,
              c -> c.blockSearchRadius)
          .add()
          .append(
              new KeyedCodec<>("UnknownTerrain", Codec.STRING),
              (c, v) -> c.unknownTerrain = v,
              c -> c.unknownTerrain)
          .add()
          .append(
              new KeyedCodec<>("AllowBreak", Codec.BOOLEAN),
              (c, v) -> c.allowBreak = v,
              c -> c.allowBreak)
          .add()
          .append(
              new KeyedCodec<>("MaxFallHeight", Codec.INTEGER),
              (c, v) -> c.maxFallHeight = v,
              c -> c.maxFallHeight)
          .add()
          .append(
              new KeyedCodec<>("HeuristicWeight", Codec.DOUBLE),
              (c, v) -> c.heuristicWeight = v,
              c -> c.heuristicWeight)
          .add()
          .append(
              new KeyedCodec<>("DeviationTolerance", Codec.DOUBLE),
              (c, v) -> c.deviationTolerance = v,
              c -> c.deviationTolerance)
          .add()
          .append(
              new KeyedCodec<>("GuidanceIntervalSeconds", Codec.INTEGER),
              (c, v) -> c.guidanceIntervalSeconds = v,
              c -> c.guidanceIntervalSeconds)
          .add()
          .append(
              new KeyedCodec<>("AscendCost", Codec.DOUBLE),
              (c, v) -> c.ascendCost = v,
              c -> c.ascendCost)
          .add()
          .append(
              new KeyedCodec<>("DescendCostPerBlock", Codec.DOUBLE),
              (c, v) -> c.descendCostPerBlock = v,
              c -> c.descendCostPerBlock)
          .add()
          .append(
              new KeyedCodec<>("ClimbUpCost", Codec.DOUBLE),
              (c, v) -> c.climbUpCost = v,
              c -> c.climbUpCost)
          .add()
          .append(
              new KeyedCodec<>("ClimbDownCost", Codec.DOUBLE),
              (c, v) -> c.climbDownCost = v,
              c -> c.climbDownCost)
          .add()
          .append(
              new KeyedCodec<>("WaterCost", Codec.DOUBLE),
              (c, v) -> c.waterCost = v,
              c -> c.waterCost)
          .add()
          .append(
              new KeyedCodec<>("BreakCost", Codec.DOUBLE),
              (c, v) -> c.breakCost = v,
              c -> c.breakCost)
          .add()
          .append(
              new KeyedCodec<>("HazardMarginCost", Codec.DOUBLE),
              (c, v) -> c.hazardMarginCost = v,
              c -> c.hazardMarginCost)
          .add()
          .append(
              new KeyedCodec<>("UnknownCost", Codec.DOUBLE),
              (c, v) -> c.unknownCost = v,
              c -> c.unknownCost)
          .add()
          .append(
              new KeyedCodec<>("HazardBlockKeys", Codec.STRING_ARRAY),
              (c, v) -> c.hazardBlockKeys = v,
              c -> c.hazardBlockKeys)
          .add()
          .append(
              new KeyedCodec<>("UnbreakableBlockKeys", Codec.STRING_ARRAY),
              (c, v) -> c.unbreakableBlockKeys = v,
              c -> c.unbreakableBlockKeys)
          .add()
          .build();

  Settings toSettings() {
    return new Settings(
        maxNodes,
        searchTimeoutMs,
        planningBudgetMs,
        tickIntervalMs,
        maxActiveSessions,
        maxGoalDistance,
        maxReplans,
        maxSegments,
        maxWaypoints,
        blockSearchRadius,
        unknownTerrain,
        allowBreak,
        maxFallHeight,
        heuristicWeight,
        deviationTolerance,
        guidanceIntervalSeconds,
        ascendCost,
        descendCostPerBlock,
        climbUpCost,
        climbDownCost,
        waterCost,
        breakCost,
        hazardMarginCost,
        unknownCost,
        hazardBlockKeys == null ? List.of() : List.of(hazardBlockKeys),
        unbreakableBlockKeys == null ? List.of() : List.of(unbreakableBlockKeys));
  }
}
