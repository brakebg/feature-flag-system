package com.example.featureflags.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Base for Admin API integration tests: clean tables and an admin client per test. */
public abstract class AdminApiTest {

  @Autowired protected MockMvc mvc;
  @Autowired protected DatabaseCleaner cleaner;
  @Autowired protected org.springframework.jdbc.core.JdbcTemplate jdbc;

  protected AdminClient admin;
  protected AdminClient anonymous;
  protected AdminClient client;

  @BeforeEach
  void setUpClients() throws Exception {
    cleaner.truncate();
    admin = new AdminClient(mvc, SecurityTestSupport.adminToken(mvc));
    anonymous = admin.as(null);
    client = admin.as(SecurityTestSupport.clientToken(mvc));
  }

  protected int auditCount() {
    return jdbc.queryForObject("SELECT count(*) FROM audit_event", Integer.class);
  }
}
