package io.github.clintgalendez.haritone.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.clintgalendez.haritone.api.NavigationStatus;
import io.github.clintgalendez.haritone.config.Settings;
import io.github.clintgalendez.haritone.navigation.NavigationSession;
import io.github.clintgalendez.haritone.navigation.Navigator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.joml.Vector3d;

/**
 * Runs on a fixed-rate scheduler thread and hands each world's sessions to that world's own thread,
 * where world data may be read. A world whose previous tick is still queued is skipped, so a slow
 * world never piles up work. Each tick spends at most the configured planning budget per world.
 */
final class WorldTicker implements Runnable {

  static final String PREFIX = "[Haritone] ";

  private final Navigator navigator;
  private final Settings settings;
  private final HytaleLogger logger;
  private final Set<String> queuedWorlds = ConcurrentHashMap.newKeySet();

  WorldTicker(Navigator navigator, Settings settings, HytaleLogger logger) {
    this.navigator = navigator;
    this.settings = settings;
    this.logger = logger;
  }

  @Override
  public void run() {
    try {
      Map<String, List<NavigationSession>> byWorld = new HashMap<>();
      for (NavigationSession session : navigator.active()) {
        byWorld.computeIfAbsent(session.world(), w -> new ArrayList<>()).add(session);
      }
      for (Map.Entry<String, List<NavigationSession>> entry : byWorld.entrySet()) {
        String name = entry.getKey();
        World world = Universe.get().getWorld(name);
        if (world == null || !world.isAlive()) {
          entry
              .getValue()
              .forEach(s -> navigator.cancel(s.player(), "the world is no longer loaded"));
          continue;
        }
        if (!queuedWorlds.add(name)) {
          continue;
        }
        world.execute(
            () -> {
              try {
                tickWorld(world, entry.getValue());
              } catch (RuntimeException e) {
                logger.at(Level.WARNING).withCause(e).log(
                    "Navigation tick failed in world %s", name);
              } finally {
                queuedWorlds.remove(name);
              }
            });
      }
    } catch (RuntimeException e) {
      // Never let an exception cancel the repeating task.
      logger.at(Level.WARNING).withCause(e).log("Navigation scheduling failed");
    }
  }

  /** Runs on the world's thread. */
  private void tickWorld(World world, List<NavigationSession> sessions) {
    HytaleBlockView view =
        new HytaleBlockView(world, settings.hazardBlockKeys(), settings.unbreakableBlockKeys());
    UUID worldId = world.getWorldConfig().getUuid();
    long planning = sessions.stream().filter(s -> s.status() == NavigationStatus.PLANNING).count();
    long budget = settings.planningBudgetMs() * 1_000_000L / Math.max(1, planning);

    for (NavigationSession session : sessions) {
      PlayerRef player = Universe.get().getPlayer(session.player());
      if (player == null || !player.isValid()) {
        navigator.cancel(session.player(), "you went offline");
        continue;
      }
      if (!worldId.equals(player.getWorldUuid())) {
        if (navigator.cancel(session.player(), "you changed worlds")) {
          player.sendMessage(Message.raw(PREFIX + "Navigation cancelled: you changed worlds."));
        }
        continue;
      }
      Ref<EntityStore> ref = player.getReference();
      if (ref == null || !ref.isValid()) {
        continue;
      }
      TransformComponent transform =
          ref.getStore().getComponent(ref, TransformComponent.getComponentType());
      if (transform == null) {
        continue;
      }
      Vector3d position = transform.getPosition();
      for (String line : session.tick(position.x, position.y, position.z, view, budget)) {
        player.sendMessage(Message.raw(PREFIX + line));
      }
    }
  }
}
