# M8-A board optimization verification — 2026-11-10

This follow-up implements the explicitly authorized cursor/query optimization and pool experiments. Original evidence remains under `benchmarks/results/k6-2026-11-10` and `investigation-2026-11-10`; nothing there is replaced by a new performance claim.

## Phase A — cursor projection

`BoardQuery` now retains the database-returned `sort_name` privately with each summary and uses the last displayed row's key, rather than issuing `SELECT lower(name)` again. Public JSON, PostgreSQL lower/C-collation, UUID tie breaking, sentinel handling and cursor validation remain unchanged. ID and name pages now both use two business SELECTs (event validation and board), excluding transaction-control operations.

Three real-PostgreSQL regression cases cover complete traversal through duplicate/case/Unicode names, native cursor-key identity, exact boundary/terminal pages for both sorts and continuation after deleting the cursor team. Targeted Java21 `LiveDiscoveryIntegrationTest` verify: **29 tests,0 failures/errors/skips, BUILD SUCCESS,1m03s**.

The same1/10/100-caller,25s/phase direct-service probe was rerun at pool10. At100 callers:7,494 calls,294.36/s, **p50 257.424ms,p95 640.998ms,p99 1010.709ms,mean acquire233.352ms,mean hold33.052ms**,0 errors. This is worse than the earlier baseline probe's567.255ms p95; no independent timing win is claimed for A. It removes a redundant round trip correctly, but shared-host conditions differed (minimum free RAM~0.82GB versus~1.52GB during the earlier investigation). [A summary](../benchmarks/results/optimization-2026-11-10/phase-a-pool-10/service-summary.json).

## Phase B — ordered candidate counts

Compared two read-only SQL prototypes against the existing isolated seed:

- Narrow candidate/count aggregation reduced broad first-page SQL to roughly6ms and matched all8 baseline payloads.
- Indexed LATERAL counts on ordered candidates, with a materialized page before wide/profile joins, reduced representative first-page cases to roughly1–2ms and also matched all8 baseline payloads exactly. Name ordering used `teams_board_name_idx`; member counts used `team_members_succession_idx` (26 probes for the25-row name page plus sentinel).

The second plan was selected. Counts/capacity/readiness are evaluated **before LIMIT**. Desired skills, literal search, event scope and keyset predicates restrict candidates first. Bounds come from the same `TeamStore.event` read in the existing REPEATABLE READ transaction; missing-event404 is preserved. Profiles/wide details join only the materialized page. This is one server SQL statement with indexed lookups, not per-team application/network queries. No counter/cache, new index, schema migration or membership writer was introduced.

Three additional PostgreSQL/API cases cover full teams ahead of eligible page candidates, empty combined filters, all filtered duplicate-name pages with exact counts, authenticated/anonymous access, missing-event404 and private sort-key exclusion. Targeted verify: **32 tests,0 failures/errors/skips, BUILD SUCCESS,1m05s**. Existing visibility, event isolation, committed snapshots and live/capacity tests also passed.

At pool10/100 callers, B versus A measured independently:

| Metric | A only | A+B |
|---|---:|---:|
| Completed service calls |7,494|33,948|
| Calls/s including think/drain |294.36|1346.44|
| Service p50 /p95 /p99 ms |257.424 /640.998 /1010.709|3.420 /9.949 /19.424|
| Mean acquisition /hold ms |233.352 /33.052|0.772 /3.120|
| Mean active /pending |9.56 /66.09|6.40 /0.64|
| Errors |0|0|

These are matched service-probe recipes, not randomized controlled host trials or k6 acceptance results. Free RAM was higher in B (~2.33GB minimum). The plan/work reduction is independently observed, but a precise causal percentage should not be inferred from one shared-machine run. Hikari usage records have millisecond granularity; reported acquisition/hold are counter-delta means, not per-request percentiles.

Evidence: [narrow prototype](../benchmarks/results/optimization-2026-11-10/prototype/), [selected prototype](../benchmarks/results/optimization-2026-11-10/lateral-prototype/), [B pool10 summary](../benchmarks/results/optimization-2026-11-10/phase-b-pool-10/service-summary.json), [final optimized plan](../benchmarks/results/optimization-2026-11-10/final-board-explain.json). Final representative SQL execution1.263ms. The reproduction helper `benchmarks/explain-board.sql` now reflects the optimized query; its original version is retained as `baseline-explain.sql` in the optimization evidence.

## Phase C — pool experiments

Identical optimized application, schema, Java21, PostgreSQL settings and direct-service recipe at each pool size; sequential fresh JVMs,1/10/100 callers for25s each,8 variants,up to3 pages and200ms think time. **This pool sweep is service-only, not three canonical k6 runs.**

| Pool /100 callers | Calls/s | Service p50 /p95 /p99 ms | Mean acquire /hold ms | Active mean/max | Pending mean/max | Errors |
|---:|---:|---:|---:|---:|---:|---:|
|10|1346.44|3.420 /9.949 /19.424|0.772 /3.120|6.40 /10|0.64 /8|0|
|16|1338.00|4.066 /10.841 /24.556|0.623 /3.862|7.84 /16|1.56 /19|0|
|20|1320.78|4.585 /13.831 /28.774|0.865 /4.516|8.92 /20|2.40 /29|0|

JVM CPU averaged1.48/1.53/1.41 logical-core equivalents; sampled whole-host CPU61.0%/62.3%/56.7%. No SQL/API errors. More connections did not improve throughput/p95 and increased recorded hold time; PostgreSQL contention/wait attribution is not directly proven by this observation. No provider-level CPU profile or write-load performance claim. Optional32 was not run because10 already had the best observed result. **Default pool remains10**; only per-process experiment environment variables changed. [Pool16](../benchmarks/results/optimization-2026-11-10/phase-b-pool-16/service-summary.json), [pool20](../benchmarks/results/optimization-2026-11-10/phase-b-pool-20/service-summary.json).

## Regression commands and results

- `python .tmp/phase-a-tests.py`: Java21 offline Maven `-Pweb -Dtest=LiveDiscoveryIntegrationTest verify`,29 passed.
- `python .tmp/phase-b-tests.py`: same command,32 passed.
- `python .tmp/optimization-full-verify.py`: Java21 Maven `-Pweb clean verify`, **201 tests,0 failures/errors/skips, BUILD SUCCESS,4m26s**. Preserved all195 baseline tests plus6 new cases.
- Public50-way invite admission: **1 HTTP201,49 TEAM_FULL409**, roster2,49 losing nonmembers,358ms,0 deadlocks/timeouts. Retained M3 independent50-connection races:one success each,5655/71/71ms. Deliberate fault-recovery cases also passed.
- `npm.cmd --prefix frontend run typecheck`: passed.
- `npm.cmd --prefix frontend test`: **16 passed**,0 failed/cancelled/skipped,13.759s.
- `npm.cmd --prefix frontend run build`: passed,25 modules,257ms, unchanged frontend bundle hashes.

Actual Maven arguments used the previously isolated writable cache/output:

```text
mvn --offline --batch-mode --no-transfer-progress -Dmaven.repo.local=D:/HackerRank/PS1/.tmp/m2-local -Dteamforge.build.directory=D:/HackerRank/PS1/.tmp/backend-verify -f backend/pom.xml -Pweb clean verify
```

The full suite used new isolated schema `teamforge_benchmark_regression_m8a`, explicit JDBC/Flyway schema and unchanged V1–V6, to keep its immutable fixture audit history separate from the benchmark seed. No database/schema reset or existing row deletion. The earlier targeted suites used the established local-test fixture cleanup, which retains synthetic audit snapshots; counts must be distinguished from changes to existing user data. Exact final integrity accounting follows the comparable run.

Temporary diagnostic commands: `python .tmp/compile-inspection.py`; `python .tmp/probe-phase-a.py`; `python .tmp/probe-b-10.py`, `probe-b-16.py`, `probe-b-20.py`; `python .tmp/phase-b-prototype.py`; `python .tmp/phase-b-lateral-prototype.py`. All service probes called the real transactional Spring bean and read existing Micrometer metrics; only owned contexts were closed.

## Phase D and final handoff

Actual original-script command: `"C:/Program Files/k6/k6.exe" run --quiet benchmarks/board.k6.js`, via private `.tmp/run-optimized-k6.py`, same Java21 executable/packaged JAR/local profile/loopback8082/PG18.6/10-connection configuration. Script SHA256 is unchanged from the investigation baseline.100 distinct sessions,30s warm-up,180s/100-VU measurement,8 variants/up to3 cursors and200ms think time; no threshold or cohort change. **Exit0, all thresholds passed**:

- 198,602 measured requests;100% success,0% errors;1103.344 requests/s.
- p50 **6.0083ms**,p95 **42.5661ms**,p99 **101.7215ms**; strictly below200ms p95.
- 498.0s k6 wall time including setup/drain/logout. Original43,883-request/602.705ms failure retained.

[Complete final evidence](../benchmarks/results/optimization-2026-11-10/k6-pool-10/), [comparison](load-test-report.md). Final90 pool samples:peak8 active/0 waiting; no sampled waiter versus baseline peak81. Different shared-host free RAM (2.83GB versus0.47GB) is disclosed; no precise causal improvement claim beyond the observed results. No production performance/general availability guarantee.

Exact benchmark fixture fingerprint remains `9dcec261ba2afa9266181d345b66e30756000ddd882ccdc072e88c4b8f1f60fc`. Public users/teams/members/profiles remained0 before/after final k6; audit count5667 stayed stable during it. **The earlier targeted A/B suites appended147 immutable synthetic audit snapshots to the prior5520**, using the existing fixture cleanup policy; no preexisting audit was changed/deleted. The full201-test suite used the isolated regression schema. These test additions must not be described as unchanged public row counts across the entire task. No development database reset, no benchmark reseed and no existing user data destroyed.

Owned k6/JVM processes were stopped; the local JMX probe ended on expected disconnect (exit1, distinct from successful k6 exit0). Fixtures retained. A reused plan-capture helper saved a valid final plan then hit a stale machine-metadata-key error; the saved JSON was validated and no load/test result was affected. Final JSON/link/whitespace checks and source/migration invariants are checked separately. Hosted GitHub Actions did not run here.

## Changed-file inventory

The complete per-file list and reasons, including each versioned SQL/JSON evidence file, are in [manifest.json](../benchmarks/results/optimization-2026-11-10/manifest.json). Primary changes:

- `backend/src/main/java/com/teamforge/teams/application/BoardQuery.java`: private native cursor key, ordered/indexed candidate count, count filters before materialized page and bounded profile join.
- `backend/src/test/java/com/teamforge/teams/LiveDiscoveryIntegrationTest.java`:6 additional cursor/count/filter/auth/privacy regressions; original tests retained.
- `benchmarks/explain-board.sql`: reproduce the implemented plan; original helper retained in evidence. `benchmarks/README.md`: current outcome/reproduction pointer. `benchmarks/results/k6-status.json`: latest actual status; original run untouched.
- `README.md`; docs/performance-investigation-m8-a, load-test-report, progress, verification-m8-a, verification-m8-a-optimization, architecture, api-contract, decisions, test-plan, requirements, implementation-plan: synchronize actual outcome, implementation, phase evidence and review boundary.
- `benchmarks/results/optimization-2026-11-10/**`: prototype plans/equivalence, A/B/pool metrics, final k6/seed/environment/monitoring, tests and hashes; see manifest for every filename.

No changes to datasource defaults, M3 admission/locking/idempotency, V1–V6, frontend source/dependencies or CI workflow. Earlier uncommitted M8 seed/investigation work was preserved. No commit/push/deployment. M8-A local optimization acceptance is complete for review; **stop before M8-B**.

The manifest also inventories every pending repository path, explicitly distinguishing earlier uncommitted M8-A work from the17 primary optimization files and61 optimization evidence files. Final result JSON, links, full201-test counts, actual k6 exit/pass, exact seed equality and owned-port cleanup checks passed; `git diff --check` passed. Repository remains uncommitted on main at `ef183c0`; no remote CI success is claimed.
