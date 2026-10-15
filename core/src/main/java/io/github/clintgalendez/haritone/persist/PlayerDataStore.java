package io.github.clintgalendez.haritone.persist;

import io.github.clintgalendez.haritone.api.BlockPos;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Stores each player's waypoints and preferences in {@code <directory>/<uuid>.properties}. That is
 * all Haritone persists about a player; nothing leaves the server.
 *
 * <p>Files are size-capped, every entry is validated on load (bad entries are skipped and
 * reported), and writes go to a temporary file that then replaces the original, so a crash never
 * leaves a half-written file.
 */
public final class PlayerDataStore {

  /** Largest file accepted on load. A full set of waypoints is far smaller. */
  public static final long MAX_FILE_BYTES = 256 * 1024;

  private static final String WAYPOINT = "wp.";
  private static final String PREF = "pref.";

  /** Waypoints and raw preference values for one player. */
  public record PlayerData(Map<String, Waypoint> waypoints, Map<String, String> prefs) {
    public PlayerData {
      waypoints = Map.copyOf(waypoints);
      prefs = Map.copyOf(prefs);
    }

    public static PlayerData empty() {
      return new PlayerData(Map.of(), Map.of());
    }
  }

  /** A load result: the usable data plus a description of anything skipped. */
  public record Loaded(PlayerData data, List<String> problems) {}

  private final Path directory;

  public PlayerDataStore(Path directory) {
    this.directory = directory;
  }

  private Path file(UUID player) {
    return directory.resolve(player + ".properties");
  }

  public Loaded load(UUID player) throws IOException {
    Path file = file(player);
    if (!Files.exists(file)) {
      return new Loaded(PlayerData.empty(), List.of());
    }
    List<String> problems = new ArrayList<>();
    if (Files.size(file) > MAX_FILE_BYTES) {
      problems.add(file.getFileName() + " is larger than " + MAX_FILE_BYTES + " bytes; ignored");
      return new Loaded(PlayerData.empty(), problems);
    }
    Properties properties = new Properties();
    try (InputStream in = Files.newInputStream(file)) {
      properties.load(in);
    } catch (IllegalArgumentException e) {
      problems.add(file.getFileName() + " is malformed (" + e.getMessage() + "); ignored");
      return new Loaded(PlayerData.empty(), problems);
    }
    Map<String, Waypoint> waypoints = new TreeMap<>();
    Map<String, String> prefs = new TreeMap<>();
    for (String key : properties.stringPropertyNames()) {
      String value = properties.getProperty(key);
      if (key.startsWith(WAYPOINT)) {
        Waypoint waypoint = parseWaypoint(key.substring(WAYPOINT.length()), value);
        if (waypoint == null) {
          problems.add("skipped invalid waypoint entry '" + key + "'");
        } else {
          waypoints.put(waypoint.name(), waypoint);
        }
      } else if (key.startsWith(PREF) && value.length() <= 64) {
        prefs.put(key.substring(PREF.length()), value);
      } else {
        problems.add("skipped unknown entry '" + key + "'");
      }
    }
    return new Loaded(new PlayerData(waypoints, prefs), problems);
  }

  public void save(UUID player, PlayerData data) throws IOException {
    Properties properties = new Properties();
    for (Waypoint waypoint : data.waypoints().values()) {
      BlockPos p = waypoint.pos();
      properties.setProperty(
          WAYPOINT + waypoint.name(), p.x() + " " + p.y() + " " + p.z() + " " + waypoint.world());
    }
    for (Map.Entry<String, String> pref : data.prefs().entrySet()) {
      properties.setProperty(PREF + pref.getKey(), pref.getValue());
    }
    Files.createDirectories(directory);
    Path target = file(player);
    Path temp = Files.createTempFile(directory, player.toString(), ".tmp");
    try {
      try (OutputStream out = Files.newOutputStream(temp)) {
        properties.store(out, "Haritone player data");
      }
      try {
        Files.move(
            temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /** Format: {@code x y z world}; the world name may contain spaces. */
  private static Waypoint parseWaypoint(String name, String value) {
    String[] parts = value.split(" ", 4);
    if (parts.length != 4) {
      return null;
    }
    try {
      int x = Integer.parseInt(parts[0]);
      int y = Integer.parseInt(parts[1]);
      int z = Integer.parseInt(parts[2]);
      if (!BlockPos.inRange(x, y, z)
          || !Waypoint.isValidName(name)
          || !Waypoint.isValidWorld(parts[3])) {
        return null;
      }
      return new Waypoint(name, parts[3], new BlockPos(x, y, z));
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
