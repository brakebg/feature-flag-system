package com.example.featureflags.group;

import com.example.featureflags.common.CodePointLength;
import com.example.featureflags.common.Keys;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Spec 6.2 {@code CreateGroupRequest}. Name is trimmed; empty description means none. */
public record CreateGroupRequest(
    @NotNull @Pattern(regexp = Keys.REGEX, message = Keys.MESSAGE) String key,
    @NotBlank @CodePointLength(min = 1, max = 100) String name,
    @CodePointLength(max = 500) String description) {

  public CreateGroupRequest {
    name = Keys.trim(name);
    description = Keys.description(description);
  }
}
