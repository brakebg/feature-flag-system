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
