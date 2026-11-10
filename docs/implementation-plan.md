# Implementation plan
M5 includes discovery, SSE and matching; M6 completes UI/accessibility. The user authorized M7 CI/security/release hardening; its local evidence and remaining hosted/container/platform gates are in verification-m7.md. M8 and later require separate authorization. Each milestone updates progress, records material decisions and reports actual checks/failures.

| Milestone | Dependency | Acceptance gate |
|---|---|---|
| M0 Specifications | None | Documentation consistency reviewed; traceability complete; M0 complete |
| M1 Foundation | Approved M0 | Java/React skeleton, migrations, CI; tooling checks pass |
| M1-A Foundation subset | Current user authorization | Backend compile/test, frontend typecheck/build, database migration verification; blockers recorded honestly |
| M1-B Authentication/profiles | M1-A gate passed; local credentials approved | Full participant flow, Java 21 verify, frontend checks and PostgreSQL verification |
| M2 Team lifecycle | Reverified M1 | Additive constraints, create/edit/leave/remove/succession/deletion/audit, dashboard; Java21 real-PostgreSQL and frontend gates |
| M3 Concurrency/idempotency | Reviewed M2; new authorization | Persisted replay, race/rollback coverage and explicit last-seat acceptance; scope admission APIs only when authorized |
| M4 Admission workflows | Reviewed M3 | Requests/invites use shared admission with transactional authorization; HTTP invite/approval concurrency tests; existing M2 removal preserved |
| M5 Live discovery/matching | M4 | Board filters/keysets, after-commit SSE/reconnect, explainable matching and HTTP/proxy verification |
| M6 Complete product UI/accessibility | Reviewed M5 | Full responsive journeys, keyboard/contrast/status verification, browser interaction tests and real HTTP/proxy smoke |
| M7 CI/security/release hardening | Reviewed M6 | Full regression and HTTP/proxy checks, isolated PostgreSQL/browser CI, secure production settings; distinguish local success from unverified hosted/container checks |
| M8 Seed/load verification | Reviewed M7 and new authorization | Validate 10,000 users/2,000 constraint-valid teams; disclose environment and measure board p95<200ms at100 concurrent users; no inferred performance claim |
| M9 Delivery | M8; hosting choice | Live smoke checks, repository, URL, report, one-page design and <=5-minute video |

Priority: concurrency correctness first, defensible decisions second, quality/tests/UX third. No gate may substitute planned checks for observed results.


The latest scope places identity/profiles in M1-B and lifecycle/removal in M2. M3 covers concurrency/idempotency, M4 admission, M5 live discovery/matching and M6 UI. M7 was explicitly authorized for CI/security/release hardening; its local gates and external limitations are recorded. M8 validates large seed constraints, behavior under load and strict LOAD-01. M9 requires verified hosting/free-tier constraints, DEPLOY-01 and SUBMIT-01. M8 requires separate authorization.
