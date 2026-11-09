# API contract

## M5 live discovery and matching (current)
GET /api/v1/hackathons/{event}/teams retains limit1–50/default50 and UUID cursor/default sort=id. New filters: skill (one normalized exact desired-skill tag), openSeats=true, status=READY|INCOMPLETE, sort=id|name. Name sorting uses PostgreSQL lower(name) COLLATE C then UUID; its nextCursor is an opaque encoded name/UUID pair. Always reuse returned cursor with the same filters/sort; reset on change. Responses {items,nextCursor} add openSeats and leaderDisplayName to each summary. Existing name/description/memberCount/minSize/maxSize/readiness/leaderId remain. No private profile fields. Invalid filters/sort/cursor return400. Reads use a committed REPEATABLE READ snapshot and bounded set-based aggregation; pagination is deterministic for unchanged data, not a snapshot across HTTP requests. Rename/filter/capacity changes may move items; reconnect/invalidation resets to page1.

GET /api/v1/hackathons/{event}/events is authenticated text/event-stream; GET /api/v1/teams/{team}/events resolves the team's event and shares the same coarse event channel. Missing resources404. All authenticated participants who can browse the event may subscribe. Events: ready and invalidate, each data {"type":"REFRESH"}; comment heartbeats every15s. No actor/team/request identifiers, invitation tokens/hashes, scores or private profile data are carried. session-expired data {"type":"SESSION_EXPIRED"} ends an expired/logged-out session's connection. Retry hint3000ms, lifetime30min, per-user2/global10000 subscriptions, queue16 per connection (configurable teamforge.sse properties). Limit429 STREAM_LIMIT. Slow/disconnected subscribers close; clients reconnect and refetch authoritative REST. Last-Event-ID is not a replay cursor; no durable event log or exactly-once delivery. Cache-Control:no-store and X-Accel-Buffering:no; proxy buffering/timeouts must be configured at deployment. SSE is a read-only GET; session/CSRF protections on mutations remain unchanged.

GET /api/v1/teams/{team}/suggestions?limit=20 (1–50) is current-leader-only. Response {items:[{userId,displayName,score,skillScore,timeScore,reasons}],windowStart,evaluatedCandidates,truncated}. Only derived scores/reasons and existing public identity/display name are returned; no email/raw skill inventory/time zone/schedule. Requester and all participants already teamed in that hackathon are excluded; other-event membership does not exclude. Candidates are global authenticated participants because there is no separate event registration workflow. A full team returns no candidates. At most10000 eligible UUID-ordered candidates are scored (one extra detects truncation), top limit by descending score then ascending UUID. Truncated=true explicitly signals candidate-cap sampling beyond supported10000 participants. Invalid limits400; missing team404; nonleader403.

Scores are rounded to2 decimals in[0,100]. skillScore=100×candidate coverage of desired skills not already present in roster / uncovered desired count; empty denominator→0. timeScore=100×candidate overlap minutes with the team's common collaboration window / team common minutes; empty/invalid data→0. Final=round(0.7×rounded skillScore+0.3×rounded timeScore,2). Window is seven UTC days starting database current UTC date. Profiles use explicit weekly windows if any, otherwise proposed weekdays09:00–17:00 local fallback; missing/invalid zone gives zero availability. DST follows Java IANA zone rules (gaps shift forward, ambiguity chooses earlier offset). For team members, intersect availability and union skills; absent profiles contribute empty availability/skills. Explanations state counts/defaults without disclosing actual schedules or matched private skill names. These formulas/defaults implement previously proposed mechanics for M5 and are recorded in decisions.md.

## M4 invitation/request contracts (current)
This section supersedes M3's lack of public admission routes. All endpoints below require a server-managed authenticated session; mutations require fresh session CSRF. Actor, leader and applicant identities are derived server-side. Unknown JSON fields are rejected. Existing team/auth/profile contracts remain available.

| Operation | Endpoint (prefix /api/v1) | Request / response |
|---|---|---|
| INVITE | POST /teams/{team}/invite | No body; current leader only; 201 {code,expiresAt}. Always generates/rotates; plaintext shown only in this response. |
| JOIN | POST /hackathons/{event}/invite-joins | JSON {code}, required Idempotency-Key; 201 {teamId,hackathonId,participantId,membershipId,memberCount}. |
| REQUEST | POST /teams/{team}/requests | No body; caller is applicant; 201 request. Repeated pending intent returns existing request without a second audit. |
| MY-REQUESTS | GET /hackathons/{event}/me/requests?cursor={uuid} | Own requests including terminal states; {items,nextCursor}, up to100 ordered by UUID. |
| INBOX | GET /teams/{team}/requests?cursor={uuid} | Current leader only; pending requests, {items,nextCursor}, up to100 ordered by UUID. |
| WITHDRAW | DELETE /teams/{team}/requests/{request} | Applicant only, pending only; 204. |
| ACCEPT | POST /teams/{team}/requests/{request}/accept | Current leader, pending only, required Idempotency-Key; 200 {id,state:"ACCEPTED",membership:{teamId,hackathonId,participantId,membershipId,memberCount}}. |
| DECLINE | POST /teams/{team}/requests/{request}/decline | Current leader, pending only; 200 request with state DECLINED. |

Request shape: {id,teamId,applicantId,displayName,state,createdAt}; only display name and opaque identity are exposed, not email/credentials/private profile fields. nextCursor is null at the end. Request histories disappear with the active team; immutable audit and successful keyed outcomes survive.

Invitation codes encode 32 cryptographically random bytes as43 URL-safe characters; only SHA-256 hash persists. Expiry is database generation time +24h; comparison uses database time inside the locked admission transaction. Reusable codes work until expiry/rotation/deletion, subject to membership/capacity. No code lookup/recovery endpoint exists. Generating again revokes the previous code; after a lost issuance response the leader must deliberately rotate again. Invitations never appear in audit, URLs, request outcomes, logs or future SSE payloads.

JOIN scopes keys to INVITE_REDEEM; fingerprints contain event and token hash. ACCEPT scopes keys to REQUEST_ACCEPT with team/request IDs. Each wraps the shared admission operation in the same transaction; inner INVITE_ADMIT/REQUEST_ADMIT scopes cannot collide with each other or the original MEMBERSHIP_ADMIT operation. The recorded outer status/body (201 JOIN,200 ACCEPT) replays even after expiry/rotation/deletion/leadership change, without another effect. Sessions/CSRF are required for replay; fresh operations recheck current authority. Same-key/different-content reuse returns409 IDEMPOTENCY_KEY_REUSED. Failed full/invalid/stale work rolls back both key records and leaves pending requests unchanged. Retention and transient recovery follow M3.

Invalid, expired, rotated, wrong-event or deleted-team invitation codes uniformly return400 INVALID_INVITE with no distinction. Oversized/malformed codes share this error; malformed JSON/unknown fields remain400 validation errors. Capacity409 TEAM_FULL; existing event membership409 ALREADY_TEAMED; stale/nonpending request409 REQUEST_RESOLVED; unavailable request404 REQUEST_NOT_FOUND; nonleader403 LEADER_REQUIRED; wrong withdrawing applicant403 FORBIDDEN. A full acceptance leaves PENDING. Success atomically accepts its source request (if any), closes all other event-pending requests and audits those transitions. A withdrawal/acceptance race yields one legal terminal state, never both effects.

Redemption is limited per authenticated account (default60/min) and direct remote IP (default600/min), with bounded20000-key single-instance memory and no forwarding-header trust. Property teamforge.security.invite-attempts-per-minute controls the account limit (IP=10x); exhaustion429 RATE_LIMITED/Retry-After60. Tests raise limits explicitly for legitimate contention; no production bypass endpoint exists. Replica-wide limiting and trusted reverse-proxy configuration remain deployment/hardening work.

## M3 contract update (current)
This supersedes M2's unkeyed-create and lost-response limitations below. All existing session authentication, CSRF, validation and resource permissions remain required.

CREATE (POST /api/v1/hackathons/{event}/teams) now requires Idempotency-Key: 1–128 ASCII characters matching `[A-Za-z0-9][A-Za-z0-9._:-]{0,127}`. The existing request and 201 team response shapes are unchanged. Scope is authenticated caller + TEAM_CREATE + key. Fingerprints include operation version, event and normalized content: stripped name, default empty description, sorted normalized distinct skills. Equivalent normalized content replays; different content returns 409 IDEMPOTENCY_KEY_REUSED. Missing/invalid keys return 400 INVALID_IDEMPOTENCY_KEY after normal security checks.

Completed requests replay the stored 201 JSON outcome, even if the team subsequently changes or is deleted; this historical result must be reconciled with current GET state. No effects or audit events are duplicated. The frontend retains one in-memory key for an unchanged pending creation intent and reuses it after failures; successful completion clears it. Keys are not session secrets.

Production admission is internal only: MembershipAdmissionService.Command(teamId,participantId,actorId,authorization,idempotencyKey), returning Result(teamId,hackathonId,participantId,membershipId,memberCount). MEMBERSHIP_ADMIT scopes keys separately; its fingerprint includes team, target and trusted authorization kind/proof. LeaderApproval checks actor against current locked leadership. Actor/proof are supplied by trusted application code, never arbitrary HTTP fields. No admission HTTP endpoint exists in M3. M4 will adapt invitation redemption and request acceptance to this service without weakening authorization.

Fresh admissions can return TEAM_NOT_FOUND (404), LEADER_REQUIRED (403), ALREADY_TEAMED or TEAM_FULL (409). Completed replay does not rerun domain authorization against changed leadership; an authenticated caller can retrieve only their own operation/key outcome. Missing/changed content is still rejected. Failed mutations roll back key claims and may be retried. An anomalous committed IN_PROGRESS yields 409 IDEMPOTENCY_IN_PROGRESS, never automatic takeover. REQUEST_BUSY is 409 after bounded database retries; these busy responses include Retry-After: 1. OUTCOME_UNKNOWN is 503 on connection failure; reconcile state and retry keyed work with its original key. No automatic record expiry is implemented. Edit/leave/removal retain existing unkeyed contracts; only create/admission gain persisted replay in M3.

Audit actions additionally include MEMBER_ADMITTED. See [storage](database-design.md), [locks](architecture.md) and [verification](verification-m3.md).

## M2 implemented team contracts (current)
All routes below require the existing authenticated session. Every mutation requires CSRF. M2 does not require/acknowledge Idempotency-Key; persisted replay semantics remain M3. Earlier tables include future proposed endpoints that remain unavailable.

| Operation | Endpoint (prefix /api/v1) | Result |
|---|---|---|
| HACKATHONS | GET /hackathons | {items:[{id,name,minSize,maxSize,active}]}, first100 |
| BOARD | GET /hackathons/{event}/teams?limit=50&cursor={uuid} | {items:[summary],nextCursor}; UUID order, maximum50; no skill/openSeats filter yet |
| MINE | GET /hackathons/{event}/me/team | 200 team or 204 without event membership |
| CREATE | POST /hackathons/{event}/teams | {name,description?,desiredSkills?}; 201 team; creator becomes member/leader atomically |
| TEAM | GET /teams/{id} | team details and ordered roster |
| MEMBERS | GET /teams/{id}/members | {items:[{userId,displayName,joinedAt,leader}]} |
| EDIT | PATCH /teams/{id} | Partial {name,description,desiredSkills}; current leader only; 200 team |
| LEAVE | DELETE /teams/{id}/members/me | {teamId,deleted,leaderId}; successor or final deletion |
| REMOVE | DELETE /teams/{id}/members/{user} | Current leader removing another member; 204 |
| AUDIT | GET /teams/{id}/audit-events?offset=0&limit=50 | Current/former participants only, including after deletion; max100, offset0..1000000 |

Team shape: {id,hackathonId,name,description,desiredSkills,leaderId,status,minSize,maxSize,members}; status INCOMPLETE below minimum, otherwise READY. Summary replaces roster with memberCount and omits desiredSkills/hackathonId. Audit items: {id,actorId,action,targetType,targetId,timestamp}; actions TEAM_CREATED, DETAILS_UPDATED, MEMBER_LEFT, MEMBER_REMOVED, LEADERSHIP_TRANSFERRED, TEAM_DELETED. Empty desiredSkills clears it; null/absent PATCH fields stay unchanged; no-op PATCH adds no audit.
Errors use existing safe envelope: 400 invalid fields/parameters or supplied actor/leader identity; 401 anonymous; 403 NOT_A_MEMBER/LEADER_REQUIRED/AUDIT_FORBIDDEN; 404 missing event/team/member; 409 ALREADY_TEAMED/TEAM_CONFLICT or USE_LEAVE for self-removal. Deleted team detail/roster returns404 while authorized history stays readable. No public join/invite/request endpoint was added. Lost-response retries may return conflict/not-found; replay-after-deletion is not yet protected by persisted idempotency.
Base /api/v1. JSON except the standard form-encoded login. Approved Spring Security server-managed sessions and local email/password identity. Secure, HttpOnly session cookies and CSRF protection are required for mutations. Never trust client actor/role values.
IDs are opaque strings; timestamps UTC ISO-8601. Error shape: {code,message,requestId,fieldErrors?}; never expose SQL or invite secrets.

Public GET /api/v1/health returns 200 {"status":"UP"}; Actuator readiness includes PostgreSQL. These do not use the business error envelope. M1/M2 runtime verification now passes; the high-level table below includes future admission/suggestion/SSE routes, not implemented by the current lifecycle slice.

| Operation ID | Endpoint | Input / result |
|---|---|---|
| BOARD | GET /hackathons/{event}/teams | cursor, limit<=50, skill, openSeats; {items,nextCursor} |
| TEAM | GET /teams/{team} | {id,name,status,leaderId,members,minSize,maxSize} |
| CREATE | POST /hackathons/{event}/teams | {name,desiredSkills}; 201 team |
| REQUEST | POST /teams/{team}/requests | {}; 201 {id,state} |
| WITHDRAW | DELETE /teams/{team}/requests/{request} | applicant only; 204 |
| ACCEPT / DECLINE | POST /teams/{team}/requests/{request}/accept or /decline | {}; 200 {id,state} |
| INVITE | POST /teams/{team}/invite | rotate; 201 {code,expiresAt}; leader only |
| JOIN | POST /hackathons/{event}/invite-joins | {code}; 201 membership |
| LEAVE | DELETE /teams/{team}/members/me | 200 {deleted,leaderId} |
| REMOVE | DELETE /teams/{team}/members/{user} | current leader removing another member only; self-removal rejected, use LEAVE; 204 |
| EDIT | PATCH /teams/{team} | {name,desiredSkills}; 200 team; proposed |
| PROFILE | GET /me; PATCH /me/profile | {displayName,skills,timeZone,collaborationWindows}; proposed |
| SUGGEST | GET /teams/{team}/suggestions | leader only proposed; {items:[{userId,score,skillScore,timeScore,reasons}]} |
| LIVE | GET /hackathons/{event}/events; GET /teams/{team}/events | text/event-stream; invalidation {type,teamId} |

Idempotency-Key is required for proposed future team mutation contracts; replay original status/body. Conflicting key payload: 409. Validation 400; unauthenticated 401; forbidden 403; missing resource 404; membership/full/stale-state conflict 409; invalid/expired invite 400 with generic code; throttled 429 plus Retry-After. Identity/profile mutations below use explicit session/uniqueness semantics and do not require Idempotency-Key.

## M1-B implemented identity/profile endpoints
All paths have /api/v1 prefix. Fetch a new CSRF token before each mutation. Cookies are HttpOnly/SameSite=Lax/Secure by default; local HTTP requires explicit local profile. Private responses are not cacheable. Registration stays signed out until login. No token is stored in localStorage.

| Operation | Endpoint | Contract |
|---|---|---|
| AUTH-CSRF | GET /auth/csrf | Public 200 {headerName:"X-CSRF-TOKEN",token:"..."}; creates/uses session |
| AUTH-REGISTER | POST /auth/register | JSON {email,password,displayName}; CSRF required; 201 {id,email,profile} |
| AUTH-LOGIN | POST /auth/login | Form-encoded email/password plus CSRF header; 204; session ID rotates; then GET /me |
| AUTH-LOGOUT | POST /auth/logout | CSRF required; 204; session invalidated and cookie expired |
| AUTH-ME | GET /me | 200 {id,email,profile}; 401 without authenticated session |
| PROFILE-GET | GET /me/profile | Own profile; 200 or 401 |
| PROFILE-PATCH | PATCH /me/profile | Partial profile JSON plus CSRF; 200 updated own profile |

Profile: {displayName,timeZone,skills:["java"],collaborationWindows:[{day:"MONDAY",start:"09:00",end:"17:00"}]}. Day is an uppercase DayOfWeek; times are local HH:mm in the named zone. Empty arrays clear collections; absent/null fields remain unchanged. See decisions.md for limits. Invalid inputs/unknown identity fields return 400; duplicate normalized email 409 IDENTITY_CONFLICT; optimistic profile conflict 409 PROFILE_CHANGED; absent/invalid CSRF 403 CSRF_INVALID; wrong/unknown login identically 401 INVALID_CREDENTIALS; auth throttling 429 RATE_LIMITED with Retry-After. Error bodies contain code/message/requestId/fieldErrors, never raw submitted secrets or SQL.

Java 21 gates now pass. Actuator root also lists public probe group names; component/connection details remain hidden. M2 lifecycle routes are implemented as above; admission/suggestion/live routes remain unavailable.


## Approved lifecycle and scoring
INVITE returns expiry 24 hours after generation; rotation invalidates previous codes. SUGGEST returns component and total scores in [0,100], total = 0.7 ? skillScore + 0.3 ? timeScore with explanations. LIVE clients reconnect and refetch BOARD/TEAM; deleted team returns 404. Persist idempotency outcomes; same key with different payload returns 409. Audit history is immutable and never exposed through an unrestricted endpoint.
