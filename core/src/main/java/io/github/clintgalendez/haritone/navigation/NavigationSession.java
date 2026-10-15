package io.github.clintgalendez.haritone.navigation;

import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.api.Goal;
import io.github.clintgalendez.haritone.api.Goals;
import io.github.clintgalendez.haritone.api.NavigationListener;
import io.github.clintgalendez.haritone.api.NavigationStatus;
import io.github.clintgalendez.haritone.pathfinding.MoveEvaluator;
import io.github.clintgalendez.haritone.pathfinding.Path;
import io.github.clintgalendez.haritone.pathfinding.PathResult;
import io.github.clintgalendez.haritone.pathfinding.PathSearch;
import io.github.clintgalendez.haritone.pathfinding.PathSettings;
import io.github.clintgalendez.haritone.pathfinding.PathStep;
import io.github.clintgalendez.haritone.world.BlockView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Guides one player to one goal. The session plans a route, follows the player's own movement along
 * it, gives instructions, re-checks the route against the world, replans when the player leaves it
 * or it becomes blocked, and chains route segments for long trips.
 *
 * <p>Haritone does not move the player. It plans and guides only.
 *
 * <p>{@link #tick} must be called from the thread that owns the world (the view passed to it is
 * read there). The other methods are synchronized and safe from any thread.
 */
public final class NavigationSession {

  /** A consistent copy of the session state, for status reports. */
  public record Snapshot(
      NavigationStatus status,
      String goal,
      String detail,
      int moves,
      int progress,
      double remaining,
      int replans,
      int segments,
      long nodesExpanded,
      long planningNanos,
      long ticks) {}

  /** What a session is asked to do. */
  public record Request(
      UUID player,
      String world,
      Goal goal,
      BlockTarget target,
      boolean planOnly,
      PathSettings pathSettings,
      GuideSettings guideSettings) {
    public Request {
      Objects.requireNonNull(player, "player");
      Objects.requireNonNull(world, "world");
      Objects.requireNonNull(pathSettings, "pathSettings");
      Objects.requireNonNull(guideSettings, "guideSettings");
      if ((goal == null) == (target == null)) {
        throw new IllegalArgumentException("exactly one of goal and target is required");
      }
    }
  }

  /** Find the nearest block with {@code key} within {@code radius}, then go next to it. */
  public record BlockTarget(String key, int radius) {
    public BlockTarget {
      Objects.requireNonNull(key, "key");
    }
  }

  private final Request request;
  private final NavigationListener listener;
  private Goal goal;
  private NavigationStatus status = NavigationStatus.IDLE;
  private String detail = "";
  private BlockSearch blockSearch;
  private PathSearch search;
  private long planningStartTick;
  private Path path;
  private int index;
  private int replans;
  private int segments;
  private int offRouteTicks;
  private long ticks;
  private long lastGuidanceTick;
  private BlockPos lastLegEnd;
  private long nodesExpanded;
  private long planningNanos;

  NavigationSession(Request request, NavigationListener listener) {
    this.request = request;
    this.listener = listener;
    this.goal = request.goal();
    setStatus(NavigationStatus.PLANNING, "planning a route to " + goalDescription());
  }

  public UUID player() {
    return request.player();
  }

  public String world() {
    return request.world();
  }

  public synchronized NavigationStatus status() {
    return status;
  }

  public synchronized Path path() {
    return path;
  }

  public synchronized Snapshot snapshot() {
    int moves = path == null ? 0 : path.moves();
    double remaining = path == null ? 0 : Guidance.remainingDistance(path, index);
    return new Snapshot(
        status,
        goalDescription(),
        detail,
        moves,
        index,
        remaining,
        replans,
        segments,
        nodesExpanded,
        planningNanos,
        ticks);
  }

  /** Stops the session. Returns false if it had already ended. */
  public synchronized boolean cancel(String reason) {
    if (status.isTerminal()) {
      return false;
    }
    if (search != null) {
      search.cancel();
    }
    setStatus(NavigationStatus.CANCELLED, reason);
    return true;
  }

  /**
   * Advances the session by one tick.
   *
   * @param px player position x (continuous)
   * @param py player feet y (continuous)
   * @param pz player position z (continuous)
   * @param view the live world, read only on the calling thread
   * @param budgetNanos compute time this tick may spend planning
   * @return messages for the player, possibly empty
   */
  public synchronized List<String> tick(
      double px, double py, double pz, BlockView view, long budgetNanos) {
    List<String> messages = new ArrayList<>();
    if (status.isTerminal()) {
      return messages;
    }
    ticks++;
    BlockPos feet = BlockPos.containing(px, py + 0.001, pz);
    MoveEvaluator evaluator = new MoveEvaluator(view, request.pathSettings());
    BlockPos standing = evaluator.snap(feet);
    BlockPos here = standing != null ? standing : feet;

    if (status == NavigationStatus.PLANNING) {
      plan(here, standing, view, budgetNanos, messages);
    } else if (status == NavigationStatus.NAVIGATING) {
      follow(px, py, pz, here, evaluator, messages);
    }
    return messages;
  }

  private void plan(
      BlockPos here, BlockPos standing, BlockView view, long budgetNanos, List<String> messages) {
    if (goal == null && !findTarget(here, view, budgetNanos, messages)) {
      return;
    }
    if (search == null) {
      if (goal.isSatisfied(here.x(), here.y(), here.z())) {
        complete("already at " + goalDescription(), messages);
        return;
      }
      if (standing == null) {
        fail("you are not standing anywhere a route can start from; move and try again", messages);
        return;
      }
      search = new PathSearch(view, standing, goal, request.pathSettings());
      planningStartTick = ticks;
    }
    if (ticks - planningStartTick > request.guideSettings().planningTimeoutTicks()) {
      search.cancel();
      fail("planning took too long", messages);
      return;
    }
    long started = System.nanoTime();
    PathResult result = search.advance(Integer.MAX_VALUE, budgetNanos);
    planningNanos += System.nanoTime() - started;
    if (result == null) {
      return;
    }
    nodesExpanded += result.expanded();
    search = null;
    switch (result.outcome()) {
      case FOUND, PARTIAL -> startFollowing(result, messages);
      case FAILED -> fail(failureText(result), messages);
      case CANCELLED -> {}
    }
  }

  /** Runs the block search; returns true once a goal is set. */
  private boolean findTarget(BlockPos here, BlockView view, long budgetNanos, List<String> msgs) {
    BlockTarget target = request.target();
    if (blockSearch == null) {
      blockSearch = new BlockSearch(view, here, target.key(), target.radius());
    }
    long started = System.nanoTime();
    BlockSearch.State state = blockSearch.advance(Integer.MAX_VALUE, budgetNanos);
    planningNanos += System.nanoTime() - started;
    return switch (state) {
      case RUNNING -> false;
      case NOT_FOUND -> {
        fail("no " + target.key() + " within " + target.radius() + " blocks", msgs);
        yield false;
      }
      case FOUND -> {
        goal = new Goals.Adjacent(blockSearch.found());
        msgs.add("Found " + target.key() + " at " + blockSearch.found() + ".");
        yield true;
      }
    };
  }

  private void startFollowing(PathResult result, List<String> messages) {
    path = result.path();
    index = 0;
    offRouteTicks = 0;
    segments++;
    StringBuilder text = new StringBuilder();
    text.append(segments == 1 ? "Route found: " : "Next segment: ")
        .append(path.moves())
        .append(" moves, about ")
        .append(Math.round(Guidance.remainingDistance(path, 0)))
        .append(" blocks");
    if (!path.reachesGoal()) {
      text.append(" (partial: it ends at ")
          .append(path.end())
          .append(", the closest point known so far)");
    }
    if (path.breakCount() > 0) {
      text.append("; you will need to break ").append(path.breakCount()).append(" blocks");
    }
    messages.add(text.append('.').toString());
    if (request.planOnly()) {
      complete("planned " + path.moves() + " moves to " + path.end(), messages);
      return;
    }
    setStatus(NavigationStatus.NAVIGATING, "following route segment " + segments);
    guide(messages);
  }

  private void follow(
      double px, double py, double pz, BlockPos here, MoveEvaluator evaluator, List<String> msgs) {
    if (goal.isSatisfied(here.x(), here.y(), here.z())) {
      complete("arrived at " + goalDescription(), msgs);
      return;
    }
    GuideSettings guide = request.guideSettings();
    List<PathStep> steps = path.steps();

    // Track progress: the closest step in a short window ahead of the current one.
    int from = Math.max(0, index - 1);
    int to = Math.min(steps.size() - 1, index + guide.lookahead());
    int nearest = index;
    double nearestDistSq = Double.POSITIVE_INFINITY;
    for (int i = from; i <= to; i++) {
      double d = steps.get(i).pos().distanceSqTo(px, py, pz);
      if (d < nearestDistSq) {
        nearestDistSq = d;
        nearest = i;
      }
    }
    index = Math.max(index, nearest);

    double tolerance = guide.deviationTolerance();
    if (nearestDistSq > tolerance * tolerance) {
      if (++offRouteTicks >= guide.offRouteGraceTicks()) {
        replan("you left the route", msgs);
      }
      return;
    }
    offRouteTicks = 0;

    if (index == steps.size() - 1) {
      if (!path.reachesGoal()) {
        nextSegment(msgs);
      }
      return; // on the final step of a full route: arrival is detected by the goal check
    }

    if (ticks % guide.revalidateEveryTicks() == 0) {
      int last = Math.min(steps.size() - 1, index + guide.lookahead());
      for (int i = index + 1; i <= last; i++) {
        BlockPos p = steps.get(i).pos();
        if (!evaluator.canStand(p.x(), p.y(), p.z())) {
          replan("the route is blocked at " + p, msgs);
          return;
        }
      }
    }
    guide(msgs);
  }

  /** Speaks when the next leg changes (one was finished) and periodically as a reminder. */
  private void guide(List<String> messages) {
    List<Guidance.Leg> legs = Guidance.legs(path, index);
    if (legs.isEmpty()) {
      return;
    }
    BlockPos legEnd = legs.getFirst().end();
    boolean due = ticks - lastGuidanceTick >= request.guideSettings().guidanceEveryTicks();
    if (!legEnd.equals(lastLegEnd) || due) {
      messages.add(Guidance.next(path, index));
      lastLegEnd = legEnd;
      lastGuidanceTick = ticks;
    }
  }

  private void nextSegment(List<String> messages) {
    if (segments >= request.guideSettings().maxSegments()) {
      fail("gave up after " + segments + " route segments", messages);
      return;
    }
    messages.add("End of this segment; planning the next one.");
    restartPlanning("planning segment " + (segments + 1));
  }

  private void replan(String why, List<String> messages) {
    if (replans >= request.guideSettings().maxReplans()) {
      fail(why + ", and the replan limit (" + replans + ") is reached", messages);
      return;
    }
    replans++;
    messages.add(capitalize(why) + "; replanning.");
    restartPlanning("replanning (" + why + ")");
  }

  private void restartPlanning(String reason) {
    path = null;
    index = 0;
    offRouteTicks = 0;
    lastLegEnd = null;
    setStatus(NavigationStatus.PLANNING, reason);
  }

  private void complete(String why, List<String> messages) {
    messages.add(capitalize(why) + ".");
    setStatus(NavigationStatus.COMPLETED, why);
  }

  private void fail(String why, List<String> messages) {
    messages.add("Navigation failed: " + why + ".");
    setStatus(NavigationStatus.FAILED, why);
  }

  private String failureText(PathResult result) {
    return switch (result.reason()) {
      case INVALID_START -> "the starting position is not standable";
      case EXHAUSTED -> "no route to " + goalDescription() + " through known terrain";
      case NODE_LIMIT -> "the search limit was reached without getting closer";
      case TIMEOUT -> "the search timed out without getting closer";
      case CANCELLED -> "cancelled";
      case GOAL_REACHED -> "unexpected";
    };
  }

  private String goalDescription() {
    if (goal != null) {
      return goal.describe();
    }
    return "nearest " + request.target().key();
  }

  private void setStatus(NavigationStatus next, String why) {
    NavigationStatus previous = status;
    status = next;
    detail = why;
    if (previous != next && listener != null) {
      listener.onStatusChange(request.player(), previous, next, why);
    }
  }

  private static String capitalize(String text) {
    return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
