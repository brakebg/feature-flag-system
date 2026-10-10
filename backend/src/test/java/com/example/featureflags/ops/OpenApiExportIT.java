package com.example.featureflags.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.IntegrationTest;
import com.example.featureflags.support.TestJson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/**
 * Gate 8 (spec 11.3): springdoc renders the OpenAPI document (12.2 M4 "OpenAPI renders"); this test
 * writes it to {@code backend/openapi.json}, which must be committed and up to date.
 */
@IntegrationTest
class OpenApiExportIT {

  @Autowired MockMvc mvc;

  @Test
  void writesTheOpenApiDocument() throws Exception {
    String body =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    JsonNode doc = TestJson.tree(body);
    assertThat(doc.get("paths").has("/api/v1/admin/groups")).isTrue();
    assertThat(doc.get("paths").has("/api/v1/admin/flags/{flagId}/toggle")).isTrue();
    assertThat(doc.get("paths").has("/api/v1/auth/login")).isTrue();
    JsonNode schemas = doc.get("components").get("schemas");
    assertThat(names(schemas.get("Flag").get("required")))
        .containsExactly(
            "createdAt",
            "createdBy",
            "enabled",
            "fullKey",
            "groupId",
            "id",
            "key",
            "updatedAt",
            "updatedBy",
            "version");
    assertThat(names(schemas.get("GroupSummary").get("required")))
        .contains("flagCount", "enabledCount", "version")
        .doesNotContain("description");
    assertThat(names(schemas.get("ToggleFlagRequest").get("required"))).containsExactly("enabled");
    assertThat(names(schemas.get("UpdateGroupRequest").get("required"))).containsExactly("version");
    assertThat(names(schemas.get("CreateGroupRequest").get("required")))
        .containsExactly("key", "name");
    assertThat(
            names(
                schemas.get("CreateGroupRequest").get("properties").get("description").get("type")))
        .containsExactlyInAnyOrder("string", "null");
    // Spec 002 4 item 3: springdoc 3 must not add "minimum" for @PositiveOrZero (D-5).
    for (String request : new String[] {"UpdateGroupRequest", "UpdateFlagRequest"}) {
      assertThat(schemas.get(request).get("properties").get("version").has("minimum"))
          .as(request)
          .isFalse();
    }
    String pretty = TestJson.prettySorted(doc) + "\n";
    Files.writeString(Path.of("openapi.json"), pretty, StandardCharsets.UTF_8);
  }

  private static java.util.List<String> names(JsonNode array) {
    java.util.List<String> out = new java.util.ArrayList<>();
    if (array != null) {
      array.forEach(n -> out.add(n.asText()));
    }
    return out;
  }

  @Test
  void swaggerUiRendersInDev() throws Exception {
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }
}
