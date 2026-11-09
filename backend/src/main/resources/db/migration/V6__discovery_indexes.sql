CREATE INDEX teams_desired_skills_gin ON teams USING gin(desired_skills);
CREATE INDEX teams_board_name_idx ON teams(hackathon_id, (lower(name) COLLATE "C"),id);
