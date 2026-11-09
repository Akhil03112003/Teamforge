# Architecture
Approved: modular Java 21 Spring Boot monolith, Spring Security, PostgreSQL/Flyway, React/TypeScript/Vite, SSE. PostgreSQL is authoritative. No external broker/cache/distributed lock by default.

Proposed modules: identity, events/profiles, teams/membership, suggestions, audit/live delivery. Controllers validate DTOs; services own transactions; repositories enforce database access.

## Transaction and lock protocol (proposed)
Persist event-participant rows before membership actions. Lock affected participant rows in ascending ID order, then team rows ascending ID, then request/invite rows. Discover IDs before locking and revalidate after locks; restart on changed assumptions. Never acquire a participant lock after a team lock.
All create/join/accept/remove/leave paths use this protocol. Leader leave locks its participant and team; concurrent acceptance locks applicant and team, then checks current leader under the team lock.
Check membership uniqueness, capacity and authority after locks. Team creation inserts creator membership atomically. All roster changes lock the team; serialize capacity check and insert. Unique event/person membership remains the final guard.
Removal/leave updates succession or deletes active team state in the same transaction. Audits commit with domain changes. Deadlocks roll back; bounded retry only for recognized transient failures.

## Idempotency (proposed)
Require Idempotency-Key for mutations, especially create/join/accept. Unique actor/operation/key, canonical payload hash, persisted response and resource ID. Claim key before domain locks; concurrent duplicates wait/replay. Different payload returns conflict. Successful outcome and mutation commit together. Retain creation keys through event lifetime, including deleted resources. Failure-retention policy remains open.

## Audit and SSE
Audit actor/action/database timestamp and relevant identifiers in the mutation transaction. Keep audit references valid after team deletion without cascading audit removal.
Publish minimal event/team invalidations after commit. Board/team clients refetch authoritative state on events and every reconnect. SSE has no durable delivery guarantee; periodic refetch bounds missed updates (interval proposed 30 seconds). Authenticated subscriptions enforce visibility and session expiry.
Single-instance delivery is proposed only. Multiple replicas require a reviewed cross-instance notification mechanism. Provider, hosting, availability and recovery targets remain open.


## Review clarifications
Approved security uses Spring Security server-managed sessions with secure cookies and CSRF protection; provider remains open. Approved idempotency persists outcomes and rejects same-key/different-payload reuse. Authentication and authorization precede replay so a key is not an access bypass.
All admission paths (creator membership, invite redemption and request acceptance) use the same participant/team lock protocol. Removal locks the target participant before the team; only the current leader can remove another member. Succession is selected under the team lock; no successor participant lock is acquired afterward. The new leader must still be a member when the transaction commits.
Proposed pending-request cleanup: discover all affected team IDs before locks, lock them in ascending order after the participant row, revalidate and restart if discovery changed; only then lock/update requests. Request creation/withdrawal also locks the applicant first. Do not acquire additional out-of-order team locks during cleanup. Concurrent leave/accept/remove rechecks authority after locking. Last-member deletion, dependent cleanup, audit and idempotency outcome are atomic.
Audit history is immutable; application role must not update/delete audit rows. Team deletion preserves snapshot identifiers without a cascading team FK. SSE emits no uncommitted state.
