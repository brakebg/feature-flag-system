# 0011 · Spec 003: high availability with per-instance caches and a database revision

Status: **Accepted** (2026-10-11). Owner-authored.

## Decision

Run the backend as several instances. Each keeps its own in-process cache, as today. A one-row
`cache_state.revision` counter in PostgreSQL is incremented under its row lock inside every
change transaction, so revision order equals commit order. A `flag_change` row per revision names
the group to reload. Every instance polls the counter every second and reloads the changed
groups. The revision is the `ETag`; an instance that receives a newer `If-None-Match` than its own
snapshot syncs before answering, so a client is never shown older data.

No Redis, no CDC, no message broker. The v1 bans stay.

## Why

Spec 001 runs exactly one instance: a crash or a deploy interrupts all clients. Two independent
architecture reviews (2026-10-11) compared: PostgreSQL `LISTEN/NOTIFY`, revision polling, a shared
Redis cache fed by Debezium CDC, and write-through to Redis. Both recommended this design.

- CDC adds a replication slot, offset storage and duplicate delivery for a row the service writes
  itself and can read with one indexed query.
- A shared Redis cache adds a failure domain and removes none: when Redis is down every instance
  falls back to the database, which spec 9.2 never sized for that. A per-instance cache keeps
  serving during a database outage, and that data cannot be stale because no write can commit.
- The earlier draft (shared Redis) had unfixable ETag flaws: revision reset after a rebuild and a
  fallback ETag that is not commit-ordered, both giving wrong `304`s.

## What changes

- The "horizontal scaling" non-goal of spec 1.2 is lifted. "No distributed cache" stays.
- AC-CACHE-4, 6 and 7 are re-expressed (spec 003 section 5).
- New spec folder `docs/specs/003-high-availability/` (milestone M11).

## Consequences

- Admin writes are serialised on one row lock (fine at admin-UI rates).
- Other instances see a change within about 1 s; the writing instance at once.
- Each instance holds the full data set in memory, as today.
- PostgreSQL stays a single point of failure for writes and cold starts.
