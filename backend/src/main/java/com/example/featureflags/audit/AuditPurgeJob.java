package com.example.featureflags.audit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spec 4.1 retention: deletes audit events older than {@code FF_AUDIT_RETENTION} (default {@code
 * P365D}) every night ({@code FF_AUDIT_PURGE_CRON}, default {@code 0 30 3 * * *}), in batches of
 * 5,000, each in its own short transaction, and logs how many rows it removed.
 */
@Component
public class AuditPurgeJob {

  static final int BATCH = 5_000;
  private static final Logger log = LoggerFactory.getLogger(AuditPurgeJob.class);

  private final AuditEventRepository events;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final Duration retention;

  public AuditPurgeJob(
      AuditEventRepository events,
      TransactionTemplate tx,
      Clock clock,
      @Value("${featureflags.audit.retention}") Duration retention) {
    this.events = events;
    this.tx = tx;
    this.clock = clock;
    this.retention = retention;
  }

  @Scheduled(cron = "${featureflags.audit.purge-cron}")
  public long purge() {
    Instant cutoff = clock.instant().minus(retention);
    long total = 0;
    try {
      int removed;
      do {
        Integer n = tx.execute(s -> events.deleteOlderThan(cutoff, BATCH));
        removed = n == null ? 0 : n;
        total += removed;
      } while (removed == BATCH);
    } finally {
      log.info("Audit purge removed {} events older than {}", total, cutoff);
    }
    return total;
  }
}
