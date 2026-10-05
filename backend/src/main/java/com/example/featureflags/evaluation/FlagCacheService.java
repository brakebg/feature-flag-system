package com.example.featureflags.evaluation;

import com.example.featureflags.common.FlagsChangedEvent;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Spec 7.2: the three in-process Caffeine caches of the Evaluation API, the revision, the
 * after-commit write path and the full reload used by warm-up, reconciliation and tests.
 *
 * <p>Readers never lock. Writers are serialised and replace whole immutable maps (copy-on-write),
 * so a reader never sees a half-applied change. Only negative entries expire (30 s); positive
 * entries stay until a write or a reload replaces them.
 */
@Service
public class FlagCacheService {

  static final Duration NEGATIVE_TTL = Duration.ofSeconds(30);
  static final long MAX_SIZE = 100_000;
  static final String ALL = "all";
  private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9-]{1,49}$");
  private static final Logger log = LoggerFactory.getLogger(FlagCacheService.class);

  private final EvaluationQueries queries;
  private final LoadingCache<String, Optional<Boolean>> flagCache;
  private final LoadingCache<String, Optional<Map<String, Boolean>>> groupCache;
  private final LoadingCache<String, Map<String, Boolean>> allFlagsCache;
  private final AtomicLong revision = new AtomicLong();

  /**
   * Serialises all cache writers. A {@link ReentrantLock}, not a monitor: on virtual threads a
   * monitor held during JDBC (warm-up, reconciliation) would pin the carrier thread (BF-5).
   */
  private final ReentrantLock writeLock = new ReentrantLock();

  /**
   * BF-1: the newest change (audit id) applied per key (see {@link #isLate}). After-commit
   * listeners of two writes to one key can run in the opposite order of their commits; an older
   * change that arrives after a newer one only invalidates the affected entries, so the next read
   * loads the committed value. Guarded by {@code writeLock}.
   */
  private final Map<String, Long> appliedSeq = new HashMap<>();

  /** Changes up to this audit id are already in the data loaded by {@link #reloadAll()}. */
  private long loadedSeq;

  public FlagCacheService(EvaluationQueries queries, Clock clock, MeterRegistry meters) {
    this.queries = queries;
    Ticker ticker = () -> clock.millis() * 1_000_000L;
    this.flagCache =
        Caffeine.newBuilder()
            .maximumSize(MAX_SIZE)
            .expireAfter(FlagCacheService.<Boolean>negativeOnly())
            .ticker(ticker)
            .recordStats()
            .build(this::loadFlag);
    this.groupCache =
        Caffeine.newBuilder()
            .maximumSize(MAX_SIZE)
            .expireAfter(FlagCacheService.<Map<String, Boolean>>negativeOnly())
            .ticker(ticker)
            .recordStats()
            .build(this::loadGroup);
    this.allFlagsCache =
        Caffeine.newBuilder().maximumSize(MAX_SIZE).recordStats().build(k -> loadAll());
    CaffeineCacheMetrics.monitor(meters, flagCache, "flagCache");
    CaffeineCacheMetrics.monitor(meters, groupCache, "groupCache");
    CaffeineCacheMetrics.monitor(meters, allFlagsCache, "allFlagsCache");
  }

  private static <V> Expiry<String, Optional<V>> negativeOnly() {
    return new Expiry<>() {
      @Override
      public long expireAfterCreate(String key, Optional<V> value, long now) {
        return value.isPresent() ? Long.MAX_VALUE : NEGATIVE_TTL.toNanos();
      }

      @Override
      public long expireAfterUpdate(String key, Optional<V> value, long now, long current) {
        return expireAfterCreate(key, value, now);
      }

      @Override
      public long expireAfterRead(String key, Optional<V> value, long now, long current) {
        return current;
      }
    };
  }

  // ------------------------------------------------------------------ read path

  public long revision() {
    return revision.get();
  }

  /** {@code GET /evaluate/flags/{group}/{flag}}; empty = unknown (404). */
  public Optional<Boolean> flag(String groupKey, String flagKey) {
    if (!KEY.matcher(groupKey).matches() || !KEY.matcher(flagKey).matches()) {
      return Optional.empty();
    }
    return flagCache.get(groupKey + "." + flagKey);
  }

  /** {@code GET /evaluate/groups/{group}}; empty = unknown (404). */
  public Optional<Map<String, Boolean>> group(String groupKey) {
    if (!KEY.matcher(groupKey).matches()) {
      return Optional.empty();
    }
    return groupCache.get(groupKey);
  }

  /** {@code GET /evaluate/flags}. */
  public Map<String, Boolean> all() {
    return allFlagsCache.get(ALL);
  }

  // ------------------------------------------------------------------ reload (warm-up, tests)

  /**
   * Loads every group and flag and replaces all cache entries (spec 7.2 warm-up, 11.5 test reset).
   * The revision starts from {@code max(audit_event.id)}.
   */
  public void reloadAll() {
    writeLock.lock();
    try {
      Snapshot db = loadSnapshot();
      flagCache.invalidateAll();
      groupCache.invalidateAll();
      allFlagsCache.invalidateAll();
      db.all().forEach((k, v) -> flagCache.put(k, Optional.of(v)));
      db.groups().forEach((k, v) -> groupCache.put(k, Optional.of(v)));
      allFlagsCache.put(ALL, db.all());
      long maxAuditId = queries.loadMaxAuditId().orElse(0L);
      revision.set(maxAuditId);
      appliedSeq.clear();
      loadedSeq = maxAuditId;
    } finally {
      writeLock.unlock();
    }
  }

  // ------------------------------------------------------------------ write path

  /**
   * Spec 7.2 write-through: applied after the admin transaction commits, before the response. A
   * rolled-back write never gets here. If applying fails, the affected entries and all-flags are
   * invalidated (WARN) so the next read reloads them. Each applied change increments the revision,
   * after the cache holds the new values.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onChange(FlagsChangedEvent event) {
    writeLock.lock();
    try {
      try {
        if (isLate(event)) {
          log.debug("Change {} arrived after a newer one; invalidating affected entries", event);
          invalidate(event);
        } else {
          apply(event);
        }
      } catch (RuntimeException e) {
        log.warn("Cache update failed for {}; invalidating affected entries", event, e);
        try {
          invalidate(event);
        } catch (RuntimeException again) {
          log.warn("Cache invalidation failed; dropping all cache entries", again);
          flagCache.invalidateAll();
          groupCache.invalidateAll();
          allFlagsCache.invalidateAll();
        }
      } finally {
        // The change is committed: the ETag must move on even if the cache update failed.
        revision.incrementAndGet();
      }
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * True if a newer change that touches the same entries was already applied (BF-1). Keys: "f:" +
   * full flag key; "gs:" + group key for group create and delete; "ga:" + group key for any change
   * in the group. Changes to different flags of one group may apply in any order.
   */
  private boolean isLate(FlagsChangedEvent event) {
    List<String> check = new ArrayList<>();
    List<String> record = new ArrayList<>();
    switch (event) {
      case FlagsChangedEvent.FlagChanged c -> flagKeys(c.groupKey(), c.flagKey(), check, record);
      case FlagsChangedEvent.FlagDeleted d -> flagKeys(d.groupKey(), d.flagKey(), check, record);
      case FlagsChangedEvent.GroupCreated g -> groupKeys(g.groupKey(), List.of(), check, record);
      case FlagsChangedEvent.GroupDeleted g -> groupKeys(g.groupKey(), g.flagKeys(), check, record);
      case FlagsChangedEvent.GroupEdited g -> {
        // No cache entry changes.
      }
    }
    boolean late =
        event.seq() <= loadedSeq
            || check.stream().anyMatch(k -> appliedSeq.getOrDefault(k, 0L) > event.seq());
    record.forEach(k -> appliedSeq.merge(k, event.seq(), Math::max));
    return late;
  }

  private static void flagKeys(String group, String flag, List<String> check, List<String> record) {
    check.addAll(List.of("f:" + group + "." + flag, "gs:" + group));
    record.addAll(List.of("f:" + group + "." + flag, "ga:" + group));
  }

  private static void groupKeys(
      String group, List<String> flags, List<String> check, List<String> record) {
    check.addAll(List.of("gs:" + group, "ga:" + group));
    record.addAll(List.of("gs:" + group, "ga:" + group));
    flags.forEach(
        f -> {
          check.add("f:" + group + "." + f);
          record.add("f:" + group + "." + f);
        });
  }

  private void apply(FlagsChangedEvent event) {
    switch (event) {
      case FlagsChangedEvent.FlagChanged c -> {
        String full = c.groupKey() + "." + c.flagKey();
        flagCache.put(full, Optional.of(c.enabled()));
        updateGroup(c.groupKey(), m -> m.put(c.flagKey(), c.enabled()));
        updateAll(m -> m.put(full, c.enabled()));
      }
      case FlagsChangedEvent.FlagDeleted d -> {
        String full = d.groupKey() + "." + d.flagKey();
        flagCache.put(full, Optional.empty());
        updateGroup(d.groupKey(), m -> m.remove(d.flagKey()));
        updateAll(m -> m.remove(full));
      }
      case FlagsChangedEvent.GroupCreated g -> groupCache.put(g.groupKey(), Optional.of(Map.of()));
      case FlagsChangedEvent.GroupDeleted g -> {
        groupCache.put(g.groupKey(), Optional.empty());
        Set<String> fullKeys = new HashSet<>();
        for (String k : g.flagKeys()) {
          String full = g.groupKey() + "." + k;
          fullKeys.add(full);
          flagCache.put(full, Optional.empty());
        }
        updateAll(m -> m.keySet().removeAll(fullKeys));
      }
      case FlagsChangedEvent.GroupEdited g -> {
        // Names and descriptions are not cached (7.2); only the revision moves on.
      }
    }
  }

  private void invalidate(FlagsChangedEvent event) {
    allFlagsCache.invalidateAll();
    switch (event) {
      case FlagsChangedEvent.FlagChanged c -> {
        flagCache.invalidate(c.groupKey() + "." + c.flagKey());
        groupCache.invalidate(c.groupKey());
      }
      case FlagsChangedEvent.FlagDeleted d -> {
        flagCache.invalidate(d.groupKey() + "." + d.flagKey());
        groupCache.invalidate(d.groupKey());
      }
      case FlagsChangedEvent.GroupCreated g -> groupCache.invalidate(g.groupKey());
      case FlagsChangedEvent.GroupDeleted g -> {
        groupCache.invalidate(g.groupKey());
        g.flagKeys().forEach(k -> flagCache.invalidate(g.groupKey() + "." + k));
      }
      case FlagsChangedEvent.GroupEdited g -> {}
    }
  }

  /** Copy-on-write change of a cached group; a missing or negative entry is dropped instead. */
  private void updateGroup(
      String groupKey, java.util.function.Consumer<Map<String, Boolean>> change) {
    Optional<Map<String, Boolean>> current = groupCache.getIfPresent(groupKey);
    if (current == null || current.isEmpty()) {
      groupCache.invalidate(groupKey);
      return;
    }
    Map<String, Boolean> copy = new TreeMap<>(current.get());
    change.accept(copy);
    groupCache.put(groupKey, Optional.of(Map.copyOf(copy)));
  }

  private void updateAll(java.util.function.Consumer<Map<String, Boolean>> change) {
    Map<String, Boolean> current = allFlagsCache.getIfPresent(ALL);
    if (current == null) {
      // A load may be running with data from before this commit: drop it, reload on next read.
      allFlagsCache.invalidate(ALL);
      return;
    }
    Map<String, Boolean> copy = new TreeMap<>(current);
    change.accept(copy);
    allFlagsCache.put(ALL, Map.copyOf(copy));
  }

  // ------------------------------------------------------------------ reconciliation

  /**
   * Spec 7.2: loads a database snapshot and compares it with the cache under the writer lock,
   * fixing each difference with the same copy-on-write updates. Returns the differences (key,
   * cached, database). Known limit (SF-M1): a write that commits while the snapshot loads, but
   * whose after-commit update still waits for the lock, shows as a difference; it is fixed to the
   * same committed value the waiting update then applies again.
   */
  List<Difference> reconcileWithDatabase() {
    writeLock.lock();
    try {
      return reconcile(loadSnapshot());
    } finally {
      writeLock.unlock();
    }
  }

  List<Difference> reconcile(Snapshot db) {
    writeLock.lock();
    try {
      List<Difference> diffs = new java.util.ArrayList<>();
      Map<String, Boolean> cachedAll = allFlagsCache.getIfPresent(ALL);
      if (cachedAll != null && !cachedAll.equals(db.all())) {
        Set<String> keys = new java.util.TreeSet<>(cachedAll.keySet());
        keys.addAll(db.all().keySet());
        for (String k : keys) {
          if (!java.util.Objects.equals(cachedAll.get(k), db.all().get(k))) {
            diffs.add(new Difference("all:" + k, cachedAll.get(k), db.all().get(k)));
          }
        }
        allFlagsCache.put(ALL, db.all());
      }
      for (Map.Entry<String, Optional<Boolean>> e : Map.copyOf(flagCache.asMap()).entrySet()) {
        Boolean cached = e.getValue().orElse(null);
        Boolean actual = db.all().get(e.getKey());
        if (!java.util.Objects.equals(cached, actual)) {
          diffs.add(new Difference(e.getKey(), cached, actual));
          flagCache.put(e.getKey(), Optional.ofNullable(actual));
        }
      }
      for (Map.Entry<String, Optional<Map<String, Boolean>>> e :
          Map.copyOf(groupCache.asMap()).entrySet()) {
        Map<String, Boolean> cached = e.getValue().orElse(null);
        Map<String, Boolean> actual = db.groups().get(e.getKey());
        if (!java.util.Objects.equals(cached, actual)) {
          diffs.add(new Difference("group:" + e.getKey(), cached, actual));
          groupCache.put(e.getKey(), Optional.ofNullable(actual));
        }
      }
      if (!diffs.isEmpty()) {
        revision.incrementAndGet();
      }
      return diffs;
    } finally {
      writeLock.unlock();
    }
  }

  /** One cache entry that differed from the database. */
  record Difference(String key, Object cached, Object database) {}

  // ------------------------------------------------------------------ loaders (database)

  Optional<Boolean> loadFlag(String fullKey) {
    int dot = fullKey.indexOf('.');
    return queries.findEnabled(fullKey.substring(0, dot), fullKey.substring(dot + 1));
  }

  Optional<Map<String, Boolean>> loadGroup(String groupKey) {
    List<EvaluationQueries.Row> rows = queries.findGroup(groupKey);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    Map<String, Boolean> flags = new TreeMap<>();
    for (EvaluationQueries.Row r : rows) {
      if (r.getFlagKey() != null) {
        flags.put(r.getFlagKey(), r.getEnabled());
      }
    }
    return Optional.of(Map.copyOf(flags));
  }

  Map<String, Boolean> loadAll() {
    return snapshotOf(queries.findAllRows()).all();
  }

  /** Everything in the database, built from one query. */
  Snapshot loadSnapshot() {
    return snapshotOf(queries.findAllRows());
  }

  private static Snapshot snapshotOf(List<EvaluationQueries.Row> rows) {
    Map<String, Map<String, Boolean>> groups = new TreeMap<>();
    Map<String, Boolean> all = new TreeMap<>();
    for (EvaluationQueries.Row r : rows) {
      Map<String, Boolean> g = groups.computeIfAbsent(r.getGroupKey(), k -> new TreeMap<>());
      if (r.getFlagKey() != null) {
        g.put(r.getFlagKey(), r.getEnabled());
        all.put(r.getGroupKey() + "." + r.getFlagKey(), r.getEnabled());
      }
    }
    Map<String, Map<String, Boolean>> frozen = new LinkedHashMap<>();
    groups.forEach((k, v) -> frozen.put(k, Map.copyOf(v)));
    return new Snapshot(Map.copyOf(frozen), Map.copyOf(all));
  }

  /** All groups (group key to flag map) and all flags (full key to value). */
  record Snapshot(Map<String, Map<String, Boolean>> groups, Map<String, Boolean> all) {}
}
