# M1-A verification ledger
Observed on 2026-11-09 in Windows PowerShell. These are local observations, not GitHub Actions results. The application acceptance gate is **partially blocked**. The original attempt below is historical; see the latest recovery results at the end.

## Environment and inspections
Read AGENTS.md and requested progress/implementation/architecture/database/API/decisions documents; inspected files with `rg --files --hidden -g '!.git'` and Git status. Initial working tree was clean on master.

| Exact command | Observed result |
|---|---|
| `java -version` | Oracle Java 24; no JDK 21 found in inspected Java/Microsoft/.jdks locations |
| `mvn -version` | Maven 3.9.12 using Java 24 |
| `node --version` | v24.19.0 |
| `npm --version` | PowerShell blocked npm.ps1 by execution policy |
| `npm.cmd --version` | 12.2.1; used .cmd thereafter |
| `docker --version` | 29.9.2; Docker user config inaccessible |
| `docker compose version` | v5.6.1 |
| `docker info --format '{{.ServerVersion}}'` | Failed: permission denied on docker_engine named pipe |
| `git status --short --branch` | master, initially clean; final changes listed below |

Registry checks with `Invoke-WebRequest -UseBasicParsing -TimeoutSec 10` to Maven Central's 3.5.16 parent and npm's Vite endpoint both returned `Unable to connect to the remote server`. `npm.cmd view` registry queries also failed to provide usable metadata. No network approval or system policy change was attempted.

## Backend (from backend/)
1. `mvn --batch-mode --no-transfer-progress -Dmaven.repo.local=D:/HackerRank/PS1/.tmp/m2 verify` failed trying to create C:\.m2\repository; PowerShell requires quoting the dotted property argument.
2. Corrected command: `mvn --batch-mode --no-transfer-progress '-Dmaven.repo.local=D:/HackerRank/PS1/.tmp/m2' verify` failed before compilation:

```text
Non-resolvable parent POM ... spring-boot-starter-parent:pom:3.5.16
Could not transfer artifact ... from/to central ... Permission denied: getsockopt
```

No backend test executed. Five tests exist in source (three database-free HTTP integration tests, two opt-in full database tests); none are reported passed. No Java 21 runtime, application startup or Flyway migration verification is claimed. XML parsing of pom.xml succeeded, which is not compilation.

## Frontend (from frontend/)
Initial offline install against the read-only user cache failed ENOTCACHED. A full-metadata flag was rejected by npm 12. A temporary programmatic installation attempt also failed cache resolution. Reused existing verified cache entries in writable .tmp/npm-cache; did not modify the user cache. A temporary ignored `.tmp/install-offline.cjs` repeatedly invoked npm install offline and copied only missing registry entries from the existing cache.

Initial dependency resolution rejected @types/node 22.10.2 because Vite requires >=22.12-compatible types; changed to cached 26.6.4. Initial build missed the optional Rolldown Windows binding. Added existing optional-package cache entries, regenerated the generated lockfile, and included Linux metadata. Initial clean install then identified missing optional fsevents metadata; repaired and reran.

Final exact commands:

```powershell
npm.cmd ci --offline --cache D:/HackerRank/PS1/.tmp/npm-cache --logs-dir D:/HackerRank/PS1/.tmp/npm-logs --no-audit --no-fund
npm.cmd run typecheck
npm.cmd run build
```

All exited 0:

```text
added 25 packages in 2s
tsc --noEmit
vite v8.3.5 building client environment for production...
17 modules transformed.
dist/index.html                  0.54 kB
dist/assets/index-D1P-k4xO.css    2.00 kB
dist/assets/index-COF5Doi1.js   221.96 kB
built in 161ms
```

This build duration is not a board endpoint benchmark. Lockfile includes Windows and Linux native bindings. CI runs ordinary npm ci with registry access; offline cache helpers are not required or committed.

`npm.cmd run dev` started Vite at 127.0.0.1:5173. `Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 http://127.0.0.1:5173/` returned 200 and the TeamForge title. The same request to /api/v1/health returned 502, with Vite reporting ECONNREFUSED 127.0.0.1:8080 (backend not running). This does not prove the React connected state. Browser access host unavailable; no screenshot, visual or keyboard runtime verification claimed. Dev server stopped afterward.

## Database fallback (from repository root)
`docker compose --env-file .env.example config --quiet` exited 0, with Docker config access warnings. Compose database could not run because daemon access is denied.

Native tools were available under C:/Program Files/PostgreSQL/18/bin. Exact fallback commands:

```powershell
& 'C:/Program Files/PostgreSQL/18/bin/initdb.exe' -D D:/HackerRank/PS1/.tmp/pgdata -U teamforge_local --auth=trust --encoding=UTF8 --no-locale
& 'C:/Program Files/PostgreSQL/18/bin/pg_ctl.exe' -D D:/HackerRank/PS1/.tmp/pgdata -l D:/HackerRank/PS1/.tmp/postgres.log -o '-p 55432 -h 127.0.0.1' start
```

initdb completed despite restricted-token warnings. pg_ctl start failed with restricted-token errors. Foreground startup succeeded:

```powershell
& 'C:/Program Files/PostgreSQL/18/bin/postgres.exe' -D D:/HackerRank/PS1/.tmp/pgdata -p 55432 -h 127.0.0.1
& 'C:/Program Files/PostgreSQL/18/bin/createdb.exe' -h 127.0.0.1 -p 55432 -U teamforge_local teamforge
& 'C:/Program Files/PostgreSQL/18/bin/psql.exe' -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 55432 -U teamforge_local -d teamforge -f backend/src/main/resources/db/migration/V1__create_hackathons.sql
& 'C:/Program Files/PostgreSQL/18/bin/psql.exe' -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 55432 -U teamforge_local -d teamforge -c '\d hackathons'
```

PostgreSQL 18.6 accepted the SQL (`CREATE TABLE`) and displayed expected PK/check constraints. No existing database was modified. Test instance used loopback-only trust authentication and was stopped; it is not the Compose configuration or a recommended deployment setup.

```powershell
& 'C:/Program Files/PostgreSQL/18/bin/psql.exe' -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 55432 -U teamforge_local -d teamforge -c "BEGIN; INSERT INTO hackathons(id,name) VALUES ('00000000-0000-0000-0000-000000000001','Foundation check'); SELECT minimum_team_size,maximum_team_size,active FROM hackathons; ROLLBACK;"
& 'C:/Program Files/PostgreSQL/18/bin/psql.exe' -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 55432 -U teamforge_local -d teamforge -c "INSERT INTO hackathons(id,name,minimum_team_size,maximum_team_size) VALUES ('00000000-0000-0000-0000-000000000002','Invalid bounds',5,2);"
& 'C:/Program Files/PostgreSQL/18/bin/pg_ctl.exe' -D D:/HackerRank/PS1/.tmp/pgdata -m fast stop
```

Valid defaults returned 2/5/false and rolled back. Invalid insert exited 1 with `violates check constraint hackathons_valid_team_limits` (expected rejection). Stop exited 0: `server stopped`. Direct SQL is not a substitute for Flyway/application tests against the pinned PostgreSQL image.

## Other checks and outstanding gates
- `git diff --check`: no whitespace errors.
- `git check-ignore .env .tmp/pgdata/PG_VERSION frontend/node_modules/react/package.json frontend/dist/index.html backend/target/example.txt`: all ignored. `.env.example` is eligible for tracking.
- PowerShell Parser.ParseFile found no syntax errors in scripts/Set-DevelopmentEnvironment.ps1; helper execution with real local credentials was not tested.
- GitHub Actions has not run; workflow is source only. No load testing, deployment, authentication flow or team feature was attempted.
- Rerun documented Maven verify on JDK 21 with registry access and TEAMFORGE_DATABASE_TESTS=true against disposable PostgreSQL. Verify full-stack connected state and responsive/keyboard UI before calling M1-A fully verified.

## Local PostgreSQL recovery ? latest results (2026-11-09)
This section supersedes earlier statements that no backend tests or Flyway execution occurred. No application dependencies or Spring Boot version were replaced.

### Configuration and credentials
No root .env existed. Preserved the existing non-placeholder example values in a newly created, ignored root .env without printing them during preservation, then restored .env.example to safe placeholders. Future startup instructions copy only when .env is absent. No existing real .env was overwritten. `git check-ignore .env` succeeded; `git ls-files --error-unmatch .env` exited 1 (not tracked).
Set-DevelopmentEnvironment.ps1 still maps POSTGRES_DB/USER/PASSWORD/PORT to the existing DB_URL/DB_USERNAME/DB_PASSWORD configuration. It now validates duplicate/invalid keys and port range and escapes the JDBC database path. Added Start-Backend.ps1: each invocation loads root .env and selects/requires JDK 21 in the same process; modes run/compile/verify do not start Docker. application.yml is unchanged.

### PostgreSQL observed results
A TcpClient.ConnectAsync('localhost',5432) probe succeeded. The first dot-source was blocked by PowerShell's Restricted execution policy; a subsequent independent Python probe read .env directly and supplied the password to psql only through its child environment, without echoing it. Configured user connection to teamforge_db exited 0; database CONNECT and public USAGE/CREATE were true. No grant/create-database SQL was necessary. Before startup both hackathons and flyway_schema_history were absent. No tables were manually created in this recovery.
The integration run applied V1 through Flyway. Final read-only psql query:
```sql
SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank;
SELECT to_regclass('public.hackathons') IS NOT NULL AS hackathons_managed;
```
returned version 1, description create hackathons, success true, hackathons_managed true. No database reset, deletion or baseline was performed. PostgreSQL is 18.6; Flyway warns its tested support ends at PostgreSQL 17. Migration and JPA validation succeeded despite that warning; no library upgrade or database-version change was made.

### Java/Maven diagnosis and exact commands
Inspected standard Java/Microsoft/Adoptium/.jdks locations: only JDK 24 found. Maven 3.9.12 still uses Java 24. The POM continues to target release 21; Java 21 runtime verification is outstanding.
No user settings.xml or project .mvn/maven.config found. Global Maven settings contain only the standard external HTTP blocker, no proxy/server or overridden local repository; it does not block HTTPS Central. The selected 3.5.16 parent is now cached. Historical online errors were socket permission failures, not evidence of invalid coordinates. No online retry was performed during recovery.

From repository root:
```powershell
mvn --offline --batch-mode --no-transfer-progress '-Dmaven.repo.local=C:/Users/akhil/.m2/repository' -f backend/pom.xml compile
mvn --offline --batch-mode --no-transfer-progress '-Dmaven.repo.local=C:/Users/akhil/.m2/repository' -f backend/pom.xml clean compile
mvn --offline --batch-mode --no-transfer-progress -e '-Dmaven.repo.local=C:/Users/akhil/.m2/repository' -f backend/pom.xml compile
mvn --offline --batch-mode --no-transfer-progress -e '-Dmaven.repo.local=C:/Users/akhil/.m2/repository' -f backend/pom.xml test-compile
```
Initial compile reported success with existing classes. Clean compile reported a classmate JAR error; the subsequent diagnostic compile completed successfully. The first verify attempt reported a spring-boot-test JAR error at test compilation; diagnostic test-compile then completed successfully. These transient JAR errors were observed, not conclusively attributed to a cause. No dependencies were changed to hide them.
An ignored Python helper loaded root .env into the Maven child environment, setting TEAMFORGE_DATABASE_TESTS=true, and ran exactly:
```powershell
mvn --offline --batch-mode --no-transfer-progress '-Dmaven.repo.local=C:/Users/akhil/.m2/repository' -f backend/pom.xml verify
```
The helper was used because local .ps1 execution is blocked; the startup script itself was not bypassed or claimed executed. First executed suite: 5 tests, 1 failure. Root cause: the Actuator root legitimately returns groups alongside status; the old exact-string assertion incorrectly rejected public group names. The corrected test explicitly allows only status/groups, asserts status UP and the two probe names, still excluding all component/connection details. Custom /api/v1/health retains its exact status-only assertion. No production security setting was relaxed.

After this test-only correction, final suite output:
```text
DatabaseIntegrationTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
HealthIntegrationTest: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```
Surefire XML files independently confirm these counts. These are real HTTP/integration tests on Java 24, including database readiness HTTP 200, custom public health HTTP 200 and permission checks. They are not Java 21 or GitHub Actions results.

### Remaining blocker and exact user action
Final verify exited 1 at Spring Boot repackage, after passing all tests and creating the ordinary JAR. Offline cache lacks:
- org.springframework.boot:spring-boot-buildpack-platform:3.5.16
- org.springframework.boot:spring-boot-loader-tools:3.5.16
- org.apache.commons:commons-compress:1.26.2
- commons-codec:commons-codec:1.17.0

No more Maven retries or network attempts were made after identifying this blocker. In a normal PowerShell terminal with registry access, install/select JDK 21 and, if organizational policy permits, allow locally written scripts for that process only:
```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK --exact
# Open a fresh terminal after installation. Only if script policy requires it:
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
Set-Location D:/HackerRank/PS1
$jdk21 = (Get-ChildItem 'C:/Program Files/Eclipse Adoptium' -Directory -Filter 'jdk-21*' | Sort-Object Name -Descending | Select-Object -First 1).FullName
if (-not $jdk21) { throw 'Select the actual JDK 21 directory.' }
./scripts/Start-Backend.ps1 -JavaHome $jdk21 -Mode verify
./scripts/Start-Backend.ps1 -JavaHome $jdk21 -Mode run
```
Installation and execution-policy commands were not executed by the agent. The verify script uses normal Maven networking to download missing plugin artifacts and enables both database tests in the same fresh process. Do not run the run command until verify succeeds. No machine/group policy change is requested.

Backend at fixed port 8080 and the frontend proxy were not started/rechecked after this blocker; health was verified at random ports by integration tests. Frontend source was unchanged, so its prior passing typecheck/build results are retained, not newly claimed. No CI run, Docker run, deployment or M1-B work occurred.
Both PowerShell scripts pass Parser.ParseFile syntax checks; actual fresh-terminal startup remains blocked by Restricted policy and absent JDK 21. No policy was changed automatically. M1-A remains incomplete until full verify/packaging on JDK 21 and the fixed-port frontend proxy check succeed. The earlier skill-guided investigation used local reproduction/source review; its Bash runner was unavailable, so no gstack runner/telemetry result is claimed.
