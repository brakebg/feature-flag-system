package com.example.featureflags.common;

import java.util.List;

/**
 * Spec 3, 7.2: published by every admin write that changes data. The evaluation cache applies it
 * after the transaction commits. Keys only: the Evaluation API never uses names or descriptions.
 */
public sealed interface FlagsChangedEvent {

  /** A flag was created, updated or toggled; {@code enabled} is its new value. */
  record FlagChanged(String groupKey, String flagKey, boolean enabled)
      implements FlagsChangedEvent {}

  record FlagDeleted(String groupKey, String flagKey) implements FlagsChangedEvent {}

  record GroupCreated(String groupKey) implements FlagsChangedEvent {}

  /** A group and all its flags (by flag key) were deleted. */
  record GroupDeleted(String groupKey, List<String> flagKeys) implements FlagsChangedEvent {
    public GroupDeleted {
      flagKeys = List.copyOf(flagKeys);
    }
  }

  /** Name or description changed: no cache entry changes, but the revision moves on. */
  record GroupEdited(String groupKey) implements FlagsChangedEvent {}
}
