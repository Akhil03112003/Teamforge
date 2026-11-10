# Progress

## Current status — M7 review handoff
M0–M7 implementation and feasible local M7 gates are complete for review; **hosted CI/container/production-platform verification remains open**. Stop before M8. All older handoffs below are historical.
- Started clean on main at42af67a; origin unchanged. Reconfirmed171 backend/15 browser baseline. Final Java21 web-profile clean verify: **195 tests,0 failures/errors/skips**, BUILD SUCCESS,4m05s. Final frontend: **16 tests passed**,13.047s; typecheck/build passed. No passing GitHub-hosted result claimed.
- CI now explicitly provisions Java21/Maven cache, PostgreSQL17.7, Node24 and Chrome/Linux dependencies; push/PR main/manual dispatch, restricted pinned actions, masked ephemeral credentials and test artifacts. GitHub API networking blocked; [trigger/inspect instructions](ci.md).
- Hardened request/form/session bounds, production cookie/profile guards, safe errors/logging, browser headers and graceful SSE shutdown. Same-origin web-JAR packaging/non-root container recipe added; Docker daemon denied, image unbuilt. Local HTTP remains separate. M3 locks/idempotency and V1–V6 unchanged; all6 migrations validated on existing native PostgreSQL18.6 (known tested-version warning remains).
- Public50-contender race passed:1 HTTP201/49 capacity409, correct roster/nonmembers,312ms, no deadlocks/timeouts. Existing full lifecycle/authentication/live/suggestion regressions preserved. Real packaged desktop/mobile browser journey passed40.341s; HTTP/Vite proxy board/SSE/reconnect smoke passed30.641s. Owned fixtures/processes only cleaned, immutable audits retained.
- Browser run found/remedied redemption announcement lost during membership remount; new regression added. Contrast test found/fixed transient enabled-button contrast. Local npm ci hit an external native-file lock; dependencies restored without manifest/lockfile change, clean isolated npm ci passed. Failures and exact commands are disclosed in [M7 verification](verification-m7.md).
- Formal CSO/advisory assessment not performed; production HTTPS/edge/signal checks, least-privilege roles, backup targets and provider/free-tier selection remain open. See [security](security.md) and [deployment prerequisites](deployment.md). No deployment/load claims, data reset, commit or push. M8/M9 not started.

## Current status — M6 review handoff
M0–M6 implemented; M6 automated, browser responsive/keyboard and production-bundle journey gates passed. Stop before M7; M7–M9 not started. Older handoffs below are historical.
- Initial Git tree clean. Reverified170-test backend baseline on JDK21 (4m50s). Final clean verify:171 tests,0 failures/errors/skips, BUILD SUCCESS/executable packaging,4m20s. Original170 preserved plus one literal/event-scoped board-search case. V1–V6 unchanged; M3 locking/idempotency/domain services unchanged.
- Cohesive app shell has Discover/My team/Invitations & requests/Profile, URL-backed hackathon restoration, whole-event search/filter/sort/pagination, native confirmations/copy, status/retry states and guarded/stale-safe mutations. SSE stays mounted across profile navigation; background refresh leaves loaded actions usable.
- Frontend15 Node/Chrome tests passed (16.143s), typecheck/build passed (25 modules,207ms; JS265.11kB/gzip80.86kB, CSS12.37kB/gzip3.56kB). No frontend dependency installed. CI now invokes these tests but remote Actions not run.
- Actual browser matrix1440×1000/768×1024/390×844 checked four views for overflow, labels, descriptions, heading hierarchy,44px targets and AX navigation. Keyboard skip-link/dialog Tab/Shift+Tab/Escape/focus restoration, computed contrast and reduced motion passed. Screenshots were inspected. No manual screen-reader/cross-browser/physical-device/complete WCAG result claimed.
- Final packaged-backend/production-Vite-preview real PostgreSQL browser flow passed three-account registration/login/profile/create/invite/request/withdraw/accept/decline/remove/redeem/succession/delete/reload/logout. Includes actual mobile registration/request/redemption and successor actions. Standalone HTTP/Vite proxy board/SSE/reconnect smoke passed.
- Earlier browser/harness timing/selector/focus failures were corrected/disclosed. Final frozen production run passed; only owned QA data/processes cleaned, immutable audits retained. Full results/remaining minor UX and manual accessibility limits: [verification](verification-m6.md).
- No reset, commit/push, deployment/load/submission or M7 work. Native PostgreSQL18.6 retains the known Flyway version warning; Testcontainers/PG17.7 and remote CI remain unverified. Hosting remains open.

## Current status — M5 review handoff
M0–M5 implemented; M5 automated gates and HTTP/proxy smoke passed. Stop before M6. Older handoffs below are historical; M6–M9 not started.
- Initial Git tree clean. Original133-test M4 baseline reverified successfully (3m51s). Final JDK21 clean verify:170 tests, zero failures/errors/skips, BUILD SUCCESS/executable packaging,4m07s. New25 PostgreSQL/HTTP discovery and12 scoring cases; all133 regressions retained.
- V6 adds discovery indexes only, applied/validated against existing local PostgreSQL; V1–V5 untouched. Board filters/name-or-ID cursor sort/capacity/public leader data use committed snapshots and bounded set queries. Admission locking/idempotency unchanged.
- SSE coalesces audited mutations per event/transaction and sends only after commit. Authenticated bounded queues/virtual writers/heartbeats/session cleanup; ready/reconnect/30s periodic REST reconciliation. No tokens/private workflow data in events. Explainable70/30 matching has deterministic UUID ties, IANA availability/default assumptions, bounded candidate work and private-field redaction.
- Public50-contender invite regression:1 HTTP201/49 capacity conflicts, correct roster/nonmembers,351ms; no ordinary race deadlocks/timeouts. M3 independent50-connection races and recovery tests passed.
- Final frontend typecheck/build passed (23 modules,171ms). Packaged backend/Vite proxy smoke passed authenticated SSE ready, committed invalidation, filtered board/suggestions, reconnect and REST reconciliation. No frontend runner/browser visual/keyboard result claimed.
- One new-test compile assertion issue and one smoke console-encoding issue were corrected and rerun; full history in verification-m5.md. Native PostgreSQL18.6 Flyway tested-version warning remains; Docker denied daemon access. Testcontainers/PG17.7/remote CI, large seed/k6 and deployment remain unexecuted.
- No dependencies installed, data reset, commit/push or M6 polish. Performance risks and complete file inventory: [verification](verification-m5.md). See [API](api-contract.md), [architecture](architecture.md), [decisions](decisions.md). Hosting selection remains open.

## Current status — M4 review handoff
M0–M4 implemented; M4 automated gates passed. Stop before M5. Historical handoffs below are superseded by this section. M5–M9 not started.
- Initial Git clean; original88-test M3 baseline rerun passed. Final Java21 Maven clean verify:133 tests, zero failures/errors/skips, BUILD SUCCESS/executable packaging,3m48s. New M4 coverage:44 workflow cases plus1 real rate-limit case; all original88 remain green.
- Public50-way authenticated invitation race:1 HTTP201,49 TEAM_FULL409, roster at maximum2, no loser memberships, one admission audit;277ms and no deadlocks/timeouts. Mixed public invitation/approval race likewise admits exactly one. Other cleanup/rotation/withdrawal/leadership/deletion/retry races passed. Separate fault test proves nested whole-workflow retry after a real lock timeout. Retained M3 independent50-connection tests also passed.
- V5 applied/validated on existing local PostgreSQL; V1–V4 unchanged. Invitations hash-only/256-bit/database24h expiry/rotation; legal request transitions/current-leader decisions; shared admission and atomic persisted replay/cleanup/audit; actual HTTP lost-response recovery for redemption and acceptance.
- Frontend request/invitation controls, statuses/withdrawal/paginated inbox and typed key/CSRF patterns implemented. Final typecheck/build passed (21 modules,182ms). No runtime visual/keyboard result claimed.
- Native PostgreSQL18.6 emits the known Flyway tested-version warning. Docker reprobe denied daemon access. PostgreSQL17.7/Testcontainers, remote GitHub Actions, large seed/load tests and deployment remain unexecuted. Hosting selection remains open.
- No dependencies installed, resets, commits/pushes, SSE, suggestions or M5 work. See [verification and file inventory](verification-m4.md), [decisions](decisions.md), [API](api-contract.md), [architecture](architecture.md).

## Current status — M3 review handoff
M0–M3 implemented; M3 automated gates passed. Stop before M4. Historical handoffs below are superseded by this section. Browser visual/keyboard runtime review, remote CI and deployment remain unverified.
- Reverified the original 61-test baseline before changes. Final Java21 Maven clean verify: 88 tests, zero failures/errors/skips; executable packaging successful, 2m52s. New concurrency/recovery class: 27 cases, 33.135s.
- Three final-seat races each used 50 independent PostgreSQL connections and distinct eligible users: exactly one admission, 49 TEAM_FULL, correct maximum roster and no loser memberships. Durations 5214/98/100ms; no ordinary-race deadlocks or lock timeouts. Deliberate recovery tests separately exercised one deadlock, released timeout retry and three-timeout bounded exhaustion.
- V4 applied/validated on existing local PostgreSQL without resetting data or editing V1–V3. Shared production admission and atomic persistent create/admission replay implemented; immutable audit retained. No public admission endpoint or M4 workflow.
- Frontend typecheck and production build passed. Create now sends/reuses an intent key. Existing lifecycle/authentication behavior remains covered. No application dependencies added.
- Docker daemon inaccessible; tests used native PostgreSQL18.6. Flyway tested-version warning remains; PostgreSQL17.7 CI, remote Actions, load benchmark, seed acceptance and deployment were not run.
- No commit/push. See [M3 verification](verification-m3.md), [decisions](decisions.md), [contract](api-contract.md) and [architecture](architecture.md). M4–M9 not started.

## Current status — M2 review handoff
M0/M1 complete. M2 implemented and automated/backend/proxy gates passed; browser visual/keyboard runtime review remains unavailable. M3–M9 not started. Stop before M3.
- Initial Git tree clean at 857528f. Reverified M1 Java21 clean verify: all34 tests passed before M2 changes.
- Additive V3: participant anchors, teams, team_members, immutable audit snapshots, event-aware uniqueness/FKs, deferred leader membership, capacity guard and an empty demo event. V1/V2 unchanged. No reset/replacement of existing data.
- Team API supports authenticated create/read/roster/edit/leave/current-leader removal, deterministic succession and final-member deletion with retained authorized audit history. All membership writes lock participant anchors before the team and recheck authority. No joining/invites/requests/SSE/suggestions.
- Final Java21 clean verify: 61 tests, 0 failures/errors/skips, executable packaging successful; 27 new real-PostgreSQL lifecycle cases. Frontend typecheck/build passed. Existing 34 tests remain green.
- Packaged app/Vite proxy verified login/profile/restoration/logout plus team create/my-team/roster/edit/audit, duplicate/self-removal conflict and final deletion/retained history. Only synthetic fixtures and smoke-owned servers were cleaned; audit snapshots remain immutable and existing services continue running.
- Browser host unavailable; no visual/keyboard runtime, GitHub Actions, deployment or load benchmark result claimed. Native PostgreSQL18.6 still emits Flyway tested-version warning (CI image17.7 not run locally).
- M3 outstanding: persisted idempotency/replay/lost-response semantics and concurrent create/admit/leave/remove/leadership/deletion races. The 50-contender last-seat test has NOT run and is not claimed passed. No commit/push or dependency changes.
- See [M2 verification](verification-m2.md), [decisions](decisions.md), [API](api-contract.md) and [database](database-design.md).

## Historical M1-B review handoff
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
