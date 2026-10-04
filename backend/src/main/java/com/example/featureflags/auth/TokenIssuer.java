package com.example.featureflags.auth;

import com.example.featureflags.common.Audiences;
import com.example.featureflags.common.JwtKeys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Spec 5.2, 5.5: issues HS256 JWTs through a Spring Security {@link JwtEncoder} (see {@link
 * HmacJwtEncoder} and ESC-003). {@code iat} and {@code exp} are whole seconds, {@code exp - iat} is
 * the TTL, {@code aud} is a one-element array and {@code scope} a space-separated string.
 */
@Component
public class TokenIssuer {

  private final JwtEncoder encoder;
  private final ClientRegistrationProperties props;
  private final Clock clock;

  public TokenIssuer(ClientRegistrationProperties props, Clock clock) {
    this.props = props;
    this.clock = clock;
    this.encoder = new HmacJwtEncoder(JwtKeys.secretKey(props.jwtSecret()));
  }

  public IssuedToken issueAdmin(String username) {
    return issue(username, Audiences.SCOPE_ADMIN, Audiences.ADMIN, props.adminTokenTtl());
  }

  public IssuedToken issueClient(String clientId, List<String> scopes) {
    return issue(clientId, String.join(" ", scopes), Audiences.SERVICE, props.clientTokenTtl());
  }

  private IssuedToken issue(String subject, String scope, String audience, Duration ttl) {
    Instant iat = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    long seconds = ttl.toSeconds();
    Instant exp = iat.plusSeconds(seconds);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(subject)
            .claim("scope", scope)
            .audience(List.of(audience))
            .issuer(props.issuer())
            .issuedAt(iat)
            .expiresAt(exp)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
    String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedToken(value, exp, seconds);
  }
}
