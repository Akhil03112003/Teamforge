# M3 verification — 2026-11-09

M3 implemented and automated gates passed; stop before M4. No commit/push. Initial Git tree was clean. No dependencies, V1–V3 migrations, credentials or existing application data were replaced.

## Executed commands and outcomes

- `python .tmp/verify-m2.py` before changes: Java21 Maven clean verify, 61 tests, zero failures/errors/skips, BUILD SUCCESS, approximately 2m14s. This established the requested baseline.
- `docker info`: daemon access denied; Docker/Testcontainers unavailable. Native local PostgreSQL18.6 was used for every database test, with real transactions and connections.
- `python .tmp/verify-m3.py`: exit0, BUILD SUCCESS, 2m52s, executable JAR packaged. The ignored helper reads root `.env` privately, sets JAVA_HOME/PATH to installed JDK21.0.13.9 and maps PostgreSQL settings to DB_URL/DB_USERNAME/DB_PASSWORD. It enables TEAMFORGE_DATABASE_TESTS=true and executes:

```powershell
& 'C:/Maven/apache-maven-3.9.12-bin/apache-maven-3.9.12/bin/mvn.cmd' `
  --offline --batch-mode --no-transfer-progress `
  -Dmaven.repo.local=D:/HackerRank/PS1/.tmp/m2-local `
  -Dteamforge.build.directory=D:/HackerRank/PS1/.tmp/backend-verify `
  -f backend/pom.xml clean verify
```

The ignored copied Maven cache avoids sandbox read-only JAR access; isolated output avoids previously observed IDE-generated error stubs. Normal build defaults/dependency versions are unchanged. Helper logs redact the configured password and are not committed.

| Test class | Tests | Failures/errors/skips | Seconds |
|---|---:|---|---:|
| ParticipantIntegrationTest | 28 | 0/0/0 | 53.223 |
| DatabaseIntegrationTest | 2 | 0/0/0 | 2.345 |
| HealthIntegrationTest | 3 | 0/0/0 | 1.205 |
| AuthRateLimitFilterTest | 1 | 0/0/0 | 0.034 |
| TeamLifecycleIntegrationTest | 27 | 0/0/0 | 60.548 |
| MembershipConcurrencyIntegrationTest | 27 | 0/0/0 | 33.135 |
| **Total** | **88** | **0/0/0** | |

Flyway validated all four migrations and applied V4 to the existing public schema (0.065s). DatabaseIntegrationTest verifies the latest successful migration is version4. V1–V3 have no diff. PostgreSQL18.6 produces the known Flyway tested-version warning: this Flyway release lists PostgreSQL17 as latest tested. No database reset or manual application-table creation occurred. Fixture cleanup is limited to test-owned active records; immutable audit snapshots remain.

## Concurrent PostgreSQL results

Each synchronized contender holds its own READ COMMITTED transaction/connection before the start barrier; pg_backend_pid proves independent connections. Pool max56 is test-class-only. Barriers, futures and transactions have bounded deadlines. Tests assert legal outcomes and database invariants rather than a particular scheduling order.

| Last-seat repetition | Contenders/connections | Success / TEAM_FULL | Duration | Deadlocks/timeouts |
|---|---|---|---|---|
| 1 | 50/50 | 1/49 | 5214ms | 0/0 |
| 2 | 50/50 | 1/49 | 98ms | 0/0 |
| 3 | 50/50 | 1/49 | 100ms | 0/0 |

Every repetition asserted final count equals configured maximum2, all49 losers have no membership in that event/team, one admission audit and one completed admission record. This invokes the same production MembershipAdmissionService intended for M4, with actual current-leader authority and distinct eligible users. It is not an invitation HTTP-flow test; M4 must add that adapter and its security tests.

Other ordinary races reported zero database deadlocks/timeouts: same-user/two-teams30ms; create/admit12ms; admit/delete16ms; leave/successor-removal10ms; duplicate create10ms; duplicate admission7ms; admit/removal9ms; admit/leader-leave4ms. Duplicate calls both return success with the same recorded result while assertions prove one effect.

Separate deliberate fault tests observed one injected deadlock and three total transaction attempts across two callers; both completed after whole-transaction retry. A released lock caused one retry and succeeded in5071ms. Sustained lock contention caused three timeouts and bounded REQUEST_BUSY in15046ms with no successful membership/key/audit. These are expected fault injections, distinct from ordinary race metrics.

Coverage also proves replay after removal/deletion, changed-content rejection, caller scoping, normalized content, unauthorized/full failures, rollback without success audit, reusable failed keys and non-takeover of committed IN_PROGRESS. A real session/CSRF HTTP create was committed while its socket response was deliberately unread/discarded; same-key retry returned the recorded original201 result and created no duplicate team.

## Frontend and limitations

`npm.cmd run typecheck` and `npm.cmd run build` in frontend passed after the key-handling changes. A later attempt using bare `npm` was blocked by PowerShell script execution policy; invoking the installed `npm.cmd` avoids that policy without changing it. No dependencies were installed.

`git diff --check` passed (only Git LF/CRLF advisories). `git check-ignore .env` confirms the real environment file is ignored. V1–V3 diff is empty. No GitHub Actions, PostgreSQL17.7 container, browser visual/keyboard review, deployment, seed-scale check or k6 benchmark ran. M4 invites/requests and their transactional authorization/consumption, SSE and suggestions remain unimplemented.

## Changed files

New production files: platform/idempotency/IdempotentMutations.java, platform/transactions/MutationTransactions.java, teams/application/AdmissionAuthorization.java, teams/application/MembershipAdmissionService.java and migration V4__persist_idempotent_mutation_outcomes.sql (all under backend/src/main).

Changed production files: ApiExceptionHandler.java, TeamController.java, TeamService.java, TeamStore.java; frontend/src/api/client.ts and frontend/src/components/TeamDashboard.tsx.

Tests: new MembershipConcurrencyIntegrationTest.java; updated DatabaseIntegrationTest.java and TeamLifecycleIntegrationTest.java. Documentation: architecture, database-design, api-contract, test-plan, decisions, implementation-plan, progress and this report. Working tree remains uncommitted with these modifications/new files; no unrelated files were changed.
