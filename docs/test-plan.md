# Test plan

## M8-A optimization acceptance
Original195 backend tests plus6 new real-PostgreSQL cursor/count/filter/auth/privacy cases passed:201 total,0 failures/errors/skips. New cases cover complete native-key traversal through case/Unicode ties, exact boundaries/terminal and deleted-cursor continuation, capacity/readiness before LIMIT, filtered duplicate-name pages with exact counts, unauthenticated401/missing-event404/private-key exclusion. Existing event-scope, committed-visibility, admission and rollback tests remain active. Public50-contender race again yielded1 success/49 capacity conflicts. All16 browser tests/typecheck/build passed.

Independent A/B/pool diagnostics remain separate from compliance. LOAD-01 now passed with the unchanged canonical script/100VUs/30s/180s and original pool10:198,602 measured requests,100% success,p9542.566ms. See [phase/every-file verification](verification-m8-a-optimization.md) and [load report](load-test-report.md). No threshold relaxation, migration change or M8-B authorization.

## M8-A seed/load gates
SEED-01 now uses the latest mixed-size fixture (400 teams each size1–5), not the older four-member-only proposal. Verify exactly10,000 users/profiles/anchors,2,000 teams,6,000 memberships,valid leaders/capacity/exclusivity, synthetic skills/zones/availability, repeated full-row match and rejection of normal/public/nonempty/corrupt targets. Six seed unit cases plus actual PostgreSQL seed/repeat/corruption-restoration and fresh-loader verification passed; safe reports are under benchmarks/results.

LOAD-01 uses100 authenticated VUs,30s warm-up then180s measured,8 filter/sort variants with actual cursors; p95 strictly<200ms. Error/traffic quality gates and secret-free summary output are documented in [benchmark instructions](../benchmarks/README.md). Actual k6v2.3.0 run:43,883 measured requests,100% success,p95 **602.705ms — FAILED**. Earlier195 backend/16 browser cases passed; two report tests reran in this execution-only follow-up. See [verification](verification-m8-a.md) and [load report](load-test-report.md); M8-A remains open until the latency gate is met.

## M7 regression gates
Retain all 171 backend cases and 15 browser cases from M6. Add 24 backend cases: eight bounded-body/form/bootstrap/config checks, four production-guard checks, one real HTTP session-cap check, ten real PostgreSQL security-envelope/CSRF/leader/event/idempotency cases and one SSE shutdown cleanup check. Add one browser regression preserving redemption announcements through membership/SSE reconciliation. These support REQ-09/10/11/13/17 and retain all concurrency/rollback gates; exact executions/counts are in [M7 verification](verification-m7.md).

CI must provision Java21, isolated PostgreSQL17.7 and Node24/Chrome/system libraries, run Maven clean verify with database tests enabled, npm ci/typecheck/browser tests/build and propagate failures. Remote hosted success must be observed separately. Test same-origin web-JAR packaging and genuine HTTP/Vite proxy board/SSE/reconnect plus authentication/lifecycle/invitation/request/suggestion journeys. Negative security cases must not print submitted secrets. Production HTTPS/edge/SIGTERM and Docker-image checks remain explicit platform gates; passing local tests does not establish seed-scale latency, SCA clearance or full security certification.

## M6 browser verification
`npm.cmd --prefix frontend test` runs15 Node/Chrome component-interaction and accessibility cases using explicit fixture API/SSE responses: mobile registration, login/error associations, keyboard navigation/skip-link route preservation, profile windows/save, duplicate creation/keys/CSRF, server search/filter parameters, invitation confirmation/copy, request withdrawal, dialog focus/Escape, SSE lifecycle/logout, responsive labels/IDs/targets/overflow, recovery messages, computed contrast/reduced-motion and URL event/history restoration/background-refresh controls.

Viewport matrix:1440×1000,768×1024,390×844 across the four authenticated views. Chrome accessibility tree, visible heading hierarchy, associated inputs/descriptions,44px action targets, keyboard outline and text contrast thresholds are checked. This is a specific custom rule set, not an axe/WCAG certification or screen-reader test. Native Clipboard API is stubbed in component checks; production code has manual-copy fallback.

`node frontend/tests/real-flow.mjs` is opt-in against a dedicated QA event with min2/max5 and existing servers; set TEAMFORGE_E2E_EVENT and TEAMFORGE_E2E_ORIGIN. It generates synthetic accounts, writes owned IDs to ignored .tmp/m6-e2e-fixtures.json and must be paired with owned-fixture cleanup. The session's private harness provisions/cleans only that event/accounts and runs genuine browser/CSRF/PostgreSQL/SSE journeys. Exact results, historical failures and remaining checks: verification-m6.md. Original170 backend cases plus a new q search case remain regression gates.

## M5 live discovery coverage
LiveDiscoveryIntegrationTest adds25 real PostgreSQL/HTTP board paging/name ties/filter combinations/capacity/event scope/validation/committed-visibility checks; SSE after-commit coalescing, rollback silence, event scope, token/private-field redaction, heartbeats/disconnect cleanup, reconnect REST, logout/session handling, connection bounds and replay silence; suggestions authorization/exclusions/ranking/privacy/stability/limits/full-team/missing-profile cases. SuggestionScoringTest adds12 cases checking70/30 arithmetic, empty denominators, IANA overlap, explicit/default windows, DST, no double-counting and deterministic explanations. Existing133 regressions/public50-way admission races remain required.

Maps REQ-01/02/08/09/10/11/12/13/17 to BOARD/LIVE/SUGGEST and SSE-01/API-29/39. Actual counts/commands/limitations are in verification-m5.md. HTTP/proxy smoke must exercise packaged backend plus Vite SSE forwarding and board refresh; frontend has no test runner, so no frontend unit/browser result is inferred from a successful build. No k6 target is claimed; M8 must measure board100 VUs/p95<200ms at seed scale and SSE/matching CPU/connection behavior.

## M4 implemented test coverage
AdmissionWorkflowsIntegrationTest covers REQ-03/04/08/09/11/12 and request portions of REQ-17 using real PostgreSQL and public HTTP/security adapters. Exact execution counts/timings are in [verification-m4.md](verification-m4.md); earlier planned-only statements below are historical.

- API-07/08/09/41: public valid join; expired/rotated/unknown/malformed uniform errors;32 random bytes encoded43 characters;64-hex hash persistence; database24h timestamps; rotation races/rollback and current-leader permission.
- API-10/11/12/13/24/25/26: duplicate/multiple pending requests; applicant-only withdrawal; leader-only decisions/inbox; full acceptance stays pending; terminal transition constraint; cleanup on create/invite/approval; competing withdrawal/departure/deletion/approval and other-team cleanup.
- API-19:50 distinct real users/sessions with CSRF and distinct keys start public invite redemption together behind a bounded barrier. Assert one201,49 TEAM_FULL409, exact maximum roster, one admission audit and49 users without membership. M3's separate50-held-connection/PID tests remain unchanged. Mixed public invite/approval final-seat competition is also asserted. HTTP operations create independent root transactions on the server; client threads do not share a transaction.
- API-20/21/22/23/28/40: concurrent same-key redemption/approval; stored replay after rotation/removal/deletion; payload mismatch; rollback of both key scopes/membership/request/audit; real unread-socket committed response loss and replay for both redemption and acceptance. Nested-root lock-timeout recovery is tested against a real blocker.
- API-30/31/33/38: session/CSRF, private inbox, applicant audit denial, cross-event membership independence, rate limiting at actual public endpoint (separate class with low test threshold), bounded request pagination.

Race logs separate successful HTTP statuses from completed calls and report elapsed time, deadlocks and lock timeouts. Fault-injected recovery is logged separately. Integration fixtures are removed by exact owned IDs; immutable audit remains. No mocked persistence/locking, test admission endpoint or disabled production authorization. Frontend typecheck/build and existing88 baseline tests must pass. Docker/Testcontainers, CI, visual browser review and large load gates must not be claimed without execution.

## M3 executed coverage (current)
See [verification-m3.md](verification-m3.md) for exact results. The 61-test M2 baseline was run before changes and remains covered in the final 88-test run. MembershipConcurrencyIntegrationTest contributes 27 executed cases, including three repetitions of the 50-user race. Tests use native real PostgreSQL because the Docker daemon is inaccessible; CI/Testcontainers execution is not claimed.

API-19's M3 gate uses the production admission service with current-leader authorization, 50 genuine eligible users, distinct keys and 50 independent PostgreSQL backend PIDs/transactions, a synchronized barrier and bounded deadlines. Each run asserts exactly one success, 49 TEAM_FULL, maximum roster size, no loser membership, one admission audit and one completed admission record. The invitation/session/CSRF HTTP variant described below remains an M4 acceptance test, not something implemented in M3.

API-20/21/22/23/40 cover concurrent duplicate creation/admission, exact stored replay, normalized fingerprints, changed payload conflicts, caller scope and retained outcomes after team deletion/member removal. API-40 additionally sends a genuine authenticated CSRF-protected create over a socket, waits for separately visible commit without reading the response, discards that response and retries via HTTP. API-26/25/42 cover admission versus deletion, leadership departure/removal and competing create/admit; legal serial outcomes and database invariants are asserted without assuming scheduling. Cross-team same-user admission proves event uniqueness. API-28 covers outer rollback without membership/key/success audit. Unauthorized and full failures leave no persisted successes; failed keys are reusable. Missing/malformed keys and committed IN_PROGRESS recovery policy are exercised.

Dedicated recovery cases deliberately produce one database deadlock, a released lock timeout and sustained timeout exhaustion. They verify whole-transaction retries, bounded REQUEST_BUSY and no leaked effects. Ordinary contention metrics are reported separately from these injected conflicts. Test fixtures clean only their own active rows; immutable audit remains. Production max pool is unchanged; this class alone uses max56 to permit 50 simultaneous held connections. Existing lifecycle/security/authentication assertions remain intact. LOAD-01 p95 <200ms at 100 VUs, seed counts, SSE, M4 HTTP admission and remote CI remain future unexecuted gates.

## M2 executed lifecycle coverage
TeamLifecycleIntegrationTest adds 27 PostgreSQL/real-HTTP cases: atomic creator membership/leader/audit; duplicate creation without orphans; cross-event membership; minimum-readiness transitions; configured limits and maximum guard; earliest/tied-time succession; final deletion/history; ordinary leave; leader removal/target audit; nonleader denial; self-removal rejection; outsider denial; missing removal target; validated edits; rollback of invalid details; private roster fields/authentication; CSRF/forged identity; direct SQL unique/composite-event/deferred-leader constraints; transaction rollback of all records; immutable actor/target/timestamp audit; recreation after leave; cursor/my-team/parameter validation; unknown resources.
Multi-member rosters are scoped SQL fixtures following the lock protocol, not a hidden joining endpoint. Fixture users/events/memberships are removed by exact owned IDs; immutable audit snapshots remain. Current totals: 61 tests, 0 failures/errors/skips on Java21/local PostgreSQL; see verification-m2.md. No 50-contender/team concurrency/idempotency suite, k6 benchmark or remote CI run is claimed. M3 must exercise concurrent create/admit/leave/remove/leadership, lock timeout/deadlock recovery and persisted replay/lost-response scenarios.

## M1-B executed coverage
Actual commands/results: [verification](verification-m1-b.md). ParticipantIntegrationTest covers 28 PostgreSQL/HTTP scenarios: account/profile creation and hash exclusion; HttpOnly CSRF session; normalized/concurrent duplicate identities; short/oversized UTF-8 passwords; invalid email/name; login/session rotation/restoration; wrong/unknown/oversized credentials; anonymous reads/updates; logout invalidation; profile round-trip/partial update; invalid zone/skills/windows/overlap; attempted identity injection; CSRF on registration/login/profile/logout; cross-session/stale pre-login tokens; malformed-body redaction. Maps to REQ-11 and participant portion of REQ-17.
HealthIntegrationTest adds three database-free HTTP/security checks in an isolated configuration. DatabaseIntegrationTest adds two V2/readiness/bounds checks. AuthRateLimitFilterTest verifies throttle/Retry-After and health availability. GitHub Actions is configured to enable database tests; it has not run remotely. All 42 business cases below remain future plans; earlier no-tests-run wording is historical foundation status.
Planned only; no results exist. JUnit 5 API tests against Testcontainers PostgreSQL with Flyway; run in GitHub Actions with Docker. Never use mocked persistence as concurrency evidence.

M1-A foundation exception: five test methods are present under backend/src/test (three database-free HTTP health/security checks, two opt-in PostgreSQL/Flyway checks). They have not run locally because Maven downloads are blocked. The 42 business cases below remain planned. CI is defined, not executed; see [actual verification](verification-m1-a.md). Later concurrency tests still require real PostgreSQL/Testcontainers.

- API-01: Create team and creator membership atomically.
- API-02: Reject create by already-teamed user.
- API-03: Allow memberships in different events.
- API-04: Reject concurrent same-event cross-team joins.
- API-05: Enforce configured maximum.
- API-06: Report below-minimum incomplete status.
- API-07: Valid invite directly joins.
- API-08: Expired invite rejected.
- API-09: Rotated invite rejected.
- API-10: Leader accepts request.
- API-11: Leader declines request.
- API-12: Nonleader cannot accept or decline.
- API-13: Applicant withdraws pending request.
- API-14: Leader removes member.
- API-15: Nonleader cannot remove member.
- API-16: Leader leave selects earliest successor.
- API-17: Nonleader leave preserves leader.
- API-18: Final leave deletes active team and dependent admission state.
- API-19: 50 synchronized distinct contenders: exactly one success and correct roster.
- API-20: Create retry replays same team.
- API-21: Join retry replays same membership.
- API-22: Concurrent same-key requests produce one effect.
- API-23: Different payload with same key conflicts.
- API-24: Acceptance versus withdrawal has one legal outcome.
- API-25: Leader departure versus acceptance rechecks authority.
- API-26: Final departure versus join serializes legally.
- API-27: Audit records actor action and timestamp.
- API-28: Rollback leaves no success audit.
- API-29: Suggestion rank and explanation match skill/time inputs.
- API-30: Unauthenticated APIs rejected.
- API-31: Cross-event/object access denied.
- API-32: Malformed inputs rejected.
- API-33: Rate limits return 429.
- API-34: Removed/expired sessions cannot subscribe.

Traceability: REQ-01→API-05/06; 02→02/03/04; 03→07/08/09; 04→10/11/12/13/24; 05→14/15; 06→16/17/25; 07→18/26; 08→19; 09→20/21/22/23; 10→SSE integration; 11→12/15/30–34 and CSRF/XSS checks; 12→27/28; 13→29; 14→CI; 15–19→delivery checks below. Operation coverage follows api-contract.md.

Concurrency: synchronize 50 authenticated distinct applicants against a team with one free slot; provide sufficient server threads/connections or document queuing, collect all responses, assert exactly one successful admission, 49 capacity conflicts and exact database membership/audit counts. Also repeat competing requests using the same participant and idempotency key. Use bounded timeouts and independent transactions.

SSE: committed updates only, authorized visibility, reconnect refetch and deleted-team handling. Browser checks: full journey, keyboard focus, labels, small screens and error announcements.
Security: CSRF, invite entropy/hash/no logging, stale leadership, object authorization, payload bounds, session expiry and connection exhaustion.

Delivery checks: validate seed counts and all invariants; proposed seed has 8000 teamed/2000 unteamed users. k6: authenticated seeded board, 100 VUs, proposed 1-minute warm-up plus 5-minute measurement; p95 <200 ms, separately report errors (proposed zero), page/filter mix, deployment resources and raw results. Do not claim success until measured.
CI runs >=20 distinct API cases including real database races. Live smoke test after deployment; verify repository/live URL/report/one-page design/video duration.


## Review additions (planned, not executed)
- API-35: Reject invalid event bounds and demonstrate different valid configured limits (configuration service/API boundary to be selected in M2).
- API-36: Leader self-removal via REMOVE is rejected; LEAVE transfers leadership or deletes the final-member team.
- API-37: Deleting the team retains immutable audit history with actor/action/time and no dangling required FK.
- API-38: Missing/invalid CSRF token on session-authenticated mutation is rejected; valid token succeeds.
- API-39: Suggestion components and weighted total remain within 0?100; explanation recomputes total.
- API-40: Retry after lost successful response replays persisted outcome without new membership/team; retained outcome after deletion does not recreate team.
- API-41: Invite generation produces configured cryptographic entropy; rotation/expiry use authoritative time and no secret appears in responses other than issuance or logs.
- API-42: Concurrent removal and leader departure yield a legal serial outcome and valid surviving leader.
API-19 uses 50 distinct unteamed invite redeemers, one valid reusable invite and one free slot; all have valid sessions/CSRF and distinct idempotency keys. Configure test rate limits to permit legitimate contention without bypassing the production transaction path. Expect one 201 and 49 capacity conflicts, no 429/5xx; assert one membership and one admission audit. Repeat across fresh fixtures.
SEED-01: exact counts above, unique event memberships, four-member teams, valid leaders and repeat-run safety. LOAD-01: seeded authenticated BOARD at 100 VUs, p95 strictly <200 ms; preserve raw metrics and environment; report all errors separately. SSE-01: live board/team invalidation and authoritative reconnect reconciliation. UI-01: complete keyboard/responsive journey. CI-01: >=20 distinct API cases actually run and pass in GitHub Actions. DEPLOY-01: live smoke checks. SUBMIT-01: repository, live URL, measured report, one-page design, video <=5 minutes. None are claimed executed.
