# Decisions — spec 002

Format: D-n, level, what, why. Changed tests are listed here too (spec 002 section 4 item 1).

## D-1 · A2: Jackson behind two classes (spec 002 §7 phase A2)

Level 1. Main code: new `common/Json` holds the only tree/mapper calls of the filters
(`ProblemWriter`, `HealthBodyFilter`). `JacksonConfig` stays the mapper configuration.
Tests: new `support/TestJson` (tree, bytes, prettySorted, converter). Behaviour unchanged;
`backend/openapi.json` unchanged.

Tests changed (plumbing only, no assertion changed; forced by A2, the Jackson 3 switch):

| File | Old | New |
| --- | --- | --- |
| auth/LoginIT, auth/TokenEndpointIT, evaluation/EvaluationIT | `json.readTree(x)` with `@Autowired ObjectMapper json` | `TestJson.tree(x)` |
| auth/SecurityRulesIT | `json.writeValueAsBytes(...)`, `adminToken(mvc, json)` | `TestJson.bytes(...)`, `adminToken(mvc)` |
| common/InternalErrorIT, group/AdminGroupsIT, support/AdminApiTest | `new AdminClient(mvc, json, token)`, `(mvc, json)` | `new AdminClient(mvc, token)`, `(mvc)` |
| group/AdminGroupsIT `type()` | `new ObjectMapper().readTree(..)` | `TestJson.tree(..)` |
| ops/OpenApiExportIT | `json.copy().enable(..).writeValueAsString(json.treeToValue(..))` | `TestJson.prettySorted(doc)` |
| support/AdminClient, SecurityTestSupport | `ObjectMapper json` parameter | removed, use `TestJson` |
| support/StandaloneMvc, common/GlobalExceptionHandlerTest | inline `Jackson2ObjectMapperBuilder` converter | `TestJson.converter()` |

`domain/ResponseJsonTest`, `domain/RequestValidationTest`, `common/JacksonConfigTest` keep the
autowired mapper on purpose: they test the Jackson configuration itself.

## D-2 · A1: deprecated API removed (spec 002 §7 A1)

Level 1. Compile with `-Xlint:all` on Boot 3.5 shows three kinds of deprecation:
`ContentResultMatchers.json(String, boolean)` (removed in Spring 7), and
`ObjectMapper.configure(MapperFeature, boolean)` in `JacksonConfig` (goes away with the Jackson 3
builder in phase B). Main code already uses the lambda DSL and `@MockitoSpyBean`.

Test changed (forced by the Spring 7 API removal; the compare strictness stays the same):

| File | Old | New |
| --- | --- | --- |
| ops/HealthIT (3 lines) | `content().json("...", true)` | `content().json("...", JsonCompareMode.STRICT)` |

## D-3 · A3: nothing else to prepare on 3.5

Level 1. Checked section 9 against the code. Security config is lambda DSL only; tests use
`@MockitoSpyBean`; Flyway, Testcontainers 2, Hibernate 7 and Tomcat 11 items need the new
platform and move to phase B. Targets found on Maven Central: Spring Boot 4.1.1 (latest 4.1.x),
springdoc-openapi 3.1.1.

## D-4 · Version overrides kept (spec 002 §3)

Level 1. Boot 4.1.1 manages Jackson 3.1.5 / 2.21.5 and Tomcat 11.0.24. Trivy (gate 10) reports
HIGH/CRITICAL CVEs with fixed versions in them (Jackson CVE-2026-89407, -89425, -68497, -91776,
-91777; Tomcat CVE-2026-65182, -65905, -68525). Overrides in `backend/pom.xml`, same minor line:
`jackson-bom.version` 3.1.7, `jackson-2-bom.version` 2.21.7 (the 2.x line still comes in through
springdoc/swagger and the annotations), `tomcat.version` 11.0.26. The PostgreSQL driver override
of spec 001 D-007 is removed: Boot 4.1.1 manages 42.7.13, the same version. Remove each override
when Boot manages a version without a HIGH or CRITICAL finding.

## D-5 · OpenAPI `minimum` removed again (spec 002 §4 item 3)

Level 1. springdoc 3.1.1 turns `@PositiveOrZero` on `version` into `"minimum": 0` in
`UpdateGroupRequest` and `UpdateFlagRequest`. The 1.0.0 contract has no `minimum`. The existing
`OpenApiConfig` customizer now clears it, so `backend/openapi.json` is byte-identical to 1.0.0
(no hunk to list). Bean Validation still rejects negative versions.

## D-6 · Form parameter read with Tomcat 11 (spec 001 5.5, spec 002 §4 item 2)

Level 1. Tomcat 11 (Servlet 6.1) throws `IllegalStateException` (`InvalidParameterException`)
from the first `getParameter` when a form pair has a bad percent escape. Spec 001 5.5 says the
pair is skipped and the request goes on (200). `TokenController.parameter` repeats the read once.
Test `ChunkedBodyIT.badFormEncodingIsHandledLikeTheContainerDoes` is unchanged and green.

## D-7 · Tests changed in phase B (library API forced; expected values unchanged)

| File | Old | New | Forced by |
| --- | --- | --- | --- |
| support/TestJson | Jackson 2 `ObjectMapper`, `Jackson2ObjectMapperBuilder` | Jackson 3 `JsonMapper`, `JacksonJsonHttpMessageConverter` + `ProblemDetailJacksonMixin` | Jackson 3, Spring 7 |
| all tests with `JsonNode` / `ObjectMapper` imports | `com.fasterxml.jackson.databind.*` | `tools.jackson.databind.*` | Jackson 3 packages |
| common/JacksonConfigTest (9 assertions) | `isInstanceOf(JsonProcessingException.class)` | `isInstanceOf(JacksonException.class)` (`tools.jackson.core`) | Jackson 3 exception base class |
| domain/ResponseJsonTest `names()` | `n.fieldNames().forEachRemaining(..)` | `out.addAll(n.propertyNames())` | Jackson 3 JsonNode API |
| support/IntegrationTest, ops/SwaggerProdIT | `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` | Boot 4 modules |
| support/IntegrationTest, common/FirewallIT, common/ChunkedBodyIT | `@AutoConfigureObservability` | `@AutoConfigureMetrics` | Boot 4 modules |
| support/PostgresContainerConfig, MigrationIT, FlywayProfileIT | `org.testcontainers.containers.PostgreSQLContainer<?>` | `org.testcontainers.postgresql.PostgreSQLContainer` | Testcontainers 2 |

Main code: `SecurityConfig.scopeAndAudience` implements `authorize` (Security 7 removed `check`),
`ReadinessConfig` imports from `org.springframework.boot.health.*`, `JacksonConfig` uses the
Jackson 3 builder (`USE_NULL_FOR_MISSING_REFERENCE_VALUES` replaces `configureReadAbsentAsNull`).
