package com.example.featureflags.group;

import static com.example.featureflags.support.Entities.saved;
import static com.example.featureflags.support.Ids.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.featureflags.audit.AuditAction;
import com.example.featureflags.audit.AuditService;
import com.example.featureflags.common.DuplicateKeyException;
import com.example.featureflags.common.FieldValidationException;
import com.example.featureflags.common.FlagsChangedEvent;
import com.example.featureflags.common.LimitReachedException;
import com.example.featureflags.common.NotFoundException;
import com.example.featureflags.common.UuidV7;
import com.example.featureflags.common.VersionConflictException;
import com.example.featureflags.flag.Flag;
import com.example.featureflags.flag.FlagService;
import com.example.featureflags.support.Entities;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

/** Spec 6.1, 4.1: group service rules (unit, mocked repositories). */
class GroupServiceTest {

  static final UUID G = id(1);

  FlagGroupRepository groups = mock(FlagGroupRepository.class);
  FlagService flags = mock(FlagService.class);
  AuditService audit = mock(AuditService.class);
  ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  JdbcTemplate jdbc = mock(JdbcTemplate.class);
  GroupService service =
      new GroupService(
          groups,
          flags,
          audit,
          events,
          new UuidV7(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
          jdbc);

  FlagGroup orders;

  @BeforeEach
  void setUp() {
    // TA-10: events must carry the id of the audit event written in the same transaction.
    when(audit.record(any(), any(), any())).thenReturn(77L);
    orders = saved(new FlagGroup(G, "orders", "Orders", null), 2);
    when(groups.findById(G)).thenReturn(Optional.of(orders));
    when(groups.findForUpdate(G)).thenReturn(Optional.of(orders));
    when(groups.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              FlagGroup g = inv.getArgument(0);
              return g.getVersion() == null ? saved(g, 0) : g;
            });
  }

  private static Map<String, Object> change(Object from, Object to) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("from", from);
    m.put("to", to);
    return m;
  }

  private static Flag flag(String key) {
    return new Flag(
        id(5), G, key, "orders." + key, null, true, Entities.T0, "admin", Entities.T0, "admin", 0);
  }

  @Test
  void createLocksChecksAuditsAndPublishes() {
    Group g = service.create(new CreateGroupRequest("payments", " Payments ", null));

    assertThat(g.key()).isEqualTo("payments");
    assertThat(g.name()).isEqualTo("Payments");
    assertThat(g.id().version()).isEqualTo(7);
    verify(jdbc).queryForList("SELECT pg_advisory_xact_lock(?)", GroupService.CREATE_LOCK);
    verify(audit).record(AuditAction.GROUP_CREATED, "payments", Map.of("name", "Payments"));
    verify(events).publishEvent(new FlagsChangedEvent.GroupCreated("payments", 77L));
  }

  @Test
  void createDuplicateAndLimit() {
    when(groups.existsByKey("orders")).thenReturn(true);
    assertThatThrownBy(() -> service.create(new CreateGroupRequest("orders", "O", null)))
        .isInstanceOf(DuplicateKeyException.class);
    when(groups.count()).thenReturn(1000L);
    assertThatThrownBy(() -> service.create(new CreateGroupRequest("new", "N", null)))
        .isInstanceOf(LimitReachedException.class);
    when(groups.count()).thenReturn(999L);
    assertThat(service.create(new CreateGroupRequest("new", "N", null)).key()).isEqualTo("new");
  }

  @Test
  void listFiltersSortsAndCounts() {
    FlagGroup a = saved(new FlagGroup(id(2), "billing", "Zeta", null), 0);
    FlagGroup b = saved(new FlagGroup(id(3), "accounts", "alpha", "d"), 0);
    Entities.set(b, "updatedAt", Entities.T0.plusSeconds(5));
    when(groups.findAll()).thenReturn(List.of(orders, a, b));
    when(flags.countsByGroup()).thenReturn(Map.of(G, new long[] {3, 1}));

    List<GroupSummary> byKey = service.list(null, null);
    assertThat(byKey)
        .extracting(GroupSummary::key)
        .containsExactly("accounts", "billing", "orders");
    assertThat(byKey.get(2).flagCount()).isEqualTo(3);
    assertThat(byKey.get(2).enabledCount()).isEqualTo(1);
    assertThat(byKey.get(0).flagCount()).isZero();
    assertThat(service.list("", "name"))
        .extracting(GroupSummary::key)
        .containsExactly("accounts", "orders", "billing");
    assertThat(service.list(null, "updatedAt"))
        .extracting(GroupSummary::key)
        .containsExactly("accounts", "billing", "orders");
    assertThat(service.list("ZET", "key")).extracting(GroupSummary::key).containsExactly("billing");
    assertThat(service.list("ount", "key"))
        .extracting(GroupSummary::key)
        .containsExactly("accounts");
    assertThatThrownBy(() -> service.list(null, "KEY"))
        .isInstanceOf(FieldValidationException.class);
  }

  @Test
  void getReturnsTheDetailWithItsFlags() {
    when(flags.flagsOf(G, "orders")).thenReturn(List.of(flag("a"), flag("b")));
    GroupDetail d = service.get(G);
    assertThat(d.flags()).extracting(Flag::key).containsExactly("a", "b");
    assertThat(d.version()).isEqualTo(2);
    assertThat(d.createdBy()).isEqualTo("admin");
    when(groups.findById(id(9))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(id(9))).isInstanceOf(NotFoundException.class);
  }

  @Test
  void updateRecordsEachChangedField() {
    Group g =
        service.update(G, new UpdateGroupRequest(Optional.of("Shop"), Optional.of("text"), 2L));
    assertThat(g.name()).isEqualTo("Shop");
    assertThat(g.description()).isEqualTo("text");
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("name", change("Orders", "Shop"));
    details.put("description", change(null, "text"));
    verify(audit).record(AuditAction.GROUP_UPDATED, "orders", details);
    verify(events).publishEvent(new FlagsChangedEvent.GroupEdited("orders", 77L));
  }

  @Test
  void updateClearingTheDescription() {
    orders.describe("old");
    service.update(G, new UpdateGroupRequest(null, Optional.empty(), 2L));
    assertThat(orders.getDescription()).isNull();
    verify(audit)
        .record(AuditAction.GROUP_UPDATED, "orders", Map.of("description", change("old", null)));
  }

  @Test
  void updateVersionAndNoOp() {
    assertThatThrownBy(
            () -> service.update(G, new UpdateGroupRequest(Optional.of("Shop"), null, 1L)))
        .isInstanceOf(VersionConflictException.class);
    assertThatThrownBy(() -> service.update(G, new UpdateGroupRequest(null, null, 3L)))
        .isInstanceOf(VersionConflictException.class);
    Group same =
        service.update(G, new UpdateGroupRequest(Optional.of("Orders"), Optional.empty(), 2L));
    assertThat(same.version()).isEqualTo(2);
    verify(groups, never()).saveAndFlush(any());
    verifyNoInteractions(audit, events);
  }

  @Test
  void deleteAuditsFullKeysSortedAndPublishesFlagKeys() {
    when(flags.flagsOf(G, "orders"))
        .thenReturn(List.of(flag("new-checkout"), flag("split-payments")));
    service.delete(G);
    verify(groups).delete(orders);
    verify(audit)
        .record(
            AuditAction.GROUP_DELETED,
            "orders",
            Map.of("deletedFlags", List.of("orders.new-checkout", "orders.split-payments")));
    verify(events)
        .publishEvent(
            new FlagsChangedEvent.GroupDeleted(
                "orders", List.of("new-checkout", "split-payments"), 77L));
  }

  @Test
  void deleteUnknownIsNotFound() {
    when(groups.findForUpdate(id(9))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete(id(9))).isInstanceOf(NotFoundException.class);
    verify(audit, never()).record(eq(AuditAction.GROUP_DELETED), anyString(), any());
  }

  @Test
  void lookupAdapterMapsGroups() {
    GroupLookupAdapter adapter = new GroupLookupAdapter(groups);
    assertThat(adapter.lock(G)).contains(new com.example.featureflags.flag.GroupRef(G, "orders"));
    when(groups.findForUpdate(id(9))).thenReturn(Optional.empty());
    assertThat(adapter.lock(id(9))).isEmpty();
  }
}
