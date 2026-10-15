package io.github.clintgalendez.haritone.pathfinding;

import io.github.clintgalendez.haritone.api.BlockPos;
import java.util.Arrays;
import java.util.List;

/**
 * Binary min-heap of search nodes with in-place priority decrease. {@link java.util.PriorityQueue}
 * cannot lower a key without an O(n) remove, which A* needs on every cheaper route found.
 *
 * <p>Order: lowest {@code f}, then lowest {@code h} (prefer nodes nearer the goal), then creation
 * order, so searches are deterministic.
 */
final class NodeHeap {

  /** A search node. Fields are mutable and owned by the search. */
  static final class Node {
    final int x;
    final int y;
    final int z;
    final long seq;
    double g;
    double h;
    double f;
    Node parent;
    Movement movement;
    List<BlockPos> breaks;
    int heapIndex = -1;

    Node(int x, int y, int z, long seq) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.seq = seq;
    }

    boolean inHeap() {
      return heapIndex >= 0;
    }
  }

  private Node[] items = new Node[1024];
  private int size;

  boolean isEmpty() {
    return size == 0;
  }

  int size() {
    return size;
  }

  void add(Node node) {
    if (size == items.length) {
      items = Arrays.copyOf(items, size * 2);
    }
    items[size] = node;
    node.heapIndex = size;
    size++;
    siftUp(node.heapIndex);
  }

  Node poll() {
    if (size == 0) {
      throw new IllegalStateException("heap is empty");
    }
    Node top = items[0];
    size--;
    if (size > 0) {
      items[0] = items[size];
      items[0].heapIndex = 0;
      siftDown(0);
    }
    items[size] = null;
    top.heapIndex = -1;
    return top;
  }

  /** Restores order after {@code node}'s priority was lowered. */
  void decreased(Node node) {
    siftUp(node.heapIndex);
  }

  private static boolean less(Node a, Node b) {
    if (a.f != b.f) {
      return a.f < b.f;
    }
    if (a.h != b.h) {
      return a.h < b.h;
    }
    return a.seq < b.seq;
  }

  private void siftUp(int index) {
    Node node = items[index];
    while (index > 0) {
      int parent = (index - 1) >>> 1;
      if (!less(node, items[parent])) {
        break;
      }
      items[index] = items[parent];
      items[index].heapIndex = index;
      index = parent;
    }
    items[index] = node;
    node.heapIndex = index;
  }

  private void siftDown(int index) {
    Node node = items[index];
    int half = size >>> 1;
    while (index < half) {
      int child = 2 * index + 1;
      int right = child + 1;
      if (right < size && less(items[right], items[child])) {
        child = right;
      }
      if (!less(items[child], node)) {
        break;
      }
      items[index] = items[child];
      items[index].heapIndex = index;
      index = child;
    }
    items[index] = node;
    node.heapIndex = index;
  }
}
