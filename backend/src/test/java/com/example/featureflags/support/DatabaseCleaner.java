package com.example.featureflags.support;

import com.example.featureflags.evaluation.FlagCacheService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Spec 11.5: integration tests start each test from empty tables. */
@Component
public class DatabaseCleaner {

  private final JdbcTemplate jdbc;
  private final FlagCacheService cache;

  public DatabaseCleaner(JdbcTemplate jdbc, FlagCacheService cache) {
    this.jdbc = jdbc;
    this.cache = cache;
  }

  /** Empties the tables and reloads the cache with the same public method as warm-up (11.5). */
  public void truncate() {
    jdbc.execute("TRUNCATE TABLE feature_flag, flag_group, audit_event RESTART IDENTITY CASCADE");
    cache.reloadAll();
  }
}
