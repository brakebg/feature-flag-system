package com.example.featureflags.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** Spec 6.2 {@code GroupSummary}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupSummary(
    UUID id,
    String key,
    String name,
    String description,
    long flagCount,
    long enabledCount,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {}
