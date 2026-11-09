# Decisions
## Approved
Stack and modular monolith as recorded in [problem statement](problem-statement.md). PostgreSQL transactions/constraints, persisted idempotency, SSE reconnect/refetch. Product defaults in that document are approved. Leader-only removal is required by latest source, superseding the earlier exclusion proposal.
## Proposed, awaiting review
Stable participant/team lock protocol; API/schema contracts; OIDC identity integration (session mechanism approved); single-instance application with managed PostgreSQL and same-origin frontend; hashed reusable rotating invites; pending-request cleanup; event-lifetime idempotency retention; 30-second reconciliation.
Scoring proposal: normalize missing desired-skill coverage and next-seven-day collaboration overlap to 0–100; weighted total 0.7/0.3; absent inputs score zero with explanation; stable ID tie-break. IANA zones handle daylight saving.
Seed proposal: 2,000 teams with four members and 2,000 unteamed users.
## Unresolved
Choose OIDC provider (managed service reduces password/security scope; local credentials add recovery and storage obligations).
Choose hosting (managed JVM/PostgreSQL simplifies operations; container VM gives control but requires patching/backups). No vendor selected.
Confirm invite reuse/rotation visibility, configuration changes after opening, scoring component formulas/windows, request lifecycle, idempotency failure retention, audit retention/access, administrator scope and profile visibility.
Set numeric rate limits, availability/backup recovery targets, SSE connection limits, load environment and error budget before relevant implementation gates.
Minimum-size readiness and direct invite join are approved defaults, not contradictions. Submission one-page design document is a later distilled artifact; architecture.md does not replace it.


## M0 consistency review ? 2026-11-09
Approved clarifications supersede earlier proposed labels: secure Spring Security server-managed sessions with CSRF; invites expire after 24 hours and rotation invalidates old codes; only current leader removes other members, leader departure uses LEAVE; scoring components/total normalized 0?100 with 70/30 weights; immutable audits survive deletion; persisted idempotency outcomes reject payload mismatch; SSE reconnect/refetch. Authentication provider remains open, independently of the approved session mechanism.
Hosting selection remains open until free-tier constraints are verified: JVM memory/CPU, PostgreSQL connection/storage limits, sleep/cold starts, SSE duration/proxy buffering, HTTPS, backups and expected benchmark capacity. No free-tier suitability is asserted.
Still proposed: exact skill/time scoring denominators/windows, OIDC provider, lock/schema mechanics, invite reuse, seed distribution, retention duration, pending cleanup, single-instance deployment. Resolved normalization/removal/expiry defaults are no longer open. No mandatory requirement was removed. M0 review found generic acceptance criteria and missing explicit tests for bounds/audit survival; traceability and tests now cover these.
