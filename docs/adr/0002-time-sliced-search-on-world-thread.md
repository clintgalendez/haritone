# ADR-0002: Time-sliced search on the world thread

- Status: accepted
- Date: 2026-10-15

## Context

Hytale world data (the chunk and entity stores) is single-threaded per world. Searching on
another thread would mean either copying a region snapshot on the world thread first, or
reading the stores unsafely. A snapshot big enough for a useful search (for example
129×65×129 blocks, about one million reads) costs more world-thread time than the search itself
usually needs.

## Decision

`PathSearch` is a resumable A* (`advance(maxExpansions, budgetNanos)`). The plugin advances
each planning session on its world's thread for a bounded slice per tick: by default 4 ms per
world, split between that world's planning sessions. Total compute per search is capped by
`SearchTimeoutMs`, and memory by `MaxNodes`. Terrain lookups are memoized per search in a
bounded LRU cache.

## Consequences

- No unsafe cross-thread world access and no snapshot copy.
- A long search takes several ticks of wall-clock time. That is fine for guidance, where an
  answer is useful within seconds.
- Results can be slightly stale. Navigation re-checks the upcoming steps against the live world
  and replans when they change.
- Because the core search is pure, it could move to a worker thread later (for example with a
  chunk-section snapshot cache) without changing its API.
