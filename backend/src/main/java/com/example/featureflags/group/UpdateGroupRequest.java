package com.example.featureflags.group;

import com.example.featureflags.common.CodePointLength;
import com.example.featureflags.common.Keys;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Optional;

/**
 * Spec 6.2 {@code UpdateGroupRequest}, a partial update (6.1). A {@code null} component means the
 * field was omitted (unchanged); {@code Optional.empty()} means it was sent as {@code null}. A
 * {@code key} in the body is ignored (4.2).
 */
public record UpdateGroupRequest(
    Optional<@NotBlank @CodePointLength(min = 1, max = 100) String> name,
    Optional<@CodePointLength(max = 500) String> description,
    @NotNull @PositiveOrZero Long version) {

  public UpdateGroupRequest {
    name = name == null ? null : name.map(Keys::trim);
    description = description == null ? null : description.map(Keys::description);
  }
}
