package com.example.featureflags.common;

/** Resource not found (404 not-found). */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}
