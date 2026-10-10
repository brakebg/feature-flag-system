package com.example.featureflags.flag;

import static com.example.featureflags.support.AdminClient.PROBLEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.AdminApiTest;
import com.example.featureflags.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** Spec 6.1, 6.2: flag endpoints of the Admin API. */
@IntegrationTest
class AdminFlagsIT extends AdminApiTest {

  private static final String UNKNOWN = "0191f0c3-0000-7000-8000-000000000999";
  private String groupId;

  @BeforeEach
  void group() throws Exception {
    groupId = admin.createGroup("orders", "Orders").get("id").asText();
  }

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

  private String flags() {
    return "/groups/" + groupId + "/flags";
  }

  private String audit(String action) {
    return jdbc.queryForObject(
        "SELECT details::text FROM audit_event WHERE action = ? ORDER BY id DESC LIMIT 1",
        String.class,
        action);
  }

  // ---------------------------------------------------------------- POST /groups/{id}/flags

  @Test
  @Tag("AC-AUD-1")
  @Tag("AC-FLAG-1")
  void createReturns201WithLocationFullKeyAndDefaultOff() throws Exception {
    ResultActions r =
        admin
            .postJson(
                flags(),
                "{\"key\":\"new-checkout\",\"description\":\"New one-page checkout\",\"createdBy\":\"x\",\"updatedBy\":\"y\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.groupId").value(groupId))
            .andExpect(jsonPath("$.key").value("new-checkout"))
            .andExpect(jsonPath("$.fullKey").value("orders.new-checkout"))
            .andExpect(jsonPath("$.description").value("New one-page checkout"))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.version").value(0))
            .andExpect(jsonPath("$.createdBy").value("admin"))
            .andExpect(jsonPath("$.updatedBy").value("admin"));
    JsonNode f = admin.body(r);
    assertThat(r.andReturn().getResponse().getHeader("Location"))
        .isEqualTo("/api/v1/admin/flags/" + f.get("id").asText());
    assertThat(audit("FLAG_CREATED")).isEqualTo("{\"enabled\": false}");
    assertThat(
            jdbc.queryForObject(
                "SELECT actor || ' ' || target_key FROM audit_event WHERE action = 'FLAG_CREATED'",
                String.class))
        .isEqualTo("admin orders.new-checkout");
  }

  @Test
  @Tag("AC-FLAG-2")
  void sameFlagKeyInTwoGroups() throws Exception {
    String other = admin.createGroup("payments", "Payments").get("id").asText();
    admin.postJson(flags(), "{\"key\":\"new-checkout\"}").andExpect(status().isCreated());
    admin
        .postJson("/groups/" + other + "/flags", "{\"key\":\"new-checkout\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fullKey").value("payments.new-checkout"));
  }

  @Test
  @Tag("ERR-POST-/admin/groups/{groupId}/flags-409")
  void duplicateKeyInTheSameGroupIs409() throws Exception {
    admin.postJson(flags(), "{\"key\":\"new-checkout\"}").andExpect(status().isCreated());
    problem(admin.postJson(flags(), "{\"key\":\"new-checkout\"}"), 409, "duplicate-key");
  }

  @Test
  @Tag("ERR-POST-/admin/groups/{groupId}/flags-409")
  void fiveHundredAndFirstFlagIsLimitReachedAndDeletingFreesASlot() throws Exception {
    jdbc.update(
        "INSERT INTO feature_flag (id, group_id, key, created_at, created_by, updated_at, updated_by, version)"
            + " SELECT ('0191f0c3-0000-7000-8000-' || lpad(n::text, 12, '0'))::uuid, ?::uuid, 'f-' || n,"
            + " now(), 'system', now(), 'system', 0 FROM generate_series(1, 500) n",
        groupId);
    problem(admin.postJson(flags(), "{\"key\":\"one-more\"}"), 409, "limit-reached");
    admin.delete("/flags/0191f0c3-0000-7000-8000-000000000001").andExpect(status().isNoContent());
    admin.postJson(flags(), "{\"key\":\"one-more\"}").andExpect(status().isCreated());
  }

  @Test
  @Tag("AC-GRP-3")
  @Tag("ERR-POST-/admin/groups/{groupId}/flags-400")
  void createValidation() throws Exception {
    for (String key : new String[] {"Orders", "1abc", "a", "has space"}) {
      validation(admin.postJson(flags(), "{\"key\":\"" + key + "\"}"), "key");
    }
    validation(admin.postJson(flags(), "{\"description\":\"x\"}"), "key");
    problem(
        admin.postJson(flags(), "{\"key\":\"ok-key\",\"enabled\":\"true\"}"),
        400,
        "malformed-request");
    problem(
        admin.postJson("/groups/not-a-uuid/flags", "{\"key\":\"ok-key\"}"),
        400,
        "malformed-request");
  }

  @Test
  @Tag("ERR-POST-/admin/groups/{groupId}/flags-404")
  @Tag("ERR-POST-/admin/groups/{groupId}/flags-413")
  @Tag("ERR-POST-/admin/groups/{groupId}/flags-401")
  @Tag("ERR-POST-/admin/groups/{groupId}/flags-403")
  void createErrors() throws Exception {
    problem(
        admin.postJson(
            "/groups/0191f0c2-0000-7000-8000-000000000999/flags", "{\"key\":\"ok-key\"}"),
        404,
        "not-found");
    problem(
        admin.postJson(flags(), "{\"key\":\"ok-key\",\"pad\":\"" + "x".repeat(65_600) + "\"}"),
        413,
        "payload-too-large");
    problem(anonymous.postJson(flags(), "{\"key\":\"ok-key\"}"), 401, "unauthorized");
    problem(client.postJson(flags(), "{\"key\":\"ok-key\"}"), 403, "forbidden");
  }

  // ---------------------------------------------------------------- PATCH /flags/{id}

  @Test
  @Tag("AC-AUD-1")
  void patchWritesExactlyOneFlagUpdatedEvent() throws Exception {
    String id = admin.createFlag(groupId, "new-checkout", false).get("id").asText();
    int before = auditCount();

    admin
        .patchJson("/flags/" + id, "{\"description\":\"New text\",\"enabled\":true,\"version\":0}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("New text"))
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.version").value(1));

    assertThat(auditCount()).isEqualTo(before + 1);
    assertThat(audit("FLAG_UPDATED"))
        .isEqualTo(
            "{\"enabled\": {\"to\": true, \"from\": false}, \"description\": {\"to\": \"New text\", \"from\": null}}");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_event WHERE action = 'FLAG_TOGGLED'", Integer.class))
        .isZero();
  }

  @Test
  void patchNoOpAndOmittedFields() throws Exception {
    String id = admin.createFlag(groupId, "new-checkout", true).get("id").asText();
    int before = auditCount();
    admin
        .patchJson("/flags/" + id, "{\"enabled\":true,\"version\":0}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(0));
    admin.patchJson("/flags/" + id, "{\"version\":0}").andExpect(jsonPath("$.version").value(0));
    assertThat(auditCount()).isEqualTo(before);
  }

  @Test
  void emptyOrNullDescriptionClears() throws Exception {
    String id =
        admin
            .body(admin.postJson(flags(), "{\"key\":\"k1\",\"description\":\"d\"}"))
            .get("id")
            .asText();
    admin
        .patchJson("/flags/" + id, "{\"description\":\"\",\"version\":0}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").doesNotExist())
        .andExpect(jsonPath("$.version").value(1));
    admin.patchJson("/flags/" + id, "{\"description\":\"e\",\"version\":1}");
    admin
        .patchJson("/flags/" + id, "{\"description\":null,\"version\":2}")
        .andExpect(jsonPath("$.description").doesNotExist());
  }

  @Test
  @Tag("ERR-PATCH-/admin/flags/{flagId}-400")
  void patchValidation() throws Exception {
    String id = admin.createFlag(groupId, "new-checkout", false).get("id").asText();
    validation(admin.patchJson("/flags/" + id, "{\"enabled\":true}"), "version");
    validation(admin.patchJson("/flags/" + id, "{\"enabled\":null,\"version\":0}"), "enabled");
    validation(
        admin.patchJson(
            "/flags/" + id, "{\"description\":\"" + "d".repeat(501) + "\",\"version\":0}"),
        "description");
    problem(
        admin.patchJson("/flags/" + id, "{\"enabled\":1,\"version\":0}"), 400, "malformed-request");
    problem(admin.patchJson("/flags/not-a-uuid", "{\"version\":0}"), 400, "malformed-request");
  }

  @Test
  @Tag("AC-FLAG-6")
  @Tag("ERR-PATCH-/admin/flags/{flagId}-409")
  void staleVersionIsConflict() throws Exception {
    String id = admin.createFlag(groupId, "new-checkout", false).get("id").asText();
    admin
        .patchJson("/flags/" + id, "{\"description\":\"a\",\"version\":0}")
        .andExpect(status().isOk());
    problem(
        admin.patchJson("/flags/" + id, "{\"description\":\"b\",\"version\":0}"),
        409,
        "version-conflict");
    problem(
        admin.patchJson("/flags/" + id, "{\"description\":\"b\",\"version\":2}"),
        409,
        "version-conflict");
  }

  @Test
  @Tag("ERR-PATCH-/admin/flags/{flagId}-404")
  @Tag("ERR-PATCH-/admin/flags/{flagId}-413")
  @Tag("ERR-PATCH-/admin/flags/{flagId}-401")
  @Tag("ERR-PATCH-/admin/flags/{flagId}-403")
  void patchErrors() throws Exception {
    String id = admin.createFlag(groupId, "new-checkout", false).get("id").asText();
    problem(admin.patchJson("/flags/" + UNKNOWN, "{\"version\":0}"), 404, "not-found");
    problem(
        admin.patchJson("/flags/" + id, "{\"version\":0,\"pad\":\"" + "x".repeat(65_600) + "\"}"),
        413,
        "payload-too-large");
    problem(anonymous.patchJson("/flags/" + id, "{\"version\":0}"), 401, "unauthorized");
    problem(client.patchJson("/flags/" + id, "{\"version\":0}"), 403, "forbidden");
  }

  // ---------------------------------------------------------------- POST /flags/{id}/toggle

  @Test
  @Tag("AC-AUD-1")
  @Tag("AC-FLAG-3")
  void toggleSetsTheValueAndIsIdempotent() throws Exception {
    String id = admin.createFlag(groupId, "new-checkout", false).get("id").asText();

    admin
        .postJson("/flags/" + id + "/toggle", "{\"enabled\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.version").value(1));
    assertThat(audit("FLAG_TOGGLED")).isEqualTo("{\"enabled\": {\"to\": true, \"from\": false}}");
    int after = auditCount();

    admin
        .postJson("/flags/" + id + "/toggle", "{\"enabled\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    assertThat(auditCount()).isEqualTo(after);
  }

  @Test
  void concurrentTogglesToTheSameValueBothSucceedOnce() throws Exception {
    String id = admin.createFlag(groupId, "new-checkout", false).get("id").asText();
    int before = auditCount();
    java.util.concurrent.ExecutorService pool =
        java.util.concurrent.Executors.newFixedThreadPool(4);
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
    for (int i = 0; i < 4; i++) {
      results.add(
          pool.submit(
              () -> {
                start.await();
                return admin
                    .postJson("/flags/" + id + "/toggle", "{\"enabled\":true}")
                    .andReturn()
                    .getResponse()
                    .getStatus();
              }));
    }
    start.countDown();
    for (var r : results) {
      assertThat(r.get(30, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
    }
    pool.shutdown();
    assertThat(auditCount()).isEqualTo(before + 1);
    admin.get("/groups/" + groupId).andExpect(jsonPath("$.flags[0].version").value(1));
  }

  @Test
  @Tag("ERR-POST-/admin/flags/{flagId}/toggle-400")
  void toggleBodyIsRequired() throws Exception {
    String id = admin.createFlag(groupId, "new-checkout", false).get("id").asText();
    problem(admin.postJson("/flags/" + id + "/toggle", ""), 400, "malformed-request");
    problem(admin.postJson("/flags/" + id + "/toggle", "{}"), 400, "malformed-request");
    problem(
        admin.postJson("/flags/" + id + "/toggle", "{\"enabled\":null}"), 400, "malformed-request");
    problem(
        admin.postJson("/flags/" + id + "/toggle", "{\"enabled\":\"yes\"}"),
        400,
        "malformed-request");
    problem(
        admin.postJson("/flags/not-a-uuid/toggle", "{\"enabled\":true}"), 400, "malformed-request");
  }

  @Test
  @Tag("ERR-POST-/admin/flags/{flagId}/toggle-404")
  @Tag("ERR-POST-/admin/flags/{flagId}/toggle-413")
  @Tag("ERR-POST-/admin/flags/{flagId}/toggle-401")
  @Tag("ERR-POST-/admin/flags/{flagId}/toggle-403")
  void toggleErrors() throws Exception {
    String id = admin.createFlag(groupId, "new-checkout", false).get("id").asText();
    problem(
        admin.postJson("/flags/" + UNKNOWN + "/toggle", "{\"enabled\":true}"), 404, "not-found");
    problem(
        admin.postJson(
            "/flags/" + id + "/toggle",
            "{\"enabled\":true,\"pad\":\"" + "x".repeat(65_600) + "\"}"),
        413,
        "payload-too-large");
    problem(
        anonymous.postJson("/flags/" + id + "/toggle", "{\"enabled\":true}"), 401, "unauthorized");
    problem(client.postJson("/flags/" + id + "/toggle", "{\"enabled\":true}"), 403, "forbidden");
  }

  // ---------------------------------------------------------------- DELETE /flags/{id}

  @Test
  @Tag("AC-FLAG-5")
  void deleteRemovesOnlyThatFlag() throws Exception {
    String a = admin.createFlag(groupId, "a-flag", true).get("id").asText();
    admin.createFlag(groupId, "b-flag", false);

    admin.delete("/flags/" + a).andExpect(status().isNoContent());

    JsonNode d = admin.body(admin.get("/groups/" + groupId));
    assertThat(d.get("flags")).hasSize(1);
    assertThat(d.get("flags").get(0).get("key").asText()).isEqualTo("b-flag");
    assertThat(audit("FLAG_DELETED")).isEqualTo("{\"enabled\": true}");
  }

  @Test
  @Tag("ERR-DELETE-/admin/flags/{flagId}-400")
  @Tag("ERR-DELETE-/admin/flags/{flagId}-404")
  @Tag("ERR-DELETE-/admin/flags/{flagId}-401")
  @Tag("ERR-DELETE-/admin/flags/{flagId}-403")
  void deleteErrors() throws Exception {
    String id = admin.createFlag(groupId, "new-checkout", false).get("id").asText();
    problem(admin.delete("/flags/not-a-uuid"), 400, "malformed-request");
    problem(admin.delete("/flags/" + UNKNOWN), 404, "not-found");
    problem(anonymous.delete("/flags/" + id), 401, "unauthorized");
    problem(client.delete("/flags/" + id), 403, "forbidden");
  }
}
