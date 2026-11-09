CREATE TABLE event_participants (
    hackathon_id UUID NOT NULL REFERENCES hackathons(id),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (hackathon_id, user_id)
);
CREATE TABLE teams (
    id UUID PRIMARY KEY,
    hackathon_id UUID NOT NULL REFERENCES hackathons(id),
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    description VARCHAR(2000) NOT NULL DEFAULT '',
    desired_skills TEXT[] NOT NULL DEFAULT '{}',
    leader_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (hackathon_id, id),
    FOREIGN KEY (hackathon_id, leader_user_id) REFERENCES event_participants(hackathon_id, user_id)
);
CREATE TABLE team_members (
    id UUID PRIMARY KEY,
    hackathon_id UUID NOT NULL,
    team_id UUID NOT NULL,
    user_id UUID NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT team_members_one_per_event UNIQUE (hackathon_id, user_id),
    UNIQUE (team_id, user_id),
    FOREIGN KEY (hackathon_id, team_id) REFERENCES teams(hackathon_id, id) ON DELETE CASCADE,
    FOREIGN KEY (hackathon_id, user_id) REFERENCES event_participants(hackathon_id, user_id)
);
ALTER TABLE teams ADD CONSTRAINT teams_leader_is_member
    FOREIGN KEY (id, leader_user_id) REFERENCES team_members(team_id, user_id)
    DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX teams_board_idx ON teams(hackathon_id, id);
CREATE INDEX team_members_succession_idx ON team_members(team_id, joined_at, id);

-- Defense in depth. Application entry paths must lock the participant anchor first.
CREATE FUNCTION enforce_team_capacity() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE capacity INTEGER;
BEGIN
    SELECT h.maximum_team_size INTO capacity
      FROM teams t JOIN hackathons h ON h.id = t.hackathon_id
      WHERE t.id = NEW.team_id AND t.hackathon_id = NEW.hackathon_id FOR UPDATE OF t;
    IF FOUND AND (SELECT count(*) FROM team_members WHERE team_id = NEW.team_id AND id <> NEW.id) >= capacity THEN
        RAISE EXCEPTION 'team capacity exceeded' USING ERRCODE = '23514', CONSTRAINT = 'team_members_capacity';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER team_members_capacity
    BEFORE INSERT OR UPDATE ON team_members FOR EACH ROW EXECUTE FUNCTION enforce_team_capacity();

-- Snapshot identifiers intentionally have no FK to active teams/users/events.
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    hackathon_id UUID NOT NULL,
    team_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL CHECK (action IN
        ('TEAM_CREATED','DETAILS_UPDATED','MEMBER_LEFT','MEMBER_REMOVED','LEADERSHIP_TRANSFERRED','TEAM_DELETED')),
    target_type VARCHAR(8) NOT NULL CHECK (target_type IN ('TEAM','USER')),
    target_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX audit_events_team_idx ON audit_events(team_id, occurred_at DESC, id DESC);
CREATE FUNCTION reject_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit events are immutable' USING ERRCODE = '55000';
END;
$$;
CREATE TRIGGER audit_events_immutable BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
CREATE TRIGGER audit_events_no_truncate BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_mutation();

-- Only an empty demo event; the 10,000-user/2,000-team seed is a later milestone.
INSERT INTO hackathons(id, name, minimum_team_size, maximum_team_size, active)
VALUES ('10000000-0000-4000-8000-000000000001', 'TeamForge Demo', 2, 5, TRUE)
ON CONFLICT (id) DO NOTHING;
