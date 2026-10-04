package com.example.featureflags.flag;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spec 4.1 {@code feature_flag}. */
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

  List<FeatureFlag> findByGroupId(UUID groupId);

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
