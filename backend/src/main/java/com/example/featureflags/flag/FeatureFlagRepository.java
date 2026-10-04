package com.example.featureflags.flag;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spec 4.1 {@code feature_flag}. */
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

  List<FeatureFlag> findByGroupId(UUID groupId);
}
