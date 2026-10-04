package com.example.featureflags.group;

import com.example.featureflags.audit.AuditAction;
import com.example.featureflags.audit.AuditService;
import com.example.featureflags.common.Changes;
import com.example.featureflags.common.DuplicateKeyException;
import com.example.featureflags.common.FlagsChangedEvent;
import com.example.featureflags.common.LimitReachedException;
import com.example.featureflags.common.NotFoundException;
import com.example.featureflags.common.UuidV7;
import com.example.featureflags.common.VersionConflictException;
import com.example.featureflags.flag.Flag;
import com.example.featureflags.flag.FlagService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spec 6.1: group list, create, detail, update and delete, each change audited (4.1). */
@Service
@Transactional
public class GroupService {

  static final int MAX_GROUPS = 1000;

  /** Advisory lock that serialises group creation, so the limit holds under concurrency. */
  static final long CREATE_LOCK = 0x46466752L;

  private final FlagGroupRepository groups;
  private final FlagService flags;
  private final AuditService audit;
  private final ApplicationEventPublisher events;
  private final UuidV7 ids;
  private final JdbcTemplate jdbc;

  public GroupService(
      FlagGroupRepository groups,
      FlagService flags,
      AuditService audit,
      ApplicationEventPublisher events,
      UuidV7 ids,
      JdbcTemplate jdbc) {
    this.groups = groups;
    this.flags = flags;
    this.audit = audit;
    this.events = events;
    this.ids = ids;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public List<GroupSummary> list(String q, String sort) {
    GroupSort order = GroupSort.parse(sort);
    Map<UUID, long[]> counts = flags.countsByGroup();
    String needle = q == null ? "" : q.toLowerCase(Locale.ROOT);
    return groups.findAll().stream()
        .filter(
            g ->
                needle.isEmpty()
                    || g.getKey().toLowerCase(Locale.ROOT).contains(needle)
                    || g.getName().toLowerCase(Locale.ROOT).contains(needle))
        .map(g -> summary(g, counts.getOrDefault(g.getId(), new long[2])))
        .sorted(order.order)
        .toList();
  }

  public Group create(CreateGroupRequest request) {
    jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", CREATE_LOCK);
    if (groups.existsByKey(request.key())) {
      throw new DuplicateKeyException("key", "Key already exists");
    }
    if (groups.count() >= MAX_GROUPS) {
      throw new LimitReachedException("At most " + MAX_GROUPS + " groups");
    }
    FlagGroup group =
        groups.saveAndFlush(
            new FlagGroup(ids.next(), request.key(), request.name(), request.description()));
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("name", group.getName());
    audit.record(AuditAction.GROUP_CREATED, group.getKey(), details);
    events.publishEvent(new FlagsChangedEvent.GroupCreated(group.getKey()));
    return Group.of(group);
  }

  @Transactional(readOnly = true)
  public GroupDetail get(UUID id) {
    FlagGroup g = load(id);
    List<Flag> list = flags.flagsOf(g.getId(), g.getKey());
    return new GroupDetail(
        g.getId(),
        g.getKey(),
        g.getName(),
        g.getDescription(),
        g.getCreatedAt(),
        g.getCreatedBy(),
        g.getUpdatedAt(),
        g.getUpdatedBy(),
        g.getVersion(),
        list);
  }

  public Group update(UUID id, UpdateGroupRequest request) {
    FlagGroup group = load(id);
    if (group.getVersion() != request.version().longValue()) {
      throw new VersionConflictException();
    }
    Changes changes = new Changes();
    if (request.name() != null && request.name().isPresent()) {
      String name = request.name().get();
      if (changes.add("name", group.getName(), name)) {
        group.rename(name);
      }
    }
    if (request.description() != null) {
      String description = request.description().orElse(null);
      if (changes.add("description", group.getDescription(), description)) {
        group.describe(description);
      }
    }
    if (changes.isEmpty()) {
      return Group.of(group);
    }
    groups.saveAndFlush(group);
    audit.record(AuditAction.GROUP_UPDATED, group.getKey(), changes.details());
    events.publishEvent(new FlagsChangedEvent.GroupEdited(group.getKey()));
    return Group.of(group);
  }

  public void delete(UUID id) {
    FlagGroup group = groups.findForUpdate(id).orElseThrow(() -> notFound(id));
    List<String> flagKeys = flags.flagsOf(id, group.getKey()).stream().map(Flag::key).toList();
    groups.delete(group);
    groups.flush();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("deletedFlags", flagKeys.stream().map(k -> group.getKey() + "." + k).toList());
    audit.record(AuditAction.GROUP_DELETED, group.getKey(), details);
    events.publishEvent(new FlagsChangedEvent.GroupDeleted(group.getKey(), flagKeys));
  }

  private FlagGroup load(UUID id) {
    return groups.findById(id).orElseThrow(() -> notFound(id));
  }

  private static NotFoundException notFound(UUID id) {
    return new NotFoundException("No group " + id);
  }

  private static GroupSummary summary(FlagGroup g, long[] counts) {
    return new GroupSummary(
        g.getId(),
        g.getKey(),
        g.getName(),
        g.getDescription(),
        counts[0],
        counts[1],
        g.getCreatedBy(),
        g.getUpdatedAt(),
        g.getUpdatedBy(),
        g.getVersion());
  }
}
