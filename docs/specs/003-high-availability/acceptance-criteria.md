# Acceptance criteria — spec 003

DRAFT for owner review. Owner-authored, locked once accepted. IDs for gate 14 (traceability).

Due milestone: 10

- [ ] **AC-HA-1** · Two backend instances on one database serve the same flags from all three Evaluation endpoints, with the same `ETag`.
- [ ] **AC-HA-2** · A change made through instance A is visible on instance B within 2 s (p99), with zero database queries for B's evaluation call.
- [ ] **AC-HA-3** · The revision in the database follows commit order with no gaps: two overlapping admin writes get consecutive revisions, and a rolled-back write leaves the revision unchanged.
- [ ] **AC-HA-4** · Monotonic ETag: a client that got `ETag "<n>"` from instance A and sends `If-None-Match: "<n>"` to instance B, which is still behind, gets either `304` or a `200` with a revision ≥ n. It never gets older data.
- [ ] **AC-HA-5** · With the database unreachable, both instances keep answering every Evaluation call from their snapshot with the same `ETag` as before; readiness stays UP; admin writes fail as today.
- [ ] **AC-HA-6** · An instance restarted after changes were made on the other instance serves the new state and the current revision as soon as it is ready.
- [ ] **AC-HA-7** · An instance that lags by more than the retention of `flag_change` does a full reload and ends at the current revision with the current data.
- [ ] **AC-HA-8** · During a rolling restart of both instances, one at a time, a client polling the Evaluation API through the UI proxy gets no 5xx and never a lower revision than it already has.
- [ ] **AC-HA-9** · The revision of an instance never decreases, also across restart and full reload.
- [ ] **AC-HA-10** · Reconciliation runs on exactly one instance per schedule; when it finds drift, every instance returns the corrected value within 2 s and the `ETag` changed.
