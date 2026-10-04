package com.example.featureflags.flag;

import java.util.Optional;
import java.util.UUID;

/**
 * Port to the group package (implemented there), so that {@code group} may use {@code flag} but not
 * the other way round (no package cycle, spec 11.3 gate 3).
 */
public interface GroupLookup {

  Optional<GroupRef> find(UUID groupId);

  /** Finds the group and locks its row until the transaction ends (flag limit, spec 9.2). */
  Optional<GroupRef> lock(UUID groupId);
}
