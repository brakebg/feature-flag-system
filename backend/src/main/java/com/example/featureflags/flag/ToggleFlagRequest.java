package com.example.featureflags.flag;

/** Spec 6.2 {@code ToggleFlagRequest}; a missing or null {@code enabled} is malformed (6.1). */
public record ToggleFlagRequest(Boolean enabled) {}
