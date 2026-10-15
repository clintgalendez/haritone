package io.github.clintgalendez.haritone.world;

import io.github.clintgalendez.haritone.api.BlockPos;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Memoizes terrain lookups for the duration of one search, with a hard size limit and
 * least-recently-used eviction. Searches look at each block several times (as feet, head, and
 * support of different neighbours), and game lookups are not free.
 *
 * <p>Results can go stale if the world changes, so create a new cache per search and never share
 * one between threads. Navigation re-validates paths against the live world while it guides.
 */
public final class BoundedTerrainCache implements BlockView {

  private final BlockView delegate;
  private final int maxEntries;
  private final Map<Long, Terrain> cache;
  private long hits;
  private long misses;

  public BoundedTerrainCache(BlockView delegate, int maxEntries) {
    if (maxEntries < 1) {
      throw new IllegalArgumentException("maxEntries must be >= 1");
    }
    this.delegate = delegate;
    this.maxEntries = maxEntries;
    this.cache =
        new LinkedHashMap<>(16, 0.75f, true) {
          private static final long serialVersionUID = 1L;

          @Override
          protected boolean removeEldestEntry(Map.Entry<Long, Terrain> eldest) {
            return size() > BoundedTerrainCache.this.maxEntries;
          }
        };
  }

  @Override
  public Terrain terrain(int x, int y, int z) {
    if (!BlockPos.inRange(x, y, z)) {
      return Terrain.UNKNOWN;
    }
    long key = BlockPos.pack(x, y, z);
    Terrain cached = cache.get(key);
    if (cached != null) {
      hits++;
      return cached;
    }
    misses++;
    Terrain terrain = delegate.terrain(x, y, z);
    cache.put(key, terrain);
    return terrain;
  }

  @Override
  public String blockKey(int x, int y, int z) {
    return delegate.blockKey(x, y, z);
  }

  @Override
  public boolean isBreakable(int x, int y, int z) {
    return delegate.isBreakable(x, y, z);
  }

  @Override
  public int minY() {
    return delegate.minY();
  }

  @Override
  public int maxY() {
    return delegate.maxY();
  }

  public int size() {
    return cache.size();
  }

  public long hits() {
    return hits;
  }

  public long misses() {
    return misses;
  }
}
