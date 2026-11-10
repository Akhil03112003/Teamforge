# Release preparation (no deployment performed)

Hosting remains open pending verified free-tier memory/CPU, connection/storage, sleep/cold-start, HTTPS, SSE and backup constraints. This document prepares a portable single-instance release; it does not authorize deployment or claim a live URL.

## Build and runtime

With Node 24, Java 21, Maven and a disposable verification database available:

```powershell
npm --prefix frontend ci
npm --prefix frontend run typecheck
npm --prefix frontend test
npm --prefix frontend run build
# Set DB_URL/DB_USERNAME/DB_PASSWORD privately, then enable the PostgreSQL suites.
$env:TEAMFORGE_DATABASE_TESTS = 'true'
mvn --batch-mode --no-transfer-progress -f backend/pom.xml -Pweb clean verify
```

The optional `web` Maven profile copies the already-built `frontend/dist` into the JAR's static resources. Always build the frontend first; the default backend build remains independent. GET `/`, `/index.html` and `/assets/**` serve public application assets, while business APIs remain authenticated. This preserves one origin for sessions/CSRF/SSE and needs no Node runtime or CORS relaxation. Client navigation uses the existing hash state.

`Dockerfile` builds the frontend, packages the web JAR and runs on a Java 21 JRE as UID/GID 10001 with the `prod` profile. Build-stage tests are skipped intentionally; the full CI/test gate must pass before accepting that image. `.dockerignore` excludes local secrets, output, Git metadata and caches. Images have not been built here because Docker daemon access is denied. Verify/pin image digests and scan the built image before release.

```powershell
docker build --tag teamforge:m7 .
# Only after selecting/reviewing a platform: provide a private runtime environment file.
docker run --rm --env-file .env.production --publish 127.0.0.1:8080:8080 --name teamforge teamforge:m7
```

These are operator instructions, not executed deployment commands. Never commit `.env.production`. A platform may inject secrets directly instead. Do not apply the local environment loader or `local` profile in production.

## Required and optional environment

| Variable | Meaning/default |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod`; container default; rejects combination with `local` |
| `DB_URL` | Required JDBC PostgreSQL URL; production certificate-verified TLS (`sslmode=verify-full`) with the provider CA configured |
| `DB_USERNAME`, `DB_PASSWORD` | Required separate runtime credentials; no URL-embedded credentials |
| `FLYWAY_DB_URL`, `FLYWAY_DB_USERNAME`, `FLYWAY_DB_PASSWORD` | Optional dedicated migration connection; fall back to runtime settings for local use |
| `DB_POOL_MAX` | 10; budget pool + migration/admin connections against provider limits |
| `PORT` | 8080; private backend HTTP behind trusted HTTPS edge |
| `JAVA_TOOL_OPTIONS` | Optional platform-reviewed memory settings; never embed secrets |

`.env.production.example` contains placeholders only. For a distinct migration role, pre-provision least-privilege runtime grants (database CONNECT, schema USAGE, necessary table SELECT/INSERT/UPDATE/DELETE and sequence access). Audit rows must not be mutable. Grant access for future migrations through reviewed default privileges. Alternatively apply Flyway in a controlled release job and disable application Flyway only after successful migration validation. No production grants/roles were created in M7.

## Operational acceptance before release

- Verify managed PostgreSQL compatibility against CI's 17.7 baseline; local tests used 18.6 with a known Flyway tested-version warning. Back up existing data and rehearse restore; do not clean/reset a production database. V1–V6 remain unchanged and Flyway clean is disabled.
- Provision one JVM instance, private database networking, secret injection, a trusted HTTPS reverse proxy and secure cookies. Session state is in-memory and not persisted; restart invalidates sessions. Multiple replicas require architectural review.
- Edge requirements: HTTPS redirect/HSTS, request-size/rate/connection bounds, no request-body logging, no SSE response buffering, idle timeout above heartbeat (15 seconds), streaming support, correct disconnect propagation and restricted backend access. Verify CSRF, cookies and EventSource over the actual domain.
- Public GET `/api/v1/health`, `/actuator/health/liveness` and `/actuator/health/readiness` reveal no sensitive details. Liveness checks process state; readiness includes database connectivity. Initial startup must finish Flyway/validation before readiness is accepted.
- SIGTERM triggers graceful web shutdown (30-second phase budget); context close drains/closes SSE first. Give the platform a stop grace period above 30 seconds. Windows process termination smoke cleanup is not a verified Linux SIGTERM test; test signal/draining behavior on the selected platform.
- Retain operational request IDs and safe aggregate metrics; do not enable Hibernate bind/debug logging, log request bodies, passwords or invite codes. Default fallback logs omit exception messages and SQL values. Set log retention/access policy, audit retention, backup RPO/RTO and alerting before launch.
- Review query plans with the actual seed/data distribution and connection budget. Existing discovery indexes/set queries are not a p95 measurement. M8 must seed 10,000 users/2,000 valid teams and measure board p95 **below 200ms at 100 concurrent users** on the disclosed environment.
- Observe actual GitHub-hosted CI, run a dependency/image vulnerability review, then complete platform HTTPS/SSE/restart checks. No platform, live deployment, load result or submission artifact is delivered by M7.

See [CI](ci.md), [security](security.md) and [M7 verification](verification-m7.md).
