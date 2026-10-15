package io.github.clintgalendez.haritone.world;

import io.github.clintgalendez.haritone.api.BlockPos;
import java.util.Arrays;

/**
 * Memoizes terrain lookups for the duration of one search, with a hard size limit. Searches look at
 * each block many times (as feet, head, support, and hazard neighbour of different positions), and
 * game lookups are not free.
 *
 * <p>Blocks are kept in 16×16×16 sections of one byte each, in a fixed number of slots chosen by
 * hashing the section position. A section that hashes to a slot already in use replaces the one
 * there, so lookups never box, allocate, or walk a map. Memory is at most {@code maxEntries} bytes
 * plus slot overhead, and sections are only allocated once a search touches them.
 *
 * <p>Results can go stale if the world changes, so create a new cache per search and never share
 * one between threads. Navigation re-validates paths against the live world while it guides.
 */
public final class BoundedTerrainCache implements BlockView {

  private static final int SHIFT = 4;
  private static final int MASK = (1 << SHIFT) - 1;
  private static final int SECTION_VOLUME = 1 << (3 * SHIFT);
  private static final Terrain[] TERRAIN = Terrain.values();

  private final BlockView delegate;
  private final long[] keys;
  private final byte[][] sections; // terrain ordinal + 1 per block; 0 = not read yet
  private final int slotMask;
  private byte[] last; // the section of the previous lookup, which most lookups hit again
  private int lastX;
  private int lastY;
  private int lastZ;
  private long hits;
  private long misses;

  public BoundedTerrainCache(BlockView delegate, int maxEntries) {
    if (maxEntries < 1) {
      throw new IllegalArgumentException("maxEntries must be >= 1");
    }
    this.delegate = delegate;
    int slots = Integer.highestOneBit(Math.max(1, maxEntries / SECTION_VOLUME));
    this.keys = new long[slots];
    this.sections = new byte[slots][];
    this.slotMask = slots - 1;
  }

  @Override
  @SuppressWarnings("EnumOrdinal") // one byte per block; TERRAIN maps the ordinal back
  public Terrain terrain(int x, int y, int z) {
    if (!BlockPos.inRange(x, y, z)) {
      return Terrain.UNKNOWN;
    }
    int sx = x >> SHIFT;
    int sy = y >> SHIFT;
    int sz = z >> SHIFT;
    byte[] section = last;
    if (section == null || sx != lastX || sy != lastY || sz != lastZ) {
      section = section(sx, sy, sz);
    }
    int index = ((y & MASK) << (2 * SHIFT)) | ((z & MASK) << SHIFT) | (x & MASK);
    byte cached = section[index];
    if (cached != 0) {
      hits++;
      return TERRAIN[cached - 1];
    }
    misses++;
    Terrain terrain = delegate.terrain(x, y, z);
    section[index] = (byte) (terrain.ordinal() + 1);
    return terrain;
  }

  private byte[] section(int sx, int sy, int sz) {
    long key = BlockPos.pack(sx, sy, sz);
    long hash = key * 0x9E3779B97F4A7C15L;
    int slot = (int) (hash ^ (hash >>> 32)) & slotMask;
    byte[] section = sections[slot];
    if (section == null) {
      section = new byte[SECTION_VOLUME];
      sections[slot] = section;
      keys[slot] = key;
    } else if (keys[slot] != key) {
      Arrays.fill(section, (byte) 0); // evict whatever held the slot, possibly the last section
      keys[slot] = key;
    }
    last = section;
    lastX = sx;
    lastY = sy;
    lastZ = sz;
    return section;
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

  /** Sections currently held. */
  public int sections() {
    int held = 0;
    for (byte[] section : sections) {
      if (section != null) {
        held++;
      }
    }
    return held;
  }

  public long hits() {
    return hits;
  }

  public long misses() {
    return misses;
  }
}
