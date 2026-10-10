package com.example.featureflags.ops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/** Spec 9.3: public health endpoints and their exact bodies. */
@IntegrationTest
class HealthIT {

  @Autowired MockMvc mvc;

  @Test
  @Tag("AC-OPS-3")
  void healthIsUpWithDatabaseStatus() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"}}}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void livenessAndReadinessAreUp() throws Exception {
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
  }

  @Test
  void infoHasBuildVersionFromVersionFileAndCommitId() throws Exception {
    String version = java.nio.file.Files.readString(java.nio.file.Path.of("..", "VERSION")).strip();
    mvc.perform(get("/actuator/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.build.version").value(version))
        .andExpect(
            jsonPath("$.build.version")
                .value(org.hamcrest.Matchers.matchesPattern("^\\d+\\.\\d+\\.\\d+$")))
        .andExpect(
            jsonPath("$.git.commit.id")
                .value(org.hamcrest.Matchers.matchesPattern("^[0-9a-f]{7,40}$")));
  }

  @Test
  void otherJsonResponsesPassTheHealthFilterUnchanged() throws Exception {
    mvc.perform(get("/no/such/path"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("https://featureflags.local/problems/unauthorized"))
        .andExpect(jsonPath("$.title").isString())
        .andExpect(jsonPath("$.detail").isString())
        .andExpect(jsonPath("$.instance").value("/no/such/path"));
  }
}
