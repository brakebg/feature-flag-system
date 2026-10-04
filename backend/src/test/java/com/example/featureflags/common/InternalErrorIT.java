package com.example.featureflags.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.group.GroupService;
import com.example.featureflags.support.AdminClient;
import com.example.featureflags.support.IntegrationTest;
import com.example.featureflags.support.SecurityTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Spec 9.1 500 row: no stack trace in the body, correlation id given. */
@IntegrationTest
class InternalErrorIT {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean GroupService groups;

  @Test
  @Tag("ERR-GET-/admin/groups-500")
  void unexpectedErrorIs500Internal() throws Exception {
    when(groups.list(any(), any())).thenThrow(new IllegalStateException("db exploded at line 42"));
    new AdminClient(mvc, json, SecurityTestSupport.adminToken(mvc, json))
        .get("/groups")
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://featureflags.local/problems/internal"))
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .exists("X-Request-Id"))
        .andExpect(content().string(Matchers.not(Matchers.containsString("exploded"))))
        .andExpect(
            content().string(Matchers.not(Matchers.containsString("IllegalStateException"))));
  }
}
