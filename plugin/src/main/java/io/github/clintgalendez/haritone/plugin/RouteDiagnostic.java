package io.github.clintgalendez.haritone.plugin;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.api.Goals;
import io.github.clintgalendez.haritone.config.PlayerPrefs;
import io.github.clintgalendez.haritone.config.Settings;
import io.github.clintgalendez.haritone.navigation.Guidance;
import io.github.clintgalendez.haritone.pathfinding.MoveEvaluator;
import io.github.clintgalendez.haritone.pathfinding.Movement;
import io.github.clintgalendez.haritone.pathfinding.PathResult;
import io.github.clintgalendez.haritone.pathfinding.PathSearch;
import io.github.clintgalendez.haritone.pathfinding.PathSettings;
import io.github.clintgalendez.haritone.pathfinding.PathStep;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * {@code /haritone route <x1> <z1> <x2> <z2> [--world=name]}: an admin and console diagnostic that
 * loads the chunks between two columns, plans a route from the surface of one to the surface of the
 * other, and reports the result. It works without a connected player, so server owners (and
 * Haritone's own integration checks) can test planning on real terrain.
 *
 * <p>Its permission node is {@code haritone.command.route}, which is not granted to player groups.
 */
final class RouteDiagnostic extends AbstractWorldCommand {

  /** Largest span along either axis, which bounds how many chunks one call loads. */
  static final int MAX_SPAN = 256;

  private final Settings settings;
  private final RequiredArg<Integer> x1;
  private final RequiredArg<Integer> z1;
  private final RequiredArg<Integer> x2;
  private final RequiredArg<Integer> z2;

  RouteDiagnostic(Settings settings) {
    super("route", "Admin diagnostic: plan between two columns: route <x1> <z1> <x2> <z2>");
    this.settings = settings;
    x1 = withRequiredArg("x1", "Start x", ArgTypes.INTEGER);
    z1 = withRequiredArg("z1", "Start z", ArgTypes.INTEGER);
    x2 = withRequiredArg("x2", "Goal x", ArgTypes.INTEGER);
    z2 = withRequiredArg("z2", "Goal z", ArgTypes.INTEGER);
  }

  @Override
  protected void execute(CommandContext context, World world, Store<EntityStore> store) {
    int ax = x1.get(context);
    int az = z1.get(context);
    int bx = x2.get(context);
    int bz = z2.get(context);
    if (Math.abs(ax - bx) > MAX_SPAN || Math.abs(az - bz) > MAX_SPAN) {
      say(context, "Keep both spans within " + MAX_SPAN + " blocks.");
      return;
    }
    Set<Long> chunks = new LinkedHashSet<>();
    int step = ChunkUtil.SIZE;
    for (int x = Math.min(ax, bx) - step; x <= Math.max(ax, bx) + step; x += step) {
      for (int z = Math.min(az, bz) - step; z <= Math.max(az, bz) + step; z += step) {
        chunks.add(ChunkUtil.indexChunkFromBlock(x, z));
      }
    }
    say(context, "Loading " + chunks.size() + " chunks in " + world.getName() + "...");
    CompletableFuture<?>[] loads =
        chunks.stream()
            .map(index -> world.getChunkStore().getChunkReferenceAsync(index))
            .toArray(CompletableFuture<?>[]::new);
    CompletableFuture.allOf(loads)
        .orTimeout(60, TimeUnit.SECONDS)
        .thenRunAsync(() -> plan(context, world, ax, az, bx, bz), world)
        .exceptionally(
            e -> {
              say(context, "Route check failed: " + e);
              return null;
            });
  }

  /** Runs on the world thread once the chunks are loaded. */
  private void plan(CommandContext context, World world, int ax, int az, int bx, int bz) {
    HytaleBlockView view =
        new HytaleBlockView(world, settings.hazardBlockKeys(), settings.unbreakableBlockKeys());
    PathSettings pathSettings =
        settings.pathSettings(PlayerPrefs.defaults(), false).withTimeoutMillis(1_000);
    MoveEvaluator evaluator = new MoveEvaluator(view, pathSettings);
    BlockPos start = evaluator.highestStandable(ax, az);
    BlockPos goal = evaluator.highestStandable(bx, bz);
    if (start == null || goal == null) {
      say(
          context,
          "No standable surface at " + (start == null ? ax + " " + az : bx + " " + bz) + ".");
      return;
    }
    say(
        context,
        "Surface: start "
            + start
            + " on "
            + view.blockKey(start.x(), start.y() - 1, start.z())
            + ", goal "
            + goal
            + " on "
            + view.blockKey(goal.x(), goal.y() - 1, goal.z()));
    PathResult result = new PathSearch(view, start, new Goals.AtBlock(goal), pathSettings).run();
    say(
        context,
        String.format(
            Locale.ROOT,
            "Result: %s (%s), expanded %d, visited %d, %.1f ms",
            result.outcome(),
            result.reason(),
            result.expanded(),
            result.visited(),
            result.elapsedNanos() / 1e6));
    if (!result.hasPath()) {
      return;
    }
    Map<Movement, Integer> moves = new EnumMap<>(Movement.class);
    for (PathStep step : result.path().steps()) {
      moves.merge(step.movement(), 1, Integer::sum);
    }
    say(
        context,
        String.format(
            Locale.ROOT,
            "Path: %d moves to %s, cost %.1f, moves by type %s",
            result.path().moves(),
            result.path().end(),
            result.path().cost(),
            moves));
    String next = Guidance.next(result.path(), 0);
    if (next != null) {
      say(context, next);
    }
  }

  private static void say(CommandContext context, String text) {
    context.sendMessage(Message.raw(WorldTicker.PREFIX + text));
  }
}
