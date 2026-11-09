CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_normalized_email CHECK (email = lower(trim(email)))
);
CREATE TABLE profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(80) NOT NULL CHECK (length(trim(display_name)) BETWEEN 1 AND 80),
    time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE profile_skills (
    user_id UUID NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    skill VARCHAR(40) NOT NULL CHECK (length(skill) BETWEEN 1 AND 40),
    PRIMARY KEY (user_id, skill)
);
CREATE INDEX profile_skills_skill_idx ON profile_skills(skill, user_id);
CREATE TABLE profile_availability (
    user_id UUID NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    slot_order INTEGER NOT NULL,
    day_of_week VARCHAR(9) NOT NULL CHECK (day_of_week IN
        ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    start_time TIME(0) NOT NULL,
    end_time TIME(0) NOT NULL,
    PRIMARY KEY (user_id, slot_order),
    CONSTRAINT availability_ordered CHECK (end_time > start_time)
);
