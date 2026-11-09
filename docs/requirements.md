# Requirements and acceptance

M4 traceability: REQ-03/04 now have invitation/request schema, authenticated APIs and dashboard controls. REQ-08 is tested through public invitation HTTP admission as well as the retained M3 production-service race. REQ-09 covers keyed redemption/acceptance and lost-response replay; REQ-02/06/07/11/12 retain their transactional lifecycle/security/audit guards through both workflows. See AdmissionWorkflowsIntegrationTest, InviteRateLimitIntegrationTest, [contracts](api-contract.md), [architecture](architecture.md), [milestones](implementation-plan.md) and [actual verification](verification-m4.md). No SSE/suggestion/large seed/load/deployment requirement is claimed implemented here.

M2 traceability (latest scope): REQ-01/02/05/06/07/12 are implemented for lifecycle paths, with REQ-11 authorization/CSRF and REQ-17's minimal dashboard. TeamLifecycleIntegrationTest has 27 real-PostgreSQL cases covering create/duplicate/cross-event/bounds/leader succession/delete/remove/security/audit/rollback and paging. Evidence: verification-m2.md. REQ-08 (50 contenders) and REQ-09's persisted replay are reserved for M3; no race/load benchmark result is claimed. The original M0 table's milestone placement is superseded by the latest implementation-plan.md.

M1-B traceability: REQ-11 now includes local registration/login/logout/restoration, session-derived private profiles, CSRF and bounded auth-IP throttling; REQ-17 includes participant forms only (team UI remains M7). Evidence: ParticipantIntegrationTest (28 API cases), HealthIntegrationTest (3 HTTP checks), DatabaseIntegrationTest (2 integration checks), AuthRateLimitFilterTest (1 unit check), frontend typecheck/build and local proxy flow. Actual outcomes: verification-m1-b.md. No future team requirement or remote CI result is claimed.
Mandatory unless identified as approved default. Details: [product](product-spec.md), [API](api-contract.md), [tests](test-plan.md).

- REQ-01: Configurable event minimum/maximum and incomplete status. Acceptance: demonstrate this behavior through API/integration assertions in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-02: One membership per person per hackathon. Acceptance: demonstrate this behavior through API/integration assertions in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-03: Expiring high-entropy rotating invitations. Acceptance: demonstrate this behavior through API/integration assertions in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-04: Current leader accepts/declines requests. Acceptance: demonstrate this behavior through API/integration assertions in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-05: Leader-only member removal. Acceptance: demonstrate this behavior through API/integration assertions in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-06: Automatic earliest-member leader succession. Acceptance: demonstrate this behavior through API/integration assertions in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-07: Last departure deletes active team. Acceptance: demonstrate this behavior through API/integration assertions in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-08: Exactly one successful final-seat join among 50 contenders. Acceptance: demonstrate this behavior through API/integration assertions in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-09: Persisted retry-safe create and join. Acceptance: demonstrate this behavior through API/integration assertions in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-10: Live board/team pages with reconciliation. Acceptance: demonstrate this behavior through API/integration assertions in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-11: Authentication authorization validation rate limits. Acceptance: demonstrate this behavior through API/integration assertions in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-12: Transactional actor/action/timestamp audit retained after deletion. Acceptance: demonstrate this behavior through API/integration assertions in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-13: Explainable 70/30 complementary-skill/time overlap suggestions. Acceptance: demonstrate this behavior through API/integration assertions in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-14: At least 20 API tests in GitHub Actions. Acceptance: demonstrate this behavior through the corresponding delivery check in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-15: Seed 10000 users and 2000 teams. Acceptance: demonstrate this behavior through the corresponding delivery check in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-16: Board p95 below 200 ms at 100 concurrent users. Acceptance: demonstrate this behavior through the corresponding delivery check in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-17: Responsive keyboard-accessible complete UI. Acceptance: demonstrate this behavior through the corresponding delivery check in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-18: Live deployment. Acceptance: demonstrate this behavior through the corresponding delivery check in test-plan.md; proposed mechanics remain subject to decisions.md.
- REQ-19: Repository URL report one-page design and video <=5 minutes. Acceptance: demonstrate this behavior through the corresponding delivery check in test-plan.md; proposed mechanics remain subject to decisions.md.

## M0 acceptance traceability
Mandatory capabilities and approved defaults are distinguished in problem-statement.md and decisions.md. Incomplete status, 24-hour expiry/rotation, earliest succession and scoring weights are approved defaults; concurrency, security, scale and delivery capabilities are mandatory. Designs below include explicitly proposed implementation mechanics, not implemented behavior.

| Requirement | Observable acceptance | Design | Planned evidence | Milestone |
|---|---|---|---|---|
| REQ-01 | Configured bounds valid; incomplete below minimum | [product-spec.md](product-spec.md) | API-05/06/35 | M2/M3 |
| REQ-02 | At most one membership/event under competing admission | [architecture.md](architecture.md) | API-02/03/04 | M3/M4 |
| REQ-03 | High entropy; 24-hour expiry; old code invalid after rotation | [database-design.md](database-design.md) | API-07/08/09/41 | M4 |
| REQ-04 | Only current leader accepts/declines pending requests | [product-spec.md](product-spec.md) | API-10/11/12/24/25 | M4 |
| REQ-05 | Only current leader removes another member; self uses leave | [api-contract.md](api-contract.md) | API-14/15/36/42 | M4 |
| REQ-06 | Earliest remaining member succeeds leader atomically | [architecture.md](architecture.md) | API-16/17/25/42 | M3/M4 |
| REQ-07 | Final leave deletes active team/dependents, retains audit | [database-design.md](database-design.md) | API-18/26/37 | M3/M4 |
| REQ-08 | 50 final-seat contenders produce exactly one admission | [architecture.md](architecture.md) | API-19 | M4 |
| REQ-09 | Persisted retry outcome; mismatch rejected; no duplicate creation/join | [architecture.md](architecture.md) | API-20/21/22/23/40 | M3/M4 |
| REQ-10 | Live board/team and reconnect authoritative refetch | [api-contract.md](api-contract.md) | SSE-01 | M6 |
| REQ-11 | Sessions/CSRF, permissions, validation and rate limits enforced | [architecture.md](architecture.md) | API-12/15/30?34/38 | M2/M4/M8 |
| REQ-12 | Every team change audits actor/action/time atomically; immutable after deletion | [database-design.md](database-design.md) | API-27/28/37 | M3/M4 |
| REQ-13 | Explainable normalized 0?100 ranking, 70% skills/30% time overlap | [product-spec.md](product-spec.md) | API-29/39 | M5 |
| REQ-14 | At least 20 distinct passing API cases in GitHub Actions | [test-plan.md](test-plan.md) | CI-01 | M1/M8 |
| REQ-15 | Seed exactly 10000 users/2000 valid teams without constraint violations | [database-design.md](database-design.md) | SEED-01 | M8 |
| REQ-16 | Seeded board p95 strictly <200ms at 100 concurrent users | [test-plan.md](test-plan.md) | LOAD-01 | M5/M8 |
| REQ-17 | Responsive keyboard-accessible entire flow | [product-spec.md](product-spec.md) | UI-01 | M7 |
| REQ-18 | Live application smoke checks succeed on selected hosting | [implementation-plan.md](implementation-plan.md) | DEPLOY-01 | M9 |
| REQ-19 | Repository/live URL/report/one-page design/video <=5min delivered | [implementation-plan.md](implementation-plan.md) | SUBMIT-01 | M9 |
