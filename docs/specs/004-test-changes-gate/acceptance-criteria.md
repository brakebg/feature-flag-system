# Acceptance criteria — spec 004

Owner-authored, locked. IDs for gate 14 (traceability).

Due milestone: 10

- [x] **AC-TC-1** · A planted change in a test whose tags are not in the active spec's `test-changes.md` (expected value, assertion method, removed assertion, `@Tag` or `[ID]` title, deleted file) makes gate 16 fail and print the file and line.
- [x] **AC-TC-2** · An import, annotation, helper signature or type-only change in a test that is not listed passes gate 16; a listed test with a changed expected value passes.
