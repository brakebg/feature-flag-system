package com.example.featureflags.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spec 4.1 {@code audit_event}. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {}
