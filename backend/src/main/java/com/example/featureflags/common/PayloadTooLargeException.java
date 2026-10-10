package com.example.featureflags.common;

/** Request body above 65,536 bytes (413 payload-too-large). */
public class PayloadTooLargeException extends RuntimeException {

  public PayloadTooLargeException() {
    super("Request body is larger than 65536 bytes");
  }
}
