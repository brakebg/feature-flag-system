package com.example.featureflags.support;

import java.util.UUID;

/** Fixed test ids (spec 12.4 step 6: no random data without a fixed seed). */
public final class Ids {

  private Ids() {}

  /** A stable UUIDv7-shaped id for a test-local number. */
  public static UUID id(int n) {
    return UUID.fromString(String.format("0191f0c2-0000-7000-8000-%012d", n));
  }
}
