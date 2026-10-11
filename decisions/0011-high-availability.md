# 0011 · Spec 003: high availability with a shared Redis cache

Status: **Proposed** (draft for owner review). Owner-authored.

## Decision (proposed)

Run the backend as several instances that share one Redis cache. Every change writes an outbox row
(`flag_change`) in the same transaction. Debezium Server moves it to a Redis stream; one applier
instance updates the cache keys in order. All instances read the flags from Redis and fall back to
the database when Redis fails. The ETag is a revision counter kept in Redis.

## Why

Spec 001 runs exactly one instance (cache and revision live in the process): a crash or a deploy
interrupts all clients. A per-instance cache must be kept in step on every instance, which is
fragile. One shared cache has one copy of the data. See issue #16 for the options considered
(PostgreSQL `LISTEN/NOTIFY`, polling, per-instance cache with Redis messages, shared cache).

## Redis distribution

Redis 8, used under its AGPLv3 option, unmodified, as a separate container. Valkey (BSD) is the
fallback; only the image changes.

## What changes

- The v1 ban on Redis and Lettuce and the "no distributed cache" non-goal are lifted for this case.
  Jedis, Hazelcast, Kafka and RabbitMQ stay banned. Caffeine is removed.
- AC-CACHE-2, 3, 4 and 6 are re-expressed (spec 003 section 6).
- New spec folder `docs/specs/003-high-availability/` (milestone M10).

## Consequences

- Redis becomes a critical part: when it fails, reads go to the database (slower, correct).
- Redis and PostgreSQL stay single points of failure in this spec.
- A change is visible to clients within about 2 s, not before the HTTP response returns.
