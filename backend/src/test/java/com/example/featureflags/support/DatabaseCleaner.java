package com.example.featureflags.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Spec 11.5: integration tests start each test from empty tables. */
@Component
public class DatabaseCleaner {

  private final JdbcTemplate jdbc;

  public DatabaseCleaner(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void truncate() {
    jdbc.execute("TRUNCATE TABLE feature_flag, flag_group, audit_event RESTART IDENTITY CASCADE");
  }
}
