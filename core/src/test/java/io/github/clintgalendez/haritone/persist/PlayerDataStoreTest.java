package io.github.clintgalendez.haritone.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.persist.PlayerDataStore.PlayerData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDataStoreTest {

  private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

  @TempDir Path dir;

  @Test
  void missingFileMeansNoData() throws Exception {
    PlayerDataStore.Loaded loaded = new PlayerDataStore(dir).load(PLAYER);
    assertEquals(PlayerData.empty(), loaded.data());
    assertEquals(List.of(), loaded.problems());
  }

  @Test
  void roundTripsWaypointsAndPrefs() throws Exception {
    PlayerDataStore store = new PlayerDataStore(dir.resolve("players"));
    PlayerData data =
        new PlayerData(
            Map.of(
                "home", new Waypoint("home", "My World", new BlockPos(-10, 64, 2_000_000)),
                "mine", new Waypoint("mine", "default", new BlockPos(5, 12, -5))),
            Map.of("allowBreak", "true"));
    store.save(PLAYER, data);
    PlayerDataStore.Loaded loaded = store.load(PLAYER);
    assertEquals(data, loaded.data());
    assertEquals(List.of(), loaded.problems());
    try (Stream<Path> files = Files.list(dir.resolve("players"))) {
      assertEquals(
          List.of(PLAYER + ".properties"),
          files.map(p -> p.getFileName().toString()).toList(),
          "no temporary files left behind");
    }
  }

  @Test
  void skipsInvalidEntriesAndReportsThem() throws Exception {
    Files.writeString(
        dir.resolve(PLAYER + ".properties"),
        """
        wp.good=1 2 3 default
        wp.bad\\ name=1 2 3 default
        wp.short=1 2
        wp.nan=a b c default
        wp.far=999999999 2 3 default
        pref.allowBreak=true
        other=1
        """,
        StandardCharsets.ISO_8859_1);
    PlayerDataStore.Loaded loaded = new PlayerDataStore(dir).load(PLAYER);
    assertEquals(
        Map.of("good", new Waypoint("good", "default", new BlockPos(1, 2, 3))),
        loaded.data().waypoints());
    assertEquals(Map.of("allowBreak", "true"), loaded.data().prefs());
    assertEquals(5, loaded.problems().size(), loaded.problems().toString());
  }

  @Test
  void oversizedFilesAreIgnored() throws Exception {
    Files.write(
        dir.resolve(PLAYER + ".properties"), new byte[(int) PlayerDataStore.MAX_FILE_BYTES + 1]);
    PlayerDataStore.Loaded loaded = new PlayerDataStore(dir).load(PLAYER);
    assertEquals(PlayerData.empty(), loaded.data());
    assertEquals(1, loaded.problems().size());
  }

  @Test
  void malformedFilesAreIgnored() throws Exception {
    Files.writeString(
        dir.resolve(PLAYER + ".properties"), "wp.x=\\uZZZZ", StandardCharsets.ISO_8859_1);
    PlayerDataStore.Loaded loaded = new PlayerDataStore(dir).load(PLAYER);
    assertEquals(PlayerData.empty(), loaded.data());
    assertTrue(loaded.problems().getFirst().contains("malformed"));
  }

  @Test
  void waypointValidation() {
    assertTrue(Waypoint.isValidName("Home_2-b"));
    assertFalse(Waypoint.isValidName("has space"));
    assertFalse(Waypoint.isValidName("x".repeat(33)));
    assertFalse(Waypoint.isValidName(null));
    assertFalse(Waypoint.isValidWorld("bad\nworld"));
    assertFalse(Waypoint.isValidWorld(""));
    assertThrows(
        IllegalArgumentException.class, () -> new Waypoint("ok", "w\u0000", new BlockPos(0, 0, 0)));
  }
}
