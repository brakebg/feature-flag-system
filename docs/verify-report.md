# Verify report

Mode: `all` · Milestone: M8 · Commit: e670c3c · 2026-10-05T13:36:59.374Z

**Result: PASS**

| # | Gate | Status | Duration |
| --- | --- | --- | --- |
| 1 | Format and lint | pass | 4.7 s |
| 2 | Compile and types | pass | 3.7 s |
| 3 | Architecture rules | pass | 3.9 s |
| 4 | Banned dependencies | pass | 1.1 s |
| 5 | Backend tests | pass | 67.9 s |
| 6 | Coverage | pass | 9.8 s |
| 7 | Mutation testing | pass | 21.1 s |
| 8 | API contract | pass | 14.2 s |
| 9 | Frontend tests | pass | 5.9 s |
| 10 | Secrets | pass | 28.9 s |
| 11 | Docker smoke test | pass | 79.2 s |
| 12 | End-to-end | pass | 146.4 s |
| 13 | Performance | pass | 98.7 s |
| 14 | Traceability | pass | 0.1 s |
| 15 | Test integrity | pass | 0.2 s |

## Gate 13 measurements

| Measure | Value | Target | Blocks the gate |
| --- | --- | --- | --- |
| Requests | 12001 | 200 req/s for 60 s | - |
| Error rate | 0 | 0 | yes |
| Failed checks | 0 | 0 | yes |
| Cache hit rate | 1.0000 | >= 0.99 | yes |
| Evaluation p95 | 2.8 ms | < 50 ms (met) | no (decision 0007) |


## Traceability (gate 14)

Milestone M8. Tests read: 513. Problems: 0. Pending (not yet due): 0.

| ID | Status | Tests |
| --- | --- | --- |
| AC-AUTH-1 | pass | ✓ vitest: src/features/auth/LoginPage.test.tsx › LoginPage (spec 8.2) > [AC-AUTH-1] wrong credentials show the generic error and stay on /login<br>✓ playwright:chromium-desktop: authentication (spec 8.2, 8.3) › [AC-AUTH-1] wrong credentials show the error and stay on /login<br>✓ playwright:chromium-narrow: authentication (spec 8.2, 8.3) › [AC-AUTH-1] wrong credentials show the error and stay on /login |
| AC-AUTH-2 | pass | ✓ vitest: src/features/auth/LoginPage.test.tsx › LoginPage (spec 8.2) > [AC-AUTH-2] admin / admin123 lands on /groups and stores the raw token<br>✓ playwright:chromium-desktop: authentication (spec 8.2, 8.3) › [AC-AUTH-2] admin / admin123 signs in and lands on /groups @cross-browser<br>✓ playwright:chromium-narrow: authentication (spec 8.2, 8.3) › [AC-AUTH-2] admin / admin123 signs in and lands on /groups @cross-browser<br>✓ playwright:firefox-desktop: authentication (spec 8.2, 8.3) › [AC-AUTH-2] admin / admin123 signs in and lands on /groups @cross-browser<br>✓ playwright:webkit-desktop: authentication (spec 8.2, 8.3) › [AC-AUTH-2] admin / admin123 signs in and lands on /groups @cross-browser |
| AC-AUTH-3 | pass | ✓ vitest: src/features/shell/AppShell.test.tsx › routes and app shell (spec 8.1, 8.3) > [AC-AUTH-3] without a token /groups redirects to /login<br>✓ playwright:chromium-desktop: authentication (spec 8.2, 8.3) › [AC-AUTH-3] without a token /groups redirects to /login<br>✓ playwright:chromium-narrow: authentication (spec 8.2, 8.3) › [AC-AUTH-3] without a token /groups redirects to /login |
| AC-AUTH-4 | pass | ✓ vitest: src/features/audit/audit.test.tsx › audit page (spec 8.6) > [AC-AUTH-4] a 401 on a real screen clears the token, the cached data and goes to /login?expired=1<br>✓ vitest: src/features/shell/AppShell.test.tsx › routes and app shell (spec 8.1, 8.3) > [AC-AUTH-4] a token whose exp is in the past is cleared and leads to /login?expired=1<br>✓ vitest: src/features/shell/AppShell.test.tsx › routes and app shell (spec 8.1, 8.3) > [AC-AUTH-4] the expired banner also shows under StrictMode (render stays pure)<br>✓ vitest: src/features/shell/AppShell.test.tsx › routes and app shell (spec 8.1, 8.3) > [AC-AUTH-4] any 401 from the Admin API clears the token and leads to /login?expired=1<br>✓ vitest: src/features/shell/AppShell.test.tsx › routes and app shell (spec 8.1, 8.3) > [AC-AUTH-4] FF-6: a token that expires during the session also clears the cached data<br>✓ playwright:chromium-desktop: authentication (spec 8.2, 8.3) › [AC-AUTH-4] an expired token leads to /login?expired=1 @cross-browser<br>✓ playwright:chromium-desktop: authentication (spec 8.2, 8.3) › [AC-AUTH-4] a 401 from an admin call (token with a changed signature) leads to /login?expired=1<br>✓ playwright:chromium-narrow: authentication (spec 8.2, 8.3) › [AC-AUTH-4] an expired token leads to /login?expired=1 @cross-browser<br>✓ playwright:chromium-narrow: authentication (spec 8.2, 8.3) › [AC-AUTH-4] a 401 from an admin call (token with a changed signature) leads to /login?expired=1<br>✓ playwright:firefox-desktop: authentication (spec 8.2, 8.3) › [AC-AUTH-4] an expired token leads to /login?expired=1 @cross-browser<br>✓ playwright:webkit-desktop: authentication (spec 8.2, 8.3) › [AC-AUTH-4] an expired token leads to /login?expired=1 @cross-browser |
| AC-AUTH-5 | removed (no test needed) | - |
| AC-AUTH-6 | pass | ✓ vitest: src/api/apiClient.test.ts › apiClient (spec 8.7) > [AC-AUTH-6] without a token no Admin API request is sent (spec 8.3)<br>✓ vitest: src/features/shell/AppShell.test.tsx › routes and app shell (spec 8.1, 8.3) > [AC-AUTH-6] sign out clears the token, goes to /login and Back does not show protected data<br>✓ playwright:chromium-desktop: authentication (spec 8.2, 8.3) › [AC-AUTH-6] sign out clears the token; Back does not show protected data<br>✓ playwright:chromium-narrow: authentication (spec 8.2, 8.3) › [AC-AUTH-6] sign out clears the token; Back does not show protected data |
| AC-GRP-1 | pass | ✓ junit: AdminGroupsIT.listShowsCountsAndOmitsMissingDescription<br>✓ vitest: src/features/groups/GroupsPage.test.tsx › groups pane (spec 8.4) > [AC-GRP-1] create group orders (name Orders): it is selected, in the list with badge 0/0, and toasted<br>✓ playwright:chromium-desktop: groups (spec 8.4, 8.5) › [AC-GRP-1] create a group: selected, in the list with badge 0/0<br>✓ playwright:chromium-narrow: groups (spec 8.4, 8.5) › [AC-GRP-1] create a group: selected, in the list with badge 0/0 |
| AC-GRP-2 | pass | ✓ junit: AdminGroupsIT.duplicateKeyIs409<br>✓ vitest: src/features/groups/GroupsPage.test.tsx › groups pane (spec 8.4) > [AC-GRP-2] a second group with key orders shows "Key already exists" on the Key field<br>✓ playwright:chromium-desktop: groups (spec 8.4, 8.5) › [AC-GRP-2] a second group with the same key shows "Key already exists"<br>✓ playwright:chromium-narrow: groups (spec 8.4, 8.5) › [AC-GRP-2] a second group with the same key shows "Key already exists" |
| AC-GRP-3 | pass | ✓ junit: AdminFlagsIT.createValidation<br>✓ junit: AdminGroupsIT.keysFailingTheRegexAreRejected<br>✓ junit: AdminGroupsIT.keysFailingTheRegexAreRejected<br>✓ junit: AdminGroupsIT.keysFailingTheRegexAreRejected<br>✓ junit: AdminGroupsIT.keysFailingTheRegexAreRejected<br>✓ vitest: src/features/flags/flags.test.tsx › flags (spec 8.4, 8.5) > [AC-GRP-3] flag key Orders is rejected in the New flag dialog without a request<br>✓ vitest: src/features/flags/flags.test.tsx › flags (spec 8.4, 8.5) > [AC-GRP-3] flag key 1abc is rejected in the New flag dialog without a request<br>✓ vitest: src/features/flags/flags.test.tsx › flags (spec 8.4, 8.5) > [AC-GRP-3] flag key a is rejected in the New flag dialog without a request<br>✓ vitest: src/features/flags/flags.test.tsx › flags (spec 8.4, 8.5) > [AC-GRP-3] flag key has space is rejected in the New flag dialog without a request<br>✓ vitest: src/features/groups/GroupsPage.test.tsx › groups pane (spec 8.4) > [AC-GRP-3] key Orders is rejected in the UI without a request<br>✓ vitest: src/features/groups/GroupsPage.test.tsx › groups pane (spec 8.4) > [AC-GRP-3] key 1abc is rejected in the UI without a request<br>✓ vitest: src/features/groups/GroupsPage.test.tsx › groups pane (spec 8.4) > [AC-GRP-3] key a is rejected in the UI without a request<br>✓ vitest: src/features/groups/GroupsPage.test.tsx › groups pane (spec 8.4) > [AC-GRP-3] key has space is rejected in the UI without a request<br>✓ vitest: src/schemas/forms.test.ts › form rules = backend rules (spec 4.2) > [AC-GRP-3] key Orders is invalid<br>✓ vitest: src/schemas/forms.test.ts › form rules = backend rules (spec 4.2) > [AC-GRP-3] key 1abc is invalid<br>✓ vitest: src/schemas/forms.test.ts › form rules = backend rules (spec 4.2) > [AC-GRP-3] key a is invalid<br>✓ vitest: src/schemas/forms.test.ts › form rules = backend rules (spec 4.2) > [AC-GRP-3] key has space is invalid<br>✓ vitest: src/schemas/forms.test.ts › form rules = backend rules (spec 4.2) > [AC-GRP-3] key -ab is invalid<br>✓ vitest: src/schemas/forms.test.ts › form rules = backend rules (spec 4.2) > [AC-GRP-3] key aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa is invalid<br>✓ playwright:chromium-desktop: groups (spec 8.4, 8.5) › [AC-GRP-3] key "Orders" is rejected in the UI and by the API<br>✓ playwright:chromium-desktop: groups (spec 8.4, 8.5) › [AC-GRP-3] key "1abc" is rejected in the UI and by the API<br>✓ playwright:chromium-desktop: groups (spec 8.4, 8.5) › [AC-GRP-3] key "a" is rejected in the UI and by the API<br>✓ playwright:chromium-desktop: groups (spec 8.4, 8.5) › [AC-GRP-3] key "has space" is rejected in the UI and by the API<br>✓ playwright:chromium-narrow: groups (spec 8.4, 8.5) › [AC-GRP-3] key "Orders" is rejected in the UI and by the API<br>✓ playwright:chromium-narrow: groups (spec 8.4, 8.5) › [AC-GRP-3] key "1abc" is rejected in the UI and by the API<br>✓ playwright:chromium-narrow: groups (spec 8.4, 8.5) › [AC-GRP-3] key "a" is rejected in the UI and by the API<br>✓ playwright:chromium-narrow: groups (spec 8.4, 8.5) › [AC-GRP-3] key "has space" is rejected in the UI and by the API |
| AC-GRP-4 | pass | ✓ junit: AdminGroupsIT.patchUpdatesNameAndDescriptionAndWritesOneAuditEvent<br>✓ vitest: src/features/groups/GroupsPage.test.tsx › group header and edit (spec 8.4, 8.5) > [AC-GRP-4] edit name and description; the key is read-only<br>✓ playwright:chromium-desktop: groups (spec 8.4, 8.5) › [AC-GRP-4] edit name and description; the key is read-only<br>✓ playwright:chromium-narrow: groups (spec 8.4, 8.5) › [AC-GRP-4] edit name and description; the key is read-only |
| AC-GRP-5 | pass | ✓ junit: AdminGroupsIT.deleteRemovesGroupAndFlagsAndAuditsTheFlagKeys<br>✓ junit: EvaluationIT.deletedGroupAndBothFlagsAre404OnTheEvaluationApi<br>✓ vitest: src/features/groups/GroupsPage.test.tsx › delete group (spec 8.5) > [AC-GRP-5] needs the exact key typed, lists the flags, then goes to /groups with a toast<br>✓ playwright:chromium-desktop: groups (spec 8.4, 8.5) › [AC-GRP-5] delete a group with 2 flags: typed key, then gone from UI, Admin API, Evaluation API; one audit event @cross-browser<br>✓ playwright:chromium-narrow: groups (spec 8.4, 8.5) › [AC-GRP-5] delete a group with 2 flags: typed key, then gone from UI, Admin API, Evaluation API; one audit event @cross-browser<br>✓ playwright:firefox-desktop: groups (spec 8.4, 8.5) › [AC-GRP-5] delete a group with 2 flags: typed key, then gone from UI, Admin API, Evaluation API; one audit event @cross-browser<br>✓ playwright:webkit-desktop: groups (spec 8.4, 8.5) › [AC-GRP-5] delete a group with 2 flags: typed key, then gone from UI, Admin API, Evaluation API; one audit event @cross-browser |
| AC-FLAG-1 | pass | ✓ junit: AdminFlagsIT.createReturns201WithLocationFullKeyAndDefaultOff<br>✓ vitest: src/features/flags/flags.test.tsx › flags (spec 8.4, 8.5) > [AC-FLAG-1] add flag new-checkout to orders, initially off, with the full key shown<br>✓ playwright:chromium-desktop: flags (spec 8.4, 8.5) › [AC-FLAG-1] add flag new-checkout, initially off, full key shown @cross-browser<br>✓ playwright:chromium-narrow: flags (spec 8.4, 8.5) › [AC-FLAG-1] add flag new-checkout, initially off, full key shown @cross-browser<br>✓ playwright:firefox-desktop: flags (spec 8.4, 8.5) › [AC-FLAG-1] add flag new-checkout, initially off, full key shown @cross-browser<br>✓ playwright:webkit-desktop: flags (spec 8.4, 8.5) › [AC-FLAG-1] add flag new-checkout, initially off, full key shown @cross-browser |
| AC-FLAG-2 | pass | ✓ junit: AdminFlagsIT.sameFlagKeyInTwoGroups<br>✓ vitest: src/features/flags/flags.test.tsx › flags (spec 8.4, 8.5) > [AC-FLAG-2] the same flag key in two groups<br>✓ playwright:chromium-desktop: flags (spec 8.4, 8.5) › [AC-FLAG-2] the same flag key in two groups<br>✓ playwright:chromium-narrow: flags (spec 8.4, 8.5) › [AC-FLAG-2] the same flag key in two groups |
| AC-FLAG-3 | pass | ✓ junit: AdminFlagsIT.toggleSetsTheValueAndIsIdempotent<br>✓ vitest: src/features/flags/flags.test.tsx › flags (spec 8.4, 8.5) > [AC-FLAG-3] toggling updates the switch at once, the badge, and stays after a reload<br>✓ vitest: src/features/flags/reviewFixes.test.tsx › final review fixes (spec 8.5) > [AC-FLAG-3] FF-1: a refetch after one toggle does not flip another toggle that is still running<br>✓ playwright:chromium-desktop: flags (spec 8.4, 8.5) › [AC-FLAG-3] [AC-EVAL-5] toggle: switch at once, badge, persists after reload, next evaluation is new @cross-browser<br>✓ playwright:chromium-narrow: flags (spec 8.4, 8.5) › [AC-FLAG-3] [AC-EVAL-5] toggle: switch at once, badge, persists after reload, next evaluation is new @cross-browser<br>✓ playwright:firefox-desktop: flags (spec 8.4, 8.5) › [AC-FLAG-3] [AC-EVAL-5] toggle: switch at once, badge, persists after reload, next evaluation is new @cross-browser<br>✓ playwright:webkit-desktop: flags (spec 8.4, 8.5) › [AC-FLAG-3] [AC-EVAL-5] toggle: switch at once, badge, persists after reload, next evaluation is new @cross-browser |
| AC-FLAG-4 | pass | ✓ vitest: src/features/flags/flags.test.tsx › flags (spec 8.4, 8.5) > [AC-FLAG-4] a failed toggle reverts the switch and shows the error toast<br>✓ vitest: src/features/groups/loadStates.test.tsx › toggle robustness (spec 8.5) > [AC-FLAG-4] the error toast still shows when the user leaves the group before the toggle fails<br>✓ vitest: src/features/groups/loadStates.test.tsx › toggle robustness (spec 8.5) > [AC-FLAG-4] a failed toggle does not undo a newer successful toggle of another flag<br>✓ playwright:chromium-desktop: flags (spec 8.4, 8.5) › [AC-FLAG-4] a failed toggle reverts the switch and shows the error toast<br>✓ playwright:chromium-narrow: flags (spec 8.4, 8.5) › [AC-FLAG-4] a failed toggle reverts the switch and shows the error toast |
| AC-FLAG-5 | pass | ✓ junit: AdminFlagsIT.deleteRemovesOnlyThatFlag<br>✓ vitest: src/features/flags/flags.test.tsx › flags (spec 8.4, 8.5) > [AC-FLAG-5] deleting one flag removes only that flag<br>✓ playwright:chromium-desktop: flags (spec 8.4, 8.5) › [AC-FLAG-5] deleting one flag removes only that flag<br>✓ playwright:chromium-narrow: flags (spec 8.4, 8.5) › [AC-FLAG-5] deleting one flag removes only that flag |
| AC-FLAG-6 | pass | ✓ junit: AdminFlagsIT.staleVersionIsConflict<br>✓ vitest: src/features/flags/flags.test.tsx › flags (spec 8.4, 8.5) > [AC-FLAG-6] editing with a stale version gives 409: dialog closes, toast, and the group is refetched<br>✓ vitest: src/features/groups/GroupsPage.test.tsx › group header and edit (spec 8.4, 8.5) > [AC-FLAG-6] a stale group version closes the dialog, shows the error toast and refetches<br>✓ playwright:chromium-desktop: flags (spec 8.4, 8.5) › [AC-FLAG-6] editing with a stale version: 409, toast, and the UI refetches<br>✓ playwright:chromium-narrow: flags (spec 8.4, 8.5) › [AC-FLAG-6] editing with a stale version: 409, toast, and the UI refetches |
| AC-EVAL-1 | pass | ✓ junit: TokenIssuerTest.aTtlWithFractionsUsesTheWholeSeconds<br>✓ junit: TokenEndpointIT.omittedOrEmptyScopeMeansAllRegisteredScopes<br>✓ junit: TokenEndpointIT.validClientGetsABearerTokenWithFlagsRead<br>✓ playwright:api: evaluation API and client tokens (spec 5.5, 7) › [AC-EVAL-1] client credentials give a bearer JWT with scope flags:read and expires_in 900<br>✓ smoke: [AC-EVAL-1] client token |
| AC-EVAL-2 | pass | ✓ junit: TokenEndpointIT.wrongSecretIsInvalidClientWithBasicChallenge<br>✓ junit: TokenEndpointIT.unknownGrantTypeIsUnsupported<br>✓ junit: TokenEndpointIT.adminScopeIsInvalidScope<br>✓ playwright:api: evaluation API and client tokens (spec 5.5, 7) › [AC-EVAL-2] wrong secret 401 invalid_client; unknown grant 400; admin scope 400 invalid_scope |
| AC-EVAL-3 | pass | ✓ junit: SecurityRulesIT.evaluationWithAdminTokenOrWrongScopeOrAudienceIs403<br>✓ junit: SecurityRulesIT.evaluationWithoutOrWithExpiredTokenIs401<br>✓ junit: EvaluationIT.clientTokenReadsAllThreeEndpoints<br>✓ playwright:api: evaluation API and client tokens (spec 5.5, 7) › [AC-EVAL-3] evaluation: no token 401, expired 401, admin token 403, client token 200 |
| AC-EVAL-4 | pass | ✓ junit: SecurityRulesIT.clientTokenOnAdminApiIs403<br>✓ playwright:api: evaluation API and client tokens (spec 5.5, 7) › [AC-EVAL-4] a client token on any admin endpoint gets 403 |
| AC-EVAL-5 | pass | ✓ junit: EvaluationIT.toggleIsVisibleOnTheNextCallWithoutQueries<br>✓ playwright:chromium-desktop: flags (spec 8.4, 8.5) › [AC-FLAG-3] [AC-EVAL-5] toggle: switch at once, badge, persists after reload, next evaluation is new @cross-browser<br>✓ playwright:chromium-narrow: flags (spec 8.4, 8.5) › [AC-FLAG-3] [AC-EVAL-5] toggle: switch at once, badge, persists after reload, next evaluation is new @cross-browser<br>✓ playwright:firefox-desktop: flags (spec 8.4, 8.5) › [AC-FLAG-3] [AC-EVAL-5] toggle: switch at once, badge, persists after reload, next evaluation is new @cross-browser<br>✓ playwright:webkit-desktop: flags (spec 8.4, 8.5) › [AC-FLAG-3] [AC-EVAL-5] toggle: switch at once, badge, persists after reload, next evaluation is new @cross-browser<br>✓ smoke: [AC-EVAL-5] evaluate returns false after toggle |
| AC-EVAL-6 | pass | ✓ junit: EvaluationIT.ifNoneMatchGives304UntilADataChange<br>✓ playwright:serial: [AC-EVAL-6] If-None-Match gives 304 until a write that changes data; failed and no-op writes keep the ETag<br>✓ playwright:serial: [AC-EVAL-6] If-None-Match gives 304 until a write that changes data; failed and no-op writes keep the ETag |
| AC-EVAL-7 | pass | ✓ junit: EvaluationIT.unknownGroupOrFlagIs404<br>✓ playwright:api: evaluation API and client tokens (spec 5.5, 7) › [AC-EVAL-7] unknown group or flag returns 404 |
| AC-CACHE-1 | pass | ✓ junit: FlagCacheServiceTest.afterReloadAllReadsAreHitsWithoutQueries<br>✓ junit: EvaluationIT.thousandCallsAfterWarmUpRunNoQueries |
| AC-CACHE-2 | pass | ✓ junit: FlagCacheServiceTest.evictedEntryReadTwiceConcurrentlyRunsOneQueryAndTheSecondReadIsAHit |
| AC-CACHE-3 | pass | ✓ junit: FlagCacheServiceTest.unknownKeysAreCachedForThirtySeconds<br>✓ junit: EvaluationIT.unknownKeyTwiceRunsOneQuery |
| AC-CACHE-4 | pass | ✓ junit: FlagCacheServiceTest.aLateFlagChangeAfterTheGroupWasDeletedDoesNotBringTheFlagBack<br>✓ junit: FlagCacheServiceTest.reconciliationMovesTheLateChangeLimitForward<br>✓ junit: FlagCacheServiceTest.parallelChangesToOneGroupAreAllKept<br>✓ junit: FlagCacheServiceTest.deletesLeaveEveryOtherEntryInPlace<br>✓ junit: FlagCacheServiceTest.aLateGroupDeleteOrCreateDoesNotUndoANewerChange<br>✓ junit: FlagCacheServiceTest.aChangeAlreadyInTheLoadedDataIsNotAppliedAgain<br>✓ junit: FlagCacheServiceTest.flagWritesUpdateAllThreeCachesWithoutQueries<br>✓ junit: FlagCacheServiceTest.groupWritesUpdateTheCaches<br>✓ junit: FlagCacheServiceTest.aChangeAppliedAfterANewerOneDoesNotOverwriteIt<br>✓ junit: EvaluationIT.createAndDeleteOfAFlagAndDeleteOfAGroupWithoutQueries<br>✓ junit: EvaluationIT.toggleIsVisibleOnTheNextCallWithoutQueries |
| AC-CACHE-5 | pass | ✓ junit: EvaluationIT.rolledBackWriteLeavesTheCacheUnchanged<br>✓ junit: EvaluationIT.writeWhoseCommitFailsLeavesTheCacheUnchanged |
| AC-CACHE-6 | pass | ✓ junit: EvaluationIT.readinessIsDownUntilWarmUpHasFinished<br>✓ playwright:api: operations (spec 9.3, 10.2) › [AC-CACHE-6] readiness is UP once the stack serves traffic<br>✓ smoke: [AC-CACHE-6] readiness DOWN before warm-up, UP after |
| AC-CACHE-7 | pass | ✓ junit: FlagCacheServiceTest.reconcileFixesDriftAndMovesTheRevision<br>✓ junit: EvaluationIT.directDatabaseChangeIsFixedByReconciliation |
| AC-CACHE-8 | pass | ✓ junit: FlagCacheServiceTest.reconcileWithoutDriftChangesNothing<br>✓ junit: EvaluationIT.reconciliationWithoutDifferencesLogsNoWarnAndKeepsTheETag |
| AC-CACHE-9 | pass | ✓ perf: [AC-CACHE-9] cache hit rate >= 99 % and no errors under 200 req/s |
| AC-AUD-1 | pass | ✓ junit: AdminFlagsIT.patchWritesExactlyOneFlagUpdatedEvent<br>✓ junit: AdminFlagsIT.createReturns201WithLocationFullKeyAndDefaultOff<br>✓ junit: AdminFlagsIT.toggleSetsTheValueAndIsIdempotent<br>✓ junit: AdminGroupsIT.patchUpdatesNameAndDescriptionAndWritesOneAuditEvent<br>✓ junit: AdminGroupsIT.deleteRemovesGroupAndFlagsAndAuditsTheFlagKeys<br>✓ junit: AuditApiIT.ownershipFollowsTheSignedInUserAndBodyFieldsAreIgnored<br>✓ junit: AuditApiIT.everyChangeWritesExactlyOneEventAndNoOpsWriteNone<br>✓ playwright:api: admin API (spec 4.3, 6, 9.2) › [AC-AUD-1] created/updated by the signed-in user, body fields ignored, one audit event per change, none for no-ops |
| AC-AUD-2 | pass | ✓ junit: AuditApiIT.targetKeyIsACaseSensitiveLiteralPrefix<br>✓ junit: AuditApiIT.newestFirstThenHighestIdAndStablePageFormat<br>✓ vitest: src/features/audit/audit.test.tsx › audit page (spec 8.6) > [AC-AUD-2] lists events newest first with labels, filters by target key, and loads more<br>✓ vitest: src/features/audit/audit.test.tsx › audit page (spec 8.6) > [AC-AUD-2] FF-2: events that move to the next page because of new events are shown once<br>✓ playwright:chromium-desktop: audit log (spec 8.6) › [AC-AUD-2] lists events newest first and filters by target key prefix @cross-browser<br>✓ playwright:chromium-narrow: audit log (spec 8.6) › [AC-AUD-2] lists events newest first and filters by target key prefix @cross-browser<br>✓ playwright:firefox-desktop: audit log (spec 8.6) › [AC-AUD-2] lists events newest first and filters by target key prefix @cross-browser<br>✓ playwright:webkit-desktop: audit log (spec 8.6) › [AC-AUD-2] lists events newest first and filters by target key prefix @cross-browser |
| AC-AUD-3 | pass | ✓ junit: AuditPurgeIT.nothingToPurge<br>✓ junit: AuditPurgeIT.deletesEventsOlderThan365DaysAndKeepsNewerOnes |
| AC-OPS-1 | pass | ✓ ops-up: [AC-OPS-1] compose up: three services running, postgres and backend healthy, UI 200 text/html within 90 s |
| AC-OPS-2 | pass | ✓ ops-standalone: [AC-OPS-2] backend image standalone with only DB env vars; UI image standalone with only BACKEND_URL |
| AC-OPS-3 | pass | ✓ junit: HealthIT.healthIsUpWithDatabaseStatus<br>✓ playwright:api: operations (spec 9.3, 10.2) › [AC-OPS-3] /actuator/health reports UP with the DB status<br>✓ smoke: [AC-OPS-3] health UP with db UP within 90 s |
| AC-OPS-4 | pass | ✓ junit: HttpsRequiredIT.tokenOverPlainHttpIsRejectedWithAProblemDetail<br>✓ junit: HttpsRequiredIT.loginOverPlainHttpIsRejected<br>✓ junit: HttpsRequiredIT.sameRequestsOverHttpsSucceed<br>✓ junit: HttpsRequiredIT.percentEncodedPathsAreCheckedToo<br>✓ playwright:api: operations (spec 9.3, 10.2) › [AC-OPS-4] UI response / carries every security header once; HSTS only with https<br>✓ playwright:api: operations (spec 9.3, 10.2) › [AC-OPS-4] UI response /groups/some-id carries every security header once; HSTS only with https<br>✓ playwright:api: operations (spec 9.3, 10.2) › [AC-OPS-4] UI response /assets/missing.js carries every security header once; HSTS only with https<br>✓ playwright:api: operations (spec 9.3, 10.2) › [AC-OPS-4] UI response /api/v1/evaluate/flags carries every security header once; HSTS only with https<br>✓ playwright:limits: [AC-OPS-4] with FF_REQUIRE_HTTPS=true, login and token over http get 403 https-required; https succeeds<br>✓ smoke: [AC-OPS-4] UI security headers (HSTS only with X-Forwarded-Proto: https) |
| ERR-POST-/auth/login-400 | pass | ✓ junit: LoginIT.invalidJsonOrWrongTypesAreMalformed<br>✓ junit: LoginIT.blankUsernameAndMissingPasswordAreValidationErrors<br>✓ junit: LoginIT.missingOrBlankFieldsAreValidationErrors |
| ERR-POST-/auth/login-401 | pass | ✓ junit: LoginIT.wrongPasswordAndUnknownUserGiveTheSameGeneric401 |
| ERR-POST-/auth/login-403 | pass | ✓ junit: HttpsRequiredIT.loginOverPlainHttpIsRejected |
| ERR-POST-/auth/token-400 | pass | ✓ junit: TokenEndpointIT.unknownGrantTypeIsUnsupported<br>✓ junit: TokenEndpointIT.adminScopeIsInvalidScope<br>✓ junit: TokenEndpointIT.missingGrantTypeOrWrongContentTypeIsInvalidRequest |
| ERR-POST-/auth/token-401 | pass | ✓ junit: TokenEndpointIT.bearerHeaderOnTheTokenEndpointIsInvalidClient<br>✓ junit: TokenEndpointIT.missingMalformedOrBodyOnlyCredentialsAreInvalidClient<br>✓ junit: TokenEndpointIT.wrongSecretIsInvalidClientWithBasicChallenge |
| ERR-POST-/auth/token-403 | pass | ✓ junit: HttpsRequiredIT.tokenOverPlainHttpIsRejectedWithAProblemDetail |
| ERR-GET-/admin/groups-400 | pass | ✓ junit: AdminGroupsIT.unknownSortIsValidationError |
| ERR-GET-/admin/groups-401 | pass | ✓ junit: SecurityRulesIT.expiredBadlySignedWrongIssuerOrGarbageTokensAre401<br>✓ junit: SecurityRulesIT.adminApiWithoutTokenIs401<br>✓ junit: AdminGroupsIT.listNeedsAnAdminToken |
| ERR-GET-/admin/groups-403 | pass | ✓ junit: SecurityRulesIT.clientTokenOnAdminApiIs403<br>✓ junit: SecurityRulesIT.adminScopeWithServiceAudienceOrServiceScopeWithAdminAudienceIs403<br>✓ junit: AdminGroupsIT.listNeedsAnAdminToken |
| ERR-POST-/admin/groups-400 | pass | ✓ junit: AdminGroupsIT.keysFailingTheRegexAreRejected<br>✓ junit: AdminGroupsIT.keysFailingTheRegexAreRejected<br>✓ junit: AdminGroupsIT.keysFailingTheRegexAreRejected<br>✓ junit: AdminGroupsIT.keysFailingTheRegexAreRejected<br>✓ junit: AdminGroupsIT.invalidJsonWrongTypesOrContentTypeAreMalformed<br>✓ junit: AdminGroupsIT.missingOrBlankNameAndLongDescriptionAreValidation |
| ERR-POST-/admin/groups-401 | pass | ✓ junit: AdminGroupsIT.createNeedsAnAdminToken |
| ERR-POST-/admin/groups-403 | pass | ✓ junit: AdminGroupsIT.createNeedsAnAdminToken |
| ERR-POST-/admin/groups-409 | pass | ✓ junit: AdminGroupsIT.parallelCreatesWithTheSameKeyGiveOneDuplicateKey<br>✓ junit: AdminGroupsIT.duplicateKeyIs409<br>✓ junit: AdminGroupsIT.thousandAndFirstGroupIsLimitReachedAndDeletingFreesASlot<br>✓ junit: AdminGroupsIT.parallelCreatesAtTheLimitLetExactlyOneThrough |
| ERR-POST-/admin/groups-413 | pass | ✓ junit: AdminGroupsIT.bodyAbove64KiBIs413 |
| ERR-GET-/admin/groups/{groupId}-400 | pass | ✓ junit: AdminGroupsIT.malformedIdIs400 |
| ERR-GET-/admin/groups/{groupId}-401 | pass | ✓ junit: AdminGroupsIT.detailNeedsAnAdminToken |
| ERR-GET-/admin/groups/{groupId}-403 | pass | ✓ junit: AdminGroupsIT.detailNeedsAnAdminToken |
| ERR-GET-/admin/groups/{groupId}-404 | pass | ✓ junit: AdminGroupsIT.unknownIdIs404 |
| ERR-PATCH-/admin/groups/{groupId}-400 | pass | ✓ junit: AdminGroupsIT.patchValidation |
| ERR-PATCH-/admin/groups/{groupId}-401 | pass | ✓ junit: AdminGroupsIT.patchErrors |
| ERR-PATCH-/admin/groups/{groupId}-403 | pass | ✓ junit: AdminGroupsIT.patchErrors |
| ERR-PATCH-/admin/groups/{groupId}-404 | pass | ✓ junit: AdminGroupsIT.patchErrors<br>✓ junit: AdminGroupsIT.patchThatWaitsForADeleteOfTheSameGroupIs404 |
| ERR-PATCH-/admin/groups/{groupId}-409 | pass | ✓ junit: AdminGroupsIT.parallelPatchesWithTheSameVersionGiveOneConflict<br>✓ junit: AdminGroupsIT.staleOrFutureVersionIsConflictEvenForANoOp |
| ERR-PATCH-/admin/groups/{groupId}-413 | pass | ✓ junit: AdminGroupsIT.patchErrors |
| ERR-DELETE-/admin/groups/{groupId}-400 | pass | ✓ junit: AdminGroupsIT.deleteErrors |
| ERR-DELETE-/admin/groups/{groupId}-401 | pass | ✓ junit: AdminGroupsIT.deleteErrors |
| ERR-DELETE-/admin/groups/{groupId}-403 | pass | ✓ junit: AdminGroupsIT.deleteErrors |
| ERR-DELETE-/admin/groups/{groupId}-404 | pass | ✓ junit: AdminGroupsIT.deleteErrors |
| ERR-POST-/admin/groups/{groupId}/flags-400 | pass | ✓ junit: AdminFlagsIT.createValidation |
| ERR-POST-/admin/groups/{groupId}/flags-401 | pass | ✓ junit: AdminFlagsIT.createErrors |
| ERR-POST-/admin/groups/{groupId}/flags-403 | pass | ✓ junit: AdminFlagsIT.createErrors |
| ERR-POST-/admin/groups/{groupId}/flags-404 | pass | ✓ junit: AdminFlagsIT.createErrors |
| ERR-POST-/admin/groups/{groupId}/flags-409 | pass | ✓ junit: AdminFlagsIT.duplicateKeyInTheSameGroupIs409<br>✓ junit: AdminFlagsIT.fiveHundredAndFirstFlagIsLimitReachedAndDeletingFreesASlot |
| ERR-POST-/admin/groups/{groupId}/flags-413 | pass | ✓ junit: AdminFlagsIT.createErrors |
| ERR-PATCH-/admin/flags/{flagId}-400 | pass | ✓ junit: AdminFlagsIT.patchValidation |
| ERR-PATCH-/admin/flags/{flagId}-401 | pass | ✓ junit: AdminFlagsIT.patchErrors |
| ERR-PATCH-/admin/flags/{flagId}-403 | pass | ✓ junit: AdminFlagsIT.patchErrors |
| ERR-PATCH-/admin/flags/{flagId}-404 | pass | ✓ junit: AdminFlagsIT.patchErrors |
| ERR-PATCH-/admin/flags/{flagId}-409 | pass | ✓ junit: AdminFlagsIT.staleVersionIsConflict |
| ERR-PATCH-/admin/flags/{flagId}-413 | pass | ✓ junit: AdminFlagsIT.patchErrors |
| ERR-POST-/admin/flags/{flagId}/toggle-400 | pass | ✓ junit: AdminFlagsIT.toggleBodyIsRequired |
| ERR-POST-/admin/flags/{flagId}/toggle-401 | pass | ✓ junit: AdminFlagsIT.toggleErrors |
| ERR-POST-/admin/flags/{flagId}/toggle-403 | pass | ✓ junit: AdminFlagsIT.toggleErrors |
| ERR-POST-/admin/flags/{flagId}/toggle-404 | pass | ✓ junit: AdminFlagsIT.toggleErrors |
| ERR-POST-/admin/flags/{flagId}/toggle-413 | pass | ✓ junit: AdminFlagsIT.toggleErrors |
| ERR-DELETE-/admin/flags/{flagId}-400 | pass | ✓ junit: AdminFlagsIT.deleteErrors |
| ERR-DELETE-/admin/flags/{flagId}-401 | pass | ✓ junit: AdminFlagsIT.deleteErrors |
| ERR-DELETE-/admin/flags/{flagId}-403 | pass | ✓ junit: AdminFlagsIT.deleteErrors |
| ERR-DELETE-/admin/flags/{flagId}-404 | pass | ✓ junit: AdminFlagsIT.deleteErrors |
| ERR-GET-/admin/audit-400 | pass | ✓ junit: AuditApiIT.badPagingParameters |
| ERR-GET-/admin/audit-401 | pass | ✓ junit: AuditApiIT.auditNeedsAnAdminToken |
| ERR-GET-/admin/audit-403 | pass | ✓ junit: AuditApiIT.auditNeedsAnAdminToken |
| ERR-GET-/evaluate/flags-401 | pass | ✓ junit: SecurityRulesIT.evaluationWithoutOrWithExpiredTokenIs401 |
| ERR-GET-/evaluate/flags-403 | pass | ✓ junit: SecurityRulesIT.evaluationWithAdminTokenOrWrongScopeOrAudienceIs403 |
| ERR-GET-/evaluate/groups/{groupKey}-401 | pass | ✓ junit: SecurityRulesIT.evaluationWithoutOrWithExpiredTokenIs401 |
| ERR-GET-/evaluate/groups/{groupKey}-403 | pass | ✓ junit: SecurityRulesIT.evaluationWithAdminTokenOrWrongScopeOrAudienceIs403 |
| ERR-GET-/evaluate/groups/{groupKey}-404 | pass | ✓ junit: EvaluationIT.unknownGroupOrFlagIs404 |
| ERR-GET-/evaluate/flags/{groupKey}/{flagKey}-401 | pass | ✓ junit: SecurityRulesIT.evaluationWithoutOrWithExpiredTokenIs401 |
| ERR-GET-/evaluate/flags/{groupKey}/{flagKey}-403 | pass | ✓ junit: SecurityRulesIT.evaluationWithAdminTokenOrWrongScopeOrAudienceIs403 |
| ERR-GET-/evaluate/flags/{groupKey}/{flagKey}-404 | pass | ✓ junit: EvaluationIT.unknownGroupOrFlagIs404 |
| ERR-GET-/admin/groups-500 | pass | ✓ junit: InternalErrorIT.unexpectedErrorIs500Internal |

### Spec 9.1 error rows

| Row | Covered by | Status |
| --- | --- | --- |
| 400 validation | ERR-POST-/admin/groups-400 | pass |
| 400 malformed-request | ERR-POST-/admin/flags/{flagId}/toggle-400 | pass |
| 401 unauthorized | ERR-GET-/admin/groups-401 | pass |
| 403 forbidden | ERR-GET-/admin/groups-403 | pass |
| 403 https-required | ERR-POST-/auth/login-403 | pass |
| 404 not-found | ERR-GET-/admin/groups/{groupId}-404 | pass |
| 409 duplicate-key | ERR-POST-/admin/groups-409 | pass |
| 409 version-conflict | ERR-PATCH-/admin/flags/{flagId}-409 | pass |
| 409 limit-reached | ERR-POST-/admin/groups/{groupId}/flags-409 | pass |
| 413 payload-too-large | ERR-POST-/admin/groups-413 | pass |
| 500 internal | ERR-GET-/admin/groups-500 | pass |

