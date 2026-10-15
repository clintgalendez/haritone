package io.github.clintgalendez.haritone.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BoundedTerrainCacheTest {

  @Test
  void neverHoldsMoreSectionsThanItsLimit() {
    GridBlockView view = GridBlockView.flat(100);
    BoundedTerrainCache cache = new BoundedTerrainCache(view, 2 * 4096);
    for (int x = 0; x < 100; x++) {
      for (int z = 0; z < 100; z++) {
        assertEquals(Terrain.OPEN, cache.terrain(x, 1, z));
      }
    }
    assertEquals(2, cache.sections());
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
  void aSectionInTheSameSlotReplacesTheOldOne() {
    GridBlockView view = GridBlockView.flat(40);
    BoundedTerrainCache cache = new BoundedTerrainCache(view, 4096); // one slot
    cache.terrain(0, 1, 0);
    cache.terrain(1, 1, 0);
    assertEquals(2, view.lookups(), "same section, different blocks");
    cache.terrain(0, 1, 0);
    assertEquals(2, view.lookups(), "cached");
    assertEquals(Terrain.SOLID, cache.terrain(20, 0, 0)); // another section takes the slot
    assertEquals(3, view.lookups());
    assertEquals(Terrain.OPEN, cache.terrain(0, 1, 0));
    assertEquals(4, view.lookups(), "the first section was dropped");
    assertEquals(1, cache.sections());
  }

  @Test
  void outOfRangeIsUnknownAndLimitIsValidated() {
    BoundedTerrainCache cache = new BoundedTerrainCache(GridBlockView.flat(2), 1);
    assertEquals(Terrain.UNKNOWN, cache.terrain(0, 10_000, 0));
    assertEquals(Terrain.UNKNOWN, cache.terrain(-1, 1, -1), "negative coordinates work");
    assertThrows(IllegalArgumentException.class, () -> new BoundedTerrainCache(cache, 0));
  }
}
