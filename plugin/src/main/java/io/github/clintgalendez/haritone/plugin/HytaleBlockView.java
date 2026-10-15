package io.github.clintgalendez.haritone.plugin;

import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockMovementSettings;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import io.github.clintgalendez.haritone.world.BlockView;
import io.github.clintgalendez.haritone.world.Terrain;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a Hytale world for the navigation engine. Only use it on the world's own thread.
 *
 * <p>Classification is conservative: unloaded sections are {@link Terrain#UNKNOWN}; blocks the
 * asset map cannot resolve are {@link Terrain#SOLID}; anything that damages entities, or whose id
 * contains a configured hazard keyword, is {@link Terrain#DANGER}. Partial blocks (slabs, stairs)
 * count as solid. Doors are whatever their material says; opening them is not modelled yet.
 */
final class HytaleBlockView implements BlockView {

  /** Hytale worlds are 320 blocks tall (y 0 to 319); feet need room for the head above. */
  static final int MIN_FEET_Y = 0;

  static final int MAX_FEET_Y = 318;

  private final SectionReader reader;

  /** Keeps the current section resolved, so reads inside it skip the reader's section lookup. */
  private final SectionReader.SectionCursor cursor;

  private final List<String> hazardKeywords;
  private final List<String> unbreakableKeywords;
  private final Map<Integer, Terrain> blockTerrain = new HashMap<>();
  private final Map<Integer, Terrain> fluidTerrain = new HashMap<>();

  HytaleBlockView(World world, List<String> hazardKeywords, List<String> unbreakableKeywords) {
    this.reader = new SectionReader(world.getChunkStore());
    this.cursor = reader.sectionCursor();
    this.hazardKeywords = lower(hazardKeywords);
    this.unbreakableKeywords = lower(unbreakableKeywords);
  }

  private static List<String> lower(List<String> keys) {
    return keys.stream().map(k -> k.toLowerCase(Locale.ROOT)).toList();
  }

  @Override
  public Terrain terrain(int x, int y, int z) {
    if (!reader.canHoldY(y) || !cursor.moveTo(x, y, z)) {
      return Terrain.UNKNOWN;
    }
    Terrain block = blockTerrain.computeIfAbsent(cursor.block(), this::classifyBlock);
    if (block == Terrain.SOLID || block == Terrain.DANGER) {
      return block;
    }
    int fluid = cursor.fluidId();
    if (fluid != Fluid.EMPTY_ID) {
      return fluidTerrain.computeIfAbsent(fluid, this::classifyFluid);
    }
    return block;
  }

  @Override
  public String blockKey(int x, int y, int z) {
    if (!reader.canHoldY(y) || !reader.hasStorageInMemory(x, y, z)) {
      return null;
    }
    BlockType type = BlockType.getAssetMap().getAsset(reader.getBlock(x, y, z));
    return type == null ? null : type.getId();
  }

  @Override
  public boolean isBreakable(int x, int y, int z) {
    if (terrain(x, y, z) != Terrain.SOLID) {
      return false;
    }
    String key = blockKey(x, y, z);
    return key != null && !matches(key, unbreakableKeywords);
  }

  @Override
  public int minY() {
    return MIN_FEET_Y;
  }

  @Override
  public int maxY() {
    return MAX_FEET_Y;
  }

  private Terrain classifyBlock(int id) {
    if (id == BlockType.EMPTY_ID) {
      return Terrain.OPEN;
    }
    BlockType type = BlockType.getAssetMap().getAsset(id);
    if (type == null || type.isUnknown()) {
      return Terrain.SOLID;
    }
    if (type.getDamageToEntities() > 0 || matches(type.getId(), hazardKeywords)) {
      return Terrain.DANGER;
    }
    BlockMovementSettings movement = type.getMovementSettings();
    if (movement != null && movement.isClimbable()) {
      return Terrain.CLIMBABLE;
    }
    return type.getMaterial() == BlockMaterial.Solid ? Terrain.SOLID : Terrain.OPEN;
  }

  private Terrain classifyFluid(int id) {
    Fluid fluid = Fluid.getAssetMap().getAsset(id);
    if (fluid == null || fluid.isUnknown()) {
      return Terrain.DANGER;
    }
    if (fluid.getDamageToEntities() > 0 || matches(fluid.getId(), hazardKeywords)) {
      return Terrain.DANGER;
    }
    return Terrain.WATER;
  }

  private static boolean matches(String id, List<String> keywords) {
    String lower = id.toLowerCase(Locale.ROOT);
    for (String keyword : keywords) {
      if (lower.contains(keyword)) {
        return true;
      }
    }
    return false;
  }
}
