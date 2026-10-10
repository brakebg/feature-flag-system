package com.example.featureflags.auth;

import java.util.List;

/** Spec 5.1: one consumer system that may use the client credentials flow. */
public record ClientRegistration(String clientId, String clientSecret, List<String> scopes) {

  public ClientRegistration {
    scopes = scopes == null ? List.of() : List.copyOf(scopes);
  }

  /** The secret is never part of {@code toString()} (spec 9.3). */
  @Override
  public String toString() {
    return "ClientRegistration[clientId=" + clientId + ", scopes=" + scopes + "]";
  }
}
