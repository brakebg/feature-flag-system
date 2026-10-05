package com.example.featureflags.flag;

import com.example.featureflags.audit.AuditAction;
import com.example.featureflags.audit.AuditService;
import com.example.featureflags.common.Changes;
import com.example.featureflags.common.DuplicateKeyException;
import com.example.featureflags.common.FlagsChangedEvent;
import com.example.featureflags.common.LimitReachedException;
import com.example.featureflags.common.NotFoundException;
import com.example.featureflags.common.UuidV7;
import com.example.featureflags.common.VersionConflictException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spec 6.1: flag create, update, toggle and delete, each with its audit event (4.1). */
@Service
@Transactional
public class FlagService {

  static final int MAX_FLAGS_PER_GROUP = 500;

  private final FeatureFlagRepository flags;
  private final GroupLookup groups;
  private final AuditService audit;
  private final ApplicationEventPublisher events;
  private final UuidV7 ids;

  public FlagService(
      FeatureFlagRepository flags,
      GroupLookup groups,
      AuditService audit,
      ApplicationEventPublisher events,
      UuidV7 ids) {
    this.flags = flags;
    this.groups = groups;
    this.audit = audit;
    this.events = events;
    this.ids = ids;
  }

  public Flag create(UUID groupId, CreateFlagRequest request) {
    GroupRef group =
        groups.lock(groupId).orElseThrow(() -> new NotFoundException("No group " + groupId));
    if (flags.existsByGroupIdAndKey(groupId, request.key())) {
      throw new DuplicateKeyException("key", "Key already exists");
    }
    if (flags.countByGroupId(groupId) >= MAX_FLAGS_PER_GROUP) {
      throw new LimitReachedException("A group holds at most " + MAX_FLAGS_PER_GROUP + " flags");
    }
    FeatureFlag flag =
        flags.saveAndFlush(
            new FeatureFlag(
                ids.next(), groupId, request.key(), request.description(), request.enabled()));
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("enabled", flag.isEnabled());
    long seq = audit.record(AuditAction.FLAG_CREATED, fullKey(group, flag), details);
    events.publishEvent(
        new FlagsChangedEvent.FlagChanged(group.key(), flag.getKey(), flag.isEnabled(), seq));
    return Flag.of(flag, group.key());
  }

  public Flag update(UUID flagId, UpdateFlagRequest request) {
    Locked locked = lockFlag(flagId);
    FeatureFlag flag = locked.flag();
    GroupRef group = locked.group();
    checkVersion(flag, request.version());
    Changes changes = new Changes();
    if (request.description() != null) {
      String description = request.description().orElse(null);
      if (changes.add("description", flag.getDescription(), description)) {
        flag.describe(description);
      }
    }
    if (request.enabled() != null && request.enabled().isPresent()) {
      boolean enabled = request.enabled().get();
      if (changes.add("enabled", flag.isEnabled(), enabled)) {
        flag.setEnabled(enabled);
      }
    }
    if (changes.isEmpty()) {
      return Flag.of(flag, group.key());
    }
    flags.saveAndFlush(flag);
    long seq = audit.record(AuditAction.FLAG_UPDATED, fullKey(group, flag), changes.details());
    events.publishEvent(
        new FlagsChangedEvent.FlagChanged(group.key(), flag.getKey(), flag.isEnabled(), seq));
    return Flag.of(flag, group.key());
  }

  public Flag toggle(UUID flagId, boolean enabled) {
    Locked locked = lockFlag(flagId);
    FeatureFlag flag = locked.flag();
    GroupRef group = locked.group();
    Changes changes = new Changes();
    if (!changes.add("enabled", flag.isEnabled(), enabled)) {
      return Flag.of(flag, group.key());
    }
    flag.setEnabled(enabled);
    flags.saveAndFlush(flag);
    long seq = audit.record(AuditAction.FLAG_TOGGLED, fullKey(group, flag), changes.details());
    events.publishEvent(
        new FlagsChangedEvent.FlagChanged(group.key(), flag.getKey(), enabled, seq));
    return Flag.of(flag, group.key());
  }

  public void delete(UUID flagId) {
    Locked locked = lockFlag(flagId);
    FeatureFlag flag = locked.flag();
    GroupRef group = locked.group();
    flags.delete(flag);
    flags.flush();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("enabled", flag.isEnabled());
    long seq = audit.record(AuditAction.FLAG_DELETED, fullKey(group, flag), details);
    events.publishEvent(new FlagsChangedEvent.FlagDeleted(group.key(), flag.getKey(), seq));
  }

  /** The flags of a group, sorted by key in code-point order (spec 6.2). */
  @Transactional(readOnly = true)
  public List<Flag> flagsOf(UUID groupId, String groupKey) {
    return flags.findByGroupId(groupId).stream()
        .sorted(Comparator.comparing(FeatureFlag::getKey))
        .map(f -> Flag.of(f, groupKey))
        .toList();
  }

  /** Flag count and enabled count per group id. */
  @Transactional(readOnly = true)
  public Map<UUID, long[]> countsByGroup() {
    Map<UUID, long[]> out = new HashMap<>();
    for (FeatureFlagRepository.GroupCounts c : flags.countsByGroup()) {
      out.put(c.getGroupId(), new long[] {c.getTotal(), c.getEnabled()});
    }
    return out;
  }

  private record Locked(FeatureFlag flag, GroupRef group) {}

  /**
   * Locks the group row, then the flag row (the same order as group create-flag and group delete),
   * so concurrent writes to one flag run one after the other: a second toggle sees the first one's
   * value (no-op, not 409), and a write racing a group delete gets 404.
   */
  private Locked lockFlag(UUID flagId) {
    UUID groupId =
        flags.findGroupId(flagId).orElseThrow(() -> new NotFoundException("No flag " + flagId));
    GroupRef group =
        groups.lock(groupId).orElseThrow(() -> new NotFoundException("No flag " + flagId));
    FeatureFlag flag =
        flags.findForUpdate(flagId).orElseThrow(() -> new NotFoundException("No flag " + flagId));
    return new Locked(flag, group);
  }

  private static void checkVersion(FeatureFlag flag, long version) {
    if (flag.getVersion() != version) {
      throw new VersionConflictException();
    }
  }

  private static String fullKey(GroupRef group, FeatureFlag flag) {
    return group.key() + "." + flag.getKey();
  }
}
