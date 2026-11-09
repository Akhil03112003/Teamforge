CREATE TABLE idempotency_records (
    actor_id UUID NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('IN_PROGRESS','COMPLETED')),
    response_status INTEGER,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (actor_id, operation, idempotency_key),
    CONSTRAINT idempotency_complete_outcome CHECK (
        (state='IN_PROGRESS' AND response_status IS NULL AND response_body IS NULL AND completed_at IS NULL)
        OR (state='COMPLETED' AND response_status BETWEEN 200 AND 299 AND response_body IS NOT NULL AND completed_at IS NOT NULL)
    )
);
-- Completed outcomes survive active resource deletion; no automatic key expiration.
CREATE INDEX idempotency_completed_idx ON idempotency_records(completed_at) WHERE state='COMPLETED';
ALTER TABLE audit_events DROP CONSTRAINT audit_events_action_check;
ALTER TABLE audit_events ADD CONSTRAINT audit_events_action_check CHECK (action IN (
    'TEAM_CREATED','DETAILS_UPDATED','MEMBER_LEFT','MEMBER_REMOVED',
    'LEADERSHIP_TRANSFERRED','TEAM_DELETED','MEMBER_ADMITTED'
));
