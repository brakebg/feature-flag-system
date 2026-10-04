package com.example.featureflags.common;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** Spec 5.1: the HS256 key is the UTF-8 bytes of {@code jwt-secret}, used as is. */
public final class JwtKeys {

  private JwtKeys() {}

  public static SecretKey secretKey(String secret) {
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }
}
