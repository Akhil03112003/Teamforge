# M8-A performance investigation — 2026-11-10

## Approved optimization follow-up — completed for review
The user authorized Phases A–D after the investigation below. Cursor reuse and the evidence-selected ordered/indexed candidate-count query are implemented; profile joins are bounded by a materialized page and all count filters precede LIMIT. Pool experiments10/16/20 favored keeping10; datasource defaults and membership transactions remain unchanged.

| Comparable HTTP metric | Original | Optimized, pool10 |
|---|---:|---:|
| Measured requests |43,883|198,602|
| Success rate |100%|100%|
| Requests/s |243.794|1103.344|
| p50 /p95 /p99 ms |307.664 /602.705 /854.632|6.008 /42.566 /101.721|
| p95<200ms |Failed|**Passed**|

Same unchanged k6 script/100 VUs/30s warm-up/180s measurement/seed/JVM/PostgreSQL settings; both pools10. k6v2.3.0 exited0, all thresholds passed. Pool samples peaked8 active/0 waiting (sampled observations, not a guarantee of no transient wait). The shared machine had more free RAM (~2.83GB versus~0.47GB), so the observed92.94% p95 reduction is not a controlled causal percentage. Original results remain intact.

Phase A alone did not show an independent timing improvement (100-caller service p95640.998ms versus earlier567.255ms). Phase B after A measured9.949ms at pool10; service pool16/20 p95 was10.841/13.831ms, with no throughput gain. These diagnostics are distinct from the comparable HTTP run. Detailed phase results, regressions, data accounting and complete changed-file inventory: [optimization verification](verification-m8-a-optimization.md). Final evidence: [k6](../benchmarks/results/optimization-2026-11-10/k6-pool-10/), [integrity](../benchmarks/results/optimization-2026-11-10/integrity.json).201 backend and16 browser tests passed. Stop before M8-B; no commit/push/deployment.

## Historical investigation before optimization approval

**Review only. No application code, configuration, migration, pool size or benchmark script changed.** The comparable HTTP acceptance result remains **p95 602.705ms — failed**. The diagnostic service timings below are not a compliance rerun.

## Finding

Connection acquisition is the dominant measured service delay at100 concurrent callers. Pool saturation is both an immediate queueing bottleneck and a symptom of how long each request occupies a connection; it is **not proof of a single600ms database query**, nor proof that increasing the pool alone is safe.

The unchanged service reproduced high latency without k6, HTTP, authentication or JSON serialization. At100 callers, mean acquisition195.66ms, mean hold28.85ms, service p95567.26ms; the10-slot pool averaged9.57 active connections and64.35 waiting callers. At1–10 callers acquisition was effectively immediate and mean hold10.66–12.38ms. The increase in hold time under load matters as well as pool capacity. About87% of the two measured mean components (acquisition+hold) was acquisition at100 callers; this is not a decomposition of the HTTP p95.

Standalone broad SQL generally takes8–11ms, rising to about10–12ms with simultaneous database sessions. Selective skill queries are cheaper. SQL contributes, but those timings do not account for the entire connection hold interval: transaction preparation/commit/reset, JDBC round trips, row mapping and scheduling are also inside that interval. The precise attribution among them is still open. Neither an N+1 fetch pattern, physical-read/sort-spill problem, nor a catastrophic generic-plan regression was found in the probes.

## Exact read path and settings

Sources: [BoardQuery](../backend/src/main/java/com/teamforge/teams/application/BoardQuery.java), [TeamStore](../backend/src/main/java/com/teamforge/teams/persistence/TeamStore.java), [controller](../backend/src/main/java/com/teamforge/teams/api/TeamController.java), [datasource](../backend/src/main/resources/application.yml), [local profile](../backend/src/main/resources/application-local.yml).

1. Spring invokes the proxied `BoardQuery.board` in a read-only REPEATABLE READ transaction. `TeamStore.event` executes `SELECT * FROM hackathons WHERE id = ?`, preserving404 for a missing event.
2. The board CTE joins teams/hackathons, leader profiles and members, computes `count(m.id)`, open seats and READY/INCOMPLETE, and groups by team/bounds/leader display name.
3. Before grouping: event scope, exact desired-skill array containment and literal case-insensitive text search via `strpos(lower(name || ' ' || description),lower(?))`. `q` is not a wildcard search.
4. Outer keyset predicate: UUID `id>?`, or `(sort_name COLLATE "C",id)>(? COLLATE "C",?)`. Capacity/status predicates use computed counts. ORDER BY uses that same key; LIMIT is requested limit+1 (26 or51). No OFFSET or total-count query.
5. For another name-sorted page, a further `SELECT lower(name) FROM teams WHERE id=?` obtains the last displayed row's PostgreSQL lower-case cursor key. UUID sorting needs no extra lookup. Thus2 business SELECTs for ID pages,3 for name pages; transaction-control operations are additional, not included in that count. No per-team/roster/profile API loop.

Pool maximum10, connection timeout10,000ms, validation timeout3,000ms; minimum was not explicitly configured and actual gauges showed10. Open-in-view is false; response serialization is outside the service transaction. Native PostgreSQL18.6, Java21.0.13.9, HikariCP6.3.3, pgJDBC42.7.11, Spring JDBC/ORM6.2.19. The existing10k-user/2k-team isolated schema was used, with no public fallback. No server settings, statistics, extensions or optimizer modes were changed.

## SQL plans and timings

Captured all8 k6 variants on their actual first3 cursor pages: **24 board cases ×3 EXPLAIN runs**, plus the event and cursor lookups. Files contain source-equivalent SQL with bound values rendered as literals, `EXPLAIN (ANALYZE, BUFFERS, SETTINGS, FORMAT JSON)` and read-only transactions. [Plan summary](../benchmarks/results/investigation-2026-11-10/plan-summary.json); [all SQL/JSON](../benchmarks/results/investigation-2026-11-10/).

Fresh psql sessions have cold backend planning caches, so their initial8–19ms planning times are **not representative of every steady-state JDBC request**. A separate persistent-session PREPARE/EXECUTE probe used matching parameter types and natural PostgreSQL plan selection, without forcing generic/custom mode. Each shape selected5 custom then2 generic plans in7 calls. Warm planning dropped to roughly0.01–0.04ms for the last generic call; no severe generic-plan slowdown appeared. [Prepared summary](../benchmarks/results/investigation-2026-11-10/prepared-summary.json).

Further SQL-only comparison:40 EXPLAIN EXECUTEs/session, discard first5, first sequential sessions then10 independent simultaneous sessions (variants0–7 plus0/1). This isolates server SQL execution from application pool acquisition; EXPLAIN instrumentation and this short repeated-query mix differ from HTTP load. [Full results/representative plans](../benchmarks/results/investigation-2026-11-10/sql-concurrency.json).

| Variant / first-page shape | Warm solo SQL median ms | Concurrent-session median ms | Plan finding |
|---|---:|---:|---|
| 0 ID/50 | 8.430 | 9.692,9.910 | Join/sort roster input, streaming group stops after51 output groups |
| 1 name/25 | 8.809 | 11.490,11.088 | Hash aggregate2,000 teams then top-N sort |
| 2 name/50/open | 9.153 | 10.687 | Count groups then reject full teams before top-N |
| 3 ID/25/INCOMPLETE | 10.681 | 10.172 | Count/status filtering; broad roster work remains |
| 4 name/50/READY/java | 2.116 | 3.318 | Desired-skill GIN used; fewer team groups |
| 5 ID/50/design | 1.450 | 2.583 | GIN and streaming grouped result |
| 6 name/25/q=Circuit | 6.297 | 8.328 | Team text predicate reduces eligible teams; scan remains |
| 7 ID/25/open/react | 1.950 | 3.482 | GIN and count-based capacity filtering |

The24 literal board cases had zero shared block reads and zero temporary block writes at the root: resident buffers, no observed sort spill. Existing desired-skills GIN is used on skill variants. Sequential scans on small unfiltered tables are not evidence of an absent index. The name index cannot simply make a post-aggregation count/status query stop at25 base teams without preserving filter semantics. Name pages process broad groups; ID streaming aggregation can stop output early, but its upstream join/sort can still process6,000 roster rows. The earlier generic statement that every board shape aggregates all2,000 groups before LIMIT needs this qualification.

Event and cursor SQL executions were approximately0.08–0.11ms alone. Their network/JDBC/transaction cost can exceed server execution time. These per-statement medians are not additive percentiles or endpoint latency.

## Direct acquisition/hold and CPU measurements

A temporary ignored diagnostic driver booted the unchanged packaged application using its existing Spring entry point/configuration and10-connection pool. It invoked the **actual transactional BoardQuery bean**, not reimplemented SQL. It used the eight existing variants/cursors and200ms think time per3-page traversal, for25s phases at1/10/100 platform-thread callers. No HTTP, auth bypass endpoint, service replacement or configuration bean was added. The packaged query bytecode matches the extracted diagnostic class byte-for-byte.

Existing Micrometer `hikaricp.connections.acquire` and `.usage` timer count/sum deltas supplied mean acquisition and borrowed-connection hold time. Gauges were sampled at500ms. Every phase recorded exactly one acquire/return per successful board call, no errors. No histogram/percentile settings were enabled: acquisition/hold **p95 is unavailable**, not invented. [Hikari6.3.3 metric definitions](https://github.com/brettwooldridge/HikariCP/blob/HikariCP-6.3.3/src/main/java/com/zaxxer/hikari/metrics/micrometer/MicrometerMetricsTracker.java).

| Callers | Calls | Mean acquire ms | Mean hold ms | Service p95 ms | Active mean/max | Idle mean | Pending mean/max |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 |303|0.017|12.38|24.19|0.12/1|9.88|0/0|
|10|3,123|0.007|10.66|23.70|1.76/8|8.24|0/0|
|100|8,563|195.66|28.85|567.26|9.57/10|0.43|64.35/76|

[Summary](../benchmarks/results/investigation-2026-11-10/service-summary.json), [raw timer/gauge snapshots](../benchmarks/results/investigation-2026-11-10/service-probe.json). At100 callers throughput was335.96 completed service calls/s including phase drain;10/0.02885 suggests a ceiling around347 calls/s **if hold time stays fixed**, consistent with the observed pool-limited rate. It is not a prediction for a larger pool: hold time already grew under load.

In the100-caller phase JVM CPU averaged0.51 logical-core equivalents, whole-host CPU averaged48.7% (sampled max58.4%), free physical memory stayed above1.52GB, and summed GC collection-time counters rose52ms in25.49s. This does not show JVM CPU saturation or GC as the main cause, but there was no GC-pause histogram or privileged PostgreSQL CPU measurement. Host CPU includes unrelated applications; do not assign the remainder to PostgreSQL. Earlier HTTP run had only~0.47GB free RAM and different resource conditions. Do not subtract its p95 from this shorter service p95 to estimate HTTP overhead.

The service-only reproduction shows k6/HTTP/JSON cannot be the **sole** explanation. It does not rule out their additional costs in the original run. Socket/protocol timing and transaction preparation/commit/reset versus main SQL remain unseparated within hold time. Preserve REPEATABLE READ; changing consistency to reduce overhead is not proposed.

## k6 workload/metric audit

-100 distinct seeded sessions; paced CSRF/login in setup, outside custom board timing. Cookies are explicitly supplied on reads. No passwords are sent on each board GET; no insecure public join or authentication bypass.
-100 VUs are concurrent closed-loop users, **not100 new requests/second**. Three cursor pages with200ms think time is aggressive browsing, not an observed human trace. Keep this declared workload for comparable acceptance; do not relax it to produce a pass.
-Connection reuse is not disabled; default keep-alive/reuse remains. Saved whole-run connecting/blocked p95 were0ms, consistent with reuse. Those aggregates include setup/warm-up/teardown, so they are not measured-board-only proofs. [k6 reuse options](https://grafana.com/docs/k6/latest/using-k6/k6-options/reference/#no-connection-reuse).
-Custom board duration uses `response.timings.duration`, the normal send+wait+receive timing excluding initial DNS/TCP/TLS. Setup/warm-up/logout are excluded; errors are not removed from latency. [k6 timing definitions](https://grafana.com/docs/k6/latest/using-k6/metrics/reference/).
-The original whole-run waiting p95 was527.18ms, receiving p95176.88ms, sending p957.05ms; **do not add these percentiles**. Client scheduling/body reception can contribute beyond server query time, especially co-located with low free RAM. No per-variant latency series was saved despite name tags, limiting tail attribution.
-Completion-window filtering occurs after JSON parsing and drops responses finishing after180s (including up to10s drain). This can censor slow tail requests and slightly shift boundary assignment. It does not explain away an already failed602.7ms p95. Proposed later improvement: retain the original primary metric for comparability and add a separately labeled started-in-window/drained cohort plus per-variant phase metrics; do not silently change the gate definition.
-A failed response has a measured failure and a failed check; the43,883 successful measured checks match the custom request count. Error/traffic thresholds passed; latency did not. Raw `http_req_*` aggregates and the custom board metric have different cohorts and must not be interchanged.
-No SSE subscribers were opened; SSE notification fan-out or reconnect refetch storms did not cause this reproduction. Separate live-connection/suggestion workloads remain necessary before deployment-scale claims.

## Prioritized proposals — not approved or applied

| Priority | Proposed next change/experiment | Expected impact | Regression risk / gate |
|---|---|---|---|
|1|Reuse returned PostgreSQL `sort_name` for the cursor instead of issuing the extra SELECT|Remove one round trip on name pages; under this uniform mix business SELECTs average2.5→2.0 (20% fewer business queries, not20% guaranteed latency)|Low; preserve PostgreSQL lower-case/C-collation key, UUID tie-break, cursor encoding, last displayed versus sentinel row; test Unicode/ties/rename snapshot cases|
|2|Prototype narrow membership-count aggregation, and defer leader-profile/wide-row joins until eligible page candidates are selected|Reduce repeated broad join/hash/sort work and connection hold; likely greatest SQL-work opportunity, magnitude unmeasured|Medium; count/capacity/readiness filters **before LIMIT**, exact membership counts, missing-event404, event scope, keyset ordering and committed snapshot must remain; compare plans and existing/new API tests|
|3|After measuring hold improvement, controlled pool sweep10/15/20 on the isolated target with identical k6 mix|May reduce queueing if DB/CPU/memory have headroom; no guarantee|More connections can inflate query time, consume PostgreSQL's100-connection budget and harm writes. Record active/pending/hold/CPU; retain50-contender and lifecycle regressions. No production default change without review|
|Supporting|Per-variant HTTP and pool timing, transaction/protocol profiling; controlled host or separate generator|Attribute the residual hold gap and test result stability|Monitoring overhead and cohort changes must be disclosed; retain comparable primary benchmark and security controls|

Do not add another index just because a sequential scan exists: GIN already works and these plans are memory-resident. Do not introduce denormalized roster counters, cache membership truth, weaken isolation, change M3 locks/idempotency or add distributed infrastructure for this investigation. No measured percent improvement or p95 compliance is promised by these proposals.

## Commands, safety and remaining gate

Executed diagnostic commands (private helpers remain ignored, never application/release dependencies):

```powershell
python .tmp/investigate-plans.py
python .tmp/investigate-prepared.py
python .tmp/compile-inspection.py
python .tmp/run-service-inspection.py
python .tmp/investigate-sql-concurrency.py
python .tmp/finalize-service-summary.py
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .tmp/m8-a-final-verify.ps1
python .tmp/verify-investigation-integrity.py
```

The temporary driver compiled against extracted unchanged JAR classes/libraries, then used Java21 `-cp <classes;lib/*;driver> BoardInspection` with the same baseline local/loopback8082/Hikari10/schema settings. Existing Flyway validated6 migrations and reported the isolated schema up to date; no migration was applied. Read-only SQL used psql `-X -w -qAt -v ON_ERROR_STOP=1` with `PGOPTIONS=-c search_path=teamforge_benchmark_m8a`, never public. Capture sources/bytecode hashes and unchanged seed/public counts: [integrity evidence](../benchmarks/results/investigation-2026-11-10/integrity.json).

Reproduce a saved plan in a configured fresh benchmark terminal:

```powershell
. ./scripts/benchmark/Load-Environment.ps1
psql -X -qAt -v ON_ERROR_STOP=1 -c 'SELECT current_database(),current_schema();'
# Confirm the isolated target before proceeding.
psql -X -qAt -v ON_ERROR_STOP=1 -f benchmarks/results/investigation-2026-11-10/variant-1-page-0.sql
psql -X -qAt -v ON_ERROR_STOP=1 -f benchmarks/results/investigation-2026-11-10/prepared-variant-4.sql
```

All probes exited successfully, the owned Spring context closed gracefully, and only read operations were performed. Full seed verification again matched10,000 users/2,000 teams and the exact prior fingerprint; recorded public counts matched the saved baseline. No application tests were rerun because there was no application/configuration change; prior195 backend/16 browser results are unchanged historical evidence, not new passes. No comparable k6 rerun was performed in this investigation. Its last result is still **failed**. Stop here for review before any optimization; M8-B/deployment/commit/push were not started.
