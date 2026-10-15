package io.github.clintgalendez.haritone.world;

/**
 * What the navigation engine needs to know about one block. Game adapters map their block data onto
 * these classes; anything they cannot classify reliably must be reported as {@link #UNKNOWN} or
 * {@link #SOLID}, never as {@link #OPEN}.
 */
public enum Terrain {
  /** Not loaded or not readable. Treated as blocked unless the unknown-terrain policy allows it. */
  UNKNOWN,
  /** Nothing to collide with: air, plants, and other non-colliding blocks. */
  OPEN,
  /** Collides and can be stood on. */
  SOLID,
  /** Non-damaging fluid. An entity can swim in it without support. */
  WATER,
  /** Hurts entities (lava, fire, damaging blocks). Never entered, stood on, or broken. */
  DANGER,
  /** Ladders, vines and similar: passable, and can be climbed up and down. */
  CLIMBABLE;

  /** Whether an entity's body may occupy this block. */
  public boolean isPassable() {
    return this == OPEN || this == WATER || this == CLIMBABLE;
  }
}
