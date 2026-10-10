package com.example.featureflags.common;

import org.springframework.boot.health.application.AvailabilityStateHealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spec 9.3: {@code /actuator/health/readiness} is 200 {@code {"status":"UP"}} or 503 {@code
 * {"status":"DOWN"}}. Spring Boot reports refused traffic as {@code OUT_OF_SERVICE}; here it is
 * {@code DOWN}.
 */
@Configuration(proxyBeanMethods = false)
public class ReadinessConfig {

  @Bean("readinessStateHealthIndicator")
  AvailabilityStateHealthIndicator readinessStateHealthIndicator(
      ApplicationAvailability availability) {
    return new AvailabilityStateHealthIndicator(
        availability,
        ReadinessState.class,
        mappings -> {
          mappings.add(ReadinessState.ACCEPTING_TRAFFIC, Status.UP);
          mappings.add(ReadinessState.REFUSING_TRAFFIC, Status.DOWN);
        });
  }
}
