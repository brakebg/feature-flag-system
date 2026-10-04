package com.example.featureflags.group;

import com.example.featureflags.flag.Flag;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Spec 6.2 {@code GroupDetail}: a group with all its flags, sorted by key (code points). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupDetail(
    UUID id,
    String key,
    String name,
    String description,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version,
    List<Flag> flags) {}
