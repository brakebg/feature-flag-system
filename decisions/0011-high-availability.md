# 0011 · Spec 003: high availability with outbox, Debezium and Redis Streams

Status: **Proposed** (draft for owner review). Owner-authored.

## Decision (proposed)

Run the backend as several instances. Keep each instance's in-process Caffeine cache. Keep the
caches in step with a transactional outbox table (`flag_change`), Debezium Server and a Redis
stream, with a database revision poll as a safety net. The ETag is derived from the outbox id.

## Why

Spec 001 runs exactly one instance (cache and revision live in the process): a crash or a deploy
interrupts all clients. See GitHub issue #16 for the options considered: PostgreSQL
`LISTEN/NOTIFY`, revision polling, Redis, outbox with CDC.

## What changes

- The v1 ban on Redis, Lettuce and message infrastructure is lifted **for this case only**.
  Jedis, Hazelcast, Kafka and RabbitMQ stay banned (spec 003 section 1).
- New spec folder `docs/specs/003-high-availability/` (milestone M10).

## Redis distribution

Redis 8, used under its AGPLv3 option, unmodified, as a separate container. Valkey (BSD) was
considered and is the fallback if the AGPL becomes a problem; only the image changes.

## Consequences

- Two new components to run (Redis, Debezium Server); PostgreSQL needs `wal_level=logical`.
- Redis and Debezium only speed up propagation; the database stays the source of truth.
- PostgreSQL stays a single point of failure.
