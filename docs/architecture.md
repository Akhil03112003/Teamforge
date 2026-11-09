# Architecture

## M1-B identity/profile slice
identity owns accounts, local password authentication and principal loading; profiles owns private participant metadata and weekly availability. Registration joins both in one transaction. Controllers derive IDs from ParticipantPrincipal, never body/path identity. DTO validation bounds inputs and excludes credential fields. PostgreSQL unique email and profile FKs protect integrity; optimistic versioning rejects conflicting writes.
Spring Security form login performs context persistence/session-ID rotation; logout invalidates the session. Browser fetches fresh session-backed CSRF before mutations and restores via GET /me. Default cookies remain Secure/HttpOnly/SameSite=Lax; local profile is opt-in for HTTP. Bounded single-instance auth-IP limiting records no passwords/email keys. Safe JSON errors expose no raw request values, SQL or stack traces.
React retains the existing visual foundation with registration/login, restoration, profile editing and sign-out. No browser credential storage, new infrastructure or application dependency is introduced. The M1-A no-login/blocked-runtime descriptions below are historical; the gate was reverified on Java 21 before this slice.
Approved: modular Java 21 Spring Boot monolith, Spring Security, PostgreSQL/Flyway, React/TypeScript/Vite, SSE. PostgreSQL is authoritative. No external broker/cache/distributed lock by default.

## M1-A foundation
backend/ targets Java 21 with Maven/Spring Boot 3.5.16. platform.health and platform.security contain infrastructure; events.persistence maps only hackathons. JPA validates rather than generates schema; Flyway applies V1 at startup. DB_URL/DB_USERNAME/DB_PASSWORD are required environment variables without credential defaults.
frontend/ uses React/TypeScript/Vite with a typed, runtime-validated health client, timeout, cancellation and retry. Development /api proxies to backend port 8080; no wildcard CORS. Compose binds PostgreSQL only to localhost. CI is defined but has not run.
Security retains CSRF and secure session cookies, excludes the generated default user, exposes only GET health endpoints and denies other requests. No login or identity integration exists. This does not implement the later authentication requirement.

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
