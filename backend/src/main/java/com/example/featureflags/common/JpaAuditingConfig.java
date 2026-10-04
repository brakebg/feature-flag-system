package com.example.featureflags.common;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Spec 4.1: JPA auditing fills created/updated at/by. Timestamps come from the injected {@link
 * Clock}, cut to microseconds (the precision of PostgreSQL TIMESTAMPTZ).
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

  @Bean
  DateTimeProvider auditingDateTimeProvider(Clock clock) {
    return () -> Optional.of(clock.instant().truncatedTo(ChronoUnit.MICROS));
  }
}
