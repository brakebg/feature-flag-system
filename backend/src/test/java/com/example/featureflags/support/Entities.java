package com.example.featureflags.support;

import com.example.featureflags.common.AuditableEntity;
import java.lang.reflect.Field;
import java.time.Instant;

/** Sets the columns that JPA fills, for unit tests without a database. */
public final class Entities {

  public static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

  private Entities() {}

  public static <T extends AuditableEntity> T saved(T entity, long version) {
    set(entity, "version", version);
    set(entity, "createdAt", T0);
    set(entity, "updatedAt", T0);
    set(entity, "createdBy", "admin");
    set(entity, "updatedBy", "admin");
    return entity;
  }

  public static void set(Object target, String name, Object value) {
    try {
      Class<?> c = target.getClass();
      while (c != null) {
        for (Field f : c.getDeclaredFields()) {
          if (f.getName().equals(name)) {
            f.setAccessible(true);
            f.set(target, value);
            return;
          }
        }
        c = c.getSuperclass();
      }
      throw new IllegalArgumentException(name);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }
}
