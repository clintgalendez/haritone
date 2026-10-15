package io.github.clintgalendez.haritone.navigation;

import io.github.clintgalendez.haritone.api.NavigationListener;
import io.github.clintgalendez.haritone.api.NavigationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns every player's navigation session: at most one per player and a server-wide cap on active
 * sessions. This is also the entry point for third-party plugins that want to guide players.
 *
 * <p>Thread-safe. Sessions must still be ticked on their world's thread.
 */
public final class Navigator {

  /** Thrown when the server-wide active session cap is reached. */
  public static final class BusyException extends Exception {
    private static final long serialVersionUID = 1L;

    BusyException(int limit) {
      super("Navigation is busy: " + limit + " sessions are already active. Try again soon.");
    }
  }

  private final Map<UUID, NavigationSession> sessions = new ConcurrentHashMap<>();
  private final List<NavigationListener> listeners = new CopyOnWriteArrayList<>();
  private final int maxActiveSessions;

  public Navigator(int maxActiveSessions) {
    if (maxActiveSessions < 1) {
      throw new IllegalArgumentException("maxActiveSessions must be >= 1");
    }
    this.maxActiveSessions = maxActiveSessions;
  }

  /**
   * Starts a session, replacing (and cancelling) the player's current one.
   *
   * @throws BusyException if too many other sessions are active
   */
  public synchronized NavigationSession start(NavigationSession.Request request)
      throws BusyException {
    long othersActive =
        sessions.values().stream()
            .filter(s -> !s.player().equals(request.player()) && !s.status().isTerminal())
            .count();
    if (othersActive >= maxActiveSessions) {
      throw new BusyException(maxActiveSessions);
    }
    NavigationSession previous = sessions.get(request.player());
    if (previous != null) {
      previous.cancel("replaced by a new goal");
    }
    NavigationSession session = new NavigationSession(request, this::fire);
    sessions.put(request.player(), session);
    return session;
  }

  /** The player's latest session, which may have ended. */
  public Optional<NavigationSession> session(UUID player) {
    return Optional.ofNullable(sessions.get(player));
  }

  /** Cancels the player's session. Returns false if there was nothing active. */
  public boolean cancel(UUID player, String reason) {
    NavigationSession session = sessions.get(player);
    return session != null && session.cancel(reason);
  }

  /** Cancels and removes the player's session, e.g. when they disconnect. */
  public void forget(UUID player, String reason) {
    NavigationSession session = sessions.remove(player);
    if (session != null) {
      session.cancel(reason);
    }
  }

  /** Cancels every session, e.g. when the plugin shuts down. */
  public void cancelAll(String reason) {
    for (NavigationSession session : sessions.values()) {
      session.cancel(reason);
    }
  }

  /** Sessions that are still planning or navigating. */
  public List<NavigationSession> active() {
    List<NavigationSession> active = new ArrayList<>();
    for (NavigationSession session : sessions.values()) {
      if (!session.status().isTerminal()) {
        active.add(session);
      }
    }
    return active;
  }

  public void addListener(NavigationListener listener) {
    listeners.add(listener);
  }

  public void removeListener(NavigationListener listener) {
    listeners.remove(listener);
  }

  private void fire(UUID player, NavigationStatus from, NavigationStatus to, String detail) {
    for (NavigationListener listener : listeners) {
      listener.onStatusChange(player, from, to, detail);
    }
  }
}
