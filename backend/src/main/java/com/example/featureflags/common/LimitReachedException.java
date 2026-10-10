package com.example.featureflags.common;

/** Group or flag limit reached (409 limit-reached). */
public class LimitReachedException extends RuntimeException {

  public LimitReachedException(String message) {
    super(message);
  }
}
