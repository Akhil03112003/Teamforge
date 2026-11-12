# Deployment preparation — M8-B

**No provider, paid resource, public deployment, registry push or live URL has been created.** Local verification is in [verification-m8-b.md](verification-m8-b.md). Hosting selection and explicit approval remain required. The passing M8-A local benchmark is not a production capacity guarantee.

## Hosting requirements

| Requirement | Acceptance before selection |
|---|---|
| Runtime | Java21 executable JAR or OCI/Linux image; one always-on instance during an event; no automatic horizontal scaling |
| Resources | Measured JVM/native/health-probe headroom;1GiB is a starting budget to evaluate, not a capacity promise; verify free-tier CPU/RAM/sleep limits |
| HTTPS edge/domain | Valid certificate/renewal, HTTPS redirect/HSTS, private origin networking, documented immediate proxy IPs/header handling |
| Streaming | SSE permitted, buffering/cache/compression disabled for streams, idle timeout>15s heartbeat (75s reference), reconnect/drain behavior tested |
| PostgreSQL | Compatible managed PostgreSQL (CI target17.7; local18.6 has a Flyway tested-support warning), direct JDBC endpoint, certificate-verified TLS, persistent storage |
| Connections | Pool remains10; reserve migration/operator/monitoring headroom; account for platform limits and any release overlap |
| Lifecycle | Readiness and liveness probes, startup allowance, SIGTERM to PID1 and stop grace>=45–60s |
| Operations | Secret injection, immutable release identity, restricted logs, backups/PITR and tested restore/RPO/RTO, image/advisory review |

In-memory sessions, rate limits and SSE notifications are single-instance. Restart invalidates sessions; clients sign in again, obtain fresh CSRF tokens and reconcile REST state. A multi-replica/zero-downtime design is not silently approved. Review any provider transaction-pooling endpoint for JDBC/session/transaction compatibility; prefer a direct connection initially.

## Artifact and local image checks

Build frontend first; the Maven `web` profile embeds `frontend/dist` in the executable JAR. Same-origin `/`, `/assets/**`, `/api/v1/**` and authenticated SSE use relative URLs; no separate Node runtime or CORS expansion is needed. Navigation uses hash state.

```powershell
npm --prefix frontend ci
npm --prefix frontend run typecheck
npm --prefix frontend test
npm --prefix frontend run build
# Set Java21 and an isolated test DB/schema, never a production DB.
$env:TEAMFORGE_DATABASE_TESTS = 'true'
mvn --batch-mode --no-transfer-progress -f backend/pom.xml -Pweb clean verify
# Local image commands only; no registry or deployment side effects.
docker build --tag teamforge:m8b .
docker image inspect teamforge:m8b --format '{{.Config.User}} {{json .Config.Healthcheck}}'
docker run --rm --entrypoint id teamforge:m8b
```

Dockerfile is multi-stage Node24/Maven-JDK21/JRE21. Runtime UID/GID10001, exec-form Java entrypoint and SIGTERM are explicit. A JRE-only compiled `deployment/HealthCheck.java` probes readiness on loopback using PORT (30s interval/5s timeout/90s start period/3 retries). No curl/runtime package manager or credentials enter the image. `compose.yml` provisions local PostgreSQL only; it is not a production deployment definition. Never remove its existing volume to prepare deployment.

Docker daemon access was denied here: image construction, Linux UID behavior and actual container signals remain unverified. Base-image tags are mutable; resolve reviewed digests, scan the built image and record its immutable ID/digest before release. Build-stage tests are skipped deliberately; acceptance relies on the full test gate, not the existence of a JAR/image. Pending work must be reviewed/committed with approval before assigning a source release SHA; no commit/push was performed here.

## Runtime environment and proxy trust

Start from placeholder-only `.env.production.example`; inject actual values privately through the selected platform. Never commit a populated file, print environment/connection strings with credentials, or pass passwords in argv/JDBC URL queries. Privileged container operators can inspect environment secrets, so restrict platform access.

| Setting | Meaning |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Secure/HttpOnly/SameSite=Lax cookies; fails with local profile or insecure flags |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Required JDBC connection and separate runtime credentials; production URL uses `sslmode=verify-full&sslrootcert=/run/secrets/postgres-ca.crt` |
| `FLYWAY_DB_URL`, `FLYWAY_DB_USERNAME`, `FLYWAY_DB_PASSWORD` | Optional migration connection, defaults to DB settings; explicitly verify the same intended DB/schema when using separate credentials |
| `DB_POOL_MAX=10`, `PORT=8080` | Current approved defaults; platform port and health probe must agree; use an unprivileged port |
| `SPRING_FLYWAY_ENABLED` | Default true; disable only after an approved separate migration job has validated/applied the release |
| `JAVA_TOOL_OPTIONS` | Optional reviewed memory controls; no secrets; do not copy benchmark overrides blindly |
| `TRUSTED_PROXY_REGEX` | Required only with `prod,proxy`; reviewed Java regex of actual immediate edge peers |

Mount the provider CA certificate read-only at the URL's explicit path, readable by UID10001; Java's non-root home need not contain an implicit PostgreSQL trust file. Never use `sslmode=require`/trust-all as equivalent to host/certificate verification. Local native PostgreSQL smoke did not verify managed-database TLS.

Default forwarded-header strategy stays **none**. If the edge enforces HTTPS/HSTS and identity/rate controls itself, leave it that way until trust is reviewed. For framework awareness of external HTTPS/client IP, explicitly select `prod,proxy` and set the reviewed peer regex. That profile uses Tomcat native forwarding, disables context-root redirect and honors only the configured immediate peers. Production startup rejects unreviewed framework forwarding, missing/invalid proxy regex and obvious unrestricted patterns. The guard cannot validate real network ownership: reviewing the regex, private networking and all proxy hops is still required.

The edge must **overwrite**, not append untrusted client values, for X-Forwarded-For/Proto/Host/Port; clear RFC Forwarded unless deliberately configured. Never trust all sources, all private networks or user-supplied headers on a public origin. Real-IP configuration with an extra CDN/LB requires another reviewed trust chain. Correct client-IP derivation matters for the existing authentication/invite/bootstrap rate limits.

Reference first-public-edge configuration: [nginx.conf.example](../deployment/nginx.conf.example). Replace the reserved domain/cert paths/upstream, bind the origin privately, validate with `nginx -t`, then test on the actual provider. The example was source-reviewed, not run in Nginx here. It sets HTTP/1.1, no SSE buffering/cache/gzip,75s timeouts and fixed forwarding headers; the app also emits `X-Accel-Buffering: no` and no-store. A local verified-certificate Python TLS proxy exercised this behavior, not a real hosting edge.

## Managed PostgreSQL provisioning (admin/operator action after approval)

Prefer a new dedicated database and restricted network path. Use provider-approved admin tools/roles; do not point the seed generator at production and do not reuse a development DB. Before routing users: enable backups/PITR, choose RPO/RTO/retention and rehearse restore into a separate target. These choices remain open.

Example SQL outline, **not executed here**, for a provider that allows role/database creation:

```sql
CREATE ROLE teamforge_migrator LOGIN;
CREATE ROLE teamforge_runtime LOGIN;
-- In interactive psql over verified TLS, use \password for each role;
-- do not put plaintext passwords in SQL files, shell history or logs.
CREATE DATABASE teamforge OWNER teamforge_migrator;
REVOKE ALL ON DATABASE teamforge FROM PUBLIC;
GRANT CONNECT ON DATABASE teamforge TO teamforge_migrator, teamforge_runtime;
-- Connect to the new teamforge database as its owner/admin:
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO teamforge_runtime;
```

Apply unchanged migrations using the approved migrator before runtime grants. If a Flyway CLI is used, match the artifact's Flyway11.7.2 and pass secrets via its environment (CLI not installed/executed here):

```powershell
# In the private migration job; set FLYWAY_URL/USER/PASSWORD through secret injection.
flyway -locations=filesystem:backend/src/main/resources/db/migration validate
flyway -locations=filesystem:backend/src/main/resources/db/migration migrate
flyway info
```

After V1–V6, grant only the runtime operations currently needed:

```sql
GRANT SELECT ON hackathons TO teamforge_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON users,profiles,profile_skills,
 profile_availability,event_participants,teams,team_members,
 idempotency_records,team_invitations,join_requests TO teamforge_runtime;
GRANT SELECT,INSERT ON audit_events TO teamforge_runtime;
REVOKE UPDATE,DELETE,TRUNCATE ON audit_events FROM teamforge_runtime;
REVOKE ALL ON flyway_schema_history FROM teamforge_runtime;
-- Runtime must not own schema/tables, have CREATE, superuser or migration-role membership.
```

Apply scoped grants explicitly in each reviewed future migration release; avoid broad future-table grants that reopen audit/history privileges. Immutable-audit triggers are defense in depth. Current UUID tables require no runtime sequence grant; review any later sequence separately. Verify negative privileges with `has_table_privilege` and `has_schema_privilege` before exposing traffic. Managed services with constrained admin permissions require provider-specific equivalents; these role/grant checks are not claimed verified locally.

Migration modes are a choice for provider review: startup Flyway can use separate migration credentials, but the JVM then possesses a DDL secret; a private release job plus `SPRING_FLYWAY_ENABLED=false` removes that secret from the runtime and must finish successfully before startup. Default startup migration behavior is preserved and verified locally on an isolated schema. Never disable migration checks to hide failure, use Flyway clean, edit V1–V6 or restore an older DB merely to roll back an application.

## Launch and smoke commands — only after provider/resource approval

The exact provider CLI/API cannot be finalized until the provider, domain, region, secret store, resource/cost limits and networking are selected. The portable runtime shape is:

```powershell
# Use a private env file for the APPROVED isolated/staging target and mount its real CA.
# For Docker Desktop DB access, localhost inside the container is not the host DB;
# use the reviewed host.docker.internal/private hostname and verified certificate name.
docker run --detach --name teamforge --env-file .env.production `
  --publish 127.0.0.1:8080:8080 --stop-timeout 60 `
  --cap-drop ALL --security-opt no-new-privileges:true `
  --read-only --tmpfs /tmp:rw,nosuid,size=128m `
  --mount type=bind,src=ABSOLUTE_PROVIDER_CA_PATH,dst=/run/secrets/postgres-ca.crt,readonly `
  teamforge:m8b
docker inspect teamforge --format '{{.State.Health.Status}}'
curl.exe --fail --max-time 5 http://127.0.0.1:8080/actuator/health/readiness
# Public, certificate-verified, same-origin check (Python3 stdlib on operator machine):
python scripts/deployment/smoke.py --base-url https://YOUR_APPROVED_DOMAIN --output .tmp/public-smoke.json
```

An immutable registry digest should replace the local tag in the actual approved release; no registry push was done. Read-only/tmpfs flags and image UID/health behavior must be tested with a working Docker daemon before deployment. Mount TLS keys only at the edge, not in the application image.

For authenticated checks, privately set SMOKE_EMAIL/SMOKE_PASSWORD for an approved **synthetic smoke account**, then:

```powershell
python scripts/deployment/smoke.py --base-url https://YOUR_APPROVED_DOMAIN `
  --authenticated --event APPROVED_HACKATHON_UUID --output .tmp/authenticated-smoke.json
# OPTIONAL, separately approved writes: adds one team then leaves its final seat;
# immutable audit snapshots remain. Do not run against an existing participant account.
python scripts/deployment/smoke.py --base-url https://YOUR_APPROVED_DOMAIN `
  --authenticated --event APPROVED_HACKATHON_UUID --write-fixture --output .tmp/write-smoke.json
```

No TLS-verification bypass exists. `--ca-file` is available only when intentionally trusting a private test CA; never use insecure skip flags. Checks include health, embedded HTML/JS/CSS, HSTS/CSP, cookie flags, login/restoration, missing-CSRF403, board, SSE ready/two spaced heartbeats and logout invalidation. The optional write fixture verifies committed invalidation/REST/final-member cleanup. Smoke output excludes passwords/session/CSRF/invite tokens. See [actual local results](verification-m8-b.md).

## Release checklist and rollback

Before enabling public routing:

1. Review/approve and commit the intended source; observe hosted CI (local206-test success is not hosted CI). Build/scan the exact image, record source SHA+image digest+environment version without secret values.
2. Verify provider pricing/free-tier limits, one-instance behavior/no sleep, database TLS/network/connection budget, role privileges, backup/restore and migration mode.
3. Confirm migration validation/checksums and schema version6, startup logs without secrets, readinessUP; ensure runtime cannot mutate audits/history or DDL.
4. Validate origin isolation, real proxy peer rules, HTTPS redirect/HSTS/cookies/CSRF, same-origin assets and live SSE across idle/reconnect/session expiry. Test actual HTTP versions/CDN paths, rate limits and appropriate startup/health behavior.
5. Test non-root/read-only container behavior, real SIGTERM, ingress drain and stop grace>=45–60s. Container readiness health is not a replacement for liveness; do not restart-loop the JVM merely because the DB is temporarily unavailable.
6. Run smoke checks, inspect safe operational logs/alerts and set access/retention policy. Do not enable bind/request-body/HTTP debug logging. Re-run provider performance separately before claiming production p95.

For rollback, retain last-known-good image digest, compatible configuration, current Flyway history and a verified backup/PITR point. Remove/drain ingress, stop the owned JVM with SIGTERM and its grace period, then start the reviewed previous image/config against the **same compatible DB**. With current unchanged V1–V6, compatibility can be checked; do not assume compatibility after future schema changes. Verify readiness, HTTPS/auth/CSRF/board/SSE and review safe logs before reopening traffic. Sessions reset; membership/audit/idempotency remain authoritative in PostgreSQL. No down migrations, clean/reset or deletion of replay outcomes. Database restore is a separate incident decision with explicit approval because it can discard committed memberships/audits/outcomes. No rollback was executed here.

Blockers for public release: provider/domain/region/budget selection; approved credentials and CA; Docker image/non-root/signal check; hosted CI/image/advisory review; managed-DB privilege/TLS/backup verification; actual edge/network/SSE validation. Stop for selection and explicit approval before provisioning or deployment.
