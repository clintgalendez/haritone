package io.github.clintgalendez.haritone.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.clintgalendez.haritone.api.BlockPos;
import io.github.clintgalendez.haritone.api.Goals;
import io.github.clintgalendez.haritone.api.NavigationListener;
import io.github.clintgalendez.haritone.api.NavigationStatus;
import io.github.clintgalendez.haritone.pathfinding.PathSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NavigatorTest {

  private static NavigationSession.Request request(UUID player) {
    return new NavigationSession.Request(
        player,
        "world",
        new Goals.AtBlock(new BlockPos(5, 1, 5)),
        null,
        false,
        PathSettings.defaults(),
        GuideSettings.defaults());
  }

  @Test
  void enforcesTheActiveSessionCap() throws Exception {
    Navigator navigator = new Navigator(2);
    navigator.start(request(UUID.randomUUID()));
    navigator.start(request(UUID.randomUUID()));
    assertThrows(Navigator.BusyException.class, () -> navigator.start(request(UUID.randomUUID())));
    assertEquals(2, navigator.active().size());
  }

  @Test
  void endedSessionsDoNotCountAgainstTheCap() throws Exception {
    Navigator navigator = new Navigator(1);
    UUID first = UUID.randomUUID();
    navigator.start(request(first));
    navigator.cancel(first, "done");
    navigator.start(request(UUID.randomUUID()));
    assertEquals(1, navigator.active().size());
  }

  @Test
  void aNewGoalReplacesThePlayersSession() throws Exception {
    Navigator navigator = new Navigator(1);
    UUID player = UUID.randomUUID();
    NavigationSession old = navigator.start(request(player));
    NavigationSession replacement = navigator.start(request(player)); // own session is not "other"
    assertEquals(NavigationStatus.CANCELLED, old.status());
    assertEquals(replacement, navigator.session(player).orElseThrow());
  }

  @Test
  void listenersSeeEveryTransition() throws Exception {
    Navigator navigator = new Navigator(4);
    List<String> events = new ArrayList<>();
    NavigationListener listener = (p, from, to, detail) -> events.add(from + "->" + to);
    navigator.addListener(listener);
    UUID player = UUID.randomUUID();
    navigator.start(request(player));
    navigator.cancel(player, "stop");
    assertEquals(List.of("IDLE->PLANNING", "PLANNING->CANCELLED"), events);
    navigator.removeListener(listener);
    navigator.start(request(player));
    assertEquals(2, events.size());
  }

  @Test
  void forgetAndCancelAll() throws Exception {
    Navigator navigator = new Navigator(4);
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    NavigationSession sa = navigator.start(request(a));
    NavigationSession sb = navigator.start(request(b));
    navigator.forget(a, "left");
    assertTrue(navigator.session(a).isEmpty());
    assertEquals(NavigationStatus.CANCELLED, sa.status());
    navigator.cancelAll("shutdown");
    assertEquals(NavigationStatus.CANCELLED, sb.status());
    assertTrue(navigator.active().isEmpty());
    assertFalse(navigator.cancel(b, "again"));
    assertThrows(IllegalArgumentException.class, () -> new Navigator(0));
  }
}
