package com.example.featureflags.ops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
                .json("{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"}}}", true));
  }

  @Test
  void livenessAndReadinessAreUp() throws Exception {
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}", true));
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}", true));
  }

  @Test
  void infoHasBuildVersion() throws Exception {
    mvc.perform(get("/actuator/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.build.version").value("1.0.0"));
  }
}
