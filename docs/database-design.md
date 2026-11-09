# Proposed database design
Not implemented. See [architecture](architecture.md) for lock protocol.

| Table | Relationships and constraints |
|---|---|
| users | Internal ID; unique identity issuer/subject; display name; no provider chosen |
| hackathons | ID, minimum >=1, maximum >= minimum, active status |
| event_participants | User/event FKs; unique(event,user); stable membership lock anchor |
| profiles / skills / profile_skills | Time zone and collaboration windows; unique user/skill association |
| teams | Event FK, name, desired skills, created time; leader participant reference |
| memberships | Team and participant references, event, joined time; unique(event,user) |
| join_requests | Team/applicant, state, timestamps; partial unique(team,applicant) WHERE pending |
| invites | Team, unique token hash, expiry, revoked timestamp |
| idempotency_records | Unique(actor,operation,key), request hash, status/result, timestamps |
| audit_events | Immutable actor/action/time, event/team snapshot identifiers and change metadata |

Use composite event-aware foreign keys to prevent cross-event membership. Proposed deferred leader-membership FK enforces that active leader belongs to the team at commit. Deletion must remove active dependent rows while retaining audit snapshots and idempotency results.
Maximum capacity, earliest succession and nonempty-team lifecycle require transactional enforcement; ordinary CHECK constraints cannot inspect roster rows.
Indexes: board(event,created_at,id), membership(team,joined_at,id), requests(team,state), participant(event,user), audit(event,team,time), invite hash, idempotency unique key.
Board uses bounded keyset pagination and indexed roster aggregation. Do not add denormalized counters without proving consistency.


## Review constraints
Approved audit immutability requires insert/read-only application access; active-team deletion cannot cascade audit deletion. Idempotency results must not cascade with teams. Invite expiry is generation time +24 hours; rotation revokes the previous code transactionally. Proposed cryptographic generation uses at least 128 random bits, stores only hashes and never logs codes.
Proposed seed: exactly 10000 synthetic users, 2000 teams with four members each, 8000 unique memberships and 2000 unteamed users in one active event with limits 2?5. Every team has exactly one valid leader; all event FKs and memberships satisfy constraints. Repeat execution must not duplicate the dataset.
