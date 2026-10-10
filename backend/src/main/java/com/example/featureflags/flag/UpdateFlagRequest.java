package com.example.featureflags.flag;

import com.example.featureflags.common.CodePointLength;
import com.example.featureflags.common.Keys;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Optional;

/**
 * Spec 6.2 {@code UpdateFlagRequest}, a partial update. A {@code null} component means omitted;
 * {@code Optional.empty()} means sent as {@code null} (clears the description; invalid for {@code
 * enabled}, see docs/DECISIONS.md D-011).
 */
public record UpdateFlagRequest(
    Optional<@CodePointLength(max = 500) String> description,
    Optional<@NotNull Boolean> enabled,
    @NotNull @PositiveOrZero Long version) {

  public UpdateFlagRequest {
    description = description == null ? null : description.map(Keys::description);
  }
}
