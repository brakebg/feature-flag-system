# Acceptance criteria — spec 003

Owner-authored, locked. IDs for gate 14 (traceability).

Due milestone: 11

"N" below is any number of instances ≥ 2. Nothing in the code may assume a fixed count. The
integration tests run with N = 3; the compose stack runs with N = 2.

- [ ] **AC-HA-1** · N backend instances on one database serve the same flags from all three Evaluation endpoints, with the same `ETag`, for N = 3 in the test.
- [ ] **AC-HA-2** · A change made through one instance is visible on every other instance within 2 s (p99), with zero database queries for their evaluation calls.
- [ ] **AC-HA-3** · The revision in the database follows commit order with no gaps: two overlapping admin writes on different instances get consecutive revisions, and a rolled-back write leaves the revision unchanged.
- [ ] **AC-HA-4** · Monotonic ETag: a client that got `ETag "<n>"` from one instance and sends `If-None-Match: "<n>"` to another instance that is still behind gets either `304` or a `200` with a revision ≥ n. It never gets older data.
- [ ] **AC-HA-5** · With the database unreachable, every instance keeps answering every Evaluation call from its snapshot with the same `ETag` as before; readiness stays UP; admin writes fail as today.
- [ ] **AC-HA-6** · An instance restarted after changes were made on other instances serves the new state and the current revision as soon as it is ready. A new instance added to a running set does the same.
- [ ] **AC-HA-7** · An instance that lags by more than the retention of `flag_change` does a full reload and ends at the current revision with the current data.
- [ ] **AC-HA-8** · During a rolling restart of all instances, one at a time, a client polling the Evaluation API through the UI proxy gets no 5xx and never a lower revision than it already has.
- [ ] **AC-HA-9** · The revision of an instance never decreases, also across restart and full reload.
- [ ] **AC-HA-10** · Reconciliation runs on exactly one instance per schedule, whichever N; when it finds drift, every instance returns the corrected value within 2 s and the `ETag` changed.
