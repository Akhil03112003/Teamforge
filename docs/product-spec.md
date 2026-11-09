# Product specification
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
