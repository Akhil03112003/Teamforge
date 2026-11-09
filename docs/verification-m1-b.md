# M1-B verification — 2026-11-09

## Changed files
- README.md
- backend/pom.xml
- backend/src/main/java/com/teamforge/identity/api/IdentityContracts.java
- backend/src/main/java/com/teamforge/identity/api/IdentityController.java
- backend/src/main/java/com/teamforge/identity/application/ParticipantService.java
- backend/src/main/java/com/teamforge/identity/persistence/AccountEntity.java
- backend/src/main/java/com/teamforge/identity/persistence/AccountRepository.java
- backend/src/main/java/com/teamforge/identity/security/ParticipantPrincipal.java
- backend/src/main/java/com/teamforge/platform/api/ApiErrors.java
- backend/src/main/java/com/teamforge/platform/api/ApiException.java
- backend/src/main/java/com/teamforge/platform/api/ApiExceptionHandler.java
- backend/src/main/java/com/teamforge/platform/security/AuthRateLimitFilter.java
- backend/src/main/java/com/teamforge/platform/security/SecurityConfiguration.java
- backend/src/main/java/com/teamforge/profiles/api/ProfileContracts.java
- backend/src/main/java/com/teamforge/profiles/api/ProfileController.java
- backend/src/main/java/com/teamforge/profiles/application/ProfileService.java
- backend/src/main/java/com/teamforge/profiles/persistence/AvailabilitySlot.java
- backend/src/main/java/com/teamforge/profiles/persistence/ProfileEntity.java
- backend/src/main/java/com/teamforge/profiles/persistence/ProfileRepository.java
- backend/src/main/resources/application-local.yml
- backend/src/main/resources/application.yml
- backend/src/main/resources/db/migration/V2__create_participant_identity_and_profiles.sql
- backend/src/test/java/com/teamforge/identity/ParticipantIntegrationTest.java
- backend/src/test/java/com/teamforge/platform/health/DatabaseIntegrationTest.java
- backend/src/test/java/com/teamforge/platform/health/HealthIntegrationTest.java
- backend/src/test/java/com/teamforge/platform/security/AuthRateLimitFilterTest.java
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
- docs/verification-m1-a.md
- docs/verification-m1-b.md
- frontend/src/App.tsx
- frontend/src/api/client.ts
- frontend/src/components/AuthForm.tsx
- frontend/src/components/ProfileForm.tsx
- frontend/src/styles.css
- frontend/vite.config.ts
- scripts/Start-Backend.ps1

## Verification results
Local observations only. M1-A passed its required Java 21 backend/proxy gate before implementation. User then explicitly approved local email/password identity. No M2 or team work.

## Backend
JDK: Temurin 21.0.13.9. Maven 3.9.12. Local PostgreSQL 18.6 at configured root .env datasource. Secrets were supplied only through the child environment; .env was not changed or displayed.
Exact final command from repository root, with JAVA_HOME/PATH set to the installed JDK and TEAMFORGE_DATABASE_TESTS=true:
```powershell
mvn --offline --batch-mode --no-transfer-progress '-Dmaven.repo.local=D:/HackerRank/PS1/.tmp/m2-local' '-Dteamforge.build.directory=D:/HackerRank/PS1/.tmp/backend-verify' -f backend/pom.xml clean verify
```
Observed: 20 main sources/four test sources compiled with release 21. Tests: 34 run, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS; executable JAR repackaged under .tmp/backend-verify. Breakdown: ParticipantIntegrationTest 28; DatabaseIntegrationTest 2; HealthIntegrationTest 3; AuthRateLimitFilterTest 1. Surefire reports are in that isolated build directory, not editor-owned target/.
Flyway applied additive V2 on the first integration run and validated both V1/V2 thereafter. JPA schema validation passed. Test cleanup removes only returned synthetic user IDs and their profiles. Existing V1 was not edited; no schema reset/truncation/manual table creation. PostgreSQL 18.6 still triggers Flyway's warning that tested support ends at 17; successful local execution does not claim verification against the CI PostgreSQL 17.7 image.

## Failures found and resolved
- Initial compiler error: wildcard imports made User ambiguous between the DTO and Spring's class; explicit imports fixed it.
- Windows AccessDeniedException when closing a cached classmate JAR in the user Maven repository. Copied the unchanged existing cache to ignored .tmp/m2-local; no dependency/version replacement or online retries.
- Existing database-free health test initially loaded full identity services without repositories. Isolated its configuration and dummy UserDetailsService under a test-only profile.
- Shared target/ then contained IDE-compiled unresolved-type stubs. javap showed literal "Unresolved compilation problems" and unqualified HealthController/SecurityConfiguration annotation descriptors. An isolated clean Maven build eliminated the stale output. Default build directory remains target for normal Maven/CI.
- First full slice run: 32 tests, 3 health-context errors. Next: 34 tests, 3 health bytecode errors. Final isolated clean run: all 34 passed.

## Frontend
From frontend/: npm.cmd run typecheck and npm.cmd run build both exited 0. Vite 8.3.5 transformed 19 modules, produced a production bundle and reported 230 ms build duration; this is not a load benchmark. No new frontend package/dependency was added.

## Local PostgreSQL and proxy flow
An ignored HTTP smoke helper launched the newly packaged JAR on 8081 with --spring.profiles.active=local and Vite on 5174 with BACKEND_ORIGIN=http://127.0.0.1:8081. No existing 8080/5173 process or PostgreSQL service was stopped. Credentials stayed in memory/environment; one generated synthetic account was removed by its returned ID after checks.
Observed via the frontend proxy:
- Packaged backend readiness and proxied health: 200.
- Register: 201; still signed out (/me 401).
- Login: 204 with HttpOnly session cookie.
- Profile update: 200 with normalized skills, named time zone and weekly availability.
- New HTTP client with same cookie jar restores /me: 200 with saved profile.
- Logout: 204; subsequent /me: 401.
All assertions passed. Smoke-owned server processes were stopped afterward; original services remain running. This exercises real HTTP/session/database/proxy behavior, not rendered React interaction.

## Limits and handoff
Browser preview returned "The browser access host is unavailable". Visual/responsive/keyboard runtime checks were not executed; JSX labels, focus/error states and responsive CSS were statically checked and type/build validated. No claim of browser visual QA, deployed service, benchmark or GitHub Actions success.
CI already runs Maven verify with TEAMFORGE_DATABASE_TESTS=true and frontend typecheck/build; no remote run was triggered. M1-B is implemented and automated gates pass; UI browser review remains an explicit limitation before production use. Review before starting M2. See local-development.md for fresh-terminal startup with -LocalHttp.
