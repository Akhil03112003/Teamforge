# Implementation plan
M0/M1-A are complete. M1-B is authorized by the latest user instruction and explicit local-credential approval. M2 and later work require separate authorization. Each milestone updates progress, records material decisions and reports actual checks/failures.

| Milestone | Dependency | Acceptance gate |
|---|---|---|
| M0 Specifications | None | Documentation consistency reviewed; traceability complete; M0 complete |
| M1 Foundation | Approved M0 | Java/React skeleton, migrations, CI; tooling checks pass |
| M1-A Foundation subset | Current user authorization | Backend compile/test, frontend typecheck/build, database migration verification; blockers recorded honestly |
| M1-B Authentication/profiles | M1-A gate passed; local credentials approved | Full participant flow, Java 21 verify, frontend checks and PostgreSQL verification |
| M2 Identity/events | M1; provider choice | Authentication, permissions, profiles, configured event limits tested |
| M3 Core membership | M2 | Create/leave/succession/deletion, idempotency and audit invariants tested |
| M4 Admission/removal | M3 | Requests, invites, removal; 50-contender test passes |
| M5 Discovery | M4 | Board pagination and explainable ranking tested |
| M6 Live updates | M5 | Authorized SSE, disconnect/refetch recovery tested |
| M7 UI | M2–M6 | Entire flow responsive and keyboard accessible |
| M8 Hardening | M7 | >=20 API tests in CI, seed validation, security and measured load report |
| M9 Delivery | M8; hosting choice | Live smoke checks, repository, URL, report, one-page design and <=5-minute video |

Priority: concurrency correctness first, defensible decisions second, quality/tests/UX third. No gate may substitute planned checks for observed results.


M2 security gate includes permission integration and validation; the latest M1-B scope moves authentication and participant profiles ahead of M2 event work. M4 tests invite entropy and expiry, all admission lock paths and leader-only removal. M8 validates rate limits and seed constraints plus strict LOAD-01. M9 requires verified hosting/free-tier constraints, DEPLOY-01 and SUBMIT-01. M0 completion alone authorizes no later milestone; M1-B now has explicit authorization.
