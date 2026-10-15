package io.github.clintgalendez.haritone.pathfinding;

import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.world.BlockView;
import io.github.clintgalendez.haritone.world.Terrain;
import java.util.ArrayList;
import java.util.List;

/**
 * Movement rules: where an entity can stand and which moves connect standing positions.
 *
 * <p>The entity is modelled as two blocks tall (feet and head) and one block wide, which matches a
 * Hytale player closely enough for block-level planning. A position is standable when both body
 * blocks are passable (or breakable, if allowed), nothing in the body is dangerous, and the entity
 * is supported: by a solid or climbable block below, by holding a climbable block, or by swimming.
 * Partial blocks such as slabs are treated as full blocks.
 */
public final class MoveEvaluator {

  private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
  private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
  private static final double SQRT2 = Math.sqrt(2);

  /** Receives each move generated from a position. */
  @FunctionalInterface
  interface MoveSink {
    void accept(int x, int y, int z, Movement movement, double cost, List<BlockPos> breaks);
  }

  private final BlockView world;
  private final PathSettings settings;
  private final CostModel costs;
  private final boolean allowUnknown;

  public MoveEvaluator(BlockView world, PathSettings settings) {
    this.world = world;
    this.settings = settings;
    this.costs = settings.costs();
    this.allowUnknown = settings.unknownTerrain() == UnknownTerrainPolicy.ALLOW;
  }

  /** Whether an entity can stand with its feet at {@code (x, y, z)} under these settings. */
  public boolean canStand(int x, int y, int z) {
    return !Double.isNaN(standCost(x, y, z, settings.allowBreak(), null));
  }

  /**
   * Finds the standable position an entity at {@code feet} is really occupying. A player on a
   * partial block, or in the middle of a jump, reports a feet block that is not standable itself.
   * Returns null if nothing nearby is standable.
   */
  public BlockPos snap(BlockPos feet) {
    int[] offsets = {0, 1, -1, -2, 2};
    for (int dy : offsets) {
      BlockPos candidate = feet.offset(0, dy, 0);
      if (BlockPos.inRange(candidate.x(), candidate.y(), candidate.z())
          && !Double.isNaN(standCost(candidate.x(), candidate.y(), candidate.z(), false, null))) {
        return candidate;
      }
    }
    return null;
  }

  /** The highest standable position in column {@code (x, z)}, or null if there is none. */
  public BlockPos highestStandable(int x, int z) {
    for (int y = world.maxY(); y >= world.minY(); y--) {
      if (!Double.isNaN(standCost(x, y, z, false, null))) {
        return new BlockPos(x, y, z);
      }
    }
    return null;
  }

  /**
   * Extra cost of standing at {@code (x, y, z)}, or {@code NaN} when the entity cannot stand there.
   * Blocks that would have to be broken are added to {@code breaksOut} when it is not null.
   */
  double standCost(int x, int y, int z, boolean allowBreak, List<BlockPos> breaksOut) {
    if (y < world.minY() || y > world.maxY() || !BlockPos.inRange(x, y + 1, z)) {
      return Double.NaN;
    }
    double extra = 0;
    Terrain feet = world.terrain(x, y, z);
    for (int dy = 0; dy <= 1; dy++) {
      Terrain body = dy == 0 ? feet : world.terrain(x, y + 1, z);
      double cost = bodyCost(body, x, y + dy, z, allowBreak, breaksOut);
      if (Double.isNaN(cost)) {
        return Double.NaN;
      }
      extra += cost;
    }
    if (feet != Terrain.WATER && feet != Terrain.CLIMBABLE) {
      Terrain below = world.terrain(x, y - 1, z);
      switch (below) {
        case SOLID, CLIMBABLE -> {}
        case UNKNOWN -> {
          if (!allowUnknown) {
            return Double.NaN;
          }
          extra += costs.unknown();
        }
        default -> {
          return Double.NaN;
        }
      }
    }
    if (nextToDanger(x, y, z)) {
      extra += costs.hazardMargin();
    }
    return extra;
  }

  private double bodyCost(
      Terrain terrain, int x, int y, int z, boolean allowBreak, List<BlockPos> breaksOut) {
    return switch (terrain) {
      case OPEN, WATER, CLIMBABLE -> 0;
      case UNKNOWN -> allowUnknown ? costs.unknown() : Double.NaN;
      case SOLID -> {
        if (allowBreak && world.isBreakable(x, y, z)) {
          if (breaksOut != null) {
            breaksOut.add(new BlockPos(x, y, z));
          }
          yield costs.breakBlock();
        }
        yield Double.NaN;
      }
      case DANGER -> Double.NaN;
    };
  }

  private boolean nextToDanger(int x, int y, int z) {
    if (costs.hazardMargin() == 0) {
      return false;
    }
    for (int dy = -1; dy <= 1; dy++) {
      for (int[] d : CARDINALS) {
        if (world.terrain(x + d[0], y + dy, z + d[1]) == Terrain.DANGER) {
          return true;
        }
      }
    }
    return world.terrain(x, y - 1, z) == Terrain.DANGER;
  }

  private boolean passable(int x, int y, int z) {
    Terrain terrain = world.terrain(x, y, z);
    return terrain.isPassable() || (allowUnknown && terrain == Terrain.UNKNOWN);
  }

  private double waterFactor(int x, int y, int z) {
    return world.terrain(x, y, z) == Terrain.WATER ? costs.water() : 1;
  }

  /** Generates every move from the standable position {@code (x, y, z)}. */
  void forEachMove(int x, int y, int z, MoveSink sink) {
    boolean allowBreak = settings.allowBreak();
    for (int[] d : CARDINALS) {
      int nx = x + d[0];
      int nz = z + d[1];

      List<BlockPos> breaks = allowBreak ? new ArrayList<>(2) : null;
      double stand = standCost(nx, y, nz, allowBreak, breaks);
      if (!Double.isNaN(stand)) {
        sink.accept(nx, y, nz, Movement.TRAVERSE, waterFactor(nx, y, nz) + stand, orEmpty(breaks));
      }

      ascend(x, y, z, nx, nz, allowBreak, sink);
      if (Double.isNaN(stand)) {
        descend(y, nx, nz, sink); // where the entity can stand, it traverses instead
      }
    }

    for (int[] d : DIAGONALS) {
      int nx = x + d[0];
      int nz = z + d[1];
      // No corner cutting: both orthogonal neighbours must have room for the whole body.
      if (passable(nx, y, z)
          && passable(nx, y + 1, z)
          && passable(x, y, nz)
          && passable(x, y + 1, nz)) {
        double stand = standCost(nx, y, nz, false, null);
        if (!Double.isNaN(stand)) {
          sink.accept(
              nx, y, nz, Movement.DIAGONAL, SQRT2 * waterFactor(nx, y, nz) + stand, List.of());
        }
      }
    }

    Terrain feet = world.terrain(x, y, z);
    Terrain below = world.terrain(x, y - 1, z);
    if (feet == Terrain.CLIMBABLE || feet == Terrain.WATER) {
      double up = standCost(x, y + 1, z, false, null);
      if (!Double.isNaN(up)) {
        boolean swim = feet == Terrain.WATER;
        sink.accept(
            x,
            y + 1,
            z,
            swim ? Movement.SWIM_UP : Movement.CLIMB_UP,
            (swim ? costs.water() : costs.climbUp()) + up,
            List.of());
      }
    }
    if (below == Terrain.CLIMBABLE || below == Terrain.WATER) {
      double down = standCost(x, y - 1, z, false, null);
      if (!Double.isNaN(down)) {
        boolean swim = below == Terrain.WATER;
        sink.accept(
            x,
            y - 1,
            z,
            swim ? Movement.SWIM_DOWN : Movement.CLIMB_DOWN,
            (swim ? costs.water() : costs.climbDown()) + down,
            List.of());
      }
    }
  }

  private static List<BlockPos> orEmpty(List<BlockPos> breaks) {
    return breaks == null ? List.of() : breaks;
  }

  private void ascend(int x, int y, int z, int nx, int nz, boolean allowBreak, MoveSink sink) {
    List<BlockPos> breaks = allowBreak ? new ArrayList<>(3) : null;
    // Jumping needs headroom above the starting position.
    double headroom = bodyCost(world.terrain(x, y + 2, z), x, y + 2, z, allowBreak, breaks);
    if (Double.isNaN(headroom)) {
      return;
    }
    double stand = standCost(nx, y + 1, nz, allowBreak, breaks);
    if (!Double.isNaN(stand)) {
      sink.accept(
          nx,
          y + 1,
          nz,
          Movement.ASCEND,
          waterFactor(nx, y + 1, nz) + costs.ascend() + headroom + stand,
          orEmpty(breaks));
    }
  }

  /** Called only when the entity cannot stand at {@code (nx, y, nz)}. */
  private void descend(int y, int nx, int nz, MoveSink sink) {
    // Step off the edge at the current level, then drop until something holds the entity.
    if (!passable(nx, y, nz) || !passable(nx, y + 1, nz)) {
      return;
    }
    for (int drop = 1; drop <= settings.maxFallHeight(); drop++) {
      int ny = y - drop;
      double stand = standCost(nx, ny, nz, false, null);
      if (!Double.isNaN(stand)) {
        sink.accept(
            nx,
            ny,
            nz,
            Movement.DESCEND,
            waterFactor(nx, ny, nz) + drop * costs.descendPerBlock() + stand,
            List.of());
        return;
      }
      if (!passable(nx, ny, nz)) {
        return;
      }
    }
  }
}
