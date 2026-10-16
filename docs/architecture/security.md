# Authentication and security

From spec 001 (`docs/SPEC.md`) section 5. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

## 5. Authentication

Both APIs use JWT bearer tokens issued by the service itself: the admin gets one by logging in with hardcoded credentials, and each consumer system gets one through the OAuth 2.0 client credentials flow with a hardcoded client id and secret. Scopes keep the two apart: admin tokens carry `admin`, consumer tokens carry `flags:read`. All credentials live in configuration, overridable by environment variables.

### 5.1 Configuration

```yaml
featureflags:
  auth:
    admin-username: ${FF_ADMIN_USERNAME:admin}
    admin-password: ${FF_ADMIN_PASSWORD:admin123}   # dev default only
    jwt-secret: ${FF_JWT_SECRET:change-me-to-a-32-byte-minimum-secret!!}
    issuer: feature-flag-service
    admin-token-ttl: ${FF_ADMIN_TOKEN_TTL:PT8H}
    client-token-ttl: ${FF_CLIENT_TOKEN_TTL:PT15M}
    clients:                                          # consumer systems
      - client-id: order-service
        client-secret: ${FF_CLIENT_ORDER_SERVICE_SECRET:order-service-dev-secret}
        scopes: [flags:read]
```

Clients bind to a `List<ClientRegistration>` record via `@ConfigurationProperties`. More clients are added as list entries (or through `FF_AUTH_CLIENTS_0_CLIENT_ID`-style environment variables).

- On startup the backend MUST fail fast if `jwt-secret` is shorter than 32 bytes, or if two clients share a `client-id`.
- The HS256 signing key is the UTF-8 bytes of `jwt-secret`, used as is (no Base64 decoding, no hashing). The 32-byte minimum counts these bytes.
- Any ISO-8601 duration of 1 second or more is accepted for `admin-token-ttl` and `client-token-ttl`. The JWT decoder uses a clock skew of 0 seconds: a token whose `exp` is at or before the current time is rejected with `401`. (The same service issues and validates tokens, so there is one clock.)
- In the `prod` profile it MUST log a WARN if any default password or secret above is still in use.
- Admin password and client secrets are compared in constant time (`MessageDigest.isEqual`). They are not stored hashed in v1 because they are config, but checks sit behind `AdminAuthenticator` and `ClientAuthenticator` interfaces so a real user or client store can replace them.
- Tokens are signed HS256 and issued with Spring Security's `NimbusJwtEncoder`; they are validated by Spring Security OAuth2 Resource Server (`spring-boot-starter-oauth2-resource-server`) with a `NimbusJwtDecoder`. The decoder checks signature, `exp`, `iss`, and that `aud` contains `feature-flag-admin` or `feature-flag-service`. A token that passes but whose scope or audience does not match the API gets `403` (5.4); any other failure gets `401`. No custom JWT filter.

### 5.2 Login flow

1. UI posts `{ "username", "password" }` to `POST /api/v1/auth/login`.
2. On success the backend returns `200 { "accessToken": "<jwt>", "expiresAt": "<ISO-8601>", "username": "admin" }`. JWT claims: `sub` = username, `scope` = `admin`, `aud` = `feature-flag-admin`, `iss` = `feature-flag-service`, `iat`, `exp`.
   Claim types: `aud` is an array with one string, `scope` is a space-separated string, `iat` and `exp` are integer seconds since the epoch, `exp - iat` equals the token TTL in seconds, and `expiresAt` is the instant of `exp`. The same rules apply to consumer tokens (5.5), where `expires_in` is the TTL in seconds.
3. On failure: `401` with a generic problem detail. The problem `detail` is exactly `Invalid username or password`, the same for an unknown username and a wrong password. Never reveal which field was wrong.
   A missing or blank `username` or `password` returns 400 with problem type `validation`; the `errors` entry has `field` = `username` or `password`. A body that is not valid JSON or has wrong types returns 400 `malformed-request`. Credentials that are present but wrong return 401 `unauthorized` (step 3).
4. UI keeps the token in `sessionStorage` and sends `Authorization: Bearer <jwt>` on every Admin API call.
   The token is stored in `sessionStorage` under the key `ff.accessToken` as the raw JWT string (no JSON wrapper). Sign out and any `401` remove this key.
5. Any `401` from the Admin API makes the UI clear the token and redirect to `/login?expired=1`.
6. Logout is client-side only: the UI discards the token (no server-side revocation in v1).

### 5.3 Rate limiting

Not in the service (decision `docs/architecture/decisions/0002-rate-limiting-at-edge.md`). Rate limiting of `POST /api/v1/auth/login` and `POST /api/v1/auth/token` is done at the edge (CDN, API gateway or ingress, section 10.2). The service MUST NOT implement its own rate limiter. The UI still handles a `429` from the edge (section 8.2).

### 5.4 Security rules (Spring Security)

| Path | Access |
| --- | --- |
| `POST /api/v1/auth/login` | Public |
| `POST /api/v1/auth/token` | Public (client authenticates with id and secret) |
| `/api/v1/admin/**` | JWT with scope `admin` and audience `feature-flag-admin` |
| `/api/v1/evaluate/**` | JWT with scope `flags:read` and audience `feature-flag-service` |
| `/actuator/health`, `/actuator/health/**`, `/actuator/info` | Public |
| `/actuator/prometheus` | JWT with scope `admin` and audience `feature-flag-admin` |
| `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | Public in `dev`, disabled in `prod` |
| Everything else | Denied |

A request to a path in the last row is answered 401 `unauthorized` without a valid token and 403 `forbidden` with a valid token. In the `prod` profile `/swagger-ui.html`, `/swagger-ui/**` and `/v3/api-docs/**` answer 404 `not-found` for everyone.

A token with the wrong scope or audience gets `403`; a missing, expired or badly signed token gets `401` with a `WWW-Authenticate: Bearer` header. The header value starts with the scheme (`Bearer`, or `Basic` for the token endpoint in 5.5); parameters after the scheme are allowed and not specified. Authorities come from the `scope` claim via `JwtGrantedAuthoritiesConverter` (`SCOPE_admin`, `SCOPE_flags:read`).

CSRF is disabled (stateless, token in header). CORS allows the origin in `FF_CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`). Preflight requests from an allowed origin get `Access-Control-Allow-Origin` with that origin, allowed methods `GET, POST, PATCH, DELETE` and allowed headers `Authorization, Content-Type, If-None-Match`. Values are compared as case-insensitive, comma-separated sets. `Access-Control-Allow-Headers` lists the requested headers that are allowed; a preflight that requests all three gets all three. A request from another origin gets no `Access-Control-Allow-Origin` header.

### 5.5 Consumer tokens (client credentials flow)

A consumer system exchanges its client id and secret for a short-lived JWT, caches it, and refreshes it before expiry.

1. Consumer calls the token endpoint (RFC 6749 section 4.4):

```http
POST /api/v1/auth/token
Authorization: Basic base64(order-service:order-service-dev-secret)
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=flags:read
```

2. On success: `200 { "access_token": "<jwt>", "token_type": "Bearer", "expires_in": 900, "scope": "flags:read" }`. JWT claims: `sub` = client id, `scope` = `flags:read`, `aud` = `feature-flag-service`, `iss`, `iat`, `exp`. The response has `Content-Type: application/json`, `Cache-Control: no-store` and `Pragma: no-cache`.
3. Errors use the OAuth 2.0 error format, not problem details: `400 { "error": "unsupported_grant_type" }`; `400 { "error": "invalid_scope" }` when any requested scope (space-separated) is not registered for the client; `400 { "error": "invalid_request" }` for a missing `grant_type` or a content type other than `application/x-www-form-urlencoded`; `401 { "error": "invalid_client" }` for a wrong or missing id or secret, a malformed `Authorization` header, or credentials sent only in the body. A 401 has `WWW-Authenticate: Basic`.
4. `scope` in the request is optional; omitted or empty (`scope=`) means all scopes registered for the client.
5. Consumer sends `Authorization: Bearer <jwt>` on every Evaluation API call and requests a new token when less than 60 s remain, or after any `401`.
6. No refresh tokens are issued; the client simply repeats step 1.

Migration path: because validation is standard OAuth2 Resource Server, the built-in token endpoint can later be removed and `spring.security.oauth2.resourceserver.jwt.issuer-uri` pointed at an external identity provider (Keycloak, Okta, Azure AD). Consumers then fetch tokens from that provider; the Evaluation API does not change.
