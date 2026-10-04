package com.example.featureflags.evaluation;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spec 3, 7.2: at startup all groups and flags are loaded into the caches. Readiness stays DOWN
 * until the warm-up has finished; {@code ff_readiness_up} exports the state (alert FFNotReady).
 */
@Component
public class CacheWarmUp implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(CacheWarmUp.class);

  private final FlagCacheService cache;
  private final ApplicationEventPublisher events;

  public CacheWarmUp(
      FlagCacheService cache,
      ApplicationEventPublisher events,
      ApplicationAvailability availability,
      MeterRegistry meters) {
    this.cache = cache;
    this.events = events;
    meters.gauge(
        "ff_readiness_up",
        availability,
        a -> a.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC ? 1 : 0);
  }

  @Override
  public void run(ApplicationArguments args) {
    warmUp();
  }

  public void warmUp() {
    AvailabilityChangeEvent.publish(events, this, ReadinessState.REFUSING_TRAFFIC);
    log.info("readiness DOWN until cache warm-up has finished");
    cache.reloadAll();
    log.info("cache warm-up finished: revision {}", cache.revision());
    AvailabilityChangeEvent.publish(events, this, ReadinessState.ACCEPTING_TRAFFIC);
  }
}
