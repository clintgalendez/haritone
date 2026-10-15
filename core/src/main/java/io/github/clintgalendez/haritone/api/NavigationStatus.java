package io.github.clintgalendez.haritone.api;

/** Lifecycle of a navigation session. */
public enum NavigationStatus {
  IDLE,
  PLANNING,
  NAVIGATING,
  COMPLETED,
  CANCELLED,
  FAILED;

  /** Whether the session has ended and will not change again. */
  public boolean isTerminal() {
    return this == COMPLETED || this == CANCELLED || this == FAILED;
  }
}
