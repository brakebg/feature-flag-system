package com.example.featureflags.flag;

import com.example.featureflags.common.CodePointLength;
import com.example.featureflags.common.Keys;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Spec 6.2 {@code CreateFlagRequest}; {@code enabled} defaults to false. */
public record CreateFlagRequest(
    @NotNull @Pattern(regexp = Keys.REGEX, message = Keys.MESSAGE) String key,
    @CodePointLength(max = 500) String description,
    Boolean enabled) {

  public CreateFlagRequest {
    description = Keys.description(description);
    enabled = Boolean.TRUE.equals(enabled);
  }
}
