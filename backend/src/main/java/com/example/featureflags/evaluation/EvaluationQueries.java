package com.example.featureflags.evaluation;

import com.example.featureflags.flag.FeatureFlag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * The only database reads of the Evaluation API (spec 7.2): one query per cache load. Used only by
 * the {@link FlagCacheService} loaders, warm-up and reconciliation (gate 3).
 */
public interface EvaluationQueries extends Repository<FeatureFlag, UUID> {

  /** One row per flag, plus one row with a null flag key for a group without flags. */
  interface Row {
    String getGroupKey();

    String getFlagKey();

    Boolean getEnabled();
  }

  @Query(
      value =
          "SELECT f.enabled FROM feature_flag f JOIN flag_group g ON g.id = f.group_id"
              + " WHERE g.key = :groupKey AND f.key = :flagKey",
      nativeQuery = true)
  Optional<Boolean> findEnabled(
      @Param("groupKey") String groupKey, @Param("flagKey") String flagKey);

  @Query(
      value =
          "SELECT g.key AS groupKey, f.key AS flagKey, f.enabled AS enabled FROM flag_group g"
              + " LEFT JOIN feature_flag f ON f.group_id = g.id WHERE g.key = :groupKey",
      nativeQuery = true)
  List<Row> findGroup(@Param("groupKey") String groupKey);

  @Query(
      value =
          "SELECT g.key AS groupKey, f.key AS flagKey, f.enabled AS enabled FROM flag_group g"
              + " LEFT JOIN feature_flag f ON f.group_id = g.id",
      nativeQuery = true)
  List<Row> findAllRows();

  @Query(value = "SELECT max(id) FROM audit_event", nativeQuery = true)
  Optional<Long> loadMaxAuditId();
}
