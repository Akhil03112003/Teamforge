# Product specification

## M5 live board and recommendations
Participants filter the board by desired skill, open seats and readiness, sort by stable team ID or name, load further pages and view team details with existing request/invite actions. Invalidation refreshes committed REST state; disconnected/reconnecting status is announced. Reconnect/periodic refresh resets to page1, preserves the selected team where it still exists and refreshes request status. A deleted selected team is cleared. No event payload is treated as a roster or permission source.

Current leaders see up to20 explainable eligible suggestions with normalized skill/time/total scores. Matching favors uncovered team-desired skills and overlap with the team's common collaboration availability. 50% skill coverage and100% overlap→65 overall;100% coverage and0% overlap→70. Empty desired/common denominators score zero rather than inventing compatibility. Weekly availability fallback is weekdays09:00–17:00 in each valid profile's IANA zone; seven UTC-day horizon/DST rules are documented in api-contract.md. Private schedules/zones/raw skill inventories are not exposed. Suggestions are advisory, not automatic admissions. M6 visual polish is not part of M5.

## M4 implemented invitations and request journeys
This section supersedes earlier statements that joining is unavailable. Leaders generate/rotate private invitation codes; code is displayed only immediately after issuance and expires after24h. Rotation invalidates old codes. Codes are reusable until expiry/rotation/deletion; valid authenticated redemption directly joins if eligibility and capacity permit.

Participants can request any viewed team while unteamed in that event, including full teams, and hold pending requests across teams. Duplicate pending creation returns the same request. Applicants view their own paginated history and withdraw pending requests. Leaders view a paginated pending inbox and accept/decline. Only PENDING can become ACCEPTED, DECLINED, WITHDRAWN or CLOSED. Full acceptance leaves pending; successful entry (including creation) closes every other pending request in that event. Declined/withdrawn/closed users may submit a new request if still eligible. Deleting a team removes active admission records but preserves immutable audit and keyed outcomes.

The dashboard adds invitation redemption, selected-team request controls, own request states/withdrawal, leader rotation and inbox decisions with loading/error/retry/status text and labeled keyboard controls. Intent keys remain in memory across failed redemption/acceptance retries; they are cleared on success. No session secrets or invitation codes are stored in browser persistent storage. Stale conflicts prompt authoritative refresh. Pending applicants cannot read the team's full audit history; actual current/former members retain that access. API details: [contract](api-contract.md). Runtime visual/keyboard verification is reported separately from static/build checks.

## M2 implemented dashboard and lifecycle
After sign-in, participants choose a hackathon, see their own team and browse bounded team summaries. Profile editing remains a separate view; logout/session restoration are preserved. Creation makes the participant the first member/leader. A team below minimum is incomplete; reaching minimum makes it ready. Maximum is enforced, and one membership per event is database-unique.
Authenticated users can view team details and display-name rosters. Only current leaders edit details/remove others. All members leave through the leave endpoint; self-removal via the removal endpoint is rejected. Leader departure chooses earliest remaining membership, breaking equal timestamps by membership UUID. The final departure deletes active team data and preserves immutable audit history. UI confirms leave/removal and refreshes authoritative membership after successful changes.
Audit visibility default: current/former participants may read that team's history using server-verified membership/immutable participation evidence, including after deletion. Strangers are denied. The dashboard shows recent history and can load older entries. No admission/join/invite or live-update controls exist yet. The empty demo event is not the large submission seed. Active status is informational; deadlines/event-admin rules are not introduced.

## M1-B participant journey
Anonymous visitors can register/sign in and obtain a CSRF token. Registration atomically creates a private default UTC profile, then asks the participant to sign in. Invalid/duplicate identity leaves them signed out. Login rotates the session; reload restores /me; logout invalidates it. Expiry returns the UI to sign-in.
Authenticated participants retrieve/edit only their own display name, normalized skills, named time zone and optional weekly availability. Profile creation belongs to registration, not a separate public endpoint. Forms provide loading/validation/error states, labeled controls, keyboard focus and responsive rows. Empty arrays clear collections, partial updates retain unspecified fields, and optimistic conflicts request a reload. No other participant ID is accepted. Validation defaults are recorded in decisions.md; team screens remain future work.
Authority: [requirements](requirements.md). Proposed details require review in [decisions](decisions.md).

## Roles and permissions
Anonymous: sign in only (proposed). Authenticated participant: browse, edit own profile, create team if unteamed, request membership, withdraw own request, redeem invite, leave own team.
Current leader additionally accepts/declines requests, removes members, rotates invites and edits team details. Authorization is checked transactionally. No administrator UI is yet specified.

## Screens and journeys
Sign-in → event board → team detail → request or invite join. Profile captures skills, IANA time zone and proposed collaboration hours. Team detail shows roster, incomplete/ready status, own request state, and leader controls. Leader sees requests and explainable candidate suggestions. Leaving shows successor or deletion confirmation. UI must support keyboard operation, focus restoration, labels, responsive layouts and announced errors/live changes.

## States
Team: incomplete below minimum; ready at minimum through maximum; deleted after final departure.
Request (proposed): pending → accepted/declined/withdrawn/closed. Only pending requests may transition. Successful membership closes other pending requests in the event.
Invite (approved lifecycle): active → expired/revoked; rotation revokes previous code.
Leader departure selects earliest remaining member, stable membership ID breaks ties (proposed).
Only the current leader may remove other members. A leader leaves through LEAVE, never REMOVE (approved). Manual transfer and administration remain unspecified.

## Edge cases
Reject cross-team membership, over-capacity admission and stale leader actions. A full-team acceptance leaves request pending (proposed). Serialize final departure against joining. Preserve audits after deletion. Reconcile live screens after reconnect or stale-action errors.


## Approved clarification
Invites expire 24 hours after generation; rotation invalidates earlier codes. Suggestion component and total scores are normalized 0?100 with total = 0.7 ? skillScore + 0.3 ? timeScore. Exact component denominators/windows remain proposed. Audit history is immutable and survives deletion. SSE reconnects always refetch authoritative state.
