package io.github.clintgalendez.haritone.command;

/**
 * Permission nodes. The Hytale integration sets {@link #COMMAND} on {@code /haritone}; the server
 * derives each subcommand's node by appending its name, e.g. {@code haritone.command.goto}.
 */
public final class Permissions {

  private Permissions() {}

  /** Root node for all Haritone commands. */
  public static final String COMMAND = "haritone.command";

  /** Needed to turn on break-aware planning for yourself. Not granted by default. */
  public static final String BREAK = "haritone.break";

  /** Node for a subcommand, e.g. {@code sub("goto")} is {@code haritone.command.goto}. */
  public static String sub(String name) {
    return COMMAND + "." + name;
  }
}
