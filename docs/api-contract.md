# Proposed API contract
Base /api/v1. JSON; approved Spring Security server-managed sessions; identity provider unresolved. Secure, HttpOnly session cookies and CSRF protection are required for mutations. Never trust client actor/role values.
IDs are opaque strings; timestamps UTC ISO-8601. Error shape: {code,message,requestId,fieldErrors?}; never expose SQL or invite secrets.

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

Idempotency-Key required for proposed mutation contract; replay original status/body. Conflicting key payload: 409. Validation 400; unauthenticated 401; forbidden 403; missing resource 404; membership/full/stale-state conflict 409; invalid/expired invite 400 with generic code; throttled 429 plus Retry-After. Authentication routes depend on provider and are not finalized.


## Approved lifecycle and scoring
INVITE returns expiry 24 hours after generation; rotation invalidates previous codes. SUGGEST returns component and total scores in [0,100], total = 0.7 ? skillScore + 0.3 ? timeScore with explanations. LIVE clients reconnect and refetch BOARD/TEAM; deleted team returns 404. Persist idempotency outcomes; same key with different payload returns 409. Audit history is immutable and never exposed through an unrestricted endpoint.
