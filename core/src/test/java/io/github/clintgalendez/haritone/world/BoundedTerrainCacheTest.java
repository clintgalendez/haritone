package io.github.clintgalendez.haritone.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BoundedTerrainCacheTest {

  @Test
  void neverHoldsMoreThanItsLimit() {
    GridBlockView view = GridBlockView.flat(100);
    BoundedTerrainCache cache = new BoundedTerrainCache(view, 50);
    for (int x = 0; x < 100; x++) {
      for (int z = 0; z < 100; z++) {
        cache.terrain(x, 1, z);
      }
    }
    assertEquals(50, cache.size());
  }

  @Test
  void repeatedLookupsHitTheCache() {
    GridBlockView view = GridBlockView.flat(10);
    BoundedTerrainCache cache = new BoundedTerrainCache(view, 100);
    for (int i = 0; i < 5; i++) {
      assertEquals(Terrain.SOLID, cache.terrain(3, 0, 3));
    }
    assertEquals(1, view.lookups());
    assertEquals(4, cache.hits());
    assertEquals(1, cache.misses());
  }

  @Test
  void evictsLeastRecentlyUsedFirst() {
    GridBlockView view = GridBlockView.flat(10);
    BoundedTerrainCache cache = new BoundedTerrainCache(view, 2);
    cache.terrain(0, 1, 0);
    cache.terrain(1, 1, 0);
    cache.terrain(0, 1, 0); // touch: (1,1,0) is now least recent
    cache.terrain(2, 1, 0); // evicts (1,1,0)
    long before = view.lookups();
    cache.terrain(0, 1, 0);
    assertEquals(before, view.lookups(), "recently used entry kept");
    cache.terrain(1, 1, 0);
    assertEquals(before + 1, view.lookups(), "least recently used entry evicted");
  }

  @Test
  void outOfRangeIsUnknownAndLimitIsValidated() {
    BoundedTerrainCache cache = new BoundedTerrainCache(GridBlockView.flat(2), 1);
    assertEquals(Terrain.UNKNOWN, cache.terrain(0, 10_000, 0));
    assertThrows(IllegalArgumentException.class, () -> new BoundedTerrainCache(cache, 0));
  }
}
