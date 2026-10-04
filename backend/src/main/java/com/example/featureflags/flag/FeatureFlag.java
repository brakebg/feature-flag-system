package com.example.featureflags.flag;

import com.example.featureflags.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Spec 4.1 {@code feature_flag}. Belongs to exactly one group ({@code group_id}, deleted with the
 * group by the database cascade). The key is immutable after creation.
 */
@Entity
@Table(name = "feature_flag")
public class FeatureFlag extends AuditableEntity {

  @Column(name = "group_id", nullable = false, updatable = false)
  private UUID groupId;

  @Column(name = "key", nullable = false, updatable = false, length = 50)
  private String key;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  protected FeatureFlag() {}

  public FeatureFlag(UUID id, UUID groupId, String key, String description, boolean enabled) {
    super(id);
    this.groupId = groupId;
    this.key = key;
    this.description = description;
    this.enabled = enabled;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public String getKey() {
    return key;
  }

  public String getDescription() {
    return description;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void describe(String description) {
    this.description = description;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
