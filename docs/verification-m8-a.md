# M8-A verification — 2026-11-10

## Latest authorized optimization result
Phases A–D and local gates now passed:201 backend/16 browser tests; unchanged original k6 at100VUs/30s/180s/pool10 measured198,602 requests,100% success,p9542.566ms (exit0). See [optimization verification](verification-m8-a-optimization.md) for independent cursor/query/pool results, data accounting and every changed file. The following initial installation/failed-run investigation is historical and retained for comparison. No M8-B/deployment/commit/push.

## Historical handoff before optimization authorization

At this earlier handoff the real k6 run had executed but the latency threshold failed. No optimization had yet been applied. The latest authorized optimization result above supersedes this status; original evidence below is retained.

Later read-only performance investigation is recorded separately in [performance-investigation-m8-a.md](performance-investigation-m8-a.md), including exact plans and direct service acquisition/hold metrics. No application/configuration/benchmark-script change or comparable k6 compliance rerun occurred during that investigation.

## Installed-k6 follow-up

Found `C:\Program Files\k6\k6.exe` v2.3.0 (e088784614,Go1.26.8); stale process PATH explained why `where.exe k6` still failed. Reverified the retained exact fixture before launching an owned Java21 benchmark JVM, bound to loopback8082 with explicit benchmark JDBC/Flyway schema and Hikari maximum10. No public-schema fallback or reseed/reset.

Actual invocation: `"C:/Program Files/k6/k6.exe" run --quiet benchmarks/board.k6.js`, through private `.tmp/run-m8-a-k6.py` with credentials loaded privately from ignored `.env.benchmark`.100 synthetic sessions were prepared outside board timing, followed by30s/100-VU warm-up and180s/100-VU measured traffic. Complete k6 wall time509.094s; wrapper including post-run verification512.656s. Exit **99**, with only `board_duration` threshold reported crossed.

Measured **43,883 requests,100% success,243.794 requests/s,p50 307.664ms,p95 602.705ms,p99 854.632ms**. All43,883 measured checks passed; the18,000-request floor and error checks passed. **602.705ms is not below200ms.** [Results/summary/metadata](../benchmarks/results/k6-2026-11-10/), [load report](load-test-report.md). No raw request-sample output or credential/body logging enabled; safe artifacts were checked for private credential values before copying.

Monitoring used local-only Hikari JMX on the owned JVM plus process/PG activity samples. Peak10/10 active connections and81 waiting; the pool was saturated during load. The local JMX probe ended after the owned application was stopped (its terminal disconnect exit1 is not the k6 exit). Post-run EXPLAIN shows aggregation of2,000 teams/6,000 roster rows before top-N paging,8.809ms for the warm standalone SQL. This does not establish the whole endpoint root cause: shared host had only~0.47GB free RAM; no privileged DB CPU/GC-pause/serialization timing was available. There were no benchmark SSE subscribers. No index/query/pool-default change was made speculatively.

Before/after full seed reports match, and public counts remained users/profiles/teams/members0,audits5520. Only owned k6/JVM/probe processes ended; fixture schema retained. Two report-adapter tests reran successfully (348.857ms). The earlier195 backend/16 browser regression results below remain valid but were **not rerun in this execution-only follow-up**; application/frontend/migrations remained unchanged. Remote hosted CI unverified. M8-A performance acceptance remains open; M8-B not started.

## Initial seed implementation and historical installation blocker

## Inspection and changes

Read AGENTS/progress/requirements/architecture/database/test plan/M7/CI/deployment docs; inspected unchanged V1–V6, BoardQuery, discovery indexes, session/CSRF/rate controls and existing native PostgreSQL/Maven/browser harness. Initial Git tree clean on main at `ef183c0` (`feat: harden TeamForge security and release pipeline`), following the committed M7. Remote GitHub-hosted status remains unverified; no remote CI success claimed.

Added:

- `scripts/benchmark/seed.py`, `HashPassword.java`, `Load-Environment.ps1`, `test_seed.py`: explicit isolated target, fixed fixture data, one-time synthetic hash input, transactional constraint-enabled inserts, exact repeat/verify and fresh-session settings. No delete/reset operation.
- `benchmarks/board.k6.js`, `result-summary.mjs`, `report.test.mjs`, `explain-board.sql`, `README.md` and safe evidence in `benchmarks/results/`.
- `.env.benchmark.example` with placeholders; actual `.env.benchmark` generated privately only because absent and ignored. Root `.env` preserved. `.gitignore` adds Python bytecode exclusions.
- This report and [load report](load-test-report.md); requirements/design/test/progress/decision references updated. No application Java/React code, migration, dependency or CI behavior changed.

## Commands and actual outcomes

| Command/check | Outcome |
|---|---|
| Java21 `python .tmp/verify-m8-a.py` | **195 tests,0 failures/errors/skips**, BUILD SUCCESS, **6m31s**; all M7 baseline retained |
| Public50 invite race in that suite | **1 HTTP201/49 TEAM_FULL409**, roster2,49 losing nonmembers,516ms;0 deadlocks/timeouts |
| M3 independent50-connection races | One success each,6751/183/171ms;0 ordinary-race deadlocks/timeouts; fault-injection recovery tests also passed |
| `npm.cmd --prefix frontend run typecheck` | Passed |
| `npm.cmd --prefix frontend test` | **16 passed**,0 failures/cancelled/skipped,**18.727s** |
| `npm.cmd --prefix frontend run build` | Passed;25 modules,457ms; unchanged bundle shape |
| `where.exe k6` / known install locations | No k6 found |
| `Invoke-WebRequest -UseBasicParsing -Uri https://api.github.com/repos/grafana/k6/releases/latest -OutFile .tmp/k6-release.json -TimeoutSec 15` | Failed: unable to connect to remote server; no repeated installation attempts |
| Isolated DB provisioning with configured role | CREATE DATABASE denied; confirmed role has neither CREATEDB nor superuser. No existing DB altered/dropped |
| Fresh isolated-schema provisioning / packaged backend | New schema created, V1–V6 applied there, readiness HTTP200; JVM later stopped |
| Seed followed by repeat using same input | **18.516s /4.672s**, exact same fingerprint and all-column fixture match; no duplicate rows |
| Real corruption/refusal test | Changed one owned synthetic profile; verify and repeat seed both rejected it without overwriting. Restored the exact original row; full verification passed again |
| Authenticated board smoke | **24 HTTP200** across8 variants/3 cursor pages, capacity shape correct; public recorded counts unchanged |
| `python -m unittest discover -s scripts/benchmark -p test_seed.py -v` / final `-q` rerun | **6 passed**, final1.726s; deterministic counts/IDs, leader/capacity/exclusivity, profiles, missing opt-in, normal/public target and search-path guards |
| `node --test benchmarks/report.test.mjs` (final) | **2 passed**,0 failures/skips,234.353ms; absent data cannot pass, strict200ms/error/count boundaries and summary formats |
| `node --check benchmarks/board.k6.js` | Syntax passed; not k6 runtime execution |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .tmp/test-m8-a-environment.ps1` | Fresh-session benchmark DB/Flyway/search-path/hash mapping passed; no global execution-policy change |
| Read-only `benchmarks/explain-board.sql` | Captured JSON plan;21.634ms SQL execution, not endpoint p95 |
| Hardware CIM queries | Permission denied; Node OS APIs successfully captured CPU/RAM/OS instead |

The private verification helper used the existing cache/output isolation and privately loaded `.env`; actual Maven arguments:

```text
mvn --offline --batch-mode --no-transfer-progress -Dmaven.repo.local=D:/HackerRank/PS1/.tmp/m2-local -Dteamforge.build.directory=D:/HackerRank/PS1/.tmp/backend-verify -f backend/pom.xml -Pweb clean verify
```

Private `.tmp/run-m8-a-seed.py` provisioned only `teamforge_benchmark_m8a` schema, launched the owned packaged Java21 app on8082 with its JDBC/Flyway target, invoked `seed.py seed --database teamforge_db --schema teamforge_benchmark_m8a --confirm-isolated --psql "C:/Program Files/PostgreSQL/18/bin/psql.exe"` twice and authenticated against real HTTP. `--output` preserved each safe report. Hashing used `seed.py hash-password --jar .tmp/backend-verify/teamforge-0.0.1-SNAPSHOT.jar --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot/bin/java.exe"`; its output was captured privately, never logged. Standalone repeatable public commands are in [benchmark instructions](../benchmarks/README.md), not dependent on private helper files.

## Counts, isolation and unresolved checks

Verified10,000 users/profiles/anchors,2,000 teams,6,000 members/audits,4,000 unteamed;400 teams of each size1–5;zero duplicates/invalid leaders/capacity violations. Profiles include30,000 skill rows and20,000 availability rows, compared in full. One benchmark event plus the migration's empty demo. Fingerprint `9dcec261ba2afa9266181d345b66e30756000ddd882ccdc072e88c4b8f1f60fc` includes the privately supplied BCrypt hash as a deterministic input.

Schema-only search paths, explicit Flyway schema and current-schema guards prevent public fallback. Public before/after recorded counts: users0,profiles0,teams0,members0,audits5520; no normal-data reset. The isolated fixture schema is retained. Actual PostgreSQL18.6 has the known Flyway tested-support warning;17.7/Docker is not verified. No server configuration or public schema was changed by seeding. No API/query changes required new application regression cases; tool tests are reported separately from195 backend and16 browser tests.

At the initial installation-blocked stage, all k6 metrics were unmeasured and status fields were null. The [latest status pointer](../benchmarks/results/k6-status.json) has since been updated by actual executions, with separate failed and passed evidence directories. [Setup/run steps](../benchmarks/README.md) remain available. No historical blocked status is presented as an executed measurement.

No benchmark cleanup/drop was executed, no development data destroyed, no commits/pushes/deployment and no M8-B work. Runtime k6 compatibility, actual100-VU latency/error/throughput and generator/application bottlenecks remain pending. Hosted CI is unverified. Git changes are limited to this milestone's benchmark tooling/results/documentation.

Final repository review:27 changed/new files (8 modified,19 new), all unstaged; HEAD/origin unchanged. `git diff --check`, all result JSON parsing and local documentation-link checks passed. `.env`, `.env.benchmark` and Python bytecode are ignored; no application/frontend/workflow/migration diff. Database client connections default to5s, seed locks10s and statements120s to bound unavailable/contended targets. A final fresh-PowerShell `seed.py verify` with those bounds again returned the identical full fixture/fingerprint.
