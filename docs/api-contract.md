# Proposed API contract
Base /api/v1. JSON except the standard form-encoded login. Approved Spring Security server-managed sessions and local email/password identity. Secure, HttpOnly session cookies and CSRF protection are required for mutations. Never trust client actor/role values.
IDs are opaque strings; timestamps UTC ISO-8601. Error shape: {code,message,requestId,fieldErrors?}; never expose SQL or invite secrets.

M1-A adds public GET /api/v1/health: 200 {"status":"UP"} for process liveness, plus status-only Actuator health/liveness/readiness (readiness includes PostgreSQL). These responses do not use the proposed business error envelope. Backend runtime verification is blocked; these are implemented source contracts, not observed endpoint results. Business endpoints below remain unimplemented and access is denied; authentication flows wait for M1-B.

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

The M1-A runtime-blocked note earlier is historical: Java 21 gate now passes. Actuator root also lists public probe group names; component/connection details remain hidden. Team/business endpoints remain unimplemented.


## Approved lifecycle and scoring
INVITE returns expiry 24 hours after generation; rotation invalidates previous codes. SUGGEST returns component and total scores in [0,100], total = 0.7 ? skillScore + 0.3 ? timeScore with explanations. LIVE clients reconnect and refetch BOARD/TEAM; deleted team returns 404. Persist idempotency outcomes; same key with different payload returns 409. Audit history is immutable and never exposed through an unrestricted endpoint.
