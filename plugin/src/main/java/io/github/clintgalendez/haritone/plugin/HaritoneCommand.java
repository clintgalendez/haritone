package io.github.clintgalendez.haritone.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.clintgalendez.haritone.command.CommandService;
import io.github.clintgalendez.haritone.command.CommandService.Reply;
import io.github.clintgalendez.haritone.command.Permissions;
import io.github.clintgalendez.haritone.config.Settings;
import org.joml.Vector3d;

/**
 * {@code /haritone} and its subcommands. Each subcommand only reads its arguments and the player's
 * position, then delegates to {@link CommandService}, which holds the logic and the tests.
 *
 * <p>The root requires {@link Permissions#COMMAND}; the server derives each subcommand's node from
 * it ({@code haritone.command.goto} and so on). Every node is also granted to the server's built-in
 * Adventure and Creative player groups, because guidance only sends chat messages. Break-aware
 * planning is checked separately against {@link Permissions#BREAK}.
 */
final class HaritoneCommand extends AbstractCommandCollection {

  /** The groups the server maps Adventure and Creative players to. */
  private static final String[] PLAYER_GROUPS = {"hytale:Adventurer", "hytale:WorldEditor"};

  HaritoneCommand(CommandService service, Settings settings) {
    super("haritone", "Plan routes and get guided along them (no automatic movement)");
    requirePermission(Permissions.COMMAND);
    setPermissionGroups(PLAYER_GROUPS);
    Goto gotoCommand = new Goto(service);
    gotoCommand.addUsageVariant(new GotoColumn(service));
    addSubCommand(gotoCommand);
    addSubCommand(new GotoLevel(service));
    addSubCommand(new Find(service));
    addSubCommand(new Plan(service));
    addSubCommand(new Simple("pause", "Pause navigation", service, CommandService::pause));
    addSubCommand(
        new Simple("resume", "Resume paused navigation", service, CommandService::resume));
    addSubCommand(new Simple("stop", "Stop navigating", service, CommandService::stop));
    addSubCommand(
        new Simple("status", "Show navigation progress", service, CommandService::status));
    addSubCommand(new Simple("help", "Show Haritone commands", service, (s, p) -> s.help()));
    addSubCommand(new Waypoints(service));
    addSubCommand(new SetPref(service));
    addSubCommand(new GetPref(service));
    addSubCommand(new ResetPref(service));
    addSubCommand(new RouteDiagnostic(settings));
  }

  /** Builds the player snapshot, runs the action, and prints the reply. */
  private abstract static class PlayerCommand extends AbstractPlayerCommand {
    final CommandService service;

    PlayerCommand(String name, String description, CommandService service) {
      super(name, description);
      this.service = service;
      setPermissionGroups(PLAYER_GROUPS);
    }

    /** Usage variant: same command name as its parent, different argument count. */
    PlayerCommand(String description, CommandService service) {
      super(description);
      this.service = service;
      setPermissionGroups(PLAYER_GROUPS);
    }

    @Override
    protected final void execute(
        CommandContext context,
        Store<EntityStore> store,
        Ref<EntityStore> ref,
        PlayerRef playerRef,
        World world) {
      TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
      if (transform == null) {
        context.sendMessage(Message.raw(WorldTicker.PREFIX + "Your position is not available."));
        return;
      }
      Vector3d pos = transform.getPosition();
      CommandService.Player player =
          new CommandService.Player(
              playerRef.getUuid(), world.getName(), pos.x, pos.y, pos.z, playerRef::hasPermission);
      Reply reply = run(context, player);
      for (String line : reply.lines()) {
        context.sendMessage(Message.raw(WorldTicker.PREFIX + line));
      }
    }

    abstract Reply run(CommandContext context, CommandService.Player player);
  }

  /** A subcommand without arguments. */
  private static final class Simple extends PlayerCommand {
    private final java.util.function.BiFunction<CommandService, CommandService.Player, Reply>
        action;

    Simple(
        String name,
        String description,
        CommandService service,
        java.util.function.BiFunction<CommandService, CommandService.Player, Reply> action) {
      super(name, description, service);
      this.action = action;
    }

    @Override
    Reply run(CommandContext context, CommandService.Player player) {
      return action.apply(service, player);
    }
  }

  private static final class Goto extends PlayerCommand {
    private final RequiredArg<Integer> x;
    private final RequiredArg<Integer> y;
    private final RequiredArg<Integer> z;

    Goto(CommandService service) {
      super("goto", "Guide me to a block: goto <x> <y> <z>, or a column: goto <x> <z>", service);
      x = withRequiredArg("x", "Block x", ArgTypes.INTEGER);
      y = withRequiredArg("y", "Block y (feet level)", ArgTypes.INTEGER);
      z = withRequiredArg("z", "Block z", ArgTypes.INTEGER);
    }

    @Override
    Reply run(CommandContext c, CommandService.Player player) {
      return service.gotoBlock(player, x.get(c), y.get(c), z.get(c));
    }
  }

  private static final class GotoColumn extends PlayerCommand {
    private final RequiredArg<Integer> x;
    private final RequiredArg<Integer> z;

    GotoColumn(CommandService service) {
      super("Guide me to a column at any height", service);
      x = withRequiredArg("x", "Block x", ArgTypes.INTEGER);
      z = withRequiredArg("z", "Block z", ArgTypes.INTEGER);
    }

    @Override
    Reply run(CommandContext c, CommandService.Player player) {
      return service.gotoColumn(player, x.get(c), z.get(c));
    }
  }

  private static final class GotoLevel extends PlayerCommand {
    private final RequiredArg<Integer> y;

    GotoLevel(CommandService service) {
      super("level", "Guide me to a height: level <y>", service);
      y = withRequiredArg("y", "Feet level to reach", ArgTypes.INTEGER);
    }

    @Override
    Reply run(CommandContext c, CommandService.Player player) {
      return service.gotoLevel(player, y.get(c));
    }
  }

  private static final class Find extends PlayerCommand {
    private final RequiredArg<String> block;
    private final OptionalArg<Integer> radius;

    Find(CommandService service) {
      super("find", "Guide me to the nearest block of a type: find <blockId> [radius]", service);
      block = withRequiredArg("block", "Block type id", ArgTypes.BLOCK_TYPE_KEY);
      radius = withOptionalArg("radius", "Search radius in blocks", ArgTypes.INTEGER);
    }

    @Override
    Reply run(CommandContext c, CommandService.Player player) {
      return service.find(player, block.get(c), radius.provided(c) ? radius.get(c) : null);
    }
  }

  private static final class Plan extends PlayerCommand {
    private final RequiredArg<Integer> x;
    private final RequiredArg<Integer> y;
    private final RequiredArg<Integer> z;

    Plan(CommandService service) {
      super("plan", "Plan a route and report it without guiding: plan <x> <y> <z>", service);
      x = withRequiredArg("x", "Block x", ArgTypes.INTEGER);
      y = withRequiredArg("y", "Block y (feet level)", ArgTypes.INTEGER);
      z = withRequiredArg("z", "Block z", ArgTypes.INTEGER);
    }

    @Override
    Reply run(CommandContext c, CommandService.Player player) {
      return service.plan(player, x.get(c), y.get(c), z.get(c));
    }
  }

  /** {@code /haritone wp add|remove|goto <name>} and {@code /haritone wp list}. */
  private static final class Waypoints extends AbstractCommandCollection {
    Waypoints(CommandService service) {
      super("wp", "Saved waypoints");
      setPermissionGroups(PLAYER_GROUPS);
      addSubCommand(
          new Named(
              "add", "Save your position as a waypoint", service, CommandService::waypointAdd));
      addSubCommand(
          new Named("remove", "Delete a waypoint", service, CommandService::waypointRemove));
      addSubCommand(
          new Named("goto", "Guide me to a waypoint", service, CommandService::waypointGoto));
      addSubCommand(
          new Simple("list", "List your waypoints", service, CommandService::waypointList));
    }
  }

  /** A subcommand taking one name argument. */
  private static final class Named extends PlayerCommand {
    interface Action {
      Reply apply(CommandService service, CommandService.Player player, String name);
    }

    private final RequiredArg<String> name;
    private final Action action;

    Named(String command, String description, CommandService service, Action action) {
      super(command, description, service);
      this.action = action;
      name = withRequiredArg("name", "Waypoint name", ArgTypes.STRING);
    }

    @Override
    Reply run(CommandContext c, CommandService.Player player) {
      return action.apply(service, player, name.get(c));
    }
  }

  private static final class SetPref extends PlayerCommand {
    private final RequiredArg<String> key;
    private final RequiredArg<String> value;

    SetPref(CommandService service) {
      super("set", "Change one of your settings: set <key> <value>", service);
      key = withRequiredArg("key", "Setting name", ArgTypes.STRING);
      value = withRequiredArg("value", "New value", ArgTypes.STRING);
    }

    @Override
    Reply run(CommandContext c, CommandService.Player player) {
      return service.set(player, key.get(c), value.get(c));
    }
  }

  private static final class GetPref extends PlayerCommand {
    private final OptionalArg<String> key;

    GetPref(CommandService service) {
      super("get", "Show your settings: get [key]", service);
      key = withOptionalArg("key", "Setting name", ArgTypes.STRING);
    }

    @Override
    Reply run(CommandContext c, CommandService.Player player) {
      return service.get(player, key.provided(c) ? key.get(c) : null);
    }
  }

  private static final class ResetPref extends PlayerCommand {
    private final OptionalArg<String> key;

    ResetPref(CommandService service) {
      super("reset", "Reset your settings: reset [key]", service);
      key = withOptionalArg("key", "Setting name", ArgTypes.STRING);
    }

    @Override
    Reply run(CommandContext c, CommandService.Player player) {
      return service.reset(player, key.provided(c) ? key.get(c) : null);
    }
  }
}
