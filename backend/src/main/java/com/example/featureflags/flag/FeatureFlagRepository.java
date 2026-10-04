package com.example.featureflags.flag;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spec 4.1 {@code feature_flag}. */
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

  List<FeatureFlag> findByGroupId(UUID groupId);

  /** The group of a flag, without loading the flag into the persistence context. */
  @Query("select f.groupId from FeatureFlag f where f.id = :id")
  Optional<UUID> findGroupId(@Param("id") UUID id);

  /** Reads the flag with a row lock ({@code SELECT ... FOR UPDATE}). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from FeatureFlag f where f.id = :id")
  Optional<FeatureFlag> findForUpdate(@Param("id") UUID id);

  long countByGroupId(UUID groupId);

  boolean existsByGroupIdAndKey(UUID groupId, String key);

  /** Flag and enabled counts per group, for {@code GET /groups}. */
  @Query(
      "select f.groupId as groupId, count(f) as total,"
          + " sum(case when f.enabled = true then 1 else 0 end) as enabled"
          + " from FeatureFlag f group by f.groupId")
  List<GroupCounts> countsByGroup();

  /** Projection of {@link #countsByGroup()}. */
  interface GroupCounts {
    UUID getGroupId();

    long getTotal();

    long getEnabled();
  }
}
