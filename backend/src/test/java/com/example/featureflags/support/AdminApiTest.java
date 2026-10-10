package com.example.featureflags.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Base for Admin API integration tests: clean tables and an admin client per test. */
public abstract class AdminApiTest {

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper json;
  @Autowired protected DatabaseCleaner cleaner;
  @Autowired protected org.springframework.jdbc.core.JdbcTemplate jdbc;

  protected AdminClient admin;
  protected AdminClient anonymous;
  protected AdminClient client;

  @BeforeEach
  void setUpClients() throws Exception {
    cleaner.truncate();
    admin = new AdminClient(mvc, json, SecurityTestSupport.adminToken(mvc, json));
    anonymous = admin.as(null);
    client = admin.as(SecurityTestSupport.clientToken(mvc, json));
  }

  protected int auditCount() {
    return jdbc.queryForObject("SELECT count(*) FROM audit_event", Integer.class);
  }
}
