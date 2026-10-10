package com.example.featureflags.group;

import com.example.featureflags.common.FieldValidationException;
import java.util.Comparator;

/** Spec 6.1 {@code GET /groups?sort=}: key and name ascending, updatedAt newest first. */
enum GroupSort {
  KEY(Comparator.comparing(GroupSummary::key)),
  NAME(Comparator.comparing(GroupSummary::name, String.CASE_INSENSITIVE_ORDER)),
  UPDATED_AT(Comparator.comparing(GroupSummary::updatedAt).reversed());

  /** Ties are ordered by key ascending (code-point order). */
  final Comparator<GroupSummary> order;

  GroupSort(Comparator<GroupSummary> primary) {
    this.order = primary.thenComparing(GroupSummary::key);
  }

  static GroupSort parse(String value) {
    if (value == null) {
      return KEY;
    }
    return switch (value) {
      case "key" -> KEY;
      case "name" -> NAME;
      case "updatedAt" -> UPDATED_AT;
      default -> throw new FieldValidationException("sort", "must be key, name or updatedAt");
    };
  }
}
