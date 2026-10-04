package com.example.featureflags.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Spec 5.1: credentials are compared in constant time ({@code MessageDigest.isEqual}). */
final class ConstantTime {

  private ConstantTime() {}

  static boolean equal(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
