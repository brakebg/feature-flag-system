package com.example.featureflags.common;

/** Stale version (409 version-conflict). */
public class VersionConflictException extends RuntimeException {

  public VersionConflictException() {
    super("The item was changed by someone else");
  }
}
