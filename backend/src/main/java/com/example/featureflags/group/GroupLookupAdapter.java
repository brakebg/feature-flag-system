package com.example.featureflags.group;

import com.example.featureflags.flag.GroupLookup;
import com.example.featureflags.flag.GroupRef;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Gives the flag package the group key and the group row lock. */
@Component
class GroupLookupAdapter implements GroupLookup {

  private final FlagGroupRepository groups;

  GroupLookupAdapter(FlagGroupRepository groups) {
    this.groups = groups;
  }

  @Override
  @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
  public Optional<GroupRef> find(UUID groupId) {
    return groups.findById(groupId).map(g -> new GroupRef(g.getId(), g.getKey()));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<GroupRef> lock(UUID groupId) {
    return groups.findForUpdate(groupId).map(g -> new GroupRef(g.getId(), g.getKey()));
  }
}
