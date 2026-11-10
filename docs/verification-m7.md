# M7 verification — 2026-11-10

M7 code/configuration and feasible local gates are complete for review. Hosted GitHub Actions, Docker image construction and target-platform HTTPS/signal checks remain **unverified**. No M8 seed/load/deployment work, commit or push was performed.

## Starting state and environment

Read AGENTS and the current specifications/progress/M6 report; reviewed controllers, security, services/transactions, migrations, browser/API client and workflow. Git started clean on `main` at `42af67a` (`feat: complete TeamForge core platform and responsive UI`). Origin is `https://github.com/Akhil03112003/Teamforge.git`; recent commits include M5 `e1115e0`, M4 `3ce162a`, M3 `8287c09` and M1/M2 `79f6fa4`.

Explicit Temurin JDK21.0.13.9 and Maven3.9.12 were used. Default machine Java24 was not used to verify this milestone. Real native PostgreSQL18.6 at localhost5432 used the existing root `.env` privately. Secrets were not printed/changed. Known Flyway warning: PostgreSQL18 is newer than its tested support17; successful local tests do not verify CI's17.7 image. Docker daemon access is denied. Existing ignored Maven cache/output isolation avoids read-only user cache/IDE output interference. Frontend checks used Node24.19.0 and installed Chrome.

## Executed commands and observed results

| Command/check | Actual outcome |
|---|---|
| `python .tmp/verify-m6.py` (baseline helper; Java21 Maven clean verify) | **171 tests, 0 failures/errors/skips**, BUILD SUCCESS, **4m05s** |
| `npm.cmd --prefix frontend test` (baseline) | **15 passed**, 0 failed/skipped, **17.003s** |
| `python .tmp/verify-m7.py` (initial hardening) | **194 tests, 0 failures/errors/skips**, BUILD SUCCESS, **4m03s** |
| `python .tmp/verify-m7.py` (final backend, `-Pweb clean verify`) | **195 tests, 0 failures/errors/skips**, BUILD SUCCESS, **4m05s** |
| Flyway during full real-PostgreSQL startup | **Validated all6 migrations**, schema up to date; no migrations changed/added/reset |
| `npm.cmd --prefix frontend ci --offline --cache D:/HackerRank/PS1/.tmp/npm-cache --no-audit --no-fund` | **Failed EPERM** unlinking a locked native rolldown module; did not stop unrelated processes |
| `npm.cmd --prefix frontend install --offline --cache D:/HackerRank/PS1/.tmp/npm-cache --no-audit --no-fund` | Restored missing local dependencies, exit0 (20 added/5 changed); native cleanup warning; **no manifest/lockfile content change** |
| Copy package.json/lockfile into `.tmp/m7-frontend-install/`; `npm.cmd --prefix .tmp/m7-frontend-install ci --offline --cache D:/HackerRank/PS1/.tmp/npm-cache --no-audit --no-fund` | **Passed**, 25 packages installed in2s in a fresh directory |
| `npm.cmd --prefix frontend run typecheck` | **Passed**, final exit0 |
| `npm.cmd --prefix frontend test` (final) | **16 passed, 0 failed/cancelled/skipped**, **13.047s** |
| `npm.cmd --prefix frontend run build` (final) | **Passed**,25 modules,172ms; JS265.12kB/gzip80.86kB, CSS12.35kB/gzip3.54kB |
| `python .tmp/package-m7.py` | Repackaged the final frontend into web JAR, **BUILD SUCCESS,5.157s**, tests intentionally skipped because backend195-case gate already passed |
| `python .tmp/m7-validate.py` | YAML parsed, backend/frontend jobs and push/PR/dispatch triggers verified; POM XML valid; JAR contains production HTML/JS |
| `python .tmp/m7-e2e.py` (final) | **Passed**, genuine PostgreSQL + packaged same-origin web JAR, desktop1440/mobile390 browser journeys, **40.341s** including startup/cleanup |
| `python .tmp/m5-smoke.py` (rerun for M7) | **Passed**, HTTP/Vite proxy board/SSE/reconnect/suggestion smoke, **30.641s** including startup/cleanup |
| `docker info --format '{{.ServerVersion}}'` | **Blocked**, Docker config/daemon pipe access denied; no image build claimed |
| `gh run list --repo Akhil03112003/Teamforge --limit 5` | **Blocked**, sandbox TCP connection to api.github.com443 denied; hosted status unverified |
| `git diff --check` (final) | Passed; line-ending conversion notices only |
| `git diff --name-only -- backend/src/main/resources/db/migration`; `git check-ignore .env .tmp/m7-verify.log` | No migration changes; local secrets/logs ignored |

The private helper scripts/logs remain ignored, not release dependencies. Backend helper executed this exact Maven argument list with Java21 and privately loaded DB environment:

```text
mvn --offline --batch-mode --no-transfer-progress -Dmaven.repo.local=D:/HackerRank/PS1/.tmp/m2-local -Dteamforge.build.directory=D:/HackerRank/PS1/.tmp/backend-verify -f backend/pom.xml -Pweb clean verify
```

Initial hardening omitted `-Pweb`. Final repackage substituted `-Pweb -DskipTests package`. Fresh-terminal normal commands (with accessible dependencies) are in [local development](local-development.md), [CI](ci.md) and [deployment](deployment.md). No GitHub-hosted result is inferred from these local commands.

## Regression and race evidence

All original171 backend tests remain active. Added24: RequestLimitsFilterTest8, ProductionGuardTest4, SessionCapacityIntegrationTest1, StreamShutdownTest1, SecurityHardeningIntegrationTest10. Cases cover bounded fixed/chunked bodies, form ambiguity/URL credentials, CSRF mutation matrix, nonleader authorization, event scoping, cross-caller idempotency isolation, safe headers/media errors, production guards, session exhaustion and stream shutdown.

Final public50-way invitation race: **1 HTTP201,49 TEAM_FULL409**, roster2 at configured maximum2, all49 losing contenders without membership,312ms, no deadlocks/lock timeouts. Mixed public invite-versus-approval: one success,30ms. Retained M3 races used50 independent connections: one success each,5546/89/74ms, no ordinary-race deadlocks/timeouts. Separate fault-injection recovery cases intentionally exercised deadlock and bounded lock timeouts; these are expected recovery tests, not spontaneous race failures. No M3 locking or persisted idempotency semantic change.

Browser final16 cases preserve the existing15 plus invitation success announcement through authoritative membership/SSE reconciliation. Genuine packaged-browser flow covered registration/login/profile/team creation/invite rotation/request/withdraw/accept/decline/removal/redemption/succession/final deletion/session restoration/logout. It verified empty local/session storage and real SSE-driven successor authority. Proxy smoke separately received ready/invalidate, checked committed board/capacity/suggestions, reconnected and refetched authoritative state. Only owned QA processes and synthetic active fixtures were cleaned; immutable audit history and existing data remain.

## Failures found and corrected

- First packaged browser run timed out waiting for redemption success. Admission committed, but changed membership altered the requests-panel React key and erased its announcement. Stable requests-panel identity plus a new browser regression fixed it; final full flow passed.
- First16-case browser run had15 passes/1 failure (13.685s): enabled-button text transitioned immediately while the old disabled background faded, briefly producing1.30:1 contrast. Removed background transition; the final16-case run passed without weakening assertions.
- Local npm ci failed on an externally held native module; restored dependencies and demonstrated clean lockfile installation separately. No process belonging to another session was terminated.
- Two ad hoc validation invocations had PowerShell/Python quoting errors before checks ran; corrected script-based validation passed. A documentation whitespace check found an extra CSS EOF blank line; corrected and rechecked. These were not application test results.

## Changed files

- CI/release: `.github/workflows/ci.yml`, `Dockerfile`, `.dockerignore`, `.env.production.example`, `backend/pom.xml`.
- Backend: `live/EventStreamHub.java`, `platform/api/ApiExceptionHandler.java`, `platform/security/{AuthRateLimitFilter,SecurityConfiguration,ProductionGuard,RequestLimitsFilter,WebServerHardening}.java`, `application.yml`, `application-prod.yml` (under the existing main Java/resources roots).
- Tests: `platform/security/{ProductionGuardTest,RequestLimitsFilterTest,SessionCapacityIntegrationTest,StreamShutdownTest}.java`, `teams/SecurityHardeningIntegrationTest.java` under backend test root.
- Frontend: `src/components/TeamDashboard.tsx`, `src/styles.css`, `tests/ui.test.mjs`.
- Documentation: README; docs/api-contract, architecture, ci, decisions, deployment, implementation-plan, local-development, progress, requirements, security, test-plan and this report.

## Remaining gates

Remote Actions **unverified**; [exact dispatch/watch/log commands](ci.md) are provided after reviewed changes are approved/committed/pushed. No commit/push/dispatch performed. Repository remains on main with M7 work uncommitted; origin/HEAD unchanged. Credentials/logs/generated output remain ignored.

Docker image build, Linux PostgreSQL17.7 execution, formal CSO assessment (required launcher missing), online dependency/image advisory scan, production TLS/edge/session/SSE and platform SIGTERM behavior are not verified. In-memory session restart invalidation, per-process limits and single-instance notifications are documented limits. Hosting/free-tier constraints, least-privilege role/grants, secret injection, backups/RPO/RTO and alerting require release review. No manual screen-reader/full WCAG/cross-browser certification is asserted. M8 large seed/p95<200ms at100 users and M9 deployment/submission remain unstarted.
