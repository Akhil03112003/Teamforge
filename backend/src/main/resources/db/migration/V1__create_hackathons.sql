-- Foundation only: event limits are independent of the unresolved identity provider.
CREATE TABLE hackathons (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL CHECK (length(trim(name)) > 0),
    minimum_team_size INTEGER NOT NULL DEFAULT 2 CHECK (minimum_team_size >= 1),
    maximum_team_size INTEGER NOT NULL DEFAULT 5,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT hackathons_valid_team_limits CHECK (maximum_team_size >= minimum_team_size)
);
