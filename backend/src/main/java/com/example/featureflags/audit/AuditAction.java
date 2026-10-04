package com.example.featureflags.audit;

/** Spec 4.1 {@code audit_event.action}. */
public enum AuditAction {
  GROUP_CREATED,
  GROUP_UPDATED,
  GROUP_DELETED,
  FLAG_CREATED,
  FLAG_UPDATED,
  FLAG_TOGGLED,
  FLAG_DELETED
}
