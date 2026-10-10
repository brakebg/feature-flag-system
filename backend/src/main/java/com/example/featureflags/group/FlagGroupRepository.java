package com.example.featureflags.group;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spec 4.1 {@code flag_group}. */
public interface FlagGroupRepository extends JpaRepository<FlagGroup, UUID> {

  Optional<FlagGroup> findByKey(String key);

  boolean existsByKey(String key);

  /** Reads the group with a row lock ({@code SELECT ... FOR UPDATE}). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from FlagGroup g where g.id = :id")
  Optional<FlagGroup> findForUpdate(@Param("id") UUID id);
}
