# Test plan

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
