package io.github.clintgalendez.haritone.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.clintgalendez.haritone.navigation.GuideSettings;
import io.github.clintgalendez.haritone.pathfinding.PathSettings;
import io.github.clintgalendez.haritone.pathfinding.UnknownTerrainPolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsTest {

  private static Settings withBreak(boolean allowBreak, int maxFall) {
    Settings d = Settings.defaults();
    return new Settings(
        d.maxNodes(),
        d.searchTimeoutMs(),
        d.planningBudgetMs(),
        d.tickIntervalMs(),
        d.maxActiveSessions(),
        d.maxGoalDistance(),
        d.maxReplans(),
        d.maxSegments(),
        d.maxWaypoints(),
        d.blockSearchRadius(),
        d.unknownTerrain(),
        allowBreak,
        maxFall,
        d.heuristicWeight(),
        d.deviationTolerance(),
        d.guidanceIntervalSeconds(),
        d.ascendCost(),
        d.descendCostPerBlock(),
        d.climbUpCost(),
        d.climbDownCost(),
        d.waterCost(),
        d.breakCost(),
        d.hazardMarginCost(),
        d.unknownCost(),
        d.hazardBlockKeys(),
        d.unbreakableBlockKeys());
  }

  @Test
  void defaultsAreValidAndBuildUsableSettings() {
    Settings settings = Settings.defaults();
    assertEquals(List.of(), settings.validate());
    PathSettings path = settings.pathSettings(PlayerPrefs.defaults(), false);
    assertEquals(UnknownTerrainPolicy.AVOID, path.unknownTerrain());
    assertFalse(path.allowBreak());
    GuideSettings guide = settings.guideSettings(PlayerPrefs.defaults());
    assertEquals(40, guide.guidanceEveryTicks(), "4 s at 10 ticks per second");
  }

  @Test
  void reportsEveryInvalidValue() {
    Settings bad =
        new Settings(
            0,
            10,
            0,
            5,
            0,
            1,
            -1,
            0,
            -1,
            0,
            "SOMETIMES",
            false,
            99,
            0.5,
            0,
            0,
            0,
            0,
            0,
            0,
            0.5,
            -1,
            -1,
            -1,
            List.of(""),
            null);
    List<String> problems = bad.validate();
    assertEquals(24, problems.size(), String.join("\n", problems));
    assertTrue(problems.contains("MaxNodes must be between 1000 and 5000000 but was 0"));
    assertTrue(problems.contains("UnknownTerrain must be AVOID or ALLOW but was SOMETIMES"));
    assertTrue(problems.contains("WaterCost must be between 1 and 1000 but was 0.5"));
    assertTrue(problems.contains("HazardBlockKeys entries must be 1 to 128 characters"));
  }

  @Test
  void nanIsRejected() {
    Settings d = Settings.defaults();
    Settings nan =
        new Settings(
            d.maxNodes(),
            d.searchTimeoutMs(),
            d.planningBudgetMs(),
            d.tickIntervalMs(),
            d.maxActiveSessions(),
            d.maxGoalDistance(),
            d.maxReplans(),
            d.maxSegments(),
            d.maxWaypoints(),
            d.blockSearchRadius(),
            "allow",
            false,
            3,
            Double.NaN,
            d.deviationTolerance(),
            d.guidanceIntervalSeconds(),
            d.ascendCost(),
            d.descendCostPerBlock(),
            d.climbUpCost(),
            d.climbDownCost(),
            d.waterCost(),
            d.breakCost(),
            d.hazardMarginCost(),
            d.unknownCost(),
            List.of(),
            List.of());
    assertEquals(1, nan.validate().size());
    assertTrue(nan.validate().getFirst().startsWith("HeuristicWeight"));
  }

  @Test
  void breakingNeedsServerSwitchPermissionAndPreference() {
    PlayerPrefs wants = new PlayerPrefs(true, null, null);
    assertFalse(withBreak(false, 3).pathSettings(wants, true).allowBreak(), "server off");
    assertFalse(withBreak(true, 3).pathSettings(wants, false).allowBreak(), "no permission");
    assertFalse(withBreak(true, 3).pathSettings(PlayerPrefs.defaults(), true).allowBreak());
    assertTrue(withBreak(true, 3).pathSettings(wants, true).allowBreak());
  }

  @Test
  void playerFallHeightIsCappedByTheServer() {
    assertEquals(
        2,
        withBreak(false, 5).pathSettings(new PlayerPrefs(false, 2, null), false).maxFallHeight());
    assertEquals(
        3,
        withBreak(false, 3).pathSettings(new PlayerPrefs(false, 9, null), false).maxFallHeight());
  }

  @Test
  void preferencesParseValidateAndRoundTrip() throws Exception {
    Settings server = withBreak(true, 4);
    PlayerPrefs prefs =
        PlayerPrefs.defaults()
            .with("ALLOWBREAK", "yes", server)
            .with("maxFallHeight", " 2 ", server)
            .with("guidanceInterval", "10", server);
    assertEquals(new PlayerPrefs(true, 2, 10), prefs);
    assertEquals(prefs, PlayerPrefs.fromMap(prefs.toMap(), server));
    assertEquals(Map.of(), PlayerPrefs.defaults().toMap());
    assertEquals("2", prefs.describe(server).get("maxFallHeight"));
    assertEquals(
        "4 (server default)", PlayerPrefs.defaults().describe(server).get("maxFallHeight"));
    assertEquals(new PlayerPrefs(true, null, 10), prefs.reset("maxfallheight"));

    assertThrows(
        PlayerPrefs.InvalidPreferenceException.class, () -> prefs.with("speed", "1", server));
    assertThrows(
        PlayerPrefs.InvalidPreferenceException.class,
        () -> prefs.with("allowBreak", "maybe", server));
    assertThrows(
        PlayerPrefs.InvalidPreferenceException.class,
        () -> prefs.with("maxFallHeight", "5", server));
    assertThrows(
        PlayerPrefs.InvalidPreferenceException.class,
        () -> prefs.with("maxFallHeight", "x", server));
    assertThrows(PlayerPrefs.InvalidPreferenceException.class, () -> prefs.reset("nope"));
  }

  @Test
  void storedPreferencesThatNoLongerValidateAreDropped() {
    Settings strict = withBreak(false, 1);
    PlayerPrefs prefs = PlayerPrefs.fromMap(Map.of("maxFallHeight", "3", "bogus", "1"), strict);
    assertEquals(PlayerPrefs.defaults(), prefs);
  }
}
