package io.github.clintgalendez.haritone.persist;

import io.github.clintgalendez.haritone.api.BlockPos;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A named position saved by a player.
 *
 * @param name 1 to 32 letters, digits, '_' or '-'
 * @param world the world it was saved in
 * @param pos the saved feet position
 */
public record Waypoint(String name, String world, BlockPos pos) {

  private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

  public Waypoint {
    if (!isValidName(name)) {
      throw new IllegalArgumentException("invalid waypoint name: " + name);
    }
    if (!isValidWorld(world)) {
      throw new IllegalArgumentException("invalid world name");
    }
    Objects.requireNonNull(pos, "pos");
  }

  public static boolean isValidName(String name) {
    return name != null && NAME.matcher(name).matches();
  }

  /** World names are stored as-is, so reject anything that could corrupt a line. */
  public static boolean isValidWorld(String world) {
    if (world == null || world.isEmpty() || world.length() > 64) {
      return false;
    }
    return world.chars().noneMatch(Character::isISOControl);
  }
}
