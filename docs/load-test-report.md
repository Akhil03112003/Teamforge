# Team-board benchmark report — M8-A, 2026-11-10

## Latest comparable result — PASSED

The authorized optimized application completed the **unchanged original k6 workload** at100 VUs,30s warm-up and180s measurement, same10k/2k seed, Java21 and PostgreSQL settings, **pool still10**. k6v2.3.0 exit **0**, all latency/error/check/traffic thresholds passed.

| Metric | Original failed run | Optimized run |
|---|---:|---:|
| Measured requests |43,883|**198,602**|
| Success /error rate |100% /0%|**100% /0%**|
| Throughput requests/s |243.794|**1103.344**|
| p50 ms |307.664|**6.008**|
| p95 ms |602.705|**42.566**|
| p99 ms |854.632|**101.721**|
| p95<200ms |Failed|**Passed**|

Evidence: [compact result](../benchmarks/results/optimization-2026-11-10/k6-pool-10/board-results.json), [complete metrics](../benchmarks/results/optimization-2026-11-10/k6-pool-10/k6-summary.json), [metadata](../benchmarks/results/optimization-2026-11-10/k6-pool-10/run-metadata.json), [runtime/pool diagnostics](../benchmarks/results/optimization-2026-11-10/k6-pool-10/diagnostics.json), [phase verification](verification-m8-a-optimization.md). Original evidence is preserved below and in its original directory.

Only query/cursor code changed; thresholds, VUs, request mix, authentication pacing, think time and metric definitions are byte-for-byte unchanged. Candidate counts use existing indexes and profiles join after the materialized page; min/max/readiness/capacity filters remain before pagination. Direct-service pool sweeps10/16/20 did not justify increasing the default. In90 sampled final-run pool observations, max active8 and max waiting0; use service counter-delta means separately for acquisition/hold timings.

Same Windows11/i5-12450H/12-logical-CPU/16.87GB machine; final captured free RAM2,827,923,456 bytes versus~471MB in the baseline. Generator, JVM and PostgreSQL are co-located; this is not a randomized trial or production-host guarantee. The observed92.94% p95 reduction has that caveat. Final k6 wall time498.0s including authentication/warm-up/drain/logout; read-only seed fingerprint before/after identical. Recorded public counts stayed stable during this run. No raw-sample output; monitoring matched the baseline approach. Native PostgreSQL18.6 retains the known Flyway tested-support warning; remote CI/PG17.7/deployment remain unverified.

## Original failed run — preserved historical baseline

**k6 executed: the p95<200ms requirement FAILED.** After the user installed k6, v2.3.0 was found at `C:\Program Files\k6\k6.exe` (the running shell's PATH was stale). The initial download blocker is resolved. A real100-VU run completed against the retained isolated dataset; no application/query/configuration default was changed to obtain the result.

Original executed evidence: [compact result](../benchmarks/results/k6-2026-11-10/board-results.json), [complete k6 summary](../benchmarks/results/k6-2026-11-10/k6-summary.json), [run metadata](../benchmarks/results/k6-2026-11-10/run-metadata.json), [diagnostics](../benchmarks/results/k6-2026-11-10/diagnostics.json). k6 exited **99** because `board_duration` crossed its latency threshold.

Subsequent **investigation only** measured acquisition/hold directly around the unchanged Spring service and captured representative prepared/literal plans: [findings and prioritized proposals](performance-investigation-m8-a.md). Those service diagnostics do not replace this comparable HTTP result; no optimization or configuration change was applied.

## Observed results

| Item | Actual result |
|---|---|
| Users / profiles / event anchors | **10,000 / 10,000 / 10,000** |
| Teams / memberships / unteamed users | **2,000 / 6,000 / 4,000** |
| Team sizes1/2/3/4/5 | **400 each** |
| Incomplete / ready / full / nearly full | **400 / 1,600 / 400 / 400** (full/nearly-full are subsets of ready) |
| Audit snapshots | **6,000** |
| Duplicate event membership / invalid leaders / overcapacity | **0 / 0 / 0** |
| Seed / verified no-op repeat time | **18.516s / 4.672s** |
| Whole-fixture repeat comparison | Exact match, including every fixture column and supplied credential hash |
| Authenticated board functional smoke | **24 HTTP200 reads**,8 filter/sort combinations ×3 pages, capacity assertions passed |
| k6 measured requests / success / throughput | **43,883 /100% /243.794 requests/s** |
| k6 p50 / p95 / p99 | **307.664 /602.705 /854.632ms** |
| p95<200ms | **FAILED** (602.705ms) |
| Error/check/traffic thresholds | Passed:0 failed measured checks;43,883 requests exceeds18,000 floor |

Safe machine-readable seed evidence: [seed](../benchmarks/results/seed-verification.json), [repeat](../benchmarks/results/seed-repeat.json), [post-corruption-check restoration](../benchmarks/results/seed-restored-verification.json), [environment](../benchmarks/results/environment.json), [initial machine](../benchmarks/results/machine.json). [Latest k6 status](../benchmarks/results/k6-status.json) follows the newest run; the original failed record remains in `k6-2026-11-10`. Raw request-sample output was not enabled; the complete aggregate summaries are preserved separately.

## Environment and isolation

Windows11 Home Single Language10.0.26200, x64 Intel Core i5-12450H,12 logical CPUs,16.87GB RAM (15.71GiB). At load-run metadata capture only **471,646,208 bytes (~0.47GB)** were free; existing developer processes were preserved. This is a shared developer machine, not a controlled production host. [Load machine snapshot](../benchmarks/results/k6-2026-11-10/machine.json). CIM hardware queries were denied; CPU/RAM/OS came from Node's OS API. JDK21.0.13.9, Maven3.9.12, Node24.19.0; packaged Spring Boot application on loopback HTTP8082, direct REST without Vite/proxy/cache. k6 v2.3.0 (e088784614,Go1.26.8) ran on this same machine. No benchmark SSE subscribers were opened.

Native PostgreSQL18.6, max_connections100, shared_buffers128MiB, work_mem4MiB, effective_cache_size4GiB, JIT on, default isolation READ COMMITTED. Board uses its existing REPEATABLE READ transaction. Hikari maximum10, unchanged timeouts/security. Flyway emitted the known PostgreSQL18 tested-support warning (tested through17); CI's17.7 has not been exercised here. Schema data/table+index footprint18,145,280 bytes at capture.

The configured application role has no CREATEDB privilege. Its permitted CREATE-schema fallback was used: **database teamforge_db, isolated schema teamforge_benchmark_m8a**, with explicit Flyway target and JDBC/psql search path containing that schema only. Existing public users/teams/members/profiles counts remained0 and immutable audit count5520 before/after seed and board smoke. No normal table was cleared or reset. Six unchanged migrations applied inside the new schema; the retained Flyway demo event has no participants/teams. The isolated schema remains available for inspection; only the owned benchmark JVM was stopped.

## Executed workload

[board.k6.js](../benchmarks/board.k6.js) prepares100 distinct synthetic sessions outside board timing, respecting the existing30-auth-attempts/minute/IP limit. A30-second100-VU warm-up precedes180 seconds at100 VUs. The eight variants exercise ID/name keysets,25/50-page sizes, capacity/readiness, skill and literal text filters, following up to3 actual cursors. A200ms think time separates traversals. Reported metrics include only requests completed inside the measured window; up to10s drain is excluded. Authentication, warm-up and logout stay outside custom board timing.

Thresholds: board p95 strictly<200ms, failures<1%, checks>99%, and at least18,000 completed measured reads. The traffic/error floors are explicit benchmark defaults. k6 wrote the complete summary plus normalized p50/p95/p99/count/success/throughput/threshold output.100 sessions were authenticated with the existing throttle, followed by30s warm-up and180s measurement. k6 wall time was509.094s including setup/drain/logout; follow-up fixture checks took total wrapper time to512.656s. All43,883 measured checks passed; latency alone failed. [Exact repeatable commands](../benchmarks/README.md).

Local monitoring enabled Hikari's local-only MBeans on the owned benchmark JVM (no public metrics endpoint). A local attach probe sampled pool/heap about every5s; a separate loop sampled PostgreSQL activity and owned Java/k6 CPU/RAM. This monitoring has overhead and is disclosed; raw sample output was disabled. Only owned processes were stopped. Seed before/after fingerprints matched; recorded public counts remained unchanged.

## Query review and limits

No API/query/index optimization was made in this measurement follow-up. Source inspection shows set-based roster aggregation with no per-team fetch loop: event read+board query, plus one name-cursor lookup. V6 discovery indexes remain unchanged; serialization shape and M3 locking/idempotency were not altered.

A read-only name-sorted first-page `EXPLAIN ANALYZE` on the seeded schema took **21.634ms** for that SQL statement: hash aggregate/joins and top-N heapsort, no sort spill in this plan. [Raw JSON plan](../benchmarks/results/board-explain.json); [reproduction SQL](../benchmarks/explain-board.sql). PostgreSQL chose sequential scans at this fixture size; that choice alone is not evidence of a missing index. This one database plan is **not endpoint latency, concurrent latency, or a k6 percentile**.

After the failed run, the same warm read-only plan took **8.809ms**: [post-run plan](../benchmarks/results/k6-2026-11-10/board-explain-after.json). It joins6,000 roster rows, aggregates2,000 team rows and sorts to return51 candidates for a50-row page. This repeated aggregate/join work is an optimization candidate; the isolated SQL time does not explain the full concurrent endpoint tail on its own.

Pool evidence:97 JMX samples,40 with waiters, peak **10 active/10 total** and **81 waiting**; peak observed heap used97,049,488 bytes, committed109,051,904. PostgreSQL samples predominantly showed active benchmark queries or brief ClientRead waits, rather than a sampled lock-wait explanation. [Pool samples](../benchmarks/results/k6-2026-11-10/pool-samples.jsonl), [activity/CPU/RAM samples](../benchmarks/results/k6-2026-11-10/runtime-samples.json). This establishes pool queueing under the workload, not that increasing the pool will solve it. No GC pause/serialization timing or privileged database CPU profile was captured. Generator and application were co-located with low host free memory, so the result cannot be generalized to a controlled deployment or blamed solely on either component.

If the real run fails, inspect generator CPU/RAM/output I/O separately from JVM GC/CPU, Hikari queueing, PostgreSQL waits/plans and serialization. Co-located generation plus low free memory can limit achievable throughput; classify from collected evidence, not assumptions. A separate generator/otherwise idle host, actual provider configuration and declared warm/cold conditions are needed for defensible comparisons. This workload does not measure live-SSE connection/reconnect storms or teammate-ranking CPU. Hosted CI, deployment and the p95 acceptance gate remain open; do not advance on a fabricated result.
