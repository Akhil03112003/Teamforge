# M8-B verification - 2026-11-10

Production deployment **preparation only**. No resource provisioning, paid service, registry push, public deployment, commit or push. Provider selection/approval remains required. Local results below are not production verification.

## Executed commands and results

Windows, Java21.0.13.9, Maven3.9.12, native PostgreSQL18.6, existing local credentials loaded privately from ignored `.env`. Docker daemon access was denied; native PostgreSQL used instead. Repository began clean at the M8-A optimization handoff; existing application/data preserved.

| Check | Actual result |
|---|---|
| Java21 `clean verify`, web packaging |206 tests,0 failures/errors/skips; BUILD SUCCESS;4m33s |
| Public50-contender regression |1 HTTP201,49 TEAM_FULL409; maximum roster2;49 losers without membership;354ms; no deadlocks/timeouts |
| Frontend typecheck |Passed |
| Frontend browser tests |16 passed,0 failed/skipped;13.391s |
| Frontend production build |Passed;25 modules;239ms |
| Readiness probe |Java21 compilation passed; exit1 before owned startup, exit0 when ready |
| Isolated production-like startup |Packaged web JAR, `prod,proxy`, pool10; V1-V6 applied successfully |
| Certificate-verified HTTPS smoke |Passed8 checks;61.031s; same-origin HTML/JS/CSS/REST/SSE |
| SSE |Committed mutation invalidation plus authoritative REST, ready and two heartbeats15.000s apart through local TLS proxy |
| Graceful context shutdown |Held SSE reached EOF, JVM exit0;12.0s including attach helper; not a Linux SIGTERM test |
| Benchmark fixture verification |Exactly10000 users/profiles,2000 teams,6000 memberships; zero capacity/duplicate/leader violations; fingerprint unchanged |
| Development public counts |Before/after users0,teams0,members0,profiles0,audits5667; unchanged |

The206 total preserves201 and adds3 production-guard cases plus2 real HTTP trusted/untrusted forwarding cases. Cookie checks verify Secure/HttpOnly/SameSite=Lax without printing cookies. The HTTPS smoke exercises login/session restoration, missing-CSRF403, board reads, team mutation/invalidation, final-member cleanup and logout401. Synthetic fixtures/audits remain only in isolated smoke schemas. No normal database reset or data deletion occurred.

Commands executed (local absolute paths are verification workarounds, not CI requirements):

```powershell
# Private helper sets JAVA_HOME, isolated JDBC/Flyway schemas and database-test flag.
python .tmp/m8-b-full-verify.py
# Exact Maven invocation inside that helper:
& 'C:/Maven/apache-maven-3.9.12-bin/apache-maven-3.9.12/bin/mvn.cmd' `
  --offline --batch-mode --no-transfer-progress `
  -Dmaven.repo.local=D:/HackerRank/PS1/.tmp/m2-local `
  -Dteamforge.build.directory=D:/HackerRank/PS1/.tmp/backend-verify `
  -f backend/pom.xml -Pweb clean verify
npm.cmd --prefix frontend run typecheck
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
& 'C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot/bin/javac.exe' `
  -d .tmp/m8-b/healthcheck deployment/HealthCheck.java
python -m py_compile scripts/deployment/smoke.py
python .tmp/m8-b/run-https-smoke-retry.py
powershell -NoProfile -ExecutionPolicy Bypass -File .tmp/m8-a-final-verify.ps1
git diff --check
```

The ignored helper created `teamforge_benchmark_regression_m8b` and fresh `teamforge_benchmark_m8b_smoke` / `_retry` schemas; it never selected public as a migration target. It binds the packaged JVM to8083 and a Python TLS edge to8443, with upstream source127.0.0.2 and an exact trust regex. The local test certificate has localhost/IP SANs and is explicitly trusted by the test client; TLS verification was not disabled or machine trust changed. Root credentials, generated synthetic passwords, certificates/private keys and raw logs remain ignored.

Reusable smoke command executed by that helper:

```powershell
# SMOKE_EMAIL/PASSWORD supplied privately, not command-line arguments.
python scripts/deployment/smoke.py --base-url https://localhost:8443 `
  --ca-file .tmp/m8-b/tls/localhost.crt --authenticated `
  --event 10000000-0000-4000-8000-000000000001 --write-fixture `
  --output .tmp/m8-b/https-smoke.json
```

Initial smoke also passed, but its shutdown harness incorrectly expected no buffered SSE bytes. It failed after a clean JVM shutdown. The corrected harness drains pending bytes and verifies EOF; the entire flow then passed in a fresh schema. Shutdown used a local-only admin/JMX context close, enabled only in the owned verification JVM; production does not enable it. All owned JVM/proxy processes were stopped.

Safe machine-readable evidence: [backend totals](../deployment/verification/backend-tests.json), [HTTPS smoke](../deployment/verification/https-smoke.json), [startup/shutdown/data counts](../deployment/verification/local-production-like.json), [fixture integrity](../deployment/verification/seed-integrity.json). Raw ignored logs: `.tmp/m8-b-full-verify.log`, `.tmp/m8-b-browser.log`, `.tmp/m8-b/application.log`.

## Changes and reasons

- `Dockerfile`, `deployment/HealthCheck.java`: dependency-free runtime readiness probe; existing non-root UID10001, exec Java and SIGTERM preserved.
- `application-proxy.yml`, `ProductionGuard.java`: explicit opt-in native forwarding, required reviewed peer regex, reject unsafe combinations and JDBC migration credentials in URLs. Default forwarding remains disabled. Regex validation cannot prove network ownership; private origin and sanitized headers remain mandatory.
- `ProductionGuardTest.java`, `TrustedProxyIntegrationTest.java`: regression coverage for configuration failures, trusted HTTPS handling and spoofed untrusted forwarding.
- `.env.production.example`: placeholder verified-TLS JDBC/CA configuration and proxy guidance; no credentials.
- `deployment/nginx.conf.example`: reviewable reference edge settings, streaming/no buffering and overwrite of forwarding headers; not an executed Nginx deployment.
- `scripts/deployment/smoke.py`: certificate-verifying same-origin/auth/CSRF/SSE smoke; explicit optional synthetic writes; no external dependency.
- `deployment/verification/*.json`: sanitized local evidence.
- `docs/deployment.md`, `docs/verification-m8-b.md`, `docs/progress.md`, `docs/decisions.md`: checklist, commands, rollback, accurate status and remaining approvals.

## Unverified / release gates

Docker daemon/config access denied: no image build, container UID/read-only execution or real container SIGTERM result. Run the exact Docker commands in [deployment.md](deployment.md) in a permitted terminal after review. Nginx reference syntax/runtime, provider edge/CDN/network trust, managed PostgreSQL certificate verification/least-privilege grants, backups/restore, image/advisory scan, actual hosted CI and deployment remain unverified. Native PG18 triggers the existing Flyway tested-version warning; CI PostgreSQL17.7 was not run here. A local Python edge is not evidence for provider buffering/idle behavior.

No k6 rerun was needed/performed for this deployment-preparation scope. The unchanged M8-A100-VU result remains p9542.566ms,100% success,1103.344rps; it is not a production latency claim. V1-V6, M3 lock/idempotency, datasource pool10 and benchmark workload are unchanged.

**Handoff:** local M8-B preparation gates passed; public release is blocked on hosting/domain/budget selection, explicit resource/deployment approval and the unverified gates above. Stop before provisioning/deployment/M9.
