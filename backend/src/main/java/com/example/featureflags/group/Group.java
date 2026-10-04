package com.example.featureflags.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** Spec 6.2 {@code Group}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Group(
    UUID id,
    String key,
    String name,
    String description,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {

  static Group of(FlagGroup g) {
    return new Group(
        g.getId(),
        g.getKey(),
        g.getName(),
        g.getDescription(),
        g.getCreatedAt(),
        g.getCreatedBy(),
        g.getUpdatedAt(),
        g.getUpdatedBy(),
        g.getVersion());
  }
}
