package com.example.featureflags.audit;

import com.example.featureflags.common.SecurityAuditor;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spec 4.1: writes audit events in the transaction of the change, and reads them for {@code GET
 * /audit} (newest first, then highest id).
 */
@Service
@Transactional
public class AuditService {

  private final AuditEventRepository events;
  private final SecurityAuditor auditor;
  private final Clock clock;
  private final MeterRegistry meters;

  public AuditService(
      AuditEventRepository events, SecurityAuditor auditor, Clock clock, MeterRegistry meters) {
    this.events = events;
    this.auditor = auditor;
    this.clock = clock;
    this.meters = meters;
  }

  /** Must run inside the transaction of the change (spec 9.5). */
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(AuditAction action, String targetKey, Map<String, Object> details) {
    String actor =
        auditor
            .getCurrentAuditor()
            .orElseThrow(() -> new IllegalStateException("audit write without a signed-in user"));
    events.save(
        new AuditEvent(
            clock.instant().truncatedTo(ChronoUnit.MICROS), actor, action, targetKey, details));
    meters.counter("ff_admin_writes_total", "action", action.name()).increment();
  }

  @Transactional(readOnly = true)
  public Page<AuditEventView> page(int page, int size, String targetKey) {
    PageRequest request = PageRequest.of(page, size);
    boolean all = targetKey == null || targetKey.isEmpty();
    if ((long) page * size > Integer.MAX_VALUE) {
      // Far past the end (spec 6.1: 200 with an empty content); JPA offsets are ints.
      long total = all ? events.count() : events.countByPrefix(likePrefix(targetKey));
      return new PageImpl<AuditEventView>(List.of(), request, total);
    }
    Page<AuditEvent> result =
        all
            ? events.findNewestFirst(request)
            : events.findByPrefixNewestFirst(likePrefix(targetKey), request);
    return result.map(AuditEventView::of);
  }

  /** A LIKE pattern in which {@code %}, {@code _} and {@code \} are literal characters. */
  static String likePrefix(String prefix) {
    return prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }
}
