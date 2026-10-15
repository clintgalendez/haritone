package io.github.clintgalendez.haritone.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.clintgalendez.haritone.pathfinding.NodeHeap.Node;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class NodeHeapTest {

  private static Node node(long seq, double f, double h) {
    Node node = new Node(0, 0, 0, seq);
    node.f = f;
    node.h = h;
    return node;
  }

  @Test
  void pollsInPriorityOrderWithDeterministicTieBreaks() {
    NodeHeap heap = new NodeHeap();
    Node a = node(0, 5, 1);
    Node b = node(1, 5, 0); // same f, closer to goal: first
    Node c = node(2, 3, 9);
    Node d = node(3, 5, 0); // same f and h as b but created later
    heap.add(a);
    heap.add(b);
    heap.add(c);
    heap.add(d);
    assertSame(c, heap.poll());
    assertSame(b, heap.poll());
    assertSame(d, heap.poll());
    assertSame(a, heap.poll());
    assertTrue(heap.isEmpty());
    assertThrows(IllegalStateException.class, heap::poll);
  }

  @Test
  void matchesSortedOrderUnderRandomInsertsAndDecreases() {
    Random random = new Random(42);
    NodeHeap heap = new NodeHeap();
    List<Node> all = new ArrayList<>();
    for (int i = 0; i < 5000; i++) {
      Node n = node(i, random.nextInt(1000), random.nextInt(10));
      all.add(n);
      heap.add(n);
      if (i % 3 == 0) {
        Node target = all.get(random.nextInt(all.size()));
        if (target.inHeap()) {
          target.f -= random.nextInt(50);
          heap.decreased(target);
        }
      }
    }
    all.sort(
        Comparator.<Node>comparingDouble(n -> n.f)
            .thenComparingDouble(n -> n.h)
            .thenComparingLong(n -> n.seq));
    assertEquals(all.size(), heap.size());
    for (Node expected : all) {
      Node actual = heap.poll();
      assertSame(expected, actual);
      assertEquals(-1, actual.heapIndex);
    }
  }
}
