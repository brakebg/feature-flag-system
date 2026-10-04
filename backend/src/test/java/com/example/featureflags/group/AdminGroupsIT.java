package com.example.featureflags.group;

import static com.example.featureflags.support.AdminClient.PROBLEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.AdminApiTest;
import com.example.featureflags.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Spec 6.1, 6.2: group endpoints of the Admin API. */
@IntegrationTest
class AdminGroupsIT extends AdminApiTest {

  private static void problem(ResultActions r, int status, String type) throws Exception {
    r.andExpect(status().is(status))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(PROBLEM + type))
        .andExpect(jsonPath("$.status").value(status));
  }

  private static void validation(ResultActions r, String field) throws Exception {
    problem(r, 400, "validation");
    r.andExpect(jsonPath("$.errors[0].field").value(field));
  }

  private static List<String> keys(JsonNode array) {
    List<String> out = new ArrayList<>();
    array.forEach(n -> out.add(n.get("key").asText()));
    return out;
  }

  // ---------------------------------------------------------------- POST /groups

  @Test
  void createReturns201WithLocationAndGroup() throws Exception {
    ResultActions r =
        admin
            .postJson("/groups", "{\"key\":\"orders\",\"name\":\"  Orders \",\"description\":\"\"}")
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.key").value("orders"))
            .andExpect(jsonPath("$.name").value("Orders"))
            .andExpect(jsonPath("$.description").doesNotExist())
            .andExpect(jsonPath("$.createdBy").value("admin"))
            .andExpect(jsonPath("$.updatedBy").value("admin"))
            .andExpect(jsonPath("$.version").value(0))
            .andExpect(jsonPath("$.flagCount").doesNotExist())
            .andExpect(jsonPath("$.createdAt").value(Matchers.endsWith("Z")))
            .andExpect(jsonPath("$.updatedAt").value(Matchers.endsWith("Z")));
    JsonNode g = admin.body(r);
    assertThat(r.andReturn().getResponse().getHeader("Location"))
        .isEqualTo("/api/v1/admin/groups/" + g.get("id").asText());
    assertThat(g.get("id").asText())
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Orders", "1abc", "a", "has space"})
  @Tag("AC-GRP-3")
  @Tag("ERR-POST-/admin/groups-400")
  void keysFailingTheRegexAreRejected(String key) throws Exception {
    validation(admin.postJson("/groups", "{\"key\":\"" + key + "\",\"name\":\"N\"}"), "key");
    assertThat(auditCount()).isZero();
  }

  @Test
  @Tag("ERR-POST-/admin/groups-400")
  void missingOrBlankNameAndLongDescriptionAreValidation() throws Exception {
    validation(admin.postJson("/groups", "{\"key\":\"orders\"}"), "name");
    validation(admin.postJson("/groups", "{\"key\":\"orders\",\"name\":\"   \"}"), "name");
    validation(admin.postJson("/groups", "{\"name\":\"N\"}"), "key");
    validation(
        admin.postJson(
            "/groups",
            "{\"key\":\"orders\",\"name\":\"N\",\"description\":\"" + "d".repeat(501) + "\"}"),
        "description");
  }

  @Test
  @Tag("ERR-POST-/admin/groups-400")
  void invalidJsonWrongTypesOrContentTypeAreMalformed() throws Exception {
    problem(admin.postJson("/groups", "{nope"), 400, "malformed-request");
    problem(admin.postJson("/groups", "{\"key\":\"orders\",\"name\":5}"), 400, "malformed-request");
    problem(
        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/admin/groups")
                .header(
                    "Authorization",
                    "Bearer "
                        + com.example.featureflags.support.SecurityTestSupport.adminToken(
                            mvc, json))
                .contentType(MediaType.TEXT_PLAIN)
                .content("{\"key\":\"orders\",\"name\":\"N\"}")),
        400,
        "malformed-request");
  }

  @Test
  @Tag("AC-GRP-2")
  @Tag("ERR-POST-/admin/groups-409")
  void duplicateKeyIs409() throws Exception {
    admin.createGroup("orders", "Orders");
    problem(
        admin.postJson("/groups", "{\"key\":\"orders\",\"name\":\"Again\"}"), 409, "duplicate-key");
    assertThat(auditCount()).isEqualTo(1);
  }

  @Test
  @Tag("ERR-POST-/admin/groups-409")
  void thousandAndFirstGroupIsLimitReachedAndDeletingFreesASlot() throws Exception {
    jdbc.update(
        "INSERT INTO flag_group (id, key, name, created_by, updated_at, updated_by, version)"
            + " SELECT ('0191f0c2-0000-7000-8000-' || lpad(n::text, 12, '0'))::uuid, 'g-' || n,"
            + " 'G', 'system', now(), 'system', 0 FROM generate_series(1, 1000) n");
    problem(
        admin.postJson("/groups", "{\"key\":\"one-more\",\"name\":\"N\"}"), 409, "limit-reached");
    admin.delete("/groups/0191f0c2-0000-7000-8000-000000000001").andExpect(status().isNoContent());
    admin
        .postJson("/groups", "{\"key\":\"one-more\",\"name\":\"N\"}")
        .andExpect(status().isCreated());
  }

  @Test
  @Tag("ERR-POST-/admin/groups-413")
  void bodyAbove64KiBIs413() throws Exception {
    problem(
        admin.postJson(
            "/groups",
            "{\"key\":\"orders\",\"name\":\"N\",\"pad\":\"" + "x".repeat(65_600) + "\"}"),
        413,
        "payload-too-large");
  }

  @Test
  @Tag("ERR-POST-/admin/groups-401")
  @Tag("ERR-POST-/admin/groups-403")
  void createNeedsAnAdminToken() throws Exception {
    problem(
        anonymous.postJson("/groups", "{\"key\":\"orders\",\"name\":\"N\"}"), 401, "unauthorized");
    problem(client.postJson("/groups", "{\"key\":\"orders\",\"name\":\"N\"}"), 403, "forbidden");
  }

  // ---------------------------------------------------------------- GET /groups

  @Test
  @Tag("AC-GRP-1")
  void listShowsCountsAndOmitsMissingDescription() throws Exception {
    JsonNode g = admin.createGroup("orders", "Orders");
    admin.createFlag(g.get("id").asText(), "new-checkout", true);
    admin.createFlag(g.get("id").asText(), "split-payments", false);
    admin.createGroup("payments", "Payments");

    admin
        .get("/groups")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value("orders"))
        .andExpect(jsonPath("$[0].flagCount").value(2))
        .andExpect(jsonPath("$[0].enabledCount").value(1))
        .andExpect(jsonPath("$[0].description").doesNotExist())
        .andExpect(jsonPath("$[0].createdBy").value("admin"))
        .andExpect(jsonPath("$[0].createdAt").doesNotExist())
        .andExpect(jsonPath("$[1].key").value("payments"))
        .andExpect(jsonPath("$[1].flagCount").value(0))
        .andExpect(jsonPath("$[1].enabledCount").value(0));
  }

  @Test
  void searchMatchesKeyOrNameCaseInsensitively() throws Exception {
    admin.createGroup("orders", "Shop Orders");
    admin.createGroup("payments", "Money");
    admin.createGroup("billing", "Invoices");

    assertThat(keys(admin.body(admin.get("/groups?q=ORD")))).containsExactly("orders");
    assertThat(keys(admin.body(admin.get("/groups?q=money")))).containsExactly("payments");
    assertThat(keys(admin.body(admin.get("/groups?q="))))
        .containsExactly("billing", "orders", "payments");
    assertThat(keys(admin.body(admin.get("/groups?q=zzz")))).isEmpty();
  }

  @Test
  void sortByKeyNameAndUpdatedAt() throws Exception {
    admin.createGroup("b-key", "alpha");
    admin.createGroup("a-key", "Charlie");
    admin.createGroup("c-key", "bravo");
    jdbc.update("UPDATE flag_group SET updated_at = '2026-10-01T10:00:00Z' WHERE key = 'b-key'");
    jdbc.update("UPDATE flag_group SET updated_at = '2026-10-01T11:00:00Z' WHERE key = 'a-key'");
    jdbc.update("UPDATE flag_group SET updated_at = '2026-10-01T12:00:00Z' WHERE key = 'c-key'");

    assertThat(keys(admin.body(admin.get("/groups")))).containsExactly("a-key", "b-key", "c-key");
    assertThat(keys(admin.body(admin.get("/groups?sort=key"))))
        .containsExactly("a-key", "b-key", "c-key");
    assertThat(keys(admin.body(admin.get("/groups?sort=name"))))
        .containsExactly("b-key", "c-key", "a-key");
    assertThat(keys(admin.body(admin.get("/groups?sort=updatedAt"))))
        .containsExactly("c-key", "a-key", "b-key");
  }

  @Test
  void keySortIsCodePointOrder() throws Exception {
    admin.createGroup("a-b", "1");
    admin.createGroup("a0", "2");
    admin.createGroup("aa", "3");
    // '-' (0x2d) < '0' (0x30) < 'a' (0x61)
    assertThat(keys(admin.body(admin.get("/groups?sort=key")))).containsExactly("a-b", "a0", "aa");
  }

  @Test
  @Tag("ERR-GET-/admin/groups-400")
  void unknownSortIsValidationError() throws Exception {
    validation(admin.get("/groups?sort=created"), "sort");
    validation(admin.get("/groups?sort="), "sort");
  }

  @Test
  @Tag("ERR-GET-/admin/groups-401")
  @Tag("ERR-GET-/admin/groups-403")
  void listNeedsAnAdminToken() throws Exception {
    problem(anonymous.get("/groups"), 401, "unauthorized");
    problem(client.get("/groups"), 403, "forbidden");
  }

  // ---------------------------------------------------------------- GET /groups/{id}

  @Test
  void detailHasFlagsSortedByKey() throws Exception {
    JsonNode g = admin.createGroup("orders", "Orders");
    String id = g.get("id").asText();
    admin.createFlag(id, "split-payments", false);
    admin.createFlag(id, "a-flag", true);
    admin.createFlag(id, "new-checkout", true);

    JsonNode d = admin.body(admin.get("/groups/" + id).andExpect(status().isOk()));
    assertThat(keys(d.get("flags"))).containsExactly("a-flag", "new-checkout", "split-payments");
    assertThat(d.get("flags").get(0).get("fullKey").asText()).isEqualTo("orders.a-flag");
    assertThat(d.get("key").asText()).isEqualTo("orders");
    assertThat(d.has("createdAt")).isTrue();
    assertThat(d.has("flagCount")).isFalse();
  }

  @Test
  @Tag("ERR-GET-/admin/groups/{groupId}-404")
  void unknownIdIs404() throws Exception {
    problem(admin.get("/groups/0191f0c2-0000-7000-8000-000000000999"), 404, "not-found");
  }

  @Test
  @Tag("ERR-GET-/admin/groups/{groupId}-400")
  void malformedIdIs400() throws Exception {
    problem(admin.get("/groups/not-a-uuid"), 400, "malformed-request");
  }

  @Test
  @Tag("ERR-GET-/admin/groups/{groupId}-401")
  @Tag("ERR-GET-/admin/groups/{groupId}-403")
  void detailNeedsAnAdminToken() throws Exception {
    String id = admin.createGroup("orders", "Orders").get("id").asText();
    problem(anonymous.get("/groups/" + id), 401, "unauthorized");
    problem(client.get("/groups/" + id), 403, "forbidden");
  }

  // ---------------------------------------------------------------- PATCH /groups/{id}

  @Test
  @Tag("AC-AUD-1")
  @Tag("AC-GRP-4")
  void patchUpdatesNameAndDescriptionAndWritesOneAuditEvent() throws Exception {
    String id = admin.createGroup("orders", "Orders").get("id").asText();

    admin
        .patchJson(
            "/groups/" + id,
            "{\"name\":\"Shop\",\"description\":\"New text\",\"version\":0,\"key\":\"x\",\"createdBy\":\"mallory\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.key").value("orders"))
        .andExpect(jsonPath("$.name").value("Shop"))
        .andExpect(jsonPath("$.description").value("New text"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.createdBy").value("admin"));

    assertThat(auditCount()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT details::text FROM audit_event WHERE action = 'GROUP_UPDATED'",
                String.class))
        .isEqualTo(
            "{\"name\": {\"to\": \"Shop\", \"from\": \"Orders\"}, \"description\": {\"to\": \"New text\", \"from\": null}}");
  }

  @Test
  void omittedFieldsStayAndNullOrEmptyDescriptionClears() throws Exception {
    String id =
        admin
            .body(
                admin.postJson(
                    "/groups", "{\"key\":\"orders\",\"name\":\"Orders\",\"description\":\"d\"}"))
            .get("id")
            .asText();
    admin
        .patchJson("/groups/" + id, "{\"description\":null,\"version\":0}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Orders"))
        .andExpect(jsonPath("$.description").doesNotExist())
        .andExpect(jsonPath("$.version").value(1));
    admin
        .patchJson("/groups/" + id, "{\"description\":\"e\",\"version\":1}")
        .andExpect(status().isOk());
    admin
        .patchJson("/groups/" + id, "{\"description\":\"\",\"version\":2}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").doesNotExist())
        .andExpect(jsonPath("$.version").value(3));
  }

  @Test
  void flagChangesDoNotChangeTheGroupVersion() throws Exception {
    String id = admin.createGroup("orders", "Orders").get("id").asText();
    String flagId = admin.createFlag(id, "a-flag", false).get("id").asText();
    admin.postJson("/flags/" + flagId + "/toggle", "{\"enabled\":true}");
    admin.patchJson("/flags/" + flagId, "{\"description\":\"x\",\"version\":1}");
    admin.delete("/flags/" + flagId);
    admin.get("/groups/" + id).andExpect(jsonPath("$.version").value(0));
    admin
        .patchJson("/groups/" + id, "{\"name\":\"Shop\",\"version\":0}")
        .andExpect(status().isOk());
  }

  @Test
  @Tag("ERR-PATCH-/admin/groups/{groupId}-400")
  void patchValidation() throws Exception {
    String id = admin.createGroup("orders", "Orders").get("id").asText();
    validation(admin.patchJson("/groups/" + id, "{\"name\":null,\"version\":0}"), "name");
    validation(admin.patchJson("/groups/" + id, "{\"name\":\"  \",\"version\":0}"), "name");
    validation(admin.patchJson("/groups/" + id, "{\"name\":\"X\"}"), "version");
    validation(admin.patchJson("/groups/" + id, "{\"name\":\"X\",\"version\":-1}"), "version");
    problem(
        admin.patchJson("/groups/" + id, "{\"name\":\"X\",\"version\":\"0\"}"),
        400,
        "malformed-request");
    problem(
        admin.patchJson("/groups/" + id, "{\"name\":\"X\",\"version\":0.5}"),
        400,
        "malformed-request");
    problem(admin.patchJson("/groups/not-a-uuid", "{\"version\":0}"), 400, "malformed-request");
  }

  @Test
  @Tag("ERR-PATCH-/admin/groups/{groupId}-409")
  void staleOrFutureVersionIsConflictEvenForANoOp() throws Exception {
    String id = admin.createGroup("orders", "Orders").get("id").asText();
    admin
        .patchJson("/groups/" + id, "{\"name\":\"Shop\",\"version\":0}")
        .andExpect(status().isOk());

    problem(
        admin.patchJson("/groups/" + id, "{\"name\":\"Other\",\"version\":0}"),
        409,
        "version-conflict");
    problem(
        admin.patchJson("/groups/" + id, "{\"name\":\"Other\",\"version\":5}"),
        409,
        "version-conflict");
    problem(
        admin.patchJson("/groups/" + id, "{\"name\":\"Shop\",\"version\":0}"),
        409,
        "version-conflict");
  }

  @Test
  void noOpPatchChangesNothing() throws Exception {
    JsonNode g = admin.createGroup("orders", "Orders");
    String id = g.get("id").asText();
    int before = auditCount();

    admin
        .patchJson("/groups/" + id, "{\"name\":\"Orders\",\"version\":0}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.updatedAt").value(g.get("updatedAt").asText()));
    admin.patchJson("/groups/" + id, "{\"version\":0}").andExpect(jsonPath("$.version").value(0));
    assertThat(auditCount()).isEqualTo(before);
  }

  @Test
  @Tag("ERR-PATCH-/admin/groups/{groupId}-404")
  @Tag("ERR-PATCH-/admin/groups/{groupId}-413")
  @Tag("ERR-PATCH-/admin/groups/{groupId}-401")
  @Tag("ERR-PATCH-/admin/groups/{groupId}-403")
  void patchErrors() throws Exception {
    String id = admin.createGroup("orders", "Orders").get("id").asText();
    problem(
        admin.patchJson("/groups/0191f0c2-0000-7000-8000-000000000999", "{\"version\":0}"),
        404,
        "not-found");
    problem(
        admin.patchJson("/groups/" + id, "{\"version\":0,\"pad\":\"" + "x".repeat(65_600) + "\"}"),
        413,
        "payload-too-large");
    problem(anonymous.patchJson("/groups/" + id, "{\"version\":0}"), 401, "unauthorized");
    problem(client.patchJson("/groups/" + id, "{\"version\":0}"), 403, "forbidden");
  }

  // ---------------------------------------------------------------- DELETE /groups/{id}

  @Test
  @Tag("AC-GRP-5")
  @Tag("AC-AUD-1")
  void deleteRemovesGroupAndFlagsAndAuditsTheFlagKeys() throws Exception {
    String id = admin.createGroup("orders", "Orders").get("id").asText();
    admin.createFlag(id, "split-payments", false);
    admin.createFlag(id, "new-checkout", true);
    int before = auditCount();

    admin.delete("/groups/" + id).andExpect(status().isNoContent());

    assertThat(auditCount()).isEqualTo(before + 1);
    assertThat(
            jdbc.queryForObject(
                "SELECT action FROM audit_event ORDER BY id DESC LIMIT 1", String.class))
        .isEqualTo("GROUP_DELETED");

    problem(admin.get("/groups/" + id), 404, "not-found");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM feature_flag", Integer.class)).isZero();
    assertThat(
            jdbc.queryForList(
                "SELECT details::text FROM audit_event WHERE action = 'GROUP_DELETED' AND target_key = 'orders'",
                String.class))
        .containsExactly(
            "{\"deletedFlags\": [\"orders.new-checkout\", \"orders.split-payments\"]}");
  }

  @Test
  void deletingAnEmptyGroupListsNoFlags() throws Exception {
    String id = admin.createGroup("orders", "Orders").get("id").asText();
    admin.delete("/groups/" + id).andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "SELECT details::text FROM audit_event WHERE action = 'GROUP_DELETED'",
                String.class))
        .isEqualTo("{\"deletedFlags\": []}");
  }

  @Test
  @Tag("ERR-DELETE-/admin/groups/{groupId}-400")
  @Tag("ERR-DELETE-/admin/groups/{groupId}-404")
  @Tag("ERR-DELETE-/admin/groups/{groupId}-401")
  @Tag("ERR-DELETE-/admin/groups/{groupId}-403")
  void deleteErrors() throws Exception {
    String id = admin.createGroup("orders", "Orders").get("id").asText();
    problem(admin.delete("/groups/not-a-uuid"), 400, "malformed-request");
    problem(admin.delete("/groups/0191f0c2-0000-7000-8000-000000000999"), 404, "not-found");
    problem(anonymous.delete("/groups/" + id), 401, "unauthorized");
    problem(client.delete("/groups/" + id), 403, "forbidden");
  }
}
