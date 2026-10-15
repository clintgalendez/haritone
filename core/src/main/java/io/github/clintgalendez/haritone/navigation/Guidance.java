package io.github.clintgalendez.haritone.navigation;

import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.pathfinding.Movement;
import io.github.clintgalendez.haritone.pathfinding.Path;
import io.github.clintgalendez.haritone.pathfinding.PathStep;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a path into short instructions. Runs of steps in the same direction merge into one leg, so
 * a long diagonal walk reads as one instruction instead of dozens of single steps.
 *
 * <p>Compass directions assume north is negative z and east is positive x. Every instruction also
 * gives target coordinates, which are authoritative.
 */
public final class Guidance {

  private Guidance() {}

  /** One merged run of similar steps. */
  public record Leg(Movement movement, int dx, int dz, int count, BlockPos end, int breaks) {}

  /** Merges the steps after {@code index} into legs. */
  public static List<Leg> legs(Path path, int index) {
    List<PathStep> steps = path.steps();
    List<Leg> legs = new ArrayList<>();
    for (int i = index + 1; i < steps.size(); i++) {
      PathStep step = steps.get(i);
      BlockPos from = steps.get(i - 1).pos();
      int dx = Integer.signum(step.pos().x() - from.x());
      int dz = Integer.signum(step.pos().z() - from.z());
      Movement kind = merged(step.movement());
      if (!legs.isEmpty()) {
        Leg last = legs.getLast();
        boolean horizontal = kind == Movement.TRAVERSE;
        boolean vertical =
            kind == Movement.CLIMB_UP
                || kind == Movement.CLIMB_DOWN
                || kind == Movement.SWIM_UP
                || kind == Movement.SWIM_DOWN;
        boolean sameDirection = last.dx() == dx && last.dz() == dz;
        if (last.movement() == kind
            && step.breaks().isEmpty()
            && ((horizontal && sameDirection) || vertical)) {
          legs.set(
              legs.size() - 1, new Leg(kind, dx, dz, last.count() + 1, step.pos(), last.breaks()));
          continue;
        }
      }
      legs.add(new Leg(kind, dx, dz, 1, step.pos(), step.breaks().size()));
    }
    return legs;
  }

  /** Diagonal and straight walking merge the same way, so treat both as a walk. */
  private static Movement merged(Movement movement) {
    return movement == Movement.DIAGONAL ? Movement.TRAVERSE : movement;
  }

  /** Approximate blocks left to walk from step {@code index} to the end. */
  public static double remainingDistance(Path path, int index) {
    double total = 0;
    List<PathStep> steps = path.steps();
    for (int i = index + 1; i < steps.size(); i++) {
      BlockPos a = steps.get(i - 1).pos();
      BlockPos b = steps.get(i).pos();
      int dx = b.x() - a.x();
      int dy = b.y() - a.y();
      int dz = b.z() - a.z();
      total += Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
    }
    return total;
  }

  /** The next one or two instructions plus the distance left, or null at the end. */
  public static String next(Path path, int index) {
    List<Leg> legs = legs(path, index);
    if (legs.isEmpty()) {
      return null;
    }
    StringBuilder text = new StringBuilder("Next: ").append(describe(legs.getFirst()));
    if (legs.size() > 1) {
      text.append(", then ").append(describe(legs.get(1)));
    }
    long left = Math.round(remainingDistance(path, index));
    text.append(". ").append(left).append(left == 1 ? " block" : " blocks").append(" left");
    if (!path.reachesGoal()) {
      text.append(" in this segment");
    }
    return text.append('.').toString();
  }

  static String describe(Leg leg) {
    String text =
        switch (leg.movement()) {
          case TRAVERSE ->
              "walk " + leg.count() + plural(leg.count()) + " " + compass(leg.dx(), leg.dz());
          case ASCEND -> "step up " + compass(leg.dx(), leg.dz());
          case DESCEND -> "drop down " + compass(leg.dx(), leg.dz());
          case CLIMB_UP -> "climb up " + leg.count();
          case CLIMB_DOWN -> "climb down " + leg.count();
          case SWIM_UP -> "swim up " + leg.count();
          case SWIM_DOWN -> "swim down " + leg.count();
          case START, DIAGONAL -> "stay";
        };
    text += " to " + leg.end();
    if (leg.breaks() > 0) {
      text += " (break " + leg.breaks() + plural(leg.breaks()) + " first)";
    }
    return text;
  }

  private static String plural(int count) {
    return count == 1 ? " block" : " blocks";
  }

  static String compass(int dx, int dz) {
    String ns = dz < 0 ? "N" : dz > 0 ? "S" : "";
    String ew = dx > 0 ? "E" : dx < 0 ? "W" : "";
    return ns + ew;
  }
}
