package com.example.featureflags.audit;

import static com.example.featureflags.support.AdminClient.PROBLEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.AdminApiTest;
import com.example.featureflags.support.IntegrationTest;
import com.example.featureflags.support.Tokens;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/** Spec 4.1, 6.1 GET /audit, AC-AUD-1, AC-AUD-2 (API part). */
@IntegrationTest
class AuditApiIT extends AdminApiTest {

  @Autowired MeterRegistry meters;

  private void insertEvent(String at, String target) {
    jdbc.update(
        "INSERT INTO audit_event (occurred_at, actor, action, target_key) VALUES (?::timestamptz, 'admin', 'GROUP_CREATED', ?)",
        at,
        target);
  }

  private static List<String> targets(JsonNode page) {
    List<String> out = new ArrayList<>();
    page.get("content").forEach(n -> out.add(n.get("targetKey").asText()));
    return out;
  }

  private static void problem(ResultActions r, int status, String type) throws Exception {
    r.andExpect(status().is(status)).andExpect(jsonPath("$.type").value(PROBLEM + type));
  }

  @Test
  @Tag("AC-AUD-2")
  void newestFirstThenHighestIdAndStablePageFormat() throws Exception {
    insertEvent("2026-10-01T10:00:00Z", "first");
    insertEvent("2026-10-01T12:00:00Z", "newest-a");
    insertEvent("2026-10-01T12:00:00Z", "newest-b");
    insertEvent("2026-10-01T11:00:00Z", "middle");

    JsonNode page = admin.body(admin.get("/audit").andExpect(status().isOk()));
    assertThat(targets(page)).containsExactly("newest-b", "newest-a", "middle", "first");
    assertThat(page.get("page").get("size").asInt()).isEqualTo(50);
    assertThat(page.get("page").get("number").asInt()).isZero();
    assertThat(page.get("page").get("totalElements").asInt()).isEqualTo(4);
    assertThat(page.get("page").get("totalPages").asInt()).isEqualTo(1);
    JsonNode e = page.get("content").get(0);
    assertThat(e.get("id").isIntegralNumber()).isTrue();
    assertThat(e.get("occurredAt").asText()).isEqualTo("2026-10-01T12:00:00Z");
    assertThat(e.get("actor").asText()).isEqualTo("admin");
    assertThat(e.get("action").asText()).isEqualTo("GROUP_CREATED");
    assertThat(e.has("details")).isFalse();
  }

  @Test
  @Tag("AC-AUD-2")
  void targetKeyIsACaseSensitiveLiteralPrefix() throws Exception {
    insertEvent("2026-10-01T10:00:00Z", "orders");
    insertEvent("2026-10-01T10:00:01Z", "orders.new-checkout");
    insertEvent("2026-10-01T10:00:02Z", "Orders.x");
    insertEvent("2026-10-01T10:00:03Z", "ord_rs");
    insertEvent("2026-10-01T10:00:04Z", "payments");

    assertThat(targets(admin.body(admin.get("/audit?targetKey=orders"))))
        .containsExactly("orders.new-checkout", "orders");
    assertThat(targets(admin.body(admin.get("/audit?targetKey=ord_")))).containsExactly("ord_rs");
    assertThat(targets(admin.body(admin.get("/audit?targetKey=%25")))).isEmpty();
    assertThat(targets(admin.body(admin.get("/audit?targetKey=")))).hasSize(5);
  }

  @Test
  void pagingAndPagePastTheEnd() throws Exception {
    for (int i = 0; i < 5; i++) {
      insertEvent("2026-10-01T10:00:0" + i + "Z", "e" + i);
    }
    JsonNode p1 = admin.body(admin.get("/audit?size=2&page=1"));
    assertThat(targets(p1)).containsExactly("e2", "e1");
    assertThat(p1.get("page").get("totalPages").asInt()).isEqualTo(3);
    JsonNode past = admin.body(admin.get("/audit?size=2&page=9").andExpect(status().isOk()));
    assertThat(past.get("content")).isEmpty();
    admin.get("/audit?size=200").andExpect(status().isOk());
    admin.get("/audit?size=1&page=0").andExpect(status().isOk());
  }

  @Test
  @Tag("ERR-GET-/admin/audit-400")
  void badPagingParameters() throws Exception {
    problem(admin.get("/audit?size=0"), 400, "validation");
    admin.get("/audit?size=201").andExpect(jsonPath("$.errors[0].field").value("size"));
    admin.get("/audit?page=-1").andExpect(jsonPath("$.errors[0].field").value("page"));
    problem(admin.get("/audit?page=abc"), 400, "malformed-request");
    problem(admin.get("/audit?size=x"), 400, "malformed-request");
  }

  @Test
  @Tag("ERR-GET-/admin/audit-401")
  @Tag("ERR-GET-/admin/audit-403")
  void auditNeedsAnAdminToken() throws Exception {
    problem(anonymous.get("/audit"), 401, "unauthorized");
    problem(client.get("/audit"), 403, "forbidden");
  }

  @Test
  @Tag("AC-AUD-1")
  void ownershipFollowsTheSignedInUserAndBodyFieldsAreIgnored() throws Exception {
    JsonNode g =
        admin.body(
            admin.postJson(
                "/groups",
                "{\"key\":\"orders\",\"name\":\"Orders\",\"createdBy\":\"mallory\",\"updatedBy\":\"mallory\"}"));
    assertThat(g.get("createdBy").asText()).isEqualTo("admin");
    String id = g.get("id").asText();
    String flagId = admin.createFlag(id, "new-checkout", false).get("id").asText();

    Instant now = Instant.now();
    String bob =
        Tokens.sign(Tokens.claims("bob", "admin", "feature-flag-admin", now, now.plusSeconds(300)));
    var asBob = admin.as(bob);
    asBob
        .patchJson("/groups/" + id, "{\"name\":\"Shop\",\"version\":0,\"updatedBy\":\"mallory\"}")
        .andExpect(jsonPath("$.updatedBy").value("bob"))
        .andExpect(jsonPath("$.createdBy").value("admin"));
    asBob
        .postJson("/flags/" + flagId + "/toggle", "{\"enabled\":true}")
        .andExpect(jsonPath("$.updatedBy").value("bob"))
        .andExpect(jsonPath("$.createdBy").value("admin"));
    assertThat(jdbc.queryForList("SELECT actor FROM audit_event ORDER BY id", String.class))
        .containsExactly("admin", "admin", "bob", "bob");
  }

  @Test
  @Tag("AC-AUD-1")
  void everyChangeWritesExactlyOneEventAndNoOpsWriteNone() throws Exception {
    String id = admin.createGroup("orders", "Orders").get("id").asText();
    String flagId = admin.createFlag(id, "new-checkout", false).get("id").asText();
    admin.patchJson("/flags/" + flagId, "{\"description\":\"d\",\"version\":0}");
    admin.postJson("/flags/" + flagId + "/toggle", "{\"enabled\":true}");
    admin.postJson("/flags/" + flagId + "/toggle", "{\"enabled\":true}");
    admin.patchJson("/groups/" + id, "{\"name\":\"Orders\",\"version\":0}");
    admin.delete("/flags/" + flagId);
    admin.delete("/groups/" + id);

    assertThat(jdbc.queryForList("SELECT action FROM audit_event ORDER BY id", String.class))
        .containsExactly(
            "GROUP_CREATED",
            "FLAG_CREATED",
            "FLAG_UPDATED",
            "FLAG_TOGGLED",
            "FLAG_DELETED",
            "GROUP_DELETED");
    assertThat(jdbc.queryForList("SELECT DISTINCT actor FROM audit_event", String.class))
        .containsExactly("admin");
  }

  @Test
  void adminWritesAreCountedPerAction() throws Exception {
    double before = count("GROUP_CREATED");
    admin.createGroup("orders", "Orders");
    assertThat(count("GROUP_CREATED")).isEqualTo(before + 1);
  }

  private double count(String action) {
    var c = meters.find("ff_admin_writes_total").tag("action", action).counter();
    return c == null ? 0 : c.count();
  }
}
