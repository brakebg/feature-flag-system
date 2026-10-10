package com.example.featureflags.common;

/** Spec 5.1, 5.4: token audiences and scopes. */
public final class Audiences {

  public static final String ADMIN = "feature-flag-admin";
  public static final String SERVICE = "feature-flag-service";
  public static final String SCOPE_ADMIN = "admin";
  public static final String SCOPE_FLAGS_READ = "flags:read";

  private Audiences() {}
}
