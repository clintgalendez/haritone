package io.github.clintgalendez.haritone.pathfinding;

/** How a path step is reached from the previous one. */
public enum Movement {
  /** The first step of every path: where the entity already stands. */
  START,
  /** One block in a cardinal direction on the same level. */
  TRAVERSE,
  /** One block diagonally on the same level, without cutting corners. */
  DIAGONAL,
  /** One block in a cardinal direction and one up (a jump or step up). */
  ASCEND,
  /** One block in a cardinal direction and one or more down (a step or fall). */
  DESCEND,
  /** One block up a ladder or vine. */
  CLIMB_UP,
  /** One block down a ladder or vine. */
  CLIMB_DOWN,
  /** One block up through water. */
  SWIM_UP,
  /** One block down through water. */
  SWIM_DOWN
}
