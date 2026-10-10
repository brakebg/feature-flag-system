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
