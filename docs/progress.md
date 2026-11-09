# Progress

## Current status — M1-B review handoff
M0/M1-A complete. M1-B implemented; automated gates passed, with browser visual/keyboard runtime review unavailable. M2–M9 not started. Stop for review before M2.
- User approved local email/password. Registration/login/logout/restoration and private participant profiles implemented; server-managed sessions, BCrypt, CSRF and bounded auth-IP rate limiting. No team workflows/SSE/suggestions.
- Before coding, reverified M1-A on installed JDK 21.0.13.9: five tests with no skips, executable packaging, backend/readiness/proxy HTTP 200. Earlier missing-JDK/plugin blockers are resolved.
- Final M1-B Java 21 isolated clean verify: 34 tests, zero failures/errors/skips, executable JAR packaging succeeded. Flyway V2 applied/validated with V1 unchanged. PostgreSQL 18.6 still triggers the documented Flyway tested-version warning; CI PostgreSQL 17.7 was not run locally.
- Frontend typecheck/build passed. Packaged backend + Vite proxy verified registration 201, login 204, saved/reloaded profile 200, logout 204 and post-logout 401 against local PostgreSQL. Only synthetic fixtures and smoke-owned processes were cleaned up; existing services remain running.
- Browser host unavailable; no visual/keyboard runtime or remote CI/deployment/benchmark result claimed. Frontend has labeled controls, focus/error/loading/status states and responsive CSS; browser QA remains a limitation.
- Read-only user-cache JAR access and IDE error stubs in target were resolved for verification by ignored cache/output isolation. Default Maven/CI output remains target. No application dependency version changes.
- No credentials changed/displayed; .env stays ignored. No commit/push. See [verification](verification-m1-b.md), [decisions](decisions.md), [startup](local-development.md).

## Historical foundation/recovery notes (superseded by current status above)
M0: complete ? all 12 existing documents inspected; documentation-only consistency review and traceability checks completed on 2026-11-09. Approved clarifications recorded; proposed mechanics remain labeled. .gitignore added.
M1: in progress. M1-A recovered with local PostgreSQL; final packaging/Java 21/startup checks remain blocked. M1-B and M2-M9 not started; stop for review.

M1-A adds backend/frontend foundation, configuration, CI and startup documentation only. No authentication or team workflows.

Review repaired generic acceptance coverage, missing explicit bounds/audit-retention tests, stale proposed labels, and request-cleanup lock-order ambiguity. Every REQ-01?19 maps to design, acceptance evidence and milestone in requirements.md. Identity provider, free-tier hosting verification and proposed mechanics remain in decisions.md.

Git repository exists. Initial M1-A working tree was clean; foundation source/configuration are new and related documentation is modified. This session did not initialize Git, commit or push.

## Initial M1-A verification ? 2026-11-09
- Java 24 and Maven 3.9.12 discovered; Java 21 runtime verification outstanding.
- Maven verify failed before compilation: parent POM download denied (getsockopt). Five backend test methods added, none executed.
- Frontend npm ci (offline), typecheck and build exited 0; 17 modules transformed. Dev HTTP returned 200; backend proxy returned 502 because backend could not run. Browser host unavailable, so no visual/runtime keyboard verification.
- Compose config parses; Docker daemon denied. Native PostgreSQL 18.6 accepted V1 SQL/defaults and rejected invalid bounds. Isolated server and frontend dev server stopped after checks.
- Full-stack connected state, Flyway history and PostgreSQL 17.7 application integration are unverified.
- [Exact commands and failures](verification-m1-a.md); [Windows startup](local-development.md). Identity/deployment providers remain open in decisions.md.
- M1-A is not declared fully verified; rerun backend/database checks with Java 21, registry access and reachable PostgreSQL. Do not start M1-B.

## M1-A local PostgreSQL recovery ? current status
- Root .env created only because absent, preserving existing configuration privately; example restored to placeholders. .env is ignored and untracked. Loader validates/map settings; new Start-Backend.ps1 loads them in every fresh process without Docker. application.yml and dependency versions unchanged.
- localhost:5432 reachable. Configured user connects to teamforge_db with CONNECT and public USAGE/CREATE. No permissions SQL required; no database reset or manually created tables.
- Flyway applied V1 and validated checksums; direct read-only history check confirms version 1 success and hackathons exists. PostgreSQL 18.6 generated a Flyway tested-version warning (latest supported 17); retained as a verification limitation.
- Offline application/test compilation completed on Java 24 targeting release 21. Final five backend integration tests passed (0 failures/errors/skips) after correcting the Actuator root allowed-field assertion. This supersedes earlier no-tests-run notes, which remain historical.
- Maven verify still exits 1 at repackage: four plugin artifacts missing from offline cache. Normal-terminal command is in local-development.md and verification-m1-a.md. No online retry, dependency replacement or downgrade.
- Only JDK 24 found. New PowerShell scripts are syntax-valid but runtime blocked by Restricted execution policy; no automatic policy change. User must run permitted fresh-terminal startup on JDK 21.
- Port-8080 backend/frontend-proxy check unexecuted after packaging blocker; integration tests verified real health/readiness HTTP 200 on random ports. Frontend untouched, previous typecheck/build not rerun. No CI/deployment result claimed.
- M1-A cannot yet be marked complete. Stop for user action; M1-B remains not started. No commit or push. No material architecture decision changed; decisions.md was not changed during this recovery.
