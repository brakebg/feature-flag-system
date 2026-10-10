package com.example.featureflags.common;

/** Key already used (409 duplicate-key). {@code field} names the request field ({@code key}). */
public class DuplicateKeyException extends RuntimeException {

  private final String field;

  public DuplicateKeyException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
