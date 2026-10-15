package io.github.clintgalendez.haritone.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;
import org.junit.jupiter.api.Test;

class BlockPosTest {

  @Test
  void packRoundTripsAcrossTheWholeRange() {
    int max = BlockPos.MAX_HORIZONTAL;
    int[][] cases = {
      {0, 0, 0},
      {-1, -1, -1},
      {max, BlockPos.MAX_Y, max},
      {-max, BlockPos.MIN_Y, -max},
      {123456, 64, -987654},
      {-30_000_000, 319, 30_000_000}
    };
    for (int[] c : cases) {
      BlockPos pos = new BlockPos(c[0], c[1], c[2]);
      assertEquals(pos, BlockPos.unpack(pos.pack()));
    }
    Random random = new Random(7);
    for (int i = 0; i < 10_000; i++) {
      BlockPos pos =
          new BlockPos(
              random.nextInt(2 * max + 1) - max,
              random.nextInt(BlockPos.MAX_Y - BlockPos.MIN_Y + 1) + BlockPos.MIN_Y,
              random.nextInt(2 * max + 1) - max);
      assertEquals(pos, BlockPos.unpack(pos.pack()));
    }
  }

  @Test
  void distinctPositionsPackDistinctly() {
    assertNotEquals(BlockPos.pack(1, 0, 0), BlockPos.pack(0, 0, 1));
    assertNotEquals(BlockPos.pack(0, 1, 0), BlockPos.pack(0, 0, 1));
    assertNotEquals(BlockPos.pack(-1, 0, 0), BlockPos.pack(0, -1, 0));
  }

  @Test
  void rejectsOutOfRangeCoordinates() {
    assertFalse(BlockPos.inRange(BlockPos.MAX_HORIZONTAL + 1, 0, 0));
    assertFalse(BlockPos.inRange(0, BlockPos.MAX_Y + 1, 0));
    assertThrows(IllegalArgumentException.class, () -> BlockPos.pack(0, -513, 0));
  }

  @Test
  void containingFloorsNegativeCoordinates() {
    assertEquals(new BlockPos(-1, 64, 3), BlockPos.containing(-0.5, 64.99, 3.0));
  }
}
