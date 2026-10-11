# Acceptance criteria — spec 003

DRAFT for owner review. Owner-authored, locked once accepted. IDs for gate 14 (traceability).

Due milestone: 10

- [ ] **AC-HA-1** · Two backend instances on one database and one Redis serve the same flags from all three Evaluation endpoints.
- [ ] **AC-HA-2** · A change made through instance A is visible on instance B within 2 s (p99) when Redis and Debezium are healthy.
- [ ] **AC-HA-3** · For the same data both instances return the same `ETag`. After a change it differs from the earlier one on both.
- [ ] **AC-HA-4** · A client that got an `ETag` from instance A and sends it as `If-None-Match` to instance B gets `304` when nothing changed.
- [ ] **AC-HA-5** · With Redis stopped, every Evaluation call is still answered, with data equal to the database, and a change made meanwhile is visible on the next call. No 5xx.
- [ ] **AC-HA-6** · When Redis is back, reads come from Redis again and show the latest data, including every change made while it was down. No stale value.
- [ ] **AC-HA-7** · After Redis restarts empty, the cache is rebuilt from the database; an `ETag` served before the restart is never served again for different data.
- [ ] **AC-HA-8** · When the active applier instance is killed, another instance takes over within 15 s; no change is lost and none is applied twice (the revision moves by one per change).
- [ ] **AC-HA-9** · With Debezium Server stopped and started again, no change is lost: after it is back both instances serve the database state.
- [ ] **AC-HA-10** · During a rolling restart of both instances, one at a time, a client polling the Evaluation API through the UI proxy gets no 5xx answer.
- [ ] **AC-HA-11** · With the cache ready, an unknown flag or group key returns 404 with zero database queries.
