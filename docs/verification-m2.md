# M2 verification — 2026-11-09

## Changed files
- README.md
- backend/src/main/java/com/teamforge/platform/api/ApiExceptionHandler.java
- backend/src/main/java/com/teamforge/platform/security/SecurityConfiguration.java
- backend/src/main/java/com/teamforge/teams/api/TeamContracts.java
- backend/src/main/java/com/teamforge/teams/api/TeamController.java
- backend/src/main/java/com/teamforge/teams/application/TeamService.java
- backend/src/main/java/com/teamforge/teams/persistence/TeamStore.java
- backend/src/main/resources/db/migration/V3__create_team_lifecycle.sql
- backend/src/test/java/com/teamforge/platform/health/DatabaseIntegrationTest.java
- backend/src/test/java/com/teamforge/teams/TeamLifecycleIntegrationTest.java
- docs/api-contract.md
- docs/architecture.md
- docs/database-design.md
- docs/decisions.md
- docs/implementation-plan.md
- docs/local-development.md
- docs/product-spec.md
- docs/progress.md
- docs/requirements.md
- docs/test-plan.md
- docs/verification-m2.md
- frontend/src/App.tsx
- frontend/src/api/client.ts
- frontend/src/components/TeamDashboard.tsx
- frontend/src/styles.css

## Scope and baseline
Read AGENTS.md and the requested progress/requirements/product/architecture/database/API/test/decision files; inspected auth/profile code, migrations, frontend client and tests. Initial Git tree was clean on master at 857528f. Before coding, reran M1 Java21 clean verify: 34 tests, 0 failures/errors/skips, executable packaging successful. No unrelated changes were overwritten.
Implemented only team domain/lifecycle and minimal dashboard. No direct join, invite/request approval, SSE, suggestions, Redis/Kafka, commit or push.

## Exact checks and results
Environment: Temurin JDK 21.0.13.9, Maven 3.9.12, PostgreSQL 18.6, Node 24.19.0, existing Vite/npm dependencies. Root .env was read privately into the child process; DB_URL/DB_USERNAME/DB_PASSWORD and TEAMFORGE_DATABASE_TESTS=true set there, without printing secrets. The existing ignored workspace artifact cache/output workaround was reused; no dependencies installed/replaced.

Final command from repository root with JAVA_HOME/PATH set to JDK21:
```powershell
mvn --offline --batch-mode --no-transfer-progress '-Dmaven.repo.local=D:/HackerRank/PS1/.tmp/m2-local' '-Dteamforge.build.directory=D:/HackerRank/PS1/.tmp/backend-verify' -f backend/pom.xml clean verify
```
Exit0 / BUILD SUCCESS. 24 main sources and five test sources compiled for release21. Final output:
```text
ParticipantIntegrationTest:       28 tests, 0 failures, 0 errors, 0 skipped
DatabaseIntegrationTest:           2 tests, 0 failures, 0 errors, 0 skipped
HealthIntegrationTest:             3 tests, 0 failures, 0 errors, 0 skipped
AuthRateLimitFilterTest:           1 test,  0 failures, 0 errors, 0 skipped
TeamLifecycleIntegrationTest:     27 tests, 0 failures, 0 errors, 0 skipped
TOTAL:                           61 tests, 0 failures, 0 errors, 0 skipped
```
Surefire XML under .tmp/backend-verify/surefire-reports independently confirms these counts. Executable JAR repackaged successfully. The initial M2 full run also passed61; repeated after the participant lock-mode refinement to validate final inputs. Final run duration2:15; not a benchmark.
Flyway applied V3 to the existing local database, then validated V1–V3. No tables were manually created. git diff --exit-code against V1/V2 returned0. Existing data was not reset; only test-owned fixtures were removed by exact IDs, while immutable audit snapshots intentionally remain. Native PostgreSQL18.6 triggers Flyway's existing warning that tested support ends at17; PostgreSQL17.7/remote CI not executed here.

From frontend/:
```powershell
npm.cmd run typecheck
npm.cmd run build
```
Both exited0. Final Vite output: 20 modules transformed; production JS245.01kB, CSS5.53kB; build167ms. This is build duration, not board latency. Reran after the successful-leave UI reconciliation change; no backend changes after the final clean verify.

## Packaged local PostgreSQL/proxy smoke
Ran an ignored helper using the final executable JAR on8081 with explicit local HTTP profile, Vite on5174 with BACKEND_ORIGIN=http://127.0.0.1:8081. Existing8080/5173 processes and the PostgreSQL service were preserved. Passwords/cookies were not printed; a generated synthetic account was removed by its returned ID after leaving its team. Its immutable audit rows remain.
Observed through the real frontend proxy:
- Health/readiness200; registration201; login204.
- Profile save and session restoration200 (M1 regression).
- Hackathon listing/my-team/roster/detail-update/audit200; team creation201 with creator leader/member and INCOMPLETE state.
- Duplicate creation409; leader self-removal409.
- Last-member leave200 with deleted=true; deleted team404; authorized retained history200 with four lifecycle events.
- Logout204; subsequent current-user401.
All assertions passed. Only smoke-owned servers stopped afterward. Multi-member transfer/removal is exercised by real-PostgreSQL HTTP integration tests using bounded SQL fixtures; there is no joining endpoint.

## Integrity and remaining limits
Database unique(event,user), composite event FKs, deferred leader-is-member FK, capacity guard and append-only audit triggers were exercised. Outer-transaction rollback test verifies team/member/anchor/audit atomicity. All implemented roster mutations use anchor FOR NO KEY UPDATE then team FOR UPDATE; authority is rechecked after locking. Details-only updates need just the team lock. Key-share compatibility rationale follows [PostgreSQL locking documentation](https://www.postgresql.org/docs/current/explicit-locking.html); this is not a claim of complete concurrency proof.
Reserved for M3: 50 last-seat contenders, simultaneous create/admit across teams, simultaneous leader departure/removal/acceptance, final leave versus admission, bounded deadlock/timeout handling, persisted idempotency claims/replays/payload mismatch/lost responses and retry after deletion. The 50-contender test has NOT run and is not reported passed. No live board performance/load test ran.
Browser preview again returned "The browser access host is unavailable". No visual/keyboard runtime QA is claimed; frontend type/build and actual HTTP/proxy flow passed. GitHub Actions workflow remains configured, but no remote execution/deployment result is claimed. No commit/push. Stop for review before M3.
