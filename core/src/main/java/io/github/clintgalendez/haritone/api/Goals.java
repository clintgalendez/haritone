package io.github.clintgalendez.haritone.api;

import java.util.List;
import java.util.Objects;

/** Built-in goal types. */
public final class Goals {

  private Goals() {}

  /** Stand with feet exactly at {@code pos}. */
  public record AtBlock(BlockPos pos) implements Goal {
    public AtBlock {
      Objects.requireNonNull(pos, "pos");
    }

    @Override
    public boolean isSatisfied(int x, int y, int z) {
      return x == pos.x() && y == pos.y() && z == pos.z();
    }

    @Override
    public double heuristic(int x, int y, int z, double verticalCost) {
      return Goal.octile(pos.x() - x, pos.z() - z) + Math.abs(pos.y() - y) * verticalCost;
    }

    @Override
    public String describe() {
      return "block " + pos;
    }
  }

  /** Stand with feet within {@code radius} blocks (Euclidean) of {@code center}. */
  public record Near(BlockPos center, int radius) implements Goal {
    public Near {
      Objects.requireNonNull(center, "center");
      if (radius < 0) {
        throw new IllegalArgumentException("radius must be >= 0");
      }
    }

    @Override
    public boolean isSatisfied(int x, int y, int z) {
      long dx = (long) x - center.x();
      long dy = (long) y - center.y();
      long dz = (long) z - center.z();
      return dx * dx + dy * dy + dz * dz <= (long) radius * radius;
    }

    @Override
    public double heuristic(int x, int y, int z, double verticalCost) {
      // Subtract the most any point inside the sphere can save (an upper bound on the cost metric
      // over a vector of length radius) so the estimate stays a lower bound.
      double h =
          Goal.octile(center.x() - x, center.z() - z) + Math.abs(center.y() - y) * verticalCost;
      return Math.max(0, h - radius * (Math.sqrt(2) + verticalCost));
    }

    @Override
    public String describe() {
      return "within " + radius + " of " + center;
    }
  }

  /** Reach column {@code (x, z)} at any height. */
  public record Column(int x, int z) implements Goal {
    @Override
    public boolean isSatisfied(int px, int py, int pz) {
      return px == x && pz == z;
    }

    @Override
    public double heuristic(int px, int py, int pz, double verticalCost) {
      return Goal.octile(x - px, z - pz);
    }

    @Override
    public String describe() {
      return "column " + x + " " + z;
    }
  }

  /** Reach height {@code y} anywhere. */
  public record Level(int y) implements Goal {
    @Override
    public boolean isSatisfied(int px, int py, int pz) {
      return py == y;
    }

    @Override
    public double heuristic(int px, int py, int pz, double verticalCost) {
      return Math.abs(y - py) * verticalCost;
    }

    @Override
    public String describe() {
      return "level y=" + y;
    }
  }

  /**
   * Stand next to {@code target}, usually a solid block you want to reach: feet within one block
   * horizontally (diagonals included) and from one below to one above it, but not inside it.
   */
  public record Adjacent(BlockPos target) implements Goal {
    public Adjacent {
      Objects.requireNonNull(target, "target");
    }

    @Override
    public boolean isSatisfied(int x, int y, int z) {
      int dx = Math.abs(x - target.x());
      int dy = y - target.y();
      int dz = Math.abs(z - target.z());
      boolean inside = dx == 0 && dz == 0 && dy == 0;
      return dx <= 1 && dz <= 1 && dy >= -1 && dy <= 1 && !inside;
    }

    @Override
    public double heuristic(int x, int y, int z, double verticalCost) {
      int dx = Math.max(0, Math.abs(target.x() - x) - 1);
      int dz = Math.max(0, Math.abs(target.z() - z) - 1);
      int dy = Math.max(0, Math.abs(target.y() - y) - 1);
      return Goal.octile(dx, dz) + dy * verticalCost;
    }

    @Override
    public String describe() {
      return "next to " + target;
    }
  }

  /** Satisfied by any of {@code goals}. The heuristic is the smallest of theirs. */
  public record AnyOf(List<Goal> goals) implements Goal {
    public AnyOf {
      goals = List.copyOf(goals);
      if (goals.isEmpty()) {
        throw new IllegalArgumentException("goals must not be empty");
      }
    }

    @Override
    public boolean isSatisfied(int x, int y, int z) {
      for (Goal goal : goals) {
        if (goal.isSatisfied(x, y, z)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public double heuristic(int x, int y, int z, double verticalCost) {
      double best = Double.POSITIVE_INFINITY;
      for (Goal goal : goals) {
        best = Math.min(best, goal.heuristic(x, y, z, verticalCost));
      }
      return best;
    }

    @Override
    public String describe() {
      return "any of " + goals.size() + " goals";
    }
  }
}
