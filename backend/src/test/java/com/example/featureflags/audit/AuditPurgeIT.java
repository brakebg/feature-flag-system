package com.example.featureflags.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.featureflags.support.DatabaseCleaner;
import com.example.featureflags.support.IntegrationTest;
import com.example.featureflags.support.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** Spec 4.1 retention, AC-AUD-3: the purge job with an injected Clock. */
@IntegrationTest
@Import(AuditPurgeIT.Clocks.class)
@ExtendWith(OutputCaptureExtension.class)
class AuditPurgeIT {

  static final Instant NOW = Instant.parse("2027-06-01T03:30:00Z");

  @TestConfiguration
  static class Clocks {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock(NOW);
    }
  }

  @Autowired AuditPurgeJob job;
  @Autowired JdbcTemplate jdbc;
  @Autowired DatabaseCleaner cleaner;

  @BeforeEach
  void clean() {
    cleaner.truncate();
  }

  private void insert(int count, String occurredAt) {
    jdbc.update(
        "INSERT INTO audit_event (occurred_at, actor, action, target_key)"
            + " SELECT ?::timestamptz, 'admin', 'GROUP_CREATED', 'g-' || n FROM generate_series(1, ?) n",
        occurredAt,
        count);
  }

  @Test
  @Tag("AC-AUD-3")
  void deletesEventsOlderThan365DaysAndKeepsNewerOnes(CapturedOutput output) {
    insert(12_001, "2026-05-31T03:29:59Z"); // 366 days old
    insert(3, "2026-06-01T03:30:01Z"); // just inside 365 days
    insert(2, "2027-05-31T00:00:00Z");

    long removed = job.purge();

    assertThat(removed).isEqualTo(12_001);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event", Integer.class)).isEqualTo(5);
    assertThat(output.getOut()).contains("12001");
  }

  @Test
  @Tag("AC-AUD-3")
  void nothingToPurge() {
    insert(2, "2027-05-31T00:00:00Z");
    assertThat(job.purge()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event", Integer.class)).isEqualTo(2);
  }
}
