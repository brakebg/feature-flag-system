package com.example.featureflags.domain;

import static com.example.featureflags.support.Ids.id;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.featureflags.audit.AuditAction;
import com.example.featureflags.audit.AuditEvent;
import com.example.featureflags.audit.AuditEventRepository;
import com.example.featureflags.flag.FeatureFlag;
import com.example.featureflags.flag.FeatureFlagRepository;
import com.example.featureflags.group.FlagGroup;
import com.example.featureflags.group.FlagGroupRepository;
import com.example.featureflags.support.DatabaseCleaner;
import com.example.featureflags.support.IntegrationTest;
import com.example.featureflags.support.MutableClock;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

/** Spec 4.1: entity mapping, ownership columns from the security context, version rules. */
@IntegrationTest
@Import(RepositoryIT.Clocks.class)
class RepositoryIT {

  static final Instant T0 = Instant.parse("2026-10-01T10:00:00.123456789Z");

  @TestConfiguration
  static class Clocks {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock(T0);
    }
  }

  @Autowired FlagGroupRepository groups;
  @Autowired FeatureFlagRepository flags;
  @Autowired AuditEventRepository audit;
  @Autowired TransactionTemplate tx;
  @Autowired DatabaseCleaner cleaner;
  @Autowired MutableClock clock;
  @Autowired EntityManager em;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    cleaner.truncate();
    clock.set(T0);
    signIn("alice");
  }

  @AfterEach
  void signOut() {
    SecurityContextHolder.clearContext();
  }

  private static void signIn(String user) {
    TestingAuthenticationToken auth = new TestingAuthenticationToken(user, null, "SCOPE_admin");
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  void newGroupGetsOwnershipColumnsTimestampsFromTheClockAndVersionZero() {
    groups.saveAndFlush(new FlagGroup(id(1), "orders", "Orders", null));

    FlagGroup loaded = groups.findByKey("orders").orElseThrow();
    assertThat(loaded.getCreatedBy()).isEqualTo("alice");
    assertThat(loaded.getUpdatedBy()).isEqualTo("alice");
    assertThat(loaded.getVersion()).isZero();
    assertThat(loaded.getCreatedAt()).isEqualTo(T0.truncatedTo(ChronoUnit.MICROS));
    assertThat(loaded.getUpdatedAt()).isEqualTo(T0.truncatedTo(ChronoUnit.MICROS));
    assertThat(loaded.getId()).isEqualTo(id(1));
  }

  @Test
  void realChangeIncrementsVersionByOneAndKeepsCreatedBy() {
    groups.saveAndFlush(new FlagGroup(id(1), "orders", "Orders", null));
    signIn("bob");
    clock.advance(Duration.ofMinutes(5));

    tx.executeWithoutResult(s -> groups.findById(id(1)).orElseThrow().rename("Orders 2"));

    FlagGroup loaded = groups.findById(id(1)).orElseThrow();
    assertThat(loaded.getVersion()).isEqualTo(1L);
    assertThat(loaded.getUpdatedBy()).isEqualTo("bob");
    assertThat(loaded.getCreatedBy()).isEqualTo("alice");
    assertThat(loaded.getUpdatedAt())
        .isEqualTo(T0.plus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MICROS));
    assertThat(loaded.getCreatedAt()).isEqualTo(T0.truncatedTo(ChronoUnit.MICROS));
  }

  @Test
  void createdByAndKeyAreNotUpdatable() {
    groups.saveAndFlush(new FlagGroup(id(1), "orders", "Orders", null));

    tx.executeWithoutResult(
        s -> {
          FlagGroup g = groups.findById(id(1)).orElseThrow();
          setField(g, "createdBy", "mallory");
          setField(g, "key", "renamed");
          g.rename("Changed");
        });

    Map<String, Object> row =
        jdbc.queryForMap("SELECT key, created_by, name FROM flag_group WHERE id = ?", id(1));
    assertThat(row).containsEntry("created_by", "alice").containsEntry("key", "orders");
    assertThat(row).containsEntry("name", "Changed");
  }

  private static void setField(Object target, String name, Object value) {
    try {
      Class<?> c = target.getClass();
      while (c != null) {
        for (var f : c.getDeclaredFields()) {
          if (f.getName().equals(name)) {
            f.setAccessible(true);
            f.set(target, value);
            return;
          }
        }
        c = c.getSuperclass();
      }
      throw new IllegalArgumentException(name);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void unchangedEntityKeepsVersion() {
    groups.saveAndFlush(new FlagGroup(id(1), "orders", "Orders", null));

    tx.executeWithoutResult(s -> groups.findById(id(1)).orElseThrow().rename("Orders"));

    assertThat(groups.findById(id(1)).orElseThrow().getVersion()).isZero();
  }

  @Test
  void flagChangeDoesNotChangeGroupVersion() {
    UUID gid = groups.saveAndFlush(new FlagGroup(id(1), "orders", "Orders", null)).getId();
    UUID fid = flags.saveAndFlush(new FeatureFlag(id(2), gid, "new-checkout", null, false)).getId();

    tx.executeWithoutResult(s -> flags.findById(fid).orElseThrow().setEnabled(true));

    assertThat(flags.findById(fid).orElseThrow().getVersion()).isEqualTo(1L);
    assertThat(groups.findById(gid).orElseThrow().getVersion()).isZero();
    assertThat(flags.findByGroupId(gid))
        .extracting(FeatureFlag::getKey)
        .containsExactly("new-checkout");
  }

  @Test
  void auditEventStoresJsonDetailsIncludingNulls() {
    Map<String, Object> change = new LinkedHashMap<>();
    change.put("from", null);
    change.put("to", "New text");
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("description", change);
    Instant at = Instant.parse("2026-10-01T14:32:05.123456Z");
    AuditEvent saved =
        audit.saveAndFlush(
            new AuditEvent(at, "admin", AuditAction.FLAG_UPDATED, "orders.new-checkout", details));

    AuditEvent loaded = audit.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getDetails()).isEqualTo(details);
    assertThat(loaded.getAction()).isEqualTo(AuditAction.FLAG_UPDATED);
    assertThat(loaded.getOccurredAt()).isEqualTo(at);
    assertThat(List.of(AuditAction.values()))
        .extracting(Enum::name)
        .containsExactly(
            "GROUP_CREATED",
            "GROUP_UPDATED",
            "GROUP_DELETED",
            "FLAG_CREATED",
            "FLAG_UPDATED",
            "FLAG_TOGGLED",
            "FLAG_DELETED");
  }
}
