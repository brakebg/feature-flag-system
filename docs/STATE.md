# State

Keep under 150 lines. Older detail: `docs/PROGRESS.md`.

## Current

- Milestone: M7 UI features — in progress
- `scripts/current-milestone`: 7

## Last green commit

- 9b4a89d (M6 test-audit fixes; full `make verify` PASS). `M6: complete` follows it.

## Last full `make verify`

- 2026-10-05 on 9b4a89d: PASS. Active and green: 1-11, 14, 15. Inactive: 12, 13 (M8).

## Chunks done in M6

- 7d76ad0 UI foundation (API client, token, routes, login, shell, components); tests AC-AUTH-1..4, 6.
- 4b0aee0 React review fixes; ed91f3c footer version from VERSION; 9b4a89d test audit fixes.

## Next 3 steps

1. M7: set `scripts/current-milestone` to 7. Build spec 8.4-8.6: `src/api/{groups,flags,audit,types}.ts`,
   `src/hooks/queries.ts` (keys ['groups'], ['group', id], ['audit', filters]; optimistic toggle),
   `src/schemas/forms.ts` (Zod = 4.2 rules, 8.5 texts, slugify), groups pane, group header, flags
   table, New/Edit group and flag dialogs, delete confirmations, audit page with Load more.
   TextField needs forwardRef for React Hook Form. Drafts exist in the old session scratchpad (m7/).
2. M7 tests tagged AC-GRP-1..5, AC-FLAG-1..6, AC-AUD-2 (UI parts); check TA-5/TA-10 from the M6
   test audit (Back after sign out with real data; query cache cleared on sign out / 401).
3. M7 end: Playwright style checks + screenshot baselines for the 5 screens (spec 11.6), design
   compare; `make verify`; audit (spec, test, design-checker, ecc:react-reviewer).

## Notes for M4/M5 (from the M2 audit)

- Packages: common <- audit <- group <- flag? No: direction is group -> flag. `flag` defines a
  `GroupLookup` port implemented in `group`; GroupDetail uses the `Flag` DTO.
- Limits (9.2): lock the group row when counting flags; group count under a lock too (DB-5).
- Audit `targetKey` prefix: escaped LIKE so the varchar_pattern_ops index is used (DB-2).
- Audit `details`: build with LinkedHashMap (JSON nulls), never Map.of.
- ArchUnit: remove `allowEmptyShould(true)` once controllers / evaluation classes exist (TA-10).
- AC-OPS-4: backend tests cover https-required; the UI header half is covered by smoke (gate 11, M5).
- AC-GRP-3 needs MockMvc tests (POST /groups and /flags with Orders, 1abc, a, has space) + UI tests.
- M5: `DatabaseCleaner` must also call `FlagCacheService.reloadAll()` (11.5, TA-14).
- M5: gauge `ff_readiness_up` (D-009); histogram for `/api/v1/evaluate/**`; smoke checks readiness
  DOWN via a 503 or the log lines `readiness DOWN until cache warm-up` / `cache warm-up finished`.

## Open escalations and blockers

- ESC-001 resolved: A (owner comment 5979791924). Due milestones may only move earlier.
- ESC-006 (open): PIT runs kept out of the tag log (D-024); smoke readiness step (D-025).
- ESC-005 (open): PIT unit tests only (D-020); duplicate-key before limit-reached (D-021).
- ESC-004 (open): token endpoint error order (D-018); FF_AUTH_CLIENTS_n replace the yml list.
- ESC-003 (open): NimbusJwtEncoder writes one-element aud as string (5.1 vs 5.2); option A (HmacJwtEncoder) in place.
- ESC-002 (open): D-011 PATCH `enabled: null` -> 400 validation; D-012 unknown method -> 404. Not blocking.

## wip/ branches

- None.

## Last processed PR comment

- 5979791924 (2026-10-04T12:12:17Z, owner: ESC-001: A).

## Notes for the next session

- Owner and builder share the GitHub login `brakebg`. Owner answers look like `ESC-NNN: X`.
- Commit only after `make verify-fast > build/last.log 2>&1 && git commit ...` (a0e498c went in red).
- Java 21 via `scripts/lib/java-env.mjs` (default java here is 25; Enforcer requires 21).
  Manual: `JAVA_HOME=$(node scripts/lib/java-env.mjs) ./mvnw ...` in `backend/`.
- Gate tools in `build/tools/` (`make tools`). Node 26 locally, tests need >= 22 (D-006). npm 11 (D-010).
- Design markup: `docs/design/N · *.html`, inside `<script type="__bundler/template">` (JSON string).
