# API contract

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
