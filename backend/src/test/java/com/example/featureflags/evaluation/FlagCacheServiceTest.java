package com.example.featureflags.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.featureflags.common.FlagsChangedEvent;
import com.example.featureflags.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Spec 7.2: cache read path, write path, reload and reconciliation (unit, mocked queries). */
@ExtendWith(OutputCaptureExtension.class)
class FlagCacheServiceTest {

  EvaluationQueries queries = mock(EvaluationQueries.class);
  MutableClock clock = new MutableClock(Instant.parse("2026-10-01T12:00:00Z"));
  SimpleMeterRegistry meters = new SimpleMeterRegistry();
  FlagCacheService cache = new FlagCacheService(queries, clock, meters);

  static EvaluationQueries.Row row(String group, String flag, Boolean enabled) {
    return new EvaluationQueries.Row() {
      @Override
      public String getGroupKey() {
        return group;
      }

      @Override
      public String getFlagKey() {
        return flag;
      }

      @Override
      public Boolean getEnabled() {
        return enabled;
      }
    };
  }

  @BeforeEach
  void data() {
    when(queries.findAllRows())
        .thenReturn(
            List.of(
                row("orders", "new-checkout", true),
                row("orders", "split-payments", false),
                row("empty", null, null)));
    when(queries.loadMaxAuditId()).thenReturn(Optional.of(42L));
  }

  @Test
  @Tag("AC-CACHE-1")
  void afterReloadAllReadsAreHitsWithoutQueries() {
    cache.reloadAll();
    for (int i = 0; i < 1000; i++) {
      assertThat(cache.flag("orders", "new-checkout")).contains(true);
      assertThat(cache.group("orders"))
          .contains(Map.of("new-checkout", true, "split-payments", false));
      assertThat(cache.all())
          .isEqualTo(Map.of("orders.new-checkout", true, "orders.split-payments", false));
    }
    assertThat(cache.group("empty")).contains(Map.of());
    verify(queries, times(1)).findAllRows();
    verify(queries, never()).findEnabled(anyString(), anyString());
    verify(queries, never()).findGroup(anyString());
    assertThat(cache.revision()).isEqualTo(42);
  }

  @Test
  void missLoadsOnceThenHits() {
    when(queries.findEnabled("orders", "new-checkout")).thenReturn(Optional.of(true));
    when(queries.findGroup("orders")).thenReturn(List.of(row("orders", "a", true)));
    assertThat(cache.flag("orders", "new-checkout")).contains(true);
    assertThat(cache.flag("orders", "new-checkout")).contains(true);
    assertThat(cache.group("orders")).contains(Map.of("a", true));
    assertThat(cache.group("orders")).contains(Map.of("a", true));
    assertThat(cache.all()).hasSize(2);
    assertThat(cache.all()).hasSize(2);
    verify(queries, times(1)).findEnabled("orders", "new-checkout");
    verify(queries, times(1)).findGroup("orders");
    verify(queries, times(1)).findAllRows();
  }

  @Test
  @Tag("AC-CACHE-3")
  void unknownKeysAreCachedForThirtySeconds() {
    when(queries.findEnabled("orders", "nope")).thenReturn(Optional.empty());
    when(queries.findGroup("nope")).thenReturn(List.of());
    assertThat(cache.flag("orders", "nope")).isEmpty();
    assertThat(cache.group("nope")).isEmpty();
    clock.advance(Duration.ofSeconds(29));
    assertThat(cache.flag("orders", "nope")).isEmpty();
    assertThat(cache.group("nope")).isEmpty();
    verify(queries, times(1)).findEnabled("orders", "nope");
    verify(queries, times(1)).findGroup("nope");

    clock.advance(Duration.ofSeconds(2));
    assertThat(cache.flag("orders", "nope")).isEmpty();
    verify(queries, times(2)).findEnabled("orders", "nope");
    assertThat(cache.group("nope")).isEmpty();
    verify(queries, times(2)).findGroup("nope");
  }

  @Test
  void positiveEntriesDoNotExpire() {
    cache.reloadAll();
    clock.advance(Duration.ofDays(30));
    assertThat(cache.flag("orders", "new-checkout")).contains(true);
    verify(queries, never()).findEnabled(anyString(), anyString());
  }

  @Test
  void keysOutsideTheRegexAreUnknownWithoutAQuery() {
    assertThat(cache.flag("Orders", "x1")).isEmpty();
    assertThat(cache.flag("orders", "a")).isEmpty();
    assertThat(cache.group("has space")).isEmpty();
    verify(queries, never()).findEnabled(anyString(), anyString());
    verify(queries, never()).findGroup(anyString());
  }

  @Test
  @Tag("AC-CACHE-2")
  void evictedEntryReadTwiceConcurrentlyRunsOneQueryAndTheSecondReadIsAHit() throws Exception {
    cache.reloadAll();
    // Evict one entry, as a size eviction would.
    caffeine("flagCache").invalidate("orders.new-checkout");
    CountDownLatch inQuery = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(queries.findEnabled("orders", "new-checkout"))
        .thenAnswer(
            inv -> {
              inQuery.countDown();
              release.await(10, TimeUnit.SECONDS);
              return Optional.of(true);
            });
    double hitsBefore = hits("flagCache");
    double missesBefore = misses("flagCache");
    ExecutorService pool = Executors.newFixedThreadPool(2);
    Future<Optional<Boolean>> first = pool.submit(() -> cache.flag("orders", "new-checkout"));
    assertThat(inQuery.await(10, TimeUnit.SECONDS)).isTrue();
    Thread[] second = new Thread[1];
    Future<Optional<Boolean>> secondRead =
        pool.submit(
            () -> {
              second[0] = Thread.currentThread();
              return cache.flag("orders", "new-checkout");
            });
    // Release the loader only when the second read waits for the same in-flight load.
    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                second[0] != null
                    && (second[0].getState() == Thread.State.WAITING
                        || second[0].getState() == Thread.State.BLOCKED
                        || second[0].getState() == Thread.State.TIMED_WAITING));
    release.countDown();
    assertThat(first.get(10, TimeUnit.SECONDS)).contains(true);
    assertThat(secondRead.get(10, TimeUnit.SECONDS)).contains(true);
    pool.shutdown();
    verify(queries, times(1)).findEnabled("orders", "new-checkout");
    assertThat(misses("flagCache") - missesBefore).isEqualTo(1);
    assertThat(hits("flagCache") - hitsBefore).isEqualTo(1);
  }

  /** The Caffeine cache behind one of the three caches (read from the field in the test only). */
  @SuppressWarnings("unchecked") // reason: the three caches are keyed by String
  private com.github.benmanes.caffeine.cache.Cache<String, ?> caffeine(String field) {
    try {
      var f = FlagCacheService.class.getDeclaredField(field);
      f.setAccessible(true);
      return (com.github.benmanes.caffeine.cache.Cache<String, ?>) f.get(cache);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private double misses(String name) {
    return meters
        .get("cache.gets")
        .tag("cache", name)
        .tag("result", "miss")
        .functionCounter()
        .count();
  }

  private double hits(String name) {
    return meters
        .get("cache.gets")
        .tag("cache", name)
        .tag("result", "hit")
        .functionCounter()
        .count();
  }

  @Test
  @Tag("AC-CACHE-4")
  void flagWritesUpdateAllThreeCachesWithoutQueries() {
    cache.reloadAll();
    cache.onChange(new FlagsChangedEvent.FlagChanged("orders", "new-checkout", false));
    cache.onChange(new FlagsChangedEvent.FlagChanged("orders", "brand-new", true));
    assertThat(cache.flag("orders", "new-checkout")).contains(false);
    assertThat(cache.flag("orders", "brand-new")).contains(true);
    assertThat(cache.group("orders"))
        .contains(Map.of("new-checkout", false, "split-payments", false, "brand-new", true));
    assertThat(cache.all())
        .containsEntry("orders.new-checkout", false)
        .containsEntry("orders.brand-new", true);

    cache.onChange(new FlagsChangedEvent.FlagDeleted("orders", "split-payments"));
    assertThat(cache.flag("orders", "split-payments")).isEmpty();
    assertThat(cache.group("orders").orElseThrow()).doesNotContainKey("split-payments");
    assertThat(cache.all()).doesNotContainKey("orders.split-payments");

    verify(queries, never()).findEnabled(anyString(), anyString());
    verify(queries, never()).findGroup(anyString());
    verify(queries, times(1)).findAllRows();
    assertThat(cache.revision()).isEqualTo(45);
  }

  @Test
  @Tag("AC-CACHE-4")
  void groupWritesUpdateTheCaches() {
    cache.reloadAll();
    cache.onChange(new FlagsChangedEvent.GroupCreated("payments"));
    assertThat(cache.group("payments")).contains(Map.of());

    cache.onChange(
        new FlagsChangedEvent.GroupDeleted("orders", List.of("new-checkout", "split-payments")));
    assertThat(cache.group("orders")).isEmpty();
    assertThat(cache.flag("orders", "new-checkout")).isEmpty();
    assertThat(cache.flag("orders", "split-payments")).isEmpty();
    assertThat(cache.all()).isEmpty();

    long before = cache.revision();
    cache.onChange(new FlagsChangedEvent.GroupEdited("payments"));
    assertThat(cache.revision()).isEqualTo(before + 1);
    assertThat(cache.group("payments")).contains(Map.of());
    verify(queries, never()).findGroup(anyString());
    verify(queries, never()).findEnabled(anyString(), anyString());
  }

  @Test
  void writeToAGroupThatIsNotCachedDropsTheEntry() {
    when(queries.findGroup("orders")).thenReturn(List.of(row("orders", "x1", true)));
    cache.onChange(new FlagsChangedEvent.FlagChanged("orders", "x1", true));
    assertThat(cache.group("orders")).contains(Map.of("x1", true));
    verify(queries, times(1)).findGroup("orders");
  }

  @Test
  void failedUpdateInvalidatesAndStillMovesTheRevision(CapturedOutput output) {
    cache.reloadAll();
    long before = cache.revision();
    cache.onChange(new FlagsChangedEvent.FlagChanged("orders", null, true));
    assertThat(cache.revision()).isEqualTo(before + 1);
    assertThat(output.getOut()).contains("WARN").contains("Cache update failed");
    cache.all();
    cache.group("orders");
    verify(queries, times(2)).findAllRows();
    verify(queries, times(1)).findGroup("orders");
  }

  @Test
  @Tag("AC-CACHE-7")
  void reconcileFixesDriftAndMovesTheRevision() {
    cache.reloadAll();
    when(queries.findAllRows())
        .thenReturn(
            List.of(
                row("orders", "new-checkout", false),
                row("orders", "split-payments", false),
                row("empty", null, null)));
    long before = cache.revision();

    List<FlagCacheService.Difference> diffs = cache.reconcileWithDatabase();

    assertThat(diffs)
        .extracting(FlagCacheService.Difference::key)
        .containsExactlyInAnyOrder(
            "orders.new-checkout", "group:orders", "all:orders.new-checkout");
    assertThat(cache.flag("orders", "new-checkout")).contains(false);
    assertThat(cache.group("orders").orElseThrow()).containsEntry("new-checkout", false);
    assertThat(cache.all()).containsEntry("orders.new-checkout", false);
    assertThat(cache.revision()).isEqualTo(before + 1);
  }

  @Test
  @Tag("AC-CACHE-8")
  void reconcileWithoutDriftChangesNothing() {
    cache.reloadAll();
    long before = cache.revision();
    assertThat(cache.reconcileWithDatabase()).isEmpty();
    assertThat(cache.revision()).isEqualTo(before);
  }

  @Test
  void reconcileAlsoFixesNegativeEntriesThatNowExist() {
    when(queries.findEnabled("orders", "late")).thenReturn(Optional.empty());
    assertThat(cache.flag("orders", "late")).isEmpty();
    when(queries.findAllRows()).thenReturn(List.of(row("orders", "late", true)));
    assertThat(cache.reconcileWithDatabase())
        .extracting(FlagCacheService.Difference::key)
        .contains("orders.late");
    assertThat(cache.flag("orders", "late")).contains(true);
  }

  @Test
  void metricsAreExportedPerCache() {
    cache.reloadAll();
    cache.flag("orders", "new-checkout");
    cache.group("orders");
    cache.all();
    when(queries.findGroup("other")).thenReturn(List.of());
    cache.group("other");
    assertThat(hits("flagCache")).isEqualTo(1);
    assertThat(misses("flagCache")).isZero();
    assertThat(hits("groupCache")).isEqualTo(1);
    assertThat(misses("groupCache")).isEqualTo(1);
    assertThat(hits("allFlagsCache")).isEqualTo(1);
    assertThat(misses("allFlagsCache")).isZero();
  }

  @Test
  void eachCacheHoldsAtMost100000Entries() {
    for (String name : new String[] {"flagCache", "groupCache", "allFlagsCache"}) {
      assertThat(caffeine(name).policy().eviction().orElseThrow().getMaximum()).isEqualTo(100_000);
    }
  }

  @Test
  void writeWhileAllFlagsIsNotCachedDropsAnyInFlightLoad() {
    cache.onChange(new FlagsChangedEvent.FlagChanged("orders", "x1", true));
    cache.all();
    verify(queries, times(1)).findAllRows();
  }

  @Test
  void revisionStartsAtZeroWithoutAuditEvents() {
    when(queries.loadMaxAuditId()).thenReturn(Optional.empty());
    cache.reloadAll();
    assertThat(cache.revision()).isZero();
  }
}
