# Acceptance criteria — spec 003

DRAFT for owner review. Owner-authored, locked once accepted. IDs for gate 14 (traceability).

Due milestone: 10

- [ ] **AC-HA-1** · Two backend instances on one database serve the same flags, group by group, from all three Evaluation endpoints.
- [ ] **AC-HA-2** · A change made through instance A is visible on instance B within 2 s (p99) when Redis and Debezium are healthy.
- [ ] **AC-HA-3** · For the same data, both instances return the same `ETag`. After a change the `ETag` is higher on both.
- [ ] **AC-HA-4** · A client that got an `ETag` from instance A and sends it as `If-None-Match` to instance B gets `304` when nothing changed.
- [ ] **AC-HA-5** · With Redis stopped, a change made through A still reaches B within the poll bound (`FF_CACHE_REVISION_POLL` + `FF_CACHE_CATCHUP_GRACE`, default 15 s).
- [ ] **AC-HA-6** · With Debezium Server stopped and started again, no change is lost: after it is back, both instances hold the database state.
- [ ] **AC-HA-7** · An instance restarted after changes were made on the other instance serves the new state as soon as it is ready.
- [ ] **AC-HA-8** · During a rolling restart of both instances, one at a time, a client polling the Evaluation API through the UI proxy gets no 5xx answer.
- [ ] **AC-HA-9** · The `revision` of an instance never decreases, also after duplicated, replayed or out-of-order stream entries.
- [ ] **AC-HA-10** · A deployment with `replicas: 1` and no Redis or Debezium still starts and passes spec 001 behaviour (single-instance mode).
