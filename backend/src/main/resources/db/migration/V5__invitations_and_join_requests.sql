CREATE TABLE team_invitations (
    id UUID PRIMARY KEY,
    hackathon_id UUID NOT NULL,
    team_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (clock_timestamp() + INTERVAL '24 hours'),
    revoked_at TIMESTAMPTZ,
    FOREIGN KEY (hackathon_id,team_id) REFERENCES teams(hackathon_id,id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX team_invitation_active ON team_invitations(team_id) WHERE revoked_at IS NULL;
CREATE TABLE join_requests (
    id UUID PRIMARY KEY,
    hackathon_id UUID NOT NULL,
    team_id UUID NOT NULL,
    applicant_id UUID NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING','ACCEPTED','DECLINED','WITHDRAWN','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (hackathon_id,team_id) REFERENCES teams(hackathon_id,id) ON DELETE CASCADE,
    FOREIGN KEY (hackathon_id,applicant_id) REFERENCES event_participants(hackathon_id,user_id)
);
CREATE UNIQUE INDEX join_requests_pending ON join_requests(team_id,applicant_id) WHERE state='PENDING';
CREATE INDEX join_requests_applicant ON join_requests(hackathon_id,applicant_id,state,id);
CREATE INDEX join_requests_inbox ON join_requests(team_id,created_at,id);
CREATE FUNCTION enforce_request_transition() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.state <> 'PENDING' OR NEW.state = 'PENDING'
       OR NEW.id <> OLD.id OR NEW.hackathon_id <> OLD.hackathon_id
       OR NEW.team_id <> OLD.team_id OR NEW.applicant_id <> OLD.applicant_id THEN
        RAISE EXCEPTION 'illegal request transition' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER join_requests_transition BEFORE UPDATE ON join_requests
    FOR EACH ROW EXECUTE FUNCTION enforce_request_transition();
ALTER TABLE audit_events DROP CONSTRAINT audit_events_action_check;
ALTER TABLE audit_events ADD CONSTRAINT audit_events_action_check CHECK (action IN (
    'TEAM_CREATED','DETAILS_UPDATED','MEMBER_LEFT','MEMBER_REMOVED',
    'LEADERSHIP_TRANSFERRED','TEAM_DELETED','MEMBER_ADMITTED','INVITE_ROTATED',
    'REQUEST_CREATED','REQUEST_ACCEPTED','REQUEST_DECLINED','REQUEST_WITHDRAWN','REQUEST_CLOSED'
));
