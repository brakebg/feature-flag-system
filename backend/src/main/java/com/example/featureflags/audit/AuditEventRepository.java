package com.example.featureflags.audit;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spec 4.1 {@code audit_event}. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

  @Query("select e from AuditEvent e order by e.occurredAt desc, e.id desc")
  Page<AuditEvent> findNewestFirst(Pageable page);

  @Query(
      "select e from AuditEvent e where e.targetKey like :pattern escape '\\'"
          + " order by e.occurredAt desc, e.id desc")
  Page<AuditEvent> findByPrefixNewestFirst(@Param("pattern") String pattern, Pageable page);

  /** Deletes at most {@code limit} events older than {@code cutoff}; returns how many. */
  @Modifying
  @Query(
      value =
          "DELETE FROM audit_event WHERE id IN"
              + " (SELECT id FROM audit_event WHERE occurred_at < :cutoff LIMIT :limit)",
      nativeQuery = true)
  int deleteOlderThan(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
