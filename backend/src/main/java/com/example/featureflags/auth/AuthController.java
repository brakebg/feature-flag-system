package com.example.featureflags.auth;

import com.example.featureflags.common.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Spec 5.2: admin login with the configured credentials. */
@RestController
public class AuthController {

  static final String INVALID = "Invalid username or password";

  private final AdminAuthenticator authenticator;
  private final TokenIssuer issuer;

  public AuthController(AdminAuthenticator authenticator, TokenIssuer issuer) {
    this.authenticator = authenticator;
    this.issuer = issuer;
  }

  @PostMapping("/api/v1/auth/login")
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest req) {
    if (!authenticator.authenticate(body.username(), body.password())) {
      return Problems.response(
          Problems.of(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", INVALID, req));
    }
    IssuedToken token = issuer.issueAdmin(body.username());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new LoginResponse(token.value(), token.expiresAt(), body.username()));
  }
}
