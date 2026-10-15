package io.github.clintgalendez.haritone.api;

/**
 * An integer block position. For a standing entity this is the block its feet occupy.
 *
 * <p>Positions pack into a {@code long} for hashing: 27 bits each for x and z and 10 bits for y.
 * That covers |x| and |z| up to {@value #MAX_HORIZONTAL} and y in [{@value #MIN_Y}, {@value
 * #MAX_Y}], beyond any reachable Hytale coordinate.
 */
public record BlockPos(int x, int y, int z) {

  public static final int MAX_HORIZONTAL = (1 << 26) - 1;
  public static final int MIN_Y = -512;
  public static final int MAX_Y = 511;

  /** Returns whether {@code (x, y, z)} can be represented and packed. */
  public static boolean inRange(int x, int y, int z) {
    return Math.abs(x) <= MAX_HORIZONTAL
        && Math.abs(z) <= MAX_HORIZONTAL
        && y >= MIN_Y
        && y <= MAX_Y;
  }

  /** Packs coordinates into a unique {@code long}. */
  public static long pack(int x, int y, int z) {
    if (!inRange(x, y, z)) {
      throw new IllegalArgumentException("Coordinates out of range: " + x + " " + y + " " + z);
    }
    return ((long) (x & 0x7FFFFFF) << 37) | ((long) (z & 0x7FFFFFF) << 10) | (y & 0x3FF);
  }

  /** Inverse of {@link #pack(int, int, int)}. */
  public static BlockPos unpack(long key) {
    int x = ((int) (key >>> 37)) << 5 >> 5;
    int z = ((int) ((key >>> 10) & 0x7FFFFFF)) << 5 >> 5;
    int y = ((int) (key & 0x3FF)) << 22 >> 22;
    return new BlockPos(x, y, z);
  }

  /** The block containing a continuous position. */
  public static BlockPos containing(double x, double y, double z) {
    return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
  }

  public long pack() {
    return pack(x, y, z);
  }

  public BlockPos offset(int dx, int dy, int dz) {
    return new BlockPos(x + dx, y + dy, z + dz);
  }

  /** Squared distance from the centre of this block's floor to a continuous point. */
  public double distanceSqTo(double px, double py, double pz) {
    double dx = x + 0.5 - px;
    double dy = y - py;
    double dz = z + 0.5 - pz;
    return dx * dx + dy * dy + dz * dz;
  }

  @Override
  public String toString() {
    return x + " " + y + " " + z;
  }
}
