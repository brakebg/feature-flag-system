package com.example.featureflags.domain;

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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.transaction.support.TransactionTemplate;

/** Spec 4.1: entity mapping, ownership columns and version rules. */
@IntegrationTest
@Import(RepositoryIT.Auditor.class)
class RepositoryIT {

  @TestConfiguration
  static class Auditor {
    static String current = "alice";

    @Bean
    AuditorAware<String> testAuditor() {
      return () -> Optional.of(current);
    }
  }

  @Autowired FlagGroupRepository groups;
  @Autowired FeatureFlagRepository flags;
  @Autowired AuditEventRepository audit;
  @Autowired TransactionTemplate tx;
  @Autowired DatabaseCleaner cleaner;

  @BeforeEach
  void clean() {
    cleaner.truncate();
    Auditor.current = "alice";
  }

  @Test
  void newGroupGetsOwnershipColumnsAndVersionZero() {
    FlagGroup g = groups.saveAndFlush(new FlagGroup(UUID.randomUUID(), "orders", "Orders", null));

    FlagGroup loaded = groups.findByKey("orders").orElseThrow();
    assertThat(loaded.getCreatedBy()).isEqualTo("alice");
    assertThat(loaded.getUpdatedBy()).isEqualTo("alice");
    assertThat(loaded.getVersion()).isZero();
    assertThat(loaded.getCreatedAt()).isEqualTo(loaded.getUpdatedAt());
    assertThat(loaded.getId()).isEqualTo(g.getId());
  }

  @Test
  void realChangeIncrementsVersionByOneAndKeepsCreatedBy() {
    UUID id =
        groups.saveAndFlush(new FlagGroup(UUID.randomUUID(), "orders", "Orders", null)).getId();
    Auditor.current = "bob";

    tx.executeWithoutResult(s -> groups.findById(id).orElseThrow().rename("Orders 2"));

    FlagGroup loaded = groups.findById(id).orElseThrow();
    assertThat(loaded.getVersion()).isEqualTo(1L);
    assertThat(loaded.getUpdatedBy()).isEqualTo("bob");
    assertThat(loaded.getCreatedBy()).isEqualTo("alice");
  }

  @Test
  void unchangedEntityKeepsVersion() {
    UUID id =
        groups.saveAndFlush(new FlagGroup(UUID.randomUUID(), "orders", "Orders", null)).getId();

    tx.executeWithoutResult(s -> groups.findById(id).orElseThrow().rename("Orders"));

    assertThat(groups.findById(id).orElseThrow().getVersion()).isZero();
  }

  @Test
  void flagChangeDoesNotChangeGroupVersion() {
    UUID gid =
        groups.saveAndFlush(new FlagGroup(UUID.randomUUID(), "orders", "Orders", null)).getId();
    UUID fid =
        flags
            .saveAndFlush(new FeatureFlag(UUID.randomUUID(), gid, "new-checkout", null, false))
            .getId();

    tx.executeWithoutResult(s -> flags.findById(fid).orElseThrow().setEnabled(true));

    assertThat(flags.findById(fid).orElseThrow().getVersion()).isEqualTo(1L);
    assertThat(groups.findById(gid).orElseThrow().getVersion()).isZero();
    assertThat(flags.findByGroupId(gid))
        .extracting(FeatureFlag::getKey)
        .containsExactly("new-checkout");
  }

  @Test
  void auditEventStoresJsonDetails() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("enabled", Map.of("from", false, "to", true));
    Instant at = Instant.parse("2026-10-01T14:32:05.123456Z");
    AuditEvent saved =
        audit.saveAndFlush(
            new AuditEvent(at, "admin", AuditAction.FLAG_TOGGLED, "orders.new-checkout", details));

    AuditEvent loaded = audit.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getDetails()).isEqualTo(Map.of("enabled", Map.of("from", false, "to", true)));
    assertThat(loaded.getAction()).isEqualTo(AuditAction.FLAG_TOGGLED);
    assertThat(loaded.getOccurredAt()).isEqualTo(at.truncatedTo(ChronoUnit.MICROS));
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
