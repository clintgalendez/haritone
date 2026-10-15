package io.github.clintgalendez.haritone.plugin;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import io.github.clintgalendez.haritone.command.CommandService;
import io.github.clintgalendez.haritone.config.Settings;
import io.github.clintgalendez.haritone.navigation.Navigator;
import io.github.clintgalendez.haritone.persist.PlayerDataStore;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Haritone's entry point on a Hytale server.
 *
 * <p>Other plugins can guide players through {@link #navigator()}: build a {@link
 * io.github.clintgalendez.haritone.navigation.NavigationSession.Request} with a custom {@link
 * io.github.clintgalendez.haritone.api.Goal} and pass it to {@link Navigator#start}. Declare {@code
 * "Haritone:Haritone"} as a dependency in your manifest.
 */
public final class HaritonePlugin extends JavaPlugin {

  private static volatile HaritonePlugin instance;

  private final Config<HaritoneConfig> config;
  private Settings settings;
  private Navigator navigator;
  private CommandService commands;
  private ScheduledFuture<?> ticker;

  public HaritonePlugin(JavaPluginInit init) {
    super(init);
    config = withConfig("config", HaritoneConfig.CODEC);
  }

  /** The running plugin, or null when Haritone is not enabled. */
  public static HaritonePlugin get() {
    return instance;
  }

  /** Session manager, for third-party plugins. Available after setup. */
  public Navigator navigator() {
    return navigator;
  }

  @Override
  protected void setup() {
    settings = loadSettings();
    navigator = new Navigator(settings.maxActiveSessions());
    commands =
        new CommandService(
            settings,
            navigator,
            new PlayerDataStore(getDataDirectory().resolve("players")),
            HytaleBlockView.MIN_FEET_Y,
            HytaleBlockView.MAX_FEET_Y,
            warning -> getLogger().at(Level.WARNING).log("%s", warning));
    getCommandRegistry().registerCommand(new HaritoneCommand(commands, settings));
    getEventRegistry()
        .register(
            PlayerDisconnectEvent.class,
            event -> commands.playerLeft(event.getPlayerRef().getUuid()));
  }

  @Override
  protected void start() {
    ticker =
        HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
            new WorldTicker(navigator, settings, getLogger()),
            settings.tickIntervalMs(),
            settings.tickIntervalMs(),
            TimeUnit.MILLISECONDS);
    instance = this;
    getLogger()
        .at(Level.INFO)
        .log(
            "Haritone %s enabled. Guide mode: routes are planned and explained; players are never"
                + " moved automatically.",
            getManifest().getVersion());
  }

  @Override
  protected void shutdown() {
    instance = null;
    if (ticker != null) {
      ticker.cancel(false);
    }
    if (navigator != null) {
      navigator.cancelAll("Haritone is shutting down");
    }
  }

  private Settings loadSettings() {
    Settings loaded = config.get().toSettings();
    List<String> problems = loaded.validate();
    if (problems.isEmpty()) {
      // Writes any keys missing from the file so admins can see every option.
      config
          .save()
          .exceptionally(
              e -> {
                getLogger().at(Level.WARNING).withCause(e).log("Could not save config.json");
                return null;
              });
      return loaded;
    }
    for (String problem : problems) {
      getLogger().at(Level.WARNING).log("config.json: %s", problem);
    }
    getLogger()
        .at(Level.WARNING)
        .log(
            "config.json has %d invalid values; using all defaults until it is fixed",
            problems.size());
    return Settings.defaults();
  }
}
