package com.example.featureflags.group;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Spec 6.1: group endpoints of the Admin API. */
@RestController
@RequestMapping("/api/v1/admin/groups")
public class GroupController {

  private final GroupService groups;

  public GroupController(GroupService groups) {
    this.groups = groups;
  }

  @GetMapping
  public List<GroupSummary> list(
      @RequestParam(required = false) String q, @RequestParam(required = false) String sort) {
    return groups.list(q, sort);
  }

  @PostMapping
  public ResponseEntity<Group> create(@Valid @RequestBody CreateGroupRequest body) {
    Group group = groups.create(body);
    return ResponseEntity.created(URI.create("/api/v1/admin/groups/" + group.id())).body(group);
  }

  @GetMapping("/{groupId}")
  public GroupDetail get(@PathVariable UUID groupId) {
    return groups.get(groupId);
  }

  @PatchMapping("/{groupId}")
  public Group update(@PathVariable UUID groupId, @Valid @RequestBody UpdateGroupRequest body) {
    return groups.update(groupId, body);
  }

  @DeleteMapping("/{groupId}")
  public ResponseEntity<Void> delete(@PathVariable UUID groupId) {
    groups.delete(groupId);
    return ResponseEntity.noContent().build();
  }
}
