package com.example.featureflags.common;

/** A validation error found in code, reported like Bean Validation (400 validation). */
public class FieldValidationException extends RuntimeException {

  private final String field;

  public FieldValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
