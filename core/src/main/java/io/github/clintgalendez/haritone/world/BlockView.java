package io.github.clintgalendez.haritone.world;

/**
 * Read-only access to world blocks. Implementations are not required to be thread-safe; the engine
 * only calls a view from the thread that owns it.
 */
public interface BlockView {

  /** Classification of the block at {@code (x, y, z)}. Never {@code null}. */
  Terrain terrain(int x, int y, int z);

  /**
   * The game's identifier for the block at {@code (x, y, z)}, or {@code null} when it is unknown or
   * not loaded. Used for block targeting.
   */
  default String blockKey(int x, int y, int z) {
    return null;
  }

  /**
   * Whether the block may be broken when break-aware planning is enabled. Only {@link
   * Terrain#SOLID} blocks are ever considered.
   */
  default boolean isBreakable(int x, int y, int z) {
    return terrain(x, y, z) == Terrain.SOLID;
  }

  /** Lowest y an entity's feet can occupy. */
  int minY();

  /** Highest y an entity's feet can occupy. */
  int maxY();
}
