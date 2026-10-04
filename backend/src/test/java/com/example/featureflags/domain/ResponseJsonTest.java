package com.example.featureflags.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.featureflags.audit.AuditEventView;
import com.example.featureflags.common.JacksonConfig;
import com.example.featureflags.flag.Flag;
import com.example.featureflags.group.Group;
import com.example.featureflags.group.GroupDetail;
import com.example.featureflags.group.GroupSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

/** Spec 6.2: response payload shapes; optional fields without a value are omitted. */
@JsonTest
@Import(JacksonConfig.class)
class ResponseJsonTest {

  @Autowired ObjectMapper mapper;

  private static final UUID G = UUID.fromString("0191f0c2-0000-7000-8000-000000000001");
  private static final UUID F = UUID.fromString("0191f0c3-0000-7000-8000-000000000001");
  private static final Instant T = Instant.parse("2026-10-01T14:32:05.123Z");

  private JsonNode json(Object o) throws Exception {
    return mapper.readTree(mapper.writeValueAsString(o));
  }

  private static List<String> names(JsonNode n) {
    List<String> out = new java.util.ArrayList<>();
    n.fieldNames().forEachRemaining(out::add);
    return out;
  }

  @Test
  void flagHasTheSpecFieldsAndOmitsMissingDescription() throws Exception {
    JsonNode n =
        json(
            new Flag(
                F,
                G,
                "new-checkout",
                "orders.new-checkout",
                null,
                false,
                T,
                "admin",
                T,
                "admin",
                0));
    assertThat(names(n))
        .containsExactlyInAnyOrder(
            "id",
            "groupId",
            "key",
            "fullKey",
            "enabled",
            "createdAt",
            "createdBy",
            "updatedAt",
            "updatedBy",
            "version");
    assertThat(n.get("updatedAt").asText()).isEqualTo("2026-10-01T14:32:05.123Z");
    assertThat(n.get("version").isIntegralNumber()).isTrue();

    JsonNode withDesc = json(new Flag(F, G, "k1", "o.k1", "text", true, T, "admin", T, "admin", 3));
    assertThat(withDesc.get("description").asText()).isEqualTo("text");
  }

  @Test
  void groupShapes() throws Exception {
    JsonNode summary =
        json(new GroupSummary(G, "orders", "Orders", null, 2, 1, "admin", T, "bob", 4));
    assertThat(names(summary))
        .containsExactlyInAnyOrder(
            "id",
            "key",
            "name",
            "flagCount",
            "enabledCount",
            "createdBy",
            "updatedAt",
            "updatedBy",
            "version");

    JsonNode group = json(new Group(G, "orders", "Orders", "d", T, "admin", T, "bob", 4));
    assertThat(names(group))
        .containsExactlyInAnyOrder(
            "id",
            "key",
            "name",
            "description",
            "createdAt",
            "createdBy",
            "updatedAt",
            "updatedBy",
            "version");

    JsonNode detail =
        json(new GroupDetail(G, "orders", "Orders", null, T, "admin", T, "bob", 4, List.of()));
    assertThat(names(detail))
        .containsExactlyInAnyOrder(
            "id",
            "key",
            "name",
            "createdAt",
            "createdBy",
            "updatedAt",
            "updatedBy",
            "version",
            "flags");
  }

  @Test
  void auditEventShape() throws Exception {
    JsonNode n = json(new AuditEventView(7L, T, "admin", "GROUP_CREATED", "orders", null));
    assertThat(names(n))
        .containsExactlyInAnyOrder("id", "occurredAt", "actor", "action", "targetKey");
    JsonNode d =
        json(
            new AuditEventView(
                8L, T, "admin", "GROUP_CREATED", "orders", Map.of("name", "Orders")));
    assertThat(d.get("details").get("name").asText()).isEqualTo("Orders");
  }
}
