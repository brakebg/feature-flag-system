package com.example.featureflags.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Spec 4.1: audit {@code details} with one {@code {"from":..,"to":..}} entry per changed field. */
public final class Changes {

  private final Map<String, Object> details = new LinkedHashMap<>();

  public boolean isEmpty() {
    return details.isEmpty();
  }

  /** Records the change when the values differ; {@code null} stays a JSON null. */
  public boolean add(String field, Object from, Object to) {
    if (Objects.equals(from, to)) {
      return false;
    }
    Map<String, Object> change = new LinkedHashMap<>();
    change.put("from", from);
    change.put("to", to);
    details.put(field, change);
    return true;
  }

  public Map<String, Object> details() {
    return details;
  }
}
