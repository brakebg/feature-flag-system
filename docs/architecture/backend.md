# Backend code structure

From spec 001 (`docs/SPEC.md`) section 9.5. Section numbers are kept, so references
such as "spec 9.1" still point here. Map of all sections: `docs/README.md`.

### 9.5 Backend code structure

Package `com.example.featureflags`, layered by feature:

- `auth/` — `AuthController` (login), `TokenController` (client credentials), `TokenIssuer` (wraps `JwtEncoder`), `AdminAuthenticator`, `ClientAuthenticator`, `ClientRegistrationProperties`.
- `group/` — `FlagGroup` entity, `FlagGroupRepository`, `GroupService`, `GroupController`, DTO records.
- `flag/` — `FeatureFlag` entity, repository, `FlagService`, `FlagController`, DTOs.
- `evaluation/` — `FlagCacheService`, `EvaluationController`.
- `audit/` — `AuditEvent` entity, `AuditService`, `AuditController`.
- `common/` — `GlobalExceptionHandler`, `SecurityConfig` (resource server, scope rules, `JwtDecoder` with issuer and audience validators), `CorsConfig`, `RequestIdFilter`, `ClockConfig` (inject `Clock` for testable timestamps), `AuditableEntity` (`@MappedSuperclass` with created/updated at/by).

Rules: controllers never touch repositories; services are `@Transactional`; entities never leave the service layer (map to DTO records); all audit writes happen in the same transaction as the change.
