# Database design

## M3 implemented V4 (current)
V1–V3 are unchanged. V4 adds idempotency_records and extends the immutable audit action check with MEMBER_ADMITTED. The M2 table descriptions below remain valid; their statement that idempotency is absent is historical.

idempotency_records has primary key (actor_id, operation, idempotency_key), request_hash CHAR(64), state, response_status, response_body JSONB, created_at and completed_at. Key length is 128; operation length 32. CHECK constraints distinguish IN_PROGRESS with null outcome fields from COMPLETED with a 2xx status, body and completion time. A partial completed_at index supports future reviewed retention operations. There are deliberately no cascading user/team FKs: replay survives active-resource deletion.

The application inserts IN_PROGRESS and records COMPLETED in one transaction with domain and audit changes; normal intermediate state is never independently committed. Unique key insertion plus row locking coordinates concurrent copies across processes. Records are retained indefinitely in M3; no scheduled deletion, expiry takeover or failed-outcome cache exists. Failed/rolled-back work leaves no record. Team-member event uniqueness, capacity guard and deferred leader FK remain the V3 integrity guards. See [transaction protocol](architecture.md) and [executed tests](verification-m3.md).

## M2 implemented schema (current)
V3 is additive; V1/V2 unchanged. The older proposed table list below remains context for future admission/idempotency work.
- event_participants: PK(hackathon_id,user_id), FKs to hackathons/users; persistent per-event/person lock anchor retained after departure.
- teams: UUID PK, hackathon FK, name/description/desired_skills, leader_user_id and created_at; UNIQUE(hackathon_id,id); leader references an event participant.
- team_members: UUID PK, joined_at; UNIQUE(hackathon_id,user_id) enforces one membership per event, UNIQUE(team_id,user_id) supports leader FK. Composite FKs enforce matching team/event and participant/event. Team deletion cascades only active memberships.
- teams_leader_is_member: deferred FK (team.id,leader_user_id) -> team_members(team_id,user_id). Creation, succession and final deletion satisfy this at commit; active teams cannot be left leaderless.
- Capacity BEFORE INSERT/UPDATE trigger locks the destination team and checks actual membership count against its hackathon maximum. Application paths acquire participant anchors first; the trigger is defense in depth, not an alternative protocol. Minimum remains derived readiness.
- audit_events: immutable UUID/event/team/actor/action/target-type/target-ID/timestamp snapshots; no active-record FKs. UPDATE/DELETE/TRUNCATE triggers reject mutation. Team deletion retains all history. Index(team_id,occurred_at DESC,id DESC) supports authorized pagination.
Indexes include teams(hackathon_id,id) for keyset discovery and team_members(team_id,joined_at,id) for stable succession. A single empty demo event is inserted on ID conflict-do-nothing, without replacing existing data. No idempotency/request/invite table is added in M2.

The logical model below includes future admission/idempotency tables. Current implemented mappings are recorded in the M2/M1-B sections; historical foundation blocking conditions are resolved. See architecture.md for the lock protocol and verification-m2.md for execution evidence.

| Table | Relationships and constraints |
|---|---|
| users | Internal ID, unique normalized local email and password hash; approved local identity |
| hackathons | ID, minimum >=1, maximum >= minimum, active status |
| event_participants | User/event FKs; unique(event,user); stable membership lock anchor |
| profiles / skills / profile_skills | Time zone and collaboration windows; unique user/skill association |
| teams | Event FK, name, desired skills, created time; leader participant reference |
| team_members | Team and participant references, event, joined time; unique(event,user) |
| join_requests | Team/applicant, state, timestamps; partial unique(team,applicant) WHERE pending |
| invites | Team, unique token hash, expiry, revoked timestamp |
| idempotency_records | Unique(actor,operation,key), request hash, status/result, timestamps |
| audit_events | Immutable actor/action/time, event/team snapshot identifiers and change metadata |

Use composite event-aware foreign keys to prevent cross-event membership. Proposed deferred leader-membership FK enforces that active leader belongs to the team at commit. Deletion must remove active dependent rows while retaining audit snapshots and idempotency results.
Maximum capacity, earliest succession and nonempty-team lifecycle require transactional enforcement; ordinary CHECK constraints cannot inspect roster rows.
Indexes: board(event,created_at,id), membership(team,joined_at,id), requests(team,state), participant(event,user), audit(event,team,time), invite hash, idempotency unique key.
Board uses bounded keyset pagination and indexed roster aggregation. Do not add denormalized counters without proving consistency.

## M1-B actual schema
- users: UUID PK; unique normalized email varchar254; password_hash varchar255; created_at timestamptz. Approved local credentials supersede the proposed issuer/subject mapping.
- profiles: user_id PK/FK users, display_name varchar80/nonblank, time_zone varchar64/default UTC, optimistic version bigint. Registration creates it atomically with the user.
- profile_skills: PK(user_id,skill), FK profiles ON DELETE CASCADE, skill varchar40, index(skill,user_id). Normalized tags implement this milestone; a shared catalog is deferred.
- profile_availability: PK(user_id,slot_order), FK profiles ON DELETE CASCADE; checked day name; start_time/end_time time(0); CHECK end_time > start_time. Service additionally enforces named zones, list bounds and non-overlap within each local day.
V2 preserves V1 unchanged. PostgreSQL uniqueness protects concurrent duplicate registration; @Version protects conflicting profile writes. Tests remove only returned fixture IDs and their cascades, never truncate shared tables. No team or membership tables are added.


## Review constraints
Approved audit immutability requires insert/read-only application access; active-team deletion cannot cascade audit deletion. Idempotency results must not cascade with teams. Invite expiry is generation time +24 hours; rotation revokes the previous code transactionally. Proposed cryptographic generation uses at least 128 random bits, stores only hashes and never logs codes.
Proposed seed: exactly 10000 synthetic users, 2000 teams with four members each, 8000 unique memberships and 2000 unteamed users in one active event with limits 2?5. Every team has exactly one valid leader; all event FKs and memberships satisfy constraints. Repeat execution must not duplicate the dataset.
