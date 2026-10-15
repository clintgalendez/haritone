package io.github.clintgalendez.haritone.pathfinding;

import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.api.Goal;
import io.github.clintgalendez.haritone.pathfinding.NodeHeap.Node;
import io.github.clintgalendez.haritone.pathfinding.PathResult.Outcome;
import io.github.clintgalendez.haritone.pathfinding.PathResult.StopReason;
import io.github.clintgalendez.haritone.world.BlockView;
import io.github.clintgalendez.haritone.world.BoundedTerrainCache;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A* search over standable block positions that can be paused and resumed.
 *
 * <p>Call {@link #advance(int, long)} repeatedly, each time with a small budget, until it returns a
 * result. This lets a game server spread one search over many ticks on the thread that owns the
 * world, instead of copying world data to another thread. Memory is bounded by {@link
 * PathSettings#maxNodes()} and total compute time by {@link PathSettings#timeoutMillis()}.
 *
 * <p>When the goal cannot be reached (it is enclosed, outside known terrain, or a limit is hit) and
 * partial paths are allowed, the result is a path to the explored position with the lowest
 * estimated remaining cost. Navigation follows it and then plans the next segment.
 *
 * <p>Not thread-safe, except {@link #cancel()}, which may be called from any thread.
 */
public final class PathSearch {

  private static final double EPSILON = 1e-9;

  private final Goal goal;
  private final PathSettings settings;
  private final MoveEvaluator evaluator;
  private final double verticalCost;
  private final Map<Long, Node> nodes = new HashMap<>();
  private final NodeHeap open = new NodeHeap();
  private final Node start;
  private Node best;
  private long nextSeq;
  private int expanded;
  private long elapsedNanos;
  private PathResult result;
  private volatile boolean cancelRequested;

  /**
   * Prepares a search. The start must be a standable position (see {@link
   * MoveEvaluator#snap(BlockPos)}); otherwise the search finishes immediately with {@link
   * StopReason#INVALID_START}.
   */
  public PathSearch(BlockView world, BlockPos start, Goal goal, PathSettings settings) {
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(start, "start");
    this.goal = Objects.requireNonNull(goal, "goal");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.evaluator =
        new MoveEvaluator(new BoundedTerrainCache(world, settings.cacheSize()), settings);
    this.verticalCost = settings.costs().minVerticalCost();

    this.start = new Node(start.x(), start.y(), start.z(), nextSeq++);
    this.start.g = 0;
    this.start.h = heuristic(start.x(), start.y(), start.z());
    this.start.f = this.start.h;
    this.start.movement = Movement.START;
    this.start.breaks = List.of();
    this.best = this.start;

    if (!BlockPos.inRange(start.x(), start.y(), start.z())
        || !evaluator.canStand(start.x(), start.y(), start.z())) {
      result = new PathResult(Outcome.FAILED, StopReason.INVALID_START, null, 0, 0, 0);
      return;
    }
    nodes.put(start.pack(), this.start);
    open.add(this.start);
  }

  /** Requests cancellation. Safe from any thread; takes effect at the next expansion. */
  public void cancel() {
    cancelRequested = true;
  }

  public boolean isDone() {
    return result != null;
  }

  /** The result, or null while the search is still running. */
  public PathResult result() {
    return result;
  }

  /** Nodes expanded so far. */
  public int expanded() {
    return expanded;
  }

  /**
   * Runs the search for at most {@code maxExpansions} expansions and {@code budgetNanos} of time.
   *
   * @return the result once the search has finished, or null if it needs more time
   */
  public PathResult advance(int maxExpansions, long budgetNanos) {
    if (result != null) {
      return result;
    }
    long sliceStart = System.nanoTime();
    PathResult done = runSlice(maxExpansions, budgetNanos, sliceStart);
    elapsedNanos += System.nanoTime() - sliceStart;
    if (done != null) {
      result =
          new PathResult(
              done.outcome(), done.reason(), done.path(), expanded, nodes.size(), elapsedNanos);
    }
    return result;
  }

  private PathResult runSlice(int maxExpansions, long budgetNanos, long sliceStart) {
    long totalBudget = settings.timeoutMillis() * 1_000_000L;
    int sliceExpansions = 0;
    while (true) {
      if (cancelRequested) {
        return finish(Outcome.CANCELLED, StopReason.CANCELLED, null);
      }
      if (open.isEmpty()) {
        return finishPartial(StopReason.EXHAUSTED);
      }
      long now = System.nanoTime();
      if (elapsedNanos + (now - sliceStart) >= totalBudget) {
        return finishPartial(StopReason.TIMEOUT);
      }
      // Always expand at least once per slice so a tiny budget still makes progress.
      if (sliceExpansions > 0
          && (sliceExpansions >= maxExpansions || now - sliceStart >= budgetNanos)) {
        return null;
      }

      Node current = open.poll();
      expanded++;
      sliceExpansions++;
      if (goal.isSatisfied(current.x, current.y, current.z)) {
        return finish(Outcome.FOUND, StopReason.GOAL_REACHED, current);
      }
      if (current.h < best.h || (current.h == best.h && current.g < best.g)) {
        best = current;
      }
      if (!expand(current)) {
        return finishPartial(StopReason.NODE_LIMIT);
      }
    }
  }

  /** Runs to completion with no slicing. Convenient for tests and off-thread use. */
  public PathResult run() {
    PathResult done;
    do {
      done = advance(Integer.MAX_VALUE, Long.MAX_VALUE);
    } while (done == null);
    return done;
  }

  /** Relaxes every neighbour; returns false when the node limit stops the search. */
  private boolean expand(Node current) {
    boolean[] withinLimit = {true};
    evaluator.forEachMove(
        current.x,
        current.y,
        current.z,
        (x, y, z, movement, cost, breaks) -> {
          if (!withinLimit[0]) {
            return;
          }
          double g = current.g + cost;
          long key = BlockPos.pack(x, y, z);
          Node neighbor = nodes.get(key);
          if (neighbor == null) {
            if (nodes.size() >= settings.maxNodes()) {
              withinLimit[0] = false;
              return;
            }
            neighbor = new Node(x, y, z, nextSeq++);
            neighbor.h = heuristic(x, y, z);
            neighbor.g = Double.POSITIVE_INFINITY;
            nodes.put(key, neighbor);
          }
          if (g + EPSILON < neighbor.g) {
            neighbor.g = g;
            neighbor.f = g + neighbor.h;
            neighbor.parent = current;
            neighbor.movement = movement;
            neighbor.breaks = List.copyOf(breaks);
            if (neighbor.inHeap()) {
              open.decreased(neighbor);
            } else {
              open.add(neighbor); // New, or reopened when a weighted heuristic found a shortcut.
            }
          }
        });
    return withinLimit[0];
  }

  private double heuristic(int x, int y, int z) {
    return goal.heuristic(x, y, z, verticalCost) * settings.heuristicWeight();
  }

  private PathResult finishPartial(StopReason reason) {
    boolean progressed = best != start && best.h < start.h;
    if (settings.allowPartial() && progressed) {
      return finish(Outcome.PARTIAL, reason, best);
    }
    return finish(Outcome.FAILED, reason, null);
  }

  private PathResult finish(Outcome outcome, StopReason reason, Node end) {
    Path path = end == null ? null : reconstruct(end, outcome == Outcome.FOUND);
    return new PathResult(outcome, reason, path, expanded, nodes.size(), elapsedNanos);
  }

  private static Path reconstruct(Node end, boolean reachesGoal) {
    Deque<PathStep> steps = new ArrayDeque<>();
    for (Node node = end; node != null; node = node.parent) {
      steps.addFirst(
          new PathStep(new BlockPos(node.x, node.y, node.z), node.movement, node.breaks));
    }
    return new Path(new ArrayList<>(steps), end.g, reachesGoal);
  }
}
