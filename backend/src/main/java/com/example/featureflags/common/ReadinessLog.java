package com.example.featureflags.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Logs every readiness change (spec 9.3 operations; gate 11 checks the order at startup). */
@Component
public class ReadinessLog {

  private static final Logger log = LoggerFactory.getLogger(ReadinessLog.class);

  @EventListener
  public void onChange(AvailabilityChangeEvent<ReadinessState> event) {
    log.info("Readiness state: {}", event.getState());
  }
}
