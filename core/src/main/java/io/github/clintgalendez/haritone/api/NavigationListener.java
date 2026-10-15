package io.github.clintgalendez.haritone.api;

import java.util.UUID;

/**
 * Receives navigation status changes. It is called on the thread that ticks the session, which in
 * the Hytale integration is the player's world thread. Implementations must be fast and must not
 * block.
 */
@FunctionalInterface
public interface NavigationListener {
  void onStatusChange(UUID player, NavigationStatus from, NavigationStatus to, String detail);
}
