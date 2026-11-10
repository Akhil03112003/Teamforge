# Security hardening and operating boundaries

M7 is a manual source/configuration review backed by executable regressions, not a penetration-test certificate. The optional CSO skill launcher is absent, so that automated assessment is **not assessed**. An online dependency/advisory scan has not run; no claim of vulnerability-free dependencies is made.

## Confirmed gaps addressed

| Gap | Fix | Regression evidence |
|---|---|---|
| JSON/chunked API bodies had no application read bound | Pre-CSRF bounded read, default 65,536 bytes; form limits, multipart disabled and 16KB header cap | `RequestLimitsFilterTest`, `SecurityHardeningIntegrationTest` fixed/chunked 413 |
| Login fields could be supplied in the URL or ambiguous repeated form parameters | Reject login query strings, duplicate/malformed form fields; retain ordinary encoded body and CSRF handling | Both filter and HTTP hardening suites |
| Anonymous CSRF bootstrap could create unbounded sessions | New-session bootstrap limit 300/IP/minute, bounded 10,000-IP map; Tomcat max 20,000 sessions, 30-minute inactivity, no disk persistence | Filter tests and real HTTP `SessionCapacityIntegrationTest` (safe 503; health remains available) |
| Production cookies could be weakened by profile/property overrides | Explicit `prod` profile; fail startup for `prod` plus `local` or insecure/non-HttpOnly cookies | `ProductionGuardTest` |
| Error paths lacked a uniform fallback and SQL exception logging could expose submitted identifiers | Safe 4xx/500 envelopes; log only random request ID and exception class; suppress Hibernate SQL exception detail and JDBC bind logging | HTTP malformed/media-type/secret-marker checks; manual logging configuration review |
| Static release pages lacked an explicit restrictive browser policy | Same-origin CSP, no-referrer, disabled camera/microphone/location; existing nosniff/frame denial/cache control retained | HTTP header regression and packaged-application browser flow |
| Long SSE requests could delay graceful shutdown | Close streams on context-close before web-server draining, reject new streams during shutdown; 30-second graceful phase budget | `StreamShutdownTest` plus retained SSE integration tests |

Limits are technical defaults, not new competition requirements. Configuration keys: `teamforge.security.max-request-bytes` (16KiB–1MiB), `auth-attempts-per-minute` (30), `invite-attempts-per-minute` (60), `new-sessions-per-minute` (300), `max-sessions` (20,000). Use trusted environment configuration; Spring's relaxed binding supports corresponding uppercase underscore names. A session cap is a memory bound, not evidence that the selected JVM can sustain 20,000 sessions.

## Existing controls reviewed and preserved

- Local email/password, BCrypt cost 12, normalized unique identities, password/DTO constraints, private profile fields omitted from public responses. Login changes session ID; logout invalidates the session. No session secrets in browser storage. No authentication-provider change.
- CSRF remains enabled for all cookie-authenticated mutations including registration, login/logout, profile, teams, invitations and requests. Fetch a CSRF token before a mutation and after login/logout. Tests exercise the mutation matrix without CSRF.
- All business endpoints require authentication; participant identity is server-derived. Current leader authority and event scope are rechecked under the existing domain locks. Nonleader actions and cross-event invitation use are rejected. No direct join bypass was added.
- Invitations retain 256-bit secure randomness, hash-only persistence, database-time expiry/rotation, safe uniform redemption errors, and account/IP throttles. Never put tokens/passwords in URLs, audit, SSE, analytics or browser storage.
- Caller/operation/key-scoped idempotency fingerprints and atomic result persistence remain unchanged. Cross-actor keys cannot replay another actor's result. Existing lost-response and mismatch tests remain active.
- SSE requires an authenticated event subscription, bounded queues/connections, heartbeat/session expiry/disconnect cleanup; payloads only invalidate state. After-commit emission and rollback suppression remain tested. Clients refetch on ready/reconnect and periodically.
- Parameterized database access, schema constraints, immutable audits and M3 participant-before-team lock ordering remain intact. Discovery uses bounded set queries and V6 indexes; this review does not establish the M8 latency target.

## Deployment conditions and residual risks

Run one application instance until cross-instance sessions, notifications and rate limits are deliberately designed. Rate limits are bounded, per-process controls and reset on restart. They are not distributed brute-force or denial-of-service protection. The edge must limit request rates/body sizes/connection counts and must not log login bodies or invite tokens.

Forwarded headers are disabled by default; spoofed client headers cannot bypass IP limits. Behind a reverse proxy, configure trustworthy client-IP handling at the edge and test legitimate shared-IP traffic before tuning limits. Do not blindly enable forwarded-header trust. Terminate HTTPS at a trusted edge, enforce HTTPS/HSTS there, strip spoofed forwarding headers, keep the backend private, and support SSE without buffering. Application HSTS is emitted for requests recognized as secure; the HTTPS edge must enforce it when TLS terminates upstream.

Use a least-privilege runtime database role and a separate migration role or controlled migration step. The local developer role is not a production privilege model. Immutable-audit triggers remain defense in depth. Keep credentials in a secret store/environment, never JDBC query strings or command arguments; configure database TLS with certificate verification. See [deployment prerequisites](deployment.md).

Password recovery, email verification, MFA, account-wide session revocation, persistent/shared rate limits and formal availability/recovery targets are not implemented. These remain product/operational decisions before exposing a real competition broadly. Complete advisory scanning and review/pin runtime images before release. No deployment provider or free-tier suitability has been selected or asserted.
