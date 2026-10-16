# 0002 — Rate limiting at the edge, not in the service

Status: **Accepted**
Decided by: owner (@Yordan), 2026-10-03
Applied in: `docs/SPEC.md` 1.2, 5.3, 8.2, 9.1, 9.5, 10.2, 11.1, 11.2, 11.4, 12.2

## 1. Decision

The Feature Flag Service does **not** rate-limit login or token requests itself.
Rate limiting is done at the edge: a CDN (for example Cloudflare), an API gateway, or the
Kubernetes ingress in front of the service.

## 2. Why

1. The edge is where this normally lives. It sees the real client IP and protects every
   instance before requests reach the service.
2. Inside the service it is weak: behind an ingress every request comes from the ingress
   IP, unless the service trusts `X-Forwarded-For`, which a client can fake.
3. It adds code and state (an in-memory counter per IP) for little value.
4. It makes black-box tests unreliable: all tests come from one IP and the counter cannot
   be reset, so failed-login tests block each other.

Not chosen: a per-account lockout in the service. With one hardcoded admin account, anyone
could lock the owner out.

## 3. Spec changes

| Spec | Change |
| --- | --- |
| 1.2 Non-goals | Added: rate limiting inside the service |
| 5.3 Rate limiting | MUST removed; service MUST NOT implement its own rate limiter; edge does it |
| 8.2 Login page | 429 message kept: the edge can return 429 with `Retry-After` |
| 9.1 Error table | `429 rate-limited` row removed |
| 9.5 Code structure | `LoginRateLimiter` removed |
| 10.2 HTTPS in production | Ingress / CDN / gateway rate-limits login and token per client IP |
| 11.1 Test layers | "rate limiter" removed from backend unit tests |
| 11.2 Acceptance criteria | AC-AUTH-5 marked `[Removed — decision 0002]`; ID kept so others do not shift |
| 11.4 Traceability | Rule added: removed IDs need no test and are skipped |
| 12.2 M3 | "rate limiter" removed |

## 4. Consequences

- Active acceptance criteria: 39 (was 40). Black-box acceptance suite coverage: 30 fully,
  2 partly, 7 white-box only. (Decision 0001 quotes the earlier 31 of 40.)
- The hosting target must configure rate limiting at the edge (open question in spec 13).
- The acceptance repo must re-copy the spec and update `docs/coverage.md`.
