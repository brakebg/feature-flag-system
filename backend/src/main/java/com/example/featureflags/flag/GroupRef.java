package com.example.featureflags.flag;

import java.util.UUID;

/** The parts of a group that the flag package needs. */
public record GroupRef(UUID id, String key) {}
