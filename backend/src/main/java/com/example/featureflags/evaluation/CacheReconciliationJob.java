package com.example.featureflags.evaluation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Spec 7.2 freshness safety net: once a day ({@code FF_CACHE_RECONCILE_CRON}, default {@code 0 0 3
 * * * *}) compares the cache with the database and fixes every difference (WARN per difference). A
 * failed run is logged at ERROR and leaves the cache as it was.
 */
@Component
public class CacheReconciliationJob {

  private static final Logger log = LoggerFactory.getLogger(CacheReconciliationJob.class);

  private final FlagCacheService cache;
  private final Clock clock;
  private final Counter drift;
  private final AtomicLong lastSuccess = new AtomicLong();

  public CacheReconciliationJob(FlagCacheService cache, Clock clock, MeterRegistry meters) {
    this.cache = cache;
    this.clock = clock;
    this.drift = meters.counter("ff_cache_reconcile_drift_total");
    meters.gauge("ff_cache_reconcile_last_success_seconds", lastSuccess);
  }

  /** Runs one reconciliation; returns the number of differences fixed, or -1 on failure. */
  @Scheduled(cron = "${featureflags.cache.reconcile-cron}")
  public int reconcile() {
    try {
      FlagCacheService.Snapshot db = cache.loadSnapshot();
      List<FlagCacheService.Difference> diffs = cache.reconcile(db);
      for (FlagCacheService.Difference d : diffs) {
        log.warn(
            "Cache drift fixed: key={} cached={} database={}", d.key(), d.cached(), d.database());
      }
      drift.increment(diffs.size());
      lastSuccess.set(clock.instant().getEpochSecond());
      log.info("Cache reconciliation finished: {} difference(s)", diffs.size());
      return diffs.size();
    } catch (RuntimeException e) {
      log.error("Cache reconciliation failed; cache left as it was", e);
      return -1;
    }
  }
}
