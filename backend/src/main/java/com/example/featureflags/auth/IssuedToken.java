package com.example.featureflags.auth;

import java.time.Instant;

/** A signed JWT with its expiry. */
public record IssuedToken(String value, Instant expiresAt, long ttlSeconds) {}
