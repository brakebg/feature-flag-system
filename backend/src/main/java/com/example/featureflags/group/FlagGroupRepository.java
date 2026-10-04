package com.example.featureflags.group;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spec 4.1 {@code flag_group}. */
public interface FlagGroupRepository extends JpaRepository<FlagGroup, UUID> {

  Optional<FlagGroup> findByKey(String key);

  boolean existsByKey(String key);
}
