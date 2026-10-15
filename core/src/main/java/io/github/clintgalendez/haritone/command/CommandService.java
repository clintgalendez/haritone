package io.github.clintgalendez.haritone.command;

import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.api.Goal;
import io.github.clintgalendez.haritone.api.Goals;
import io.github.clintgalendez.haritone.config.PlayerPrefs;
import io.github.clintgalendez.haritone.config.Settings;
import io.github.clintgalendez.haritone.navigation.NavigationSession;
import io.github.clintgalendez.haritone.navigation.Navigator;
import io.github.clintgalendez.haritone.persist.PlayerDataStore;
import io.github.clintgalendez.haritone.persist.PlayerDataStore.PlayerData;
import io.github.clintgalendez.haritone.persist.Waypoint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Everything the {@code /haritone} commands do, independent of the game's command system so it can
 * be tested directly. The game adapter parses arguments, checks the node permissions, and passes
 * the results here; this class validates values and runs the action.
 */
public final class CommandService {

  /** The player running a command, as seen at that moment. */
  public record Player(
      UUID id, String world, double x, double y, double z, Predicate<String> permissions) {}

  /** Lines to show the player. {@code ok} is false for errors. */
  public record Reply(boolean ok, List<String> lines) {
    public Reply {
      lines = List.copyOf(lines);
    }

    static Reply ok(String... lines) {
      return new Reply(true, List.of(lines));
    }

    static Reply error(String line) {
      return new Reply(false, List.of(line));
    }
  }

  private final Settings settings;
  private final Navigator navigator;
  private final PlayerDataStore store;
  private final int worldMinY;
  private final int worldMaxY;
  private final Consumer<String> warnings;
  private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

  /**
   * @param settings validated server settings
   * @param worldMinY lowest feet y in the game's worlds
   * @param worldMaxY highest feet y in the game's worlds
   * @param warnings receives problems worth logging, such as unreadable player files
   */
  public CommandService(
      Settings settings,
      Navigator navigator,
      PlayerDataStore store,
      int worldMinY,
      int worldMaxY,
      Consumer<String> warnings) {
    this.settings = settings;
    this.navigator = navigator;
    this.store = store;
    this.worldMinY = worldMinY;
    this.worldMaxY = worldMaxY;
    this.warnings = warnings;
  }

  public Reply gotoBlock(Player player, int x, int y, int z) {
    return navigate(player, x, z, y, new Goals.AtBlock(new BlockPos(x, y, z)), false);
  }

  public Reply gotoColumn(Player player, int x, int z) {
    return navigate(player, x, z, null, new Goals.Column(x, z), false);
  }

  public Reply gotoLevel(Player player, int y) {
    return navigate(
        player,
        (int) Math.floor(player.x()),
        (int) Math.floor(player.z()),
        y,
        new Goals.Level(y),
        false);
  }

  /** Plans a route to a block and reports it without guiding. */
  public Reply plan(Player player, int x, int y, int z) {
    return navigate(player, x, z, y, new Goals.AtBlock(new BlockPos(x, y, z)), true);
  }

  public Reply find(Player player, String blockKey, Integer radius) {
    int r = radius == null ? settings.blockSearchRadius() : radius;
    if (r < 1 || r > settings.blockSearchRadius()) {
      return Reply.error("Radius must be between 1 and " + settings.blockSearchRadius() + ".");
    }
    if (blockKey == null || blockKey.isBlank() || blockKey.length() > 128) {
      return Reply.error("Give a block id to look for, for example Rock_Stone.");
    }
    return start(
        player,
        null,
        new NavigationSession.BlockTarget(blockKey.trim(), r),
        false,
        "Looking for the nearest " + blockKey.trim() + " within " + r + " blocks.");
  }

  public Reply stop(Player player) {
    return navigator.cancel(player.id(), "stopped by player")
        ? Reply.ok("Navigation stopped.")
        : Reply.error("You are not navigating.");
  }

  public Reply status(Player player) {
    Optional<NavigationSession> session = navigator.session(player.id());
    if (session.isEmpty()) {
      return Reply.ok("Idle: no navigation this session.");
    }
    NavigationSession.Snapshot s = session.get().snapshot();
    List<String> lines = new ArrayList<>();
    lines.add("Status: " + s.status() + " (" + s.detail() + ")");
    lines.add("Goal: " + s.goal());
    if (s.moves() > 0) {
      lines.add(
          "Progress: step "
              + s.progress()
              + " of "
              + s.moves()
              + ", about "
              + Math.round(s.remaining())
              + " blocks left in this segment");
    }
    lines.add(
        String.format(
            Locale.ROOT,
            "Segments: %d, replans: %d, nodes searched: %d, planning time: %d ms",
            s.segments(),
            s.replans(),
            s.nodesExpanded(),
            s.planningNanos() / 1_000_000));
    return new Reply(true, lines);
  }

  public Reply waypointAdd(Player player, String name) {
    if (!Waypoint.isValidName(name)) {
      return Reply.error("Waypoint names use 1 to 32 letters, digits, '_' or '-'.");
    }
    if (!Waypoint.isValidWorld(player.world())) {
      return Reply.error("This world's name cannot be stored.");
    }
    Optional<PlayerData> data = data(player.id());
    if (data.isEmpty()) {
      return Reply.error("Your saved data could not be read; see the server log.");
    }
    Map<String, Waypoint> waypoints = new TreeMap<>(data.get().waypoints());
    String key = name.toLowerCase(Locale.ROOT);
    boolean replacing = waypoints.containsKey(key);
    if (!replacing && waypoints.size() >= settings.maxWaypoints()) {
      return Reply.error(
          "You already have the maximum of " + settings.maxWaypoints() + " waypoints.");
    }
    BlockPos pos = BlockPos.containing(player.x(), player.y() + 0.001, player.z());
    if (!BlockPos.inRange(pos.x(), pos.y(), pos.z())) {
      return Reply.error("Your position cannot be stored.");
    }
    waypoints.put(key, new Waypoint(key, player.world(), pos));
    return save(
        player.id(),
        new PlayerData(waypoints, data.get().prefs()),
        (replacing ? "Updated" : "Saved") + " waypoint " + key + " at " + pos + ".");
  }

  public Reply waypointRemove(Player player, String name) {
    Optional<PlayerData> data = data(player.id());
    if (data.isEmpty()) {
      return Reply.error("Your saved data could not be read; see the server log.");
    }
    String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
    Map<String, Waypoint> waypoints = new TreeMap<>(data.get().waypoints());
    if (waypoints.remove(key) == null) {
      return Reply.error("No waypoint named " + name + ".");
    }
    return save(
        player.id(),
        new PlayerData(waypoints, data.get().prefs()),
        "Removed waypoint " + key + ".");
  }

  public Reply waypointList(Player player) {
    Optional<PlayerData> data = data(player.id());
    if (data.isEmpty()) {
      return Reply.error("Your saved data could not be read; see the server log.");
    }
    if (data.get().waypoints().isEmpty()) {
      return Reply.ok("No waypoints yet. Save one with /haritone wp add <name>.");
    }
    List<String> lines = new ArrayList<>();
    lines.add("Waypoints (" + data.get().waypoints().size() + "/" + settings.maxWaypoints() + "):");
    new TreeMap<>(data.get().waypoints())
        .values()
        .forEach(w -> lines.add("  " + w.name() + ": " + w.pos() + " in " + w.world()));
    return new Reply(true, lines);
  }

  public Reply waypointGoto(Player player, String name) {
    Optional<PlayerData> data = data(player.id());
    if (data.isEmpty()) {
      return Reply.error("Your saved data could not be read; see the server log.");
    }
    Waypoint waypoint =
        data.get().waypoints().get(name == null ? "" : name.toLowerCase(Locale.ROOT));
    if (waypoint == null) {
      return Reply.error("No waypoint named " + name + ".");
    }
    if (!waypoint.world().equals(player.world())) {
      return Reply.error(
          "Waypoint "
              + waypoint.name()
              + " is in world "
              + waypoint.world()
              + "; travel there first.");
    }
    return gotoBlock(player, waypoint.pos().x(), waypoint.pos().y(), waypoint.pos().z());
  }

  public Reply set(Player player, String key, String value) {
    Optional<PlayerData> data = data(player.id());
    if (data.isEmpty()) {
      return Reply.error("Your saved data could not be read; see the server log.");
    }
    PlayerPrefs prefs = PlayerPrefs.fromMap(data.get().prefs(), settings);
    PlayerPrefs updated;
    try {
      updated = prefs.with(key, value, settings);
    } catch (PlayerPrefs.InvalidPreferenceException e) {
      return Reply.error(e.getMessage());
    }
    if (updated.allowBreak() && !prefs.allowBreak()) {
      if (!settings.allowBreak()) {
        return Reply.error("Break-aware planning is disabled on this server.");
      }
      if (!player.permissions().test(Permissions.BREAK)) {
        return Reply.error(
            "You need the " + Permissions.BREAK + " permission to plan through blocks.");
      }
    }
    String canonical = PlayerPrefs.canonicalKey(key);
    return save(
        player.id(),
        new PlayerData(data.get().waypoints(), updated.toMap()),
        canonical
            + " is now "
            + updated.describe(settings).get(canonical)
            + ". It applies to your next navigation.");
  }

  public Reply get(Player player, String key) {
    Optional<PlayerData> data = data(player.id());
    if (data.isEmpty()) {
      return Reply.error("Your saved data could not be read; see the server log.");
    }
    Map<String, String> values =
        PlayerPrefs.fromMap(data.get().prefs(), settings).describe(settings);
    if (key != null) {
      String canonical = PlayerPrefs.canonicalKey(key);
      if (canonical == null) {
        return Reply.error(
            "Unknown setting '" + key + "'. Settings: " + String.join(", ", PlayerPrefs.KEYS));
      }
      return Reply.ok(canonical + " = " + values.get(canonical));
    }
    List<String> lines = new ArrayList<>();
    values.forEach((k, v) -> lines.add(k + " = " + v));
    return new Reply(true, lines);
  }

  public Reply reset(Player player, String key) {
    Optional<PlayerData> data = data(player.id());
    if (data.isEmpty()) {
      return Reply.error("Your saved data could not be read; see the server log.");
    }
    PlayerPrefs prefs = PlayerPrefs.fromMap(data.get().prefs(), settings);
    PlayerPrefs updated;
    if (key == null) {
      updated = PlayerPrefs.defaults();
    } else {
      try {
        updated = prefs.reset(key);
      } catch (PlayerPrefs.InvalidPreferenceException e) {
        return Reply.error(e.getMessage());
      }
    }
    return save(
        player.id(),
        new PlayerData(data.get().waypoints(), updated.toMap()),
        key == null ? "All settings reset." : PlayerPrefs.canonicalKey(key) + " reset.");
  }

  public Reply help() {
    return Reply.ok(
        "Haritone plans routes and guides you along them. It does not move you.",
        "/haritone goto <x> <y> <z>  - guide to a block",
        "/haritone goto <x> <z>  - guide to a column",
        "/haritone level <y>  - guide to a height",
        "/haritone find <blockId> [radius]  - guide to the nearest block of a type",
        "/haritone plan <x> <y> <z>  - plan and report a route without guiding",
        "/haritone stop | status",
        "/haritone wp add|remove|goto <name>, /haritone wp list",
        "/haritone set <key> <value>, get [key], reset [key]  - keys: "
            + String.join(", ", PlayerPrefs.KEYS));
  }

  /** Forgets a player who left: cancels navigation and drops cached data. */
  public void playerLeft(UUID player) {
    navigator.forget(player, "player left");
    cache.remove(player);
  }

  private Reply navigate(Player player, int x, int z, Integer y, Goal goal, boolean planOnly) {
    if (y != null && (y < worldMinY || y > worldMaxY)) {
      return Reply.error("y must be between " + worldMinY + " and " + worldMaxY + ".");
    }
    if (!BlockPos.inRange(x, y == null ? 0 : y, z)) {
      return Reply.error("Those coordinates are out of range.");
    }
    double dx = x + 0.5 - player.x();
    double dz = z + 0.5 - player.z();
    double distance = Math.sqrt(dx * dx + dz * dz);
    if (distance > settings.maxGoalDistance()) {
      return Reply.error(
          "That is "
              + Math.round(distance)
              + " blocks away; the limit is "
              + settings.maxGoalDistance()
              + ".");
    }
    return start(player, goal, null, planOnly, "Planning a route to " + goal.describe() + ".");
  }

  private Reply start(
      Player player,
      Goal goal,
      NavigationSession.BlockTarget target,
      boolean planOnly,
      String message) {
    Optional<PlayerData> data = data(player.id());
    PlayerPrefs prefs =
        data.map(d -> PlayerPrefs.fromMap(d.prefs(), settings)).orElse(PlayerPrefs.defaults());
    boolean mayBreak = player.permissions().test(Permissions.BREAK);
    NavigationSession.Request request =
        new NavigationSession.Request(
            player.id(),
            player.world(),
            goal,
            target,
            planOnly,
            settings.pathSettings(prefs, mayBreak),
            settings.guideSettings(prefs));
    try {
      navigator.start(request);
    } catch (Navigator.BusyException e) {
      return Reply.error(e.getMessage());
    }
    return Reply.ok(message);
  }

  private Optional<PlayerData> data(UUID player) {
    PlayerData cached = cache.get(player);
    if (cached != null) {
      return Optional.of(cached);
    }
    try {
      PlayerDataStore.Loaded loaded = store.load(player);
      loaded.problems().forEach(p -> warnings.accept("Player data " + player + ": " + p));
      cache.put(player, loaded.data());
      return Optional.of(loaded.data());
    } catch (IOException e) {
      warnings.accept("Could not read player data for " + player + ": " + e);
      return Optional.empty();
    }
  }

  private Reply save(UUID player, PlayerData data, String message) {
    try {
      store.save(player, data);
    } catch (IOException e) {
      warnings.accept("Could not save player data for " + player + ": " + e);
      return Reply.error("Could not save; see the server log.");
    }
    cache.put(player, data);
    return Reply.ok(message);
  }
}
