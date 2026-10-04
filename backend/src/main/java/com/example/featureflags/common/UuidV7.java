package com.example.featureflags.common;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Generates UUIDv7 ids (RFC 9562): 48-bit Unix millis, version 7, variant 2, random rest. */
@Component
public class UuidV7 {

  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public UuidV7(Clock clock) {
    this.clock = clock;
  }

  public UUID next() {
    long millis = clock.millis();
    long msb = (millis << 16) | 0x7000L | (random.nextInt() & 0x0FFFL);
    long lsb = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
    return new UUID(msb, lsb);
  }
}
