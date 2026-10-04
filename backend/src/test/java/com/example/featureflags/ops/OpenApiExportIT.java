package com.example.featureflags.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Gate 8 (spec 11.3): springdoc renders the OpenAPI document (12.2 M4 "OpenAPI renders"); this test
 * writes it to {@code backend/openapi.json}, which must be committed and up to date.
 */
@IntegrationTest
class OpenApiExportIT {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void writesTheOpenApiDocument() throws Exception {
    String body =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    JsonNode doc = json.readTree(body);
    assertThat(doc.get("paths").has("/api/v1/admin/groups")).isTrue();
    assertThat(doc.get("paths").has("/api/v1/admin/flags/{flagId}/toggle")).isTrue();
    assertThat(doc.get("paths").has("/api/v1/auth/login")).isTrue();
    String pretty =
        json.copy()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(json.treeToValue(doc, Object.class))
            + "\n";
    Files.writeString(Path.of("openapi.json"), pretty, StandardCharsets.UTF_8);
  }

  @Test
  void swaggerUiRendersInDev() throws Exception {
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }
}
