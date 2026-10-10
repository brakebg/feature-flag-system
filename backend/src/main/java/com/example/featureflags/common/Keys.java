package com.example.featureflags.common;

/** Spec 4.2: the key rule shared by groups and flags, and text normalisation. */
public final class Keys {

  public static final String REGEX = "^[a-z][a-z0-9-]{1,49}$";
  public static final String MESSAGE = "must match " + REGEX;

  private Keys() {}

  /** Spec 4.2 item 1: names are trimmed before validation and storing. */
  public static String trim(String s) {
    return s == null ? null : s.strip();
  }

  /** Spec 4.2 item 3: {@code ""} or {@code null} means no description. */
  public static String description(String s) {
    return s == null || s.isEmpty() ? null : s;
  }
}
