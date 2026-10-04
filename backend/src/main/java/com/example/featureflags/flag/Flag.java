package com.example.featureflags.flag;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** Spec 6.2 {@code Flag}. {@code fullKey} is {@code <groupKey>.<flagKey>}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Flag(
    UUID id,
    UUID groupId,
    String key,
    String fullKey,
    String description,
    boolean enabled,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {

  static Flag of(FeatureFlag f, String groupKey) {
    return new Flag(
        f.getId(),
        f.getGroupId(),
        f.getKey(),
        groupKey + "." + f.getKey(),
        f.getDescription(),
        f.isEnabled(),
        f.getCreatedAt(),
        f.getCreatedBy(),
        f.getUpdatedAt(),
        f.getUpdatedBy(),
        f.getVersion());
  }
}
