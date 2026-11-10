# TeamForge local development (M1/M2)

## M6 frontend checks
From the repository root, with existing frontend dependencies installed:

```powershell
npm.cmd --prefix frontend run typecheck
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend test
```

The test command starts its own fixture Vite server on127.0.0.1:5175, fails if that port is occupied, and drives installed Chrome/Chromium through Node's built-in runner. Set BROWSER_BIN if automatic Windows/Linux discovery does not find it. No browser package download or production database is needed for these15 tests. Isolated ignored test profiles use sandbox-disabled browser flags for this restricted Windows environment; production application settings are unchanged. Missing Chrome is reported as a failure, never silently counted as passing tests.

For the opt-in real flow, start the normal backend and frontend using the commands below (or use Vite preview after building). Set TEAMFORGE_E2E_ORIGIN to that frontend origin and TEAMFORGE_E2E_EVENT to a dedicated min2/max5 QA hackathon; then run `node frontend/tests/real-flow.mjs`. It creates synthetic accounts through the UI and writes exact IDs for cleanup in .tmp/m6-e2e-fixtures.json. The verification report records the isolated harness used in this session; existing user events/accounts must not be reset. Never put invitation codes or credentials in the origin/event variables.

Requires JDK 21, Maven >=3.6.3, Node 24 LTS/npm, and reachable local PostgreSQL. Docker is optional. Java must be a JDK, not just a JRE. Dependency installation needs registry access unless all required artifacts are cached.

## Database and backend
From a PowerShell terminal:
```powershell
Set-Location D:\HackerRank\PS1
if (-not (Test-Path -LiteralPath .env)) { Copy-Item .env.example .env }
notepad .env
# Preserve existing real values. Replace placeholders only; never print .env.
# Use your configured local PostgreSQL user and teamforge_db database.
# Values must be simple KEY=value entries, without shell quotes.
# Local PostgreSQL must be listening; do not start Compose on its occupied port.
Test-NetConnection localhost -Port 5432
# Select the actual installed JDK 21. Adjust if installed elsewhere.
$jdk21 = (Get-ChildItem 'C:/Program Files/Eclipse Adoptium' -Directory -Filter 'jdk-21*' | Sort-Object Name -Descending | Select-Object -First 1).FullName
if (-not $jdk21) { throw 'Install/select JDK 21 before continuing.' }
./scripts/Start-Backend.ps1 -JavaHome $jdk21 -Mode run -LocalHttp
```

Spring Boot does not automatically read the root .env. The dot-sourced script maps local Compose settings to DB_URL/DB_USERNAME/DB_PASSWORD without printing secrets or evaluating shell text. Maven commands use the installed Maven; no wrapper is included yet. If PowerShell policy blocks the local script, use a process-scoped policy only if permitted by your organization. Do not change machine policy. Start-Backend.ps1 loads the root .env itself in every fresh terminal, selects the passed JDK, requires Java 21, and invokes Maven. It never starts Docker. The loader validates required keys and port; .env uses unquoted KEY=value entries and supports equals signs inside values. Do not run it with tracing/transcription that would capture environment assignments.

## Frontend (second terminal)
```powershell
Set-Location D:\HackerRank\PS1\frontend
npm.cmd ci
npm.cmd run dev
```
Open http://127.0.0.1:5173. Vite proxies /api to http://127.0.0.1:8080; no permissive CORS configuration is necessary. Keep default ports for these commands. The page checks the real public endpoint and offers retry after failure; no simulated success state.

## Verify
In a fresh terminal with local PostgreSQL running:
```powershell
Set-Location D:\HackerRank\PS1
$jdk21 = (Get-ChildItem 'C:/Program Files/Eclipse Adoptium' -Directory -Filter 'jdk-21*' | Sort-Object Name -Descending | Select-Object -First 1).FullName
if (-not $jdk21) { throw 'Install/select JDK 21 before continuing.' }
./scripts/Start-Backend.ps1 -JavaHome $jdk21 -Mode compile
./scripts/Start-Backend.ps1 -JavaHome $jdk21 -Mode verify
Push-Location frontend
npm.cmd run typecheck
npm.cmd run build
Pop-Location
# With backend running in its own terminal:
Invoke-RestMethod http://localhost:8080/api/v1/health
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
Invoke-RestMethod http://127.0.0.1:5173/api/v1/health
```
Without TEAMFORGE_DATABASE_TESTS=true, the database test class is skipped; the three HTTP health/security tests use a database-free Spring context. This is not evidence of migration success. Database-enabled tests boot the full application, check Flyway history/checksums and readiness, and reject invalid event bounds. Use a disposable local/test database.

Health /api/v1/health reports process liveness only: {"status":"UP"}. Actuator readiness includes database health; public probe responses expose status only. The Actuator root additionally lists public probe names; component/connection details remain hidden. M1-B now exposes registration/login/logout and own-profile endpoints; no generated development user. CSRF remains enabled. Cookies are Secure by default; -LocalHttp explicitly enables loopback HTTP development only. Omit it with HTTPS.

Stop the TeamForge frontend/backend with Ctrl+C. Do not stop or reset an existing shared local PostgreSQL service. Only if using the optional Compose database, stop it with:
```powershell
Set-Location D:\HackerRank\PS1
docker compose down
```
This preserves the database volume. Do not remove volumes unless you intend to delete local data. No deployed frontend/server routing is selected; Vite preview is only a build preview and does not provide the development API proxy.

## Local database checks and recovery
Root .env holds POSTGRES_DB=teamforge_db, POSTGRES_USER, POSTGRES_PASSWORD and POSTGRES_PORT for your existing server. The datasource URL is assembled by the loader. application.yml already references DB_URL/DB_USERNAME/DB_PASSWORD and remains unchanged. Never copy the example over an existing .env or put credentials in shell arguments.

In a normal terminal where script execution is permitted, check the configured user without printing its password:
```powershell
Set-Location D:/HackerRank/PS1
. ./scripts/Set-DevelopmentEnvironment.ps1
$previousPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $env:DB_PASSWORD
    & 'C:/Program Files/PostgreSQL/18/bin/psql.exe' -X -w -v ON_ERROR_STOP=1 -h localhost -p 5432 -U $env:DB_USERNAME -d teamforge_db -c 'SELECT current_database(), has_schema_privilege(current_user, ''public'', ''CREATE''); SELECT version,success FROM flyway_schema_history ORDER BY installed_rank;'
} finally { $env:PGPASSWORD = $previousPassword }
```
Use your configured port if different. History query is for after application/tests start successfully. Flyway alone creates application tables; do not apply V1 manually or baseline an unknown schema. The recovery check found database CONNECT and public USAGE/CREATE permissions already granted, so no grant/create SQL was needed and no database was reset.

## Required user actions if blocked
The Codex environment found only JDK 24. Install JDK 21 in your normal terminal if not installed elsewhere:
```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK --exact
```
Open a new terminal after installation, select its directory as shown above, and run Start-Backend.ps1. The local execution policy was Restricted; if permitted by your organization's policy, enable local scripts for this terminal only:
```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
```
This command was not executed by the agent. Do not change machine/group policy. Then the exact normal-terminal verification command is:
```powershell
Set-Location D:/HackerRank/PS1
$jdk21 = (Get-ChildItem 'C:/Program Files/Eclipse Adoptium' -Directory -Filter 'jdk-21*' | Sort-Object Name -Descending | Select-Object -First 1).FullName
if (-not $jdk21) { throw 'JDK 21 directory not found.' }
./scripts/Start-Backend.ps1 -JavaHome $jdk21 -Mode verify
```
The script calls mvn --batch-mode --no-transfer-progress -f backend/pom.xml verify with the datasource variables and TEAMFORGE_DATABASE_TESTS=true set in that same process. It does not need variables from another terminal. Normal Maven can download missing artifacts; no dependency downgrade is needed. In Codex, network download was previously denied; current cached artifacts now permit offline verification on Java 24. This does not substitute for JDK 21 verification.

## Optional Docker database
Only when local PostgreSQL is not already using the configured port:
```powershell
Set-Location D:/HackerRank/PS1
docker compose --env-file .env up -d --wait
```
Never start a second database on an occupied port. Deployment and authentication provider selection remain outside M1-A.

## M1-B authentication/profile workflow
Local email/password is approved. After verify succeeds, restart the backend with -Mode run -LocalHttp; an already-running M1-A process lacks the new endpoints. Start Vite, then register a synthetic account, sign in, save profile, reload and sign out. Registration asks you to sign in. Client fetches fresh CSRF before each mutation; no credential/session storage in localStorage.
TEAMFORGE_DATABASE_TESTS=true enables identity/profile HTTP tests. Flyway applies V2; tests clean up only returned fixture IDs. Never target production. JDK 21 and previously missing plugin artifacts are now installed/cached; the earlier blocked instructions are historical recovery guidance.
If the editor compiles to target/, isolate verification to avoid IDE stubs overwriting classes:
```powershell
Set-Location D:/HackerRank/PS1
. ./scripts/Set-DevelopmentEnvironment.ps1
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$env:TEAMFORGE_DATABASE_TESTS = 'true'
mvn --batch-mode --no-transfer-progress '-Dteamforge.build.directory=D:/HackerRank/PS1/.tmp/backend-verify' -f backend/pom.xml clean verify
```
Adjust the installed JDK patch directory if needed. Default Maven/CI still uses target/. Codex additionally used offline mode and an ignored copy of the existing artifact cache; normal terminals need neither workaround unless encountering the same access problem.
For isolated runtime checks when 8080/5173 are occupied:
```powershell
# Backend terminal after loading .env and selecting JDK 21 above:
java -jar .tmp/backend-verify/teamforge-0.0.1-SNAPSHOT.jar --server.port=8081 --spring.profiles.active=local
# Separate frontend terminal:
Set-Location D:/HackerRank/PS1/frontend
$env:BACKEND_ORIGIN = 'http://127.0.0.1:8081'
npm.cmd run dev -- --port 5174
```
Every backend terminal loads .env itself. BACKEND_ORIGIN is only a development proxy setting. Do not enable the local cookie profile in deployment. No Docker is required with reachable local PostgreSQL.

## M2 team dashboard
Restart the backend from this checkout after verify; Flyway applies additive V3 automatically. An empty TeamForge Demo hackathon is provided without overwriting existing events. Sign in and use Teams to select an event, create/view/edit a team, leave, and read retained history. Your profile/logout remain available. Joining/invites/SSE are not present, so multi-member succession/removal is currently demonstrated with integration-test fixtures; no admission shortcut is exposed.
The Java21 clean-verify command above now runs 61 tests with TEAMFORGE_DATABASE_TESTS=true. Inspect .tmp/backend-verify/surefire-reports for the isolated run. No database reset, V1/V2 rewrite or large seed is needed. The 50-contender test is reserved for M3.
