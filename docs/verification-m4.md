# M4 verification — 2026-11-09

M4 implemented and automated gates passed. Stop for review before M5. No commit/push; initial Git tree was clean. V1–V4 unchanged; no database reset, dependency installation or new infrastructure.

## Executed commands

- Read AGENTS and requirement/product/architecture/database/API/test/decision/progress specifications; inspected existing admission/idempotency/lifecycle/security, V1–V4 and test/frontend implementations.
- `git status --short`: initially clean.
- `python .tmp/verify-m3.py`: baseline Java21 Maven clean verify,88 tests,0 failures/errors/skips, BUILD SUCCESS,2m52s. This run used existing sources before M4 was copied to the build.
- `python .tmp/verify-m4.py`: intermediate runs120 tests/3m36s, then128 tests/3m43s; zero failures/errors/skips and executable packaging successful. Final run:133 tests, zero failures/errors/skips, BUILD SUCCESS,3m48s; executable JAR packaged.
- Flyway applied V5 to existing local PostgreSQL18.6 in0.094s, validated all five migrations. DatabaseIntegrationTest now asserts latest successful version5. V1–V4 diff is empty. No hand-created application tables or reset.
- `npm.cmd run typecheck` and `npm.cmd run build` in frontend passed after the invitation/request/pagination changes. Final source checks exited0; Vite transformed21 modules and built in182ms (JS251.41kB/gzip77.21kB, CSS5.53kB/gzip1.79kB).
- `git -c core.safecrlf=false diff --check`: passed. `git check-ignore .env`: confirms ignored. No credentials printed/changed or committed.

The ignored verification helper reads root .env privately, maps PostgreSQL settings to Spring variables and sets JDK21.0.13.9 in JAVA_HOME/PATH, TEAMFORGE_DATABASE_TESTS=true. It runs:

```powershell
& 'C:/Maven/apache-maven-3.9.12-bin/apache-maven-3.9.12/bin/mvn.cmd' `
  --offline --batch-mode --no-transfer-progress `
  -Dmaven.repo.local=D:/HackerRank/PS1/.tmp/m2-local `
  -Dteamforge.build.directory=D:/HackerRank/PS1/.tmp/backend-verify `
  -f backend/pom.xml clean verify
```

Copied cached artifacts and isolated ignored output retain the previous sandbox/IDE workaround; normal Maven/CI defaults and dependency versions are unchanged. No network retry/dependency replacement. Java default is not relied upon. Helper stdout is password-redacted and kept only in ignored .tmp.

## Evidence and boundaries

Real PostgreSQL is used throughout. Docker/Testcontainers is unavailable in this environment; no mocked locking/uniqueness substitutes. Existing M3 tests retain50 independent held connection/PID barriers. M4 public races use50 real distinct authenticated sessions with CSRF/keys and synchronized HTTP sends; each server call starts its own root transaction. Pool56 permits meaningful contention. HTTP status assertions distinguish one admission from completed losing calls, and verify roster/loser membership/audit state. No unauthenticated/test-only joining endpoint.

M4 covers private high-entropy/hash-only24h invitation generation, database-time expiry, rotation, rate limits at the real endpoint, pending uniqueness/multiple teams, applicant withdrawal, current-leader decisions, full-team pending preservation, event cleanup, legal transitions, paging, permissions/CSRF and audit visibility. Race cases include invite versus approval, rotation, withdrawal, leader leave/deletion, competing invitations/approvals and cross-team cleanup. Both actual HTTP response-loss tests wait for separately visible commit while consuming no response bytes, then replay original stored status/body. Outer rollback tests retain no membership/key/request-success audit. Expected fault-injected timeout recovery is reported separately from normal contention.

PostgreSQL18.6 still produces Flyway's warning that latest tested version is17. PostgreSQL17.7 container/remote GitHub Actions, runtime visual/keyboard review, scale seed, k6, deployment, SSE and suggestions were not run/implemented. No performance acceptance claim from these small race timings. Hosting/free-tier selection remains open. Existing test fixture cleanup uses exact owned identifiers; immutable audit snapshots remain.

## Final test results

All original88 tests remain passing;45 new M4 cases executed. Reports are in ignored .tmp/backend-verify/surefire-reports; sanitized full command output is in ignored .tmp/m4-verify.log.

| Test class | Tests | Failures/errors/skips | Seconds |
|---|---:|---|---:|
| ParticipantIntegrationTest | 28 | 0/0/0 | 51.19 |
| DatabaseIntegrationTest | 2 | 0/0/0 | 2.47 |
| HealthIntegrationTest | 3 | 0/0/0 | 1.161 |
| AuthRateLimitFilterTest | 1 | 0/0/0 | 0.033 |
| AdmissionWorkflowsIntegrationTest | 44 | 0/0/0 | 56.875 |
| InviteRateLimitIntegrationTest | 1 | 0/0/0 | 4.144 |
| MembershipConcurrencyIntegrationTest | 27 | 0/0/0 | 32.46 |
| TeamLifecycleIntegrationTest | 27 | 0/0/0 | 58.201 |
| **Total** | **133** | **0/0/0** | |

## Final concurrency outcomes

- Public50-way invitation race:277ms, exactly1 HTTP201 and49 TEAM_FULL409. Final roster2/configured maximum2;49 contenders without membership; one admission audit. No ordinary deadlock/timeout. All callers were genuine distinct eligible users with authenticated sessions/CSRF and unique keys. The synchronized timer starts after session preparation; this is correctness evidence, not the100-VU board latency benchmark.
- Public invite versus leader approval:32ms; one successful admission, one capacity conflict, maximum preserved. Other service races: rotation10ms, leader departure13ms, cleanup versus other-team deletion19ms, withdrawal19ms, same-user different invitations13ms, invite versus approval9ms, deletion5ms, concurrent approvals/cleanup11ms. All reported zero deadlocks/timeouts.
- Duplicate acceptance/redemption:9ms each, both calls replay the same result with one membership/admission audit.
- Deliberately blocked nested redemption: one real lock timeout, root retried the whole workflow and committed one admission. This is separate from normal race metrics.
- Retained M3 three50-connection/PID races:5077/99/79ms, exactly1 success each, zero normal deadlocks/timeouts. Deliberate M3 recovery observed one deadlock (three total attempts across two callers), one released timeout (5051ms) and three bounded sustained timeouts (15046ms). No success effects leaked on failure.
- Real unread-socket HTTP response-loss tests for both JOIN and ACCEPT replay the original stored201/200 outcome. Membership, resolved requests and both idempotency scopes commit together; audits remain immutable.

`docker info --format '{{.ServerVersion}}'` was reprobed: exit1, Docker configuration and daemon pipe access denied. Testcontainers could not run. Native PostgreSQL was used; the17.7 CI environment remains unverified. No application test failed in the baseline, intermediate or final runs. Final `git diff --check` passed.

## Files changed

- README.md
- backend/src/main/java/com/teamforge/platform/idempotency/IdempotentMutations.java
- backend/src/main/java/com/teamforge/platform/security/InviteRateLimitFilter.java
- backend/src/main/java/com/teamforge/platform/security/SecurityConfiguration.java
- backend/src/main/java/com/teamforge/platform/transactions/MutationTransactions.java
- backend/src/main/java/com/teamforge/teams/api/AdmissionController.java
- backend/src/main/java/com/teamforge/teams/application/AdmissionAuthorization.java
- backend/src/main/java/com/teamforge/teams/application/AdmissionWorkflows.java
- backend/src/main/java/com/teamforge/teams/application/MembershipAdmissionService.java
- backend/src/main/java/com/teamforge/teams/persistence/RequestResolution.java
- backend/src/main/java/com/teamforge/teams/persistence/TeamStore.java
- backend/src/main/resources/db/migration/V5__invitations_and_join_requests.sql
- backend/src/test/java/com/teamforge/platform/health/DatabaseIntegrationTest.java
- backend/src/test/java/com/teamforge/teams/AdmissionWorkflowsIntegrationTest.java
- backend/src/test/java/com/teamforge/teams/InviteRateLimitIntegrationTest.java
- docs/api-contract.md
- docs/architecture.md
- docs/database-design.md
- docs/decisions.md
- docs/implementation-plan.md
- docs/product-spec.md
- docs/progress.md
- docs/requirements.md
- docs/test-plan.md
- docs/verification-m4.md
- frontend/src/api/client.ts
- frontend/src/components/AdmissionPanel.tsx
- frontend/src/components/TeamDashboard.tsx

Working tree remains uncommitted (modified tracked files plus the new files listed above); nothing staged, committed or pushed. No unrelated files were changed. M5 is not started.
