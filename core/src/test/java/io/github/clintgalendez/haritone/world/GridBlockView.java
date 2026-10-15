package io.github.clintgalendez.haritone.world;

import io.github.clintgalendez.haritone.api.BlockPos;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * In-memory world for tests. Inside the known area, blocks below {@code groundY} are solid and
 * blocks at or above it are open unless overridden. Everything outside the known area is unknown.
 *
 * <p>{@link #parse(String...)} builds a flat map from rows of characters, one row per z and one
 * column per x, with the walking level at y = 1:
 *
 * <pre>
 *   .  floor          #  wall (two blocks high)    S  start    G  goal
 *   L  lava pit       W  water at feet level       ?  unknown column
 *   H  ladder column  =  one-block step (y = 1 solid)
 * </pre>
 */
public final class GridBlockView implements BlockView {

  private final Map<Long, Terrain> blocks = new HashMap<>();
  private final Map<Long, String> keys = new HashMap<>();
  private final Set<Long> unbreakable = new HashSet<>();
  private final int minX;
  private final int maxX;
  private final int minZ;
  private final int maxZ;
  private final int groundY;
  private long lookups;
  public BlockPos start;
  public BlockPos goal;

  public GridBlockView(int minX, int maxX, int minZ, int maxZ, int groundY) {
    this.minX = minX;
    this.maxX = maxX;
    this.minZ = minZ;
    this.maxZ = maxZ;
    this.groundY = groundY;
  }

  /** Flat ground with feet level at y = 1, known for x and z in [0, size). */
  public static GridBlockView flat(int size) {
    return new GridBlockView(0, size - 1, 0, size - 1, 1);
  }

  public static GridBlockView parse(String... rows) {
    int width = rows[0].length();
    GridBlockView view = new GridBlockView(0, width - 1, 0, rows.length - 1, 1);
    for (int z = 0; z < rows.length; z++) {
      String row = rows[z];
      for (int x = 0; x < row.length(); x++) {
        switch (row.charAt(x)) {
          case '.' -> {}
          case '#' -> view.fill(x, 1, z, x, 2, z, Terrain.SOLID);
          case 'L' -> view.set(x, 0, z, Terrain.DANGER);
          case 'W' -> view.fill(x, 0, z, x, 1, z, Terrain.WATER);
          case '?' -> view.fill(x, 0, z, x, 319, z, Terrain.UNKNOWN);
          case 'H' -> view.fill(x, 1, z, x, 6, z, Terrain.CLIMBABLE);
          case '=' -> view.set(x, 1, z, Terrain.SOLID);
          case 'S' -> view.start = new BlockPos(x, 1, z);
          case 'G' -> view.goal = new BlockPos(x, 1, z);
          default -> throw new IllegalArgumentException("unknown map char " + row.charAt(x));
        }
      }
    }
    return view;
  }

  public GridBlockView set(int x, int y, int z, Terrain terrain) {
    blocks.put(BlockPos.pack(x, y, z), terrain);
    return this;
  }

  public GridBlockView fill(int x1, int y1, int z1, int x2, int y2, int z2, Terrain terrain) {
    for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
      for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
          set(x, y, z, terrain);
        }
      }
    }
    return this;
  }

  public GridBlockView key(int x, int y, int z, String key) {
    keys.put(BlockPos.pack(x, y, z), key);
    return this;
  }

  public GridBlockView unbreakable(int x, int y, int z) {
    unbreakable.add(BlockPos.pack(x, y, z));
    return this;
  }

  public long lookups() {
    return lookups;
  }

  @Override
  public Terrain terrain(int x, int y, int z) {
    lookups++;
    Terrain override = blocks.get(BlockPos.pack(x, y, z));
    if (override != null) {
      return override;
    }
    if (x < minX || x > maxX || z < minZ || z > maxZ || y < minY() || y > maxY() + 2) {
      return Terrain.UNKNOWN;
    }
    return y < groundY ? Terrain.SOLID : Terrain.OPEN;
  }

  @Override
  public String blockKey(int x, int y, int z) {
    String key = keys.get(BlockPos.pack(x, y, z));
    if (key != null) {
      return key;
    }
    return switch (terrain(x, y, z)) {
      case SOLID -> "Rock";
      case OPEN -> "Empty";
      default -> null;
    };
  }

  @Override
  public boolean isBreakable(int x, int y, int z) {
    return terrain(x, y, z) == Terrain.SOLID && !unbreakable.contains(BlockPos.pack(x, y, z));
  }

  @Override
  public int minY() {
    return 0;
  }

  @Override
  public int maxY() {
    return 317;
  }
}
