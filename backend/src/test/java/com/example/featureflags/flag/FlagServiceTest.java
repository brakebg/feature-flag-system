package com.example.featureflags.flag;

import static com.example.featureflags.support.Entities.saved;
import static com.example.featureflags.support.Ids.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.featureflags.audit.AuditAction;
import com.example.featureflags.audit.AuditService;
import com.example.featureflags.common.DuplicateKeyException;
import com.example.featureflags.common.FlagsChangedEvent;
import com.example.featureflags.common.LimitReachedException;
import com.example.featureflags.common.NotFoundException;
import com.example.featureflags.common.UuidV7;
import com.example.featureflags.common.VersionConflictException;
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
import org.mockito.invocation.InvocationOnMock;
import org.springframework.context.ApplicationEventPublisher;

/** Spec 6.1, 4.1: flag service rules (unit, mocked repositories). */
class FlagServiceTest {

  static final UUID G = id(1);
  static final UUID F = id(2);
  static final GroupRef ORDERS = new GroupRef(G, "orders");

  FeatureFlagRepository flags = mock(FeatureFlagRepository.class);
  GroupLookup groups = mock(GroupLookup.class);
  AuditService audit = mock(AuditService.class);
  ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  FlagService service =
      new FlagService(
          flags, groups, audit, events, new UuidV7(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));

  FeatureFlag existing;

  @BeforeEach
  void setUp() {
    // TA-10: events must carry the id of the audit event written in the same transaction.
    when(audit.record(any(), any(), any())).thenReturn(77L);
    existing = saved(new FeatureFlag(F, G, "new-checkout", null, false), 3);
    when(flags.findGroupId(F)).thenReturn(Optional.of(G));
    when(flags.findForUpdate(F)).thenReturn(Optional.of(existing));
    when(groups.lock(G)).thenReturn(Optional.of(ORDERS));
    when(flags.saveAndFlush(any())).thenAnswer(FlagServiceTest::first);
  }

  private static Object first(InvocationOnMock inv) {
    FeatureFlag f = inv.getArgument(0);
    return f.getVersion() == null ? saved(f, 0) : f;
  }

  private static Map<String, Object> change(Object from, Object to) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("from", from);
    m.put("to", to);
    return m;
  }

  @Test
  void createSavesAuditsAndPublishes() {
    Flag f = service.create(G, new CreateFlagRequest("split-payments", "", true));

    assertThat(f.fullKey()).isEqualTo("orders.split-payments");
    assertThat(f.enabled()).isTrue();
    assertThat(f.description()).isNull();
    assertThat(f.id().version()).isEqualTo(7);
    verify(audit)
        .record(AuditAction.FLAG_CREATED, "orders.split-payments", Map.of("enabled", true));
    verify(events)
        .publishEvent(new FlagsChangedEvent.FlagChanged("orders", "split-payments", true, 77L));
  }

  @Test
  void createChecksGroupDuplicateAndLimit() {
    when(groups.lock(id(9))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.create(id(9), new CreateFlagRequest("ab", null, null)))
        .isInstanceOf(NotFoundException.class);

    when(flags.existsByGroupIdAndKey(G, "dup")).thenReturn(true);
    assertThatThrownBy(() -> service.create(G, new CreateFlagRequest("dup", null, null)))
        .isInstanceOf(DuplicateKeyException.class);

    when(flags.countByGroupId(G)).thenReturn(500L);
    assertThatThrownBy(() -> service.create(G, new CreateFlagRequest("new", null, null)))
        .isInstanceOf(LimitReachedException.class);
    when(flags.countByGroupId(G)).thenReturn(499L);
    assertThat(service.create(G, new CreateFlagRequest("new", null, null)).key()).isEqualTo("new");
    verify(flags).saveAndFlush(any());
  }

  @Test
  void updateWritesOneEventWithEveryChangedField() {
    Flag f = service.update(F, new UpdateFlagRequest(Optional.of("text"), Optional.of(true), 3L));

    assertThat(f.description()).isEqualTo("text");
    assertThat(f.enabled()).isTrue();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("description", change(null, "text"));
    details.put("enabled", change(false, true));
    verify(audit).record(AuditAction.FLAG_UPDATED, "orders.new-checkout", details);
    verify(events)
        .publishEvent(new FlagsChangedEvent.FlagChanged("orders", "new-checkout", true, 77L));
  }

  @Test
  void updateDescriptionOnlyStillPublishes() {
    service.update(F, new UpdateFlagRequest(Optional.of("text"), null, 3L));
    verify(audit)
        .record(
            AuditAction.FLAG_UPDATED,
            "orders.new-checkout",
            Map.of("description", change(null, "text")));
    verify(events)
        .publishEvent(new FlagsChangedEvent.FlagChanged("orders", "new-checkout", false, 77L));
  }

  @Test
  void updateVersionMismatchIsConflictEvenForANoOp() {
    assertThatThrownBy(() -> service.update(F, new UpdateFlagRequest(null, null, 2L)))
        .isInstanceOf(VersionConflictException.class);
    assertThatThrownBy(() -> service.update(F, new UpdateFlagRequest(null, null, 4L)))
        .isInstanceOf(VersionConflictException.class);
    verifyNoInteractions(audit, events);
  }

  @Test
  void noOpUpdateWritesNothing() {
    Flag f = service.update(F, new UpdateFlagRequest(Optional.empty(), Optional.of(false), 3L));
    assertThat(f.version()).isEqualTo(3);
    verify(flags, never()).saveAndFlush(any());
    verifyNoInteractions(audit, events);
  }

  @Test
  void toggleChangesOnlyWhenTheValueDiffers() {
    Flag f = service.toggle(F, true);
    assertThat(f.enabled()).isTrue();
    verify(audit)
        .record(
            AuditAction.FLAG_TOGGLED,
            "orders.new-checkout",
            Map.of("enabled", change(false, true)));
    verify(events)
        .publishEvent(new FlagsChangedEvent.FlagChanged("orders", "new-checkout", true, 77L));

    FlagServiceTest other = new FlagServiceTest();
    other.setUp();
    Flag same = other.service.toggle(F, false);
    assertThat(same.enabled()).isFalse();
    assertThat(same.version()).isEqualTo(3);
    verifyNoInteractions(other.audit, other.events);
  }

  @Test
  void deleteAuditsTheLastValue() {
    existing.setEnabled(true);
    service.delete(F);
    verify(flags).delete(existing);
    verify(audit).record(AuditAction.FLAG_DELETED, "orders.new-checkout", Map.of("enabled", true));
    verify(events).publishEvent(new FlagsChangedEvent.FlagDeleted("orders", "new-checkout", 77L));
  }

  @Test
  void flagDeletedWhileWaitingForTheLockIsNotFound() {
    when(flags.findForUpdate(F)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.toggle(F, true)).isInstanceOf(NotFoundException.class);
    verifyNoInteractions(audit, events);
  }

  @Test
  void unknownFlagIsNotFound() {
    when(flags.findGroupId(id(9))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.toggle(id(9), true)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.delete(id(9))).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.update(id(9), new UpdateFlagRequest(null, null, 0L)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void flagsOfAreSortedByCodePoint() {
    when(flags.findByGroupId(G))
        .thenReturn(
            List.of(
                saved(new FeatureFlag(id(3), G, "aa", null, false), 0),
                saved(new FeatureFlag(id(4), G, "a-b", null, true), 0),
                saved(new FeatureFlag(id(5), G, "a0", null, false), 0)));
    assertThat(service.flagsOf(G, "orders"))
        .extracting(Flag::key)
        .containsExactly("a-b", "a0", "aa");
    assertThat(service.flagsOf(G, "orders").get(0).fullKey()).isEqualTo("orders.a-b");
  }

  @Test
  void countsByGroupMapsTheProjection() {
    FeatureFlagRepository.GroupCounts c = mock(FeatureFlagRepository.GroupCounts.class);
    when(c.getGroupId()).thenReturn(G);
    when(c.getTotal()).thenReturn(5L);
    when(c.getEnabled()).thenReturn(2L);
    when(flags.countsByGroup()).thenReturn(List.of(c));
    assertThat(service.countsByGroup().get(G)).containsExactly(5L, 2L);
  }

  @Test
  void missingGroupOfAnExistingFlagIsNotFound() {
    when(groups.lock(G)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.toggle(F, true)).isInstanceOf(NotFoundException.class);
    verify(audit, never()).record(eq(AuditAction.FLAG_TOGGLED), any(), any());
  }
}
