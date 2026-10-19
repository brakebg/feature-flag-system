# Testing

From spec 001 (`docs/SPEC.md`) section 11, 11.1, 11.5, 11.6. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

## 11. Testing and acceptance criteria

The build is accepted when every checkbox below passes in CI; backend line coverage ≥ 80 % on `service` and `controller` packages, frontend ≥ 70 % on `src/features` and `src/api`.

### 11.1 Test layers

| Layer | Tooling | Must cover |
| --- | --- | --- |
| Backend unit | JUnit 5, AssertJ, Mockito | Services: validation, uniqueness, cascade delete, audit details, cache hit, miss and load, write-through for every change type, rollback leaves cache unchanged, JWT create/parse/expiry |
| Backend integration | `@SpringBootTest` + MockMvc + Testcontainers PostgreSQL | Every endpoint in sections 6 and 7: happy path, 400, 401, 404, 409; ETag / 304; Flyway migrations apply on empty DB |
| Frontend unit | Vitest, RTL, MSW | Login form errors, group/flag modals with validation, optimistic toggle + revert, delete-group typed confirmation, 401 → redirect |
| End-to-end | Playwright against docker-compose | Scenarios in 11.2 |

### 11.5 Test environments and data isolation

Every test run uses throwaway containers, and the database and the cache are always reset together, never the database alone (a direct database change would leave the cache stale, by design).

- **Backend integration tests:** Testcontainers starts a fresh PostgreSQL container per run (container reuse off in CI). Before each test the harness truncates the tables and calls `FlagCacheService.reloadAll()`, the same public method used by warm-up and reconciliation, so no test-only code exists in the service.
- **End-to-end, smoke and performance tests:** each run starts a new docker-compose stack under a unique project name (`ff-e2e-<run-id>`) with no persistent volumes, and removes it with `docker compose down -v` in a shell `trap`, so it is removed even when tests fail.
- **Inside one end-to-end run:** each test creates its own data through the Admin API with unique keys (`e2e-<test-id>-...`), asserts only on that data, and deletes it through the Admin API in `afterEach`. Tests are independent and run with 4 parallel workers.
- **Tests that need global state:** tests that depend on the global evaluation `revision` or ETag (304 behaviour, revision increases) run in a project with 1 worker that starts after the parallel project has finished, so no other test writes at the same time. The group-limit test (9.2) counts all groups in the service, so it runs in its own stack with 1 worker. The flag-limit test uses its own group and runs in the shared stack. The cache hit-rate test (AC-CACHE-9) counts cache metrics for the whole service, so it runs in the same 1-worker project, after the parallel project. It creates its own group and flags, then runs the 9.2 load against those keys only. The hit rate is computed from the difference of the counters read just before and just after the load (9.2), so earlier traffic does not count.
- **Negative authentication cases:** tests may sign JWTs (HS256) with the documented dev `FF_JWT_SECRET` from 5.1 (wrong `aud`, `scope` or `iss`, expired). Tests never need the secret of a non-dev stack. UI tests may read `sessionStorage['ff.accessToken']` to check that a token is stored or removed, and may write it only with a negative token (expired, or with a changed signature) to reach the session-expired state.
- **Screenshot tests** run in their own fresh stack, seeded through the Admin API with the sample data shown in the designs; relative-time and username cells are masked so screenshots do not depend on the clock.

### 11.6 UI testing

**Browsers (Playwright projects)**

| Project | Viewport | Runs |
| --- | --- | --- |
| `chromium-desktop` | 1440 × 900 | Full suite |
| `chromium-narrow` | 800 × 900 | Full suite |
| `firefox-desktop` | 1440 × 900 | Tests tagged `@cross-browser` only |
| `webkit-desktop` | 1440 × 900 | Tests tagged `@cross-browser` only |

The `@cross-browser` subset has 6–8 tests: login, session-expired redirect, toggle a flag, create a flag, delete-group confirmation, audit log. Browsers never run in `make verify-fast`.

UI tests may use Playwright request interception (`page.route`) to answer browser requests to `/api/**` with a fake status, an abort or a delay, to reach states the stack cannot produce (toggle failure, 429 with `Retry-After`, network error, slow response, empty group list (`GET /api/v1/admin/groups` answered with `200 []`)).

In the black-box suite, API tests run once in an `api` project; UI tests run in the browser projects above; the serial project of 11.5 starts after all other projects have finished.

**Matching the design**

1. Style checks: Playwright reads computed styles and asserts the key design values, for example (spec 005 values) the primary button and switch-on background `#3D59A8`, the `Sign in` button `#1C2433`, the white pill top bar, body font IBM Plex Sans, keys in IBM Plex Mono, and 52 px login inputs with a 16 px radius. Design values live once in `frontend/src/styles/tokens.css`; tests compare against the values taken from `docs/product/design/`, not against `tokens.css`. These checks are AC-STYLE-1 (11.2, Design).
2. Screenshot regression: from M7, `toHaveScreenshot` baselines for all five screens on `chromium-desktop` are committed (`maxDiffPixelRatio` 0.01). Changing a baseline requires a `<spec>/builder/DECISIONS.md` entry naming the screen and the reason; gate 15 checks that every changed baseline file is listed there.
3. Review gallery: the final run writes `docs/design-compare/index.html`, showing each screen's screenshot next to its design PNG at the same size, for the owner's final review. There is no automated pixel comparison against the design PNGs, because font rendering and sample data differences would cause constant false failures.
