package com.example.featureflags.auth;

import java.time.Instant;

/** Spec 5.2 login response. */
public record LoginResponse(String accessToken, Instant expiresAt, String username) {}
