package com.example.featureflags.common;

import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spec 5.1, 5.4: OAuth2 resource server with HS256 JWTs. The decoder checks signature, {@code exp}
 * (no clock skew), {@code iss} and that {@code aud} contains one of the two audiences; a failure is
 * 401. Each API then needs its scope and its audience; a mismatch is 403.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

  private static final List<String> AUDIENCES = List.of(Audiences.ADMIN, Audiences.SERVICE);

  /**
   * BF-3, spec 9.1: a request that the security firewall rejects (for example {@code //} or {@code
   * ;} in the path) gets a 400 {@code malformed-request} problem detail, not the default error
   * body.
   */
  @Bean
  RequestRejectedHandler requestRejectedHandler(ProblemWriter problems) {
    return (request, response, e) ->
        problems.write(
            request,
            response,
            HttpStatus.BAD_REQUEST,
            "malformed-request",
            "Malformed request",
            "The request URL is not allowed");
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtDecoder decoder,
      ProblemWriter problems,
      @Value("${featureflags.require-https}") boolean requireHttps)
      throws Exception {
    AuthenticationEntryPoint unauthorized =
        (req, res, e) -> {
          res.setHeader("WWW-Authenticate", "Bearer");
          problems.write(
              req,
              res,
              HttpStatus.UNAUTHORIZED,
              "unauthorized",
              "Unauthorized",
              "Missing, expired or invalid token");
        };
    AccessDeniedHandler forbidden =
        (req, res, e) ->
            problems.write(
                req,
                res,
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Forbidden",
                "Token does not grant access to this resource");

    JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(scopes);

    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/token")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**", "/actuator/prometheus")
                    .access(scopeAndAudience(Audiences.SCOPE_ADMIN, Audiences.ADMIN))
                    .requestMatchers("/api/v1/evaluate/**")
                    .access(scopeAndAudience(Audiences.SCOPE_FLAGS_READ, Audiences.SERVICE))
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(converter))
                    .bearerTokenResolver(bearerTokenResolver())
                    .authenticationEntryPoint(unauthorized)
                    .accessDeniedHandler(forbidden))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
        .addFilterBefore(
            new HttpsRequiredFilter(requireHttps, problems), BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  /**
   * Bearer tokens are read only on protected paths: a stale {@code Authorization} header on a
   * public path (login, token, health, info) must not turn it into a 401 (spec 5.4, 5.5).
   */
  @Bean
  BearerTokenResolver bearerTokenResolver() {
    DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
    return request -> {
      String path = RequestPaths.of(request);
      boolean open =
          path.equals("/api/v1/auth/login")
              || path.equals("/api/v1/auth/token")
              || path.equals("/actuator/health")
              || path.startsWith("/actuator/health/")
              || path.equals("/actuator/info");
      return open ? null : delegate.resolve(request);
    };
  }

  static AuthorizationManager<RequestAuthorizationContext> scopeAndAudience(
      String scope, String audience) {
    return (Supplier<? extends Authentication> auth, RequestAuthorizationContext ctx) -> {
      if (!(auth.get() instanceof JwtAuthenticationToken jwt)) {
        return new AuthorizationDecision(false);
      }
      boolean hasScope =
          jwt.getAuthorities().stream().anyMatch(a -> ("SCOPE_" + scope).equals(a.getAuthority()));
      List<String> aud = jwt.getToken().getAudience();
      return new AuthorizationDecision(hasScope && aud != null && aud.contains(audience));
    };
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${featureflags.auth.jwt-secret}") String secret,
      @Value("${featureflags.auth.issuer}") String issuer,
      Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(JwtKeys.secretKey(secret))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            notExpired(clock), new JwtIssuerValidator(issuer), knownAudience()));
    return decoder;
  }

  /** Spec 5.1: clock skew 0; a token whose {@code exp} is at or before now is rejected. */
  static OAuth2TokenValidator<Jwt> notExpired(Clock clock) {
    return jwt -> {
      Instant exp = jwt.getExpiresAt();
      if (exp != null && Instant.now(clock).isBefore(exp)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "Token expired", null));
    };
  }

  static OAuth2TokenValidator<Jwt> knownAudience() {
    return jwt -> {
      List<String> aud = jwt.getAudience();
      if (aud != null && aud.stream().anyMatch(AUDIENCES::contains)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "Unknown audience", null));
    };
  }

  /** Spec 5.4: CORS for {@code FF_CORS_ALLOWED_ORIGINS} (comma-separated). */
  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${featureflags.cors-allowed-origins}") String origins) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(
        Arrays.stream(origins.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList());
    c.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-None-Match"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }
}
