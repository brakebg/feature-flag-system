package com.example.featureflags.group;

import com.example.featureflags.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Spec 4.1 {@code flag_group}. The key is immutable after creation. */
@Entity
@Table(name = "flag_group")
public class FlagGroup extends AuditableEntity {

  @Column(name = "key", nullable = false, updatable = false, length = 50)
  private String key;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", length = 500)
  private String description;

  protected FlagGroup() {}

  public FlagGroup(UUID id, String key, String name, String description) {
    super(id);
    this.key = key;
    this.name = name;
    this.description = description;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public void rename(String name) {
    this.name = name;
  }

  public void describe(String description) {
    this.description = description;
  }
}
