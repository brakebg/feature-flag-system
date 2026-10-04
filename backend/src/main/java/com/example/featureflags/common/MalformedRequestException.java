package com.example.featureflags.common;

/** Malformed request (400 malformed-request). */
public class MalformedRequestException extends RuntimeException {

  public MalformedRequestException(String message) {
    super(message);
  }
}
