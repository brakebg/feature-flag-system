package com.example.featureflags.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spec 5.5: OAuth 2.0 client credentials (RFC 6749 4.4). The client authenticates with HTTP Basic
 * only; errors use the OAuth 2.0 error format.
 */
@RestController
public class TokenController {

  private final ClientAuthenticator clients;
  private final TokenIssuer issuer;

  public TokenController(ClientAuthenticator clients, TokenIssuer issuer) {
    this.clients = clients;
    this.issuer = issuer;
  }

  /** Spec 5.5 step 2 success body. */
  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") long expiresIn,
      String scope) {}

  @PostMapping("/api/v1/auth/token")
  public ResponseEntity<?> token(HttpServletRequest req) {
    if (!isForm(req.getContentType())) {
      return error(HttpStatus.BAD_REQUEST, "invalid_request");
    }
    Optional<ClientRegistration> client = basicCredentials(req).flatMap(this::authenticate);
    if (client.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"feature-flag-service\"")
          .cacheControl(CacheControl.noStore())
          .header(HttpHeaders.PRAGMA, "no-cache")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("error", "invalid_client"));
    }
    String grant = req.getParameter("grant_type");
    if (grant == null || grant.isEmpty()) {
      return error(HttpStatus.BAD_REQUEST, "invalid_request");
    }
    if (!"client_credentials".equals(grant)) {
      return error(HttpStatus.BAD_REQUEST, "unsupported_grant_type");
    }
    List<String> registered = client.get().scopes();
    String requested = req.getParameter("scope");
    List<String> granted;
    if (requested == null || requested.isBlank()) {
      granted = registered;
    } else {
      granted = new ArrayList<>();
      for (String s : requested.strip().split(" +")) {
        if (!registered.contains(s)) {
          return error(HttpStatus.BAD_REQUEST, "invalid_scope");
        }
        if (!granted.contains(s)) {
          granted.add(s);
        }
      }
    }
    IssuedToken token = issuer.issueClient(client.get().clientId(), granted);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new TokenResponse(
                token.value(), "Bearer", token.ttlSeconds(), String.join(" ", granted)));
  }

  private static boolean isForm(String contentType) {
    if (contentType == null) {
      return false;
    }
    try {
      return MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(
          MediaType.parseMediaType(contentType));
    } catch (org.springframework.http.InvalidMediaTypeException e) {
      return false;
    }
  }

  /**
   * {@code [clientId, secret]} from {@code Authorization: Basic}, form-url-decoded (RFC 6749
   * 2.3.1).
   */
  /**
   * Checks the credentials as sent, then form-url-decoded (RFC 6749 2.3.1), so both plain and
   * encoded client secrets work.
   */
  private Optional<ClientRegistration> authenticate(String[] raw) {
    Optional<ClientRegistration> plain = clients.authenticate(raw[0], raw[1]);
    if (plain.isPresent()) {
      return plain;
    }
    try {
      String id = java.net.URLDecoder.decode(raw[0], StandardCharsets.UTF_8);
      String secret = java.net.URLDecoder.decode(raw[1], StandardCharsets.UTF_8);
      if (id.equals(raw[0]) && secret.equals(raw[1])) {
        return Optional.empty();
      }
      return clients.authenticate(id, secret);
    } catch (IllegalArgumentException notEncoded) {
      return Optional.empty();
    }
  }

  private static Optional<String[]> basicCredentials(HttpServletRequest req) {
    String header = req.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
      return Optional.empty();
    }
    try {
      String decoded =
          new String(
              Base64.getDecoder().decode(header.substring(6).strip()), StandardCharsets.UTF_8);
      int colon = decoded.indexOf(':');
      if (colon < 0) {
        return Optional.empty();
      }
      return Optional.of(new String[] {decoded.substring(0, colon), decoded.substring(colon + 1)});
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("error", code));
  }
}
