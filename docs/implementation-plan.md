# Implementation plan
M5 now includes discovery, SSE and matching per latest user scope. Automated and HTTP/proxy gates passed; evidence/limitations in verification-m5.md; M6 complete product UI/accessibility gates passed; M7 and later require separate authorization. Each milestone updates progress, records material decisions and reports actual checks/failures.

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
| M7 Next scope to review | Reviewed M6 | Await user-defined next milestone; original UI scope completed in M6 |
| M8 Hardening | M7 | >=20 API tests in CI, seed validation, security and measured load report |
| M9 Delivery | M8; hosting choice | Live smoke checks, repository, URL, report, one-page design and <=5-minute video |

Priority: concurrency correctness first, defensible decisions second, quality/tests/UX third. No gate may substitute planned checks for observed results.


The latest scope places identity/profiles in M1-B and lifecycle/removal in M2. M3 covers explicitly requested concurrency/idempotency acceptance; later admission scope still requires authorization. M8 validates rate limits, large seed constraints and strict LOAD-01. M9 requires verified hosting/free-tier constraints, DEPLOY-01 and SUBMIT-01. M6 completion does not authorize M7.
