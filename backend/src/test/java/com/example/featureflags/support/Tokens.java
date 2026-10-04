package com.example.featureflags.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-signed test tokens (spec 11.5: tests may sign HS256 JWTs with the documented dev secret, for
 * example with a wrong aud, scope or iss, or expired).
 */
public final class Tokens {

  public static final String DEV_SECRET = "change-me-to-a-32-byte-minimum-secret!!";

  private Tokens() {}

  public static Map<String, Object> claims(
      String sub, String scope, String aud, Instant iat, Instant exp) {
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("sub", sub);
    c.put("scope", scope);
    c.put("aud", List.of(aud));
    c.put("iss", "feature-flag-service");
    c.put("iat", iat.getEpochSecond());
    c.put("exp", exp.getEpochSecond());
    return c;
  }

  public static String sign(Map<String, Object> claims, String secret) {
    try {
      JWSObject jws = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(claims));
      jws.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
      return jws.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String sign(Map<String, Object> claims) {
    return sign(claims, DEV_SECRET);
  }
}
