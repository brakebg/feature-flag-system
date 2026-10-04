package com.example.featureflags.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;

/**
 * Spring Security {@link JwtEncoder} that signs HS256 with Nimbus and keeps {@code aud} a JSON
 * array even with one value (spec 5.2). {@code NimbusJwtEncoder} writes a one-element audience as a
 * plain string (Nimbus {@code JWTClaimsSet}), which 5.2 does not allow; see ESC-003. Instants are
 * written as integer seconds.
 */
public class HmacJwtEncoder implements JwtEncoder {

  private final MACSigner signer;

  public HmacJwtEncoder(SecretKey key) {
    try {
      this.signer = new MACSigner(key);
    } catch (JOSEException e) {
      throw new IllegalArgumentException("invalid HS256 key", e);
    }
  }

  @Override
  public Jwt encode(JwtEncoderParameters parameters) throws JwtEncodingException {
    JwtClaimsSet claims = parameters.getClaims();
    Map<String, Object> json = new LinkedHashMap<>();
    claims
        .getClaims()
        .forEach(
            (name, value) -> {
              if (value instanceof Instant i) {
                json.put(name, i.getEpochSecond());
              } else if (value instanceof List<?> list) {
                json.put(name, List.copyOf(list));
              } else {
                json.put(name, value);
              }
            });
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build();
    JWSObject jws = new JWSObject(header, new Payload(json));
    try {
      jws.sign(signer);
    } catch (JOSEException e) {
      throw new JwtEncodingException("cannot sign token", e);
    }
    return new Jwt(
        jws.serialize(),
        claims.getIssuedAt(),
        claims.getExpiresAt(),
        Map.of("alg", "HS256", "typ", "JWT"),
        claims.getClaims());
  }
}
