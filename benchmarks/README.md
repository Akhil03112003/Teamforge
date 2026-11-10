# Isolated TeamForge benchmark

M8-A provides opt-in seed tooling and an unchanged k6 board workload. Latest optimized100-VU result:198,602 measured requests,100% success,p95 **42.566ms — PASSED**, original pool10. Earlier602.705ms failure is preserved. [Load report](../docs/load-test-report.md), [phase verification/file inventory](../docs/verification-m8-a-optimization.md), [actual results](results/optimization-2026-11-10/). Nothing runs automatically on startup or against production.

## Dataset and isolation

Version `teamforge-m8a-v1`: one benchmark event, minimum2/maximum5; exactly10,000 users/profiles/anchors,2,000 teams,6,000 memberships,4,000 unteamed users and6,000 creation/admission audit snapshots. There are400 teams of each size1–5:400 incomplete,1,600 ready,400 full,400 nearly full and800 other open ready teams. Skills cycle through10 tags, profiles through8 IANA zones with two weekly availability windows; teams have two desired skills. All emails use reserved `example.test`; names/descriptions are synthetic.

UUIDv5 identifiers, timestamps, row order and distributions are fixed. The supplied BCrypt hash is a fixture input: hash a synthetic password once, keep it privately, and reuse it for exact reproducibility. New environments with a different salted hash have a different full fingerprint. `seed` verifies every fixture table/column on repeat and becomes a no-op; changed rows cause refusal, never repair or deletion. The marker/fingerprint and counts do not contain credentials. The empty Flyway demo event remains separate, with no teams/participants.

Require either a dedicated database named `teamforge_benchmark_*`, or an explicitly selected schema with that prefix. Schema mode sets **only that schema** in JDBC/psql search paths and explicitly targets Flyway there. It never falls back to `public`. Normal database/schema targets, missing opt-in, nonempty unmarked fixtures and corrupt repeats are rejected. All constraints, capacity/audit triggers and deferred leader FKs stay enabled. Bulk offline inserts are fixtures, not a new production admission path. A transaction-local advisory lock serializes seed attempts only; M3 runtime locking is unchanged. No reset/delete operation exists in the seed tool.

## Fresh PowerShell setup

Use Python3, JDK21, Maven, PostgreSQL client tools and a reachable server. Add your PostgreSQL `bin` directory to PATH. Use separate fresh terminals, not the ordinary development startup script. If local script policy blocks dot-sourcing, open `powershell.exe -NoProfile -ExecutionPolicy Bypass` for this session only; do not change global policy.

```powershell
Set-Location D:\HackerRank\PS1
if (-not (Test-Path .env.benchmark)) { Copy-Item .env.benchmark.example .env.benchmark }
# Edit .env.benchmark privately. Never reuse real participant credentials.
# Select JDK21 installed on your machine, for example:
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot'
$env:Path = "$env:JAVA_HOME\bin;C:\Program Files\PostgreSQL\18\bin;$env:Path"
. ./scripts/benchmark/Load-Environment.ps1
```

Choose **one** provisioning option for a NEW environment. Current session's retained schema already exists; do not recreate it.

1. Preferred dedicated database: set PGDATABASE to `teamforge_benchmark_m8a`, omit PGSCHEMA, load environment, then `createdb --maintenance-db=postgres teamforge_benchmark_m8a`. Requires CREATEDB or administrator provisioning. Never drop an existing database to make creation pass.
2. If database creation is denied: set PGDATABASE to the existing database and PGSCHEMA to `teamforge_benchmark_m8a`, reload environment, then `psql -X -v ON_ERROR_STOP=1 -c 'CREATE SCHEMA teamforge_benchmark_m8a;'`. Requires database CREATE. An existing schema is an error; use the existing verified fixture or a separately named new benchmark schema. No normal tables are cleared. M8-A actually used this option.

Package the existing backend after the regression gate; this command skips tests deliberately and is not evidence they passed:

```powershell
mvn --batch-mode --no-transfer-progress -f backend/pom.xml -DskipTests package
if (-not $env:BENCHMARK_PASSWORD_HASH) {
  $env:BENCHMARK_PASSWORD_HASH = python scripts/benchmark/seed.py hash-password --jar backend/target/teamforge-0.0.1-SNAPSHOT.jar
  if ($LASTEXITCODE -ne 0) { throw 'Synthetic password hashing failed' }
  Add-Content .env.benchmark ("BENCHMARK_PASSWORD_HASH=" + $env:BENCHMARK_PASSWORD_HASH)
}
java -jar backend/target/teamforge-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8082 --spring.profiles.active=local
```

Hashing reuses crypto dependencies in the executable JAR; no Python package/extension is installed. Password never enters argv. App startup applies unchanged V1–V6 to the isolated target and validates schema. Do not start it with the normal development environment or a different Flyway target.

In terminal2, set location/PATH/JAVA_HOME and dot-source the benchmark loader as above:

```powershell
Invoke-RestMethod http://127.0.0.1:8082/actuator/health/readiness
$seedArgs = @('--database', $env:PGDATABASE, '--confirm-isolated')
if ($env:PGSCHEMA) { $seedArgs += @('--schema', $env:PGSCHEMA) }
New-Item -ItemType Directory -Force .tmp/benchmark | Out-Null
python scripts/benchmark/seed.py seed @seedArgs --output .tmp/benchmark/seed.json
python scripts/benchmark/seed.py seed @seedArgs --output .tmp/benchmark/seed-repeat.json
python scripts/benchmark/seed.py verify @seedArgs --output .tmp/benchmark/seed-verified.json
$env:BENCHMARK_EVENT = (Get-Content .tmp/benchmark/seed-verified.json -Raw | ConvertFrom-Json).eventId
$env:CONFIRM_BENCHMARK = 'teamforge-m8a-v1'
```

Any nonzero exit is a failure; stop and inspect privately. Do not clear data to bypass a guard. For this sandbox's already-built JAR, substitute `.tmp/backend-verify/teamforge-0.0.1-SNAPSHOT.jar`; normal Maven output remains `backend/target`.

## Run k6 in a normal network-enabled terminal

k6 v2.3.0 is now installed at `C:\Program Files\k6\k6.exe`; restart the terminal if PATH is stale, or invoke that full path. For other machines without it, use the [official Windows instructions](https://grafana.com/docs/k6/latest/set-up/install-k6/):

```powershell
winget install k6 --source winget
k6 version
# In the configured benchmark terminal2 from above:
k6 version | Out-File .tmp/benchmark/k6-version.txt
k6 run benchmarks/board.k6.js
if ($LASTEXITCODE -ne 0) { throw 'Load run failed or thresholds were not met; preserve results' }
Get-Content .tmp/benchmark/board-results.json
```

The script has no remote JS imports. It authenticates100 distinct seeded users sequentially, with2.1s spacing respecting the unchanged30/minute/IP limit. Setup is outside measured board timing (allow about4minutes); setup failure aborts. Then100 VUs warm for30s and sustain100 VUs for180s. Eight combinations cover ID/name sorting,25/50 limits, open seats, readiness, Java/design/React skills and literal text search. Each traversal follows up to3 actual returned cursors with unchanged filters. A200ms think time separates traversals. No browser cache or proxy is used; target is the real authenticated REST endpoint. Logout invalidates only the owned sessions.

Custom measured-only metrics exclude setup, warm-up and responses completed after the180s window (up to10s drain). Report p50/p95/p99, completed request count, failure/success rate and requests/180s. Thresholds: **p95<200ms**, failure rate<1%, checks>99%, at least18,000 measured requests. Error/traffic floors are benchmark choices, not new business requirements. Raw k6 metrics and the compact report are written to `.tmp/benchmark/k6-summary.json` and `board-results.json`; absent metrics never report a pass. k6's exit code remains authoritative for execution/threshold failures.

Optional request samples: `k6 run --out json=.tmp/benchmark/k6-samples.json benchmarks/board.k6.js`. Capture the added disk/CPU overhead; don't compare runs with different output settings without disclosure. Never enable HTTP debug/body logging or export session data. Official semantics: [scenarios](https://grafana.com/docs/k6/latest/using-k6/scenarios/), [cookies](https://grafana.com/docs/k6/latest/using-k6/cookies/), [custom summary](https://grafana.com/docs/k6/latest/results-output/end-of-test/custom-summary/).

## Diagnose and clean up

If p95 fails, preserve results and capture CPU/RAM/GC, generator CPU/disk, Hikari active/pending, database activity/waits and representative query plans. Re-run on an otherwise idle machine and, when possible, a separate load-generator host. Do not infer the cause solely from a slow run or disable correctness/security controls. Existing health exposure intentionally omits pool metrics; obtain operator-only diagnostics without making them public. Source review shows constant event+aggregate queries, plus one name-cursor lookup, not per-team queries. SSE invalidations can cause additional reads in real clients; this board-only workload does not measure10,000 live SSE connections, reconnect storms or suggestion CPU.

```powershell
psql -X -qAt -v ON_ERROR_STOP=1 -v benchmark_event=$env:BENCHMARK_EVENT -f benchmarks/explain-board.sql > .tmp/benchmark/board-plan.json
```

This is a read-only ANALYZE plan for the representative name-sorted first page; adapt filters/cursors to the slow variant before changing indexes/query shape. SQL execution timing alone is not endpoint p95.

Stop only your benchmark JVM (Ctrl+C). Fixture cleanup is optional and was **not executed**. First verify `current_database()`, `current_schema()` and `benchmark_metadata.version` match the intended isolated target. For schema mode, only then execute `DROP SCHEMA teamforge_benchmark_m8a CASCADE;` on that explicit database. For dedicated-database mode, inspect its marker, disconnect benchmark clients, then use `dropdb --maintenance-db=postgres teamforge_benchmark_m8a`. These destroy the isolated fixtures only; never substitute `public`, the development database or a computed unchecked name. Retain safe reports, and remove private `.env.benchmark` only when it is no longer needed.

Seed tests: `python -m unittest discover -s scripts/benchmark -p test_seed.py -v`. Report adapter tests: `node --test benchmarks/report.test.mjs`. Node syntax/report tests are not k6 runtime verification.
