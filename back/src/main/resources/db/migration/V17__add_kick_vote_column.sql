ALTER TABLE challenge_groups ADD COLUMN kick_vote_started_at DATETIME(6) NULL;
ALTER TABLE group_members ADD COLUMN kick_vote_choice VARCHAR(20) NOT NULL DEFAULT 'NONE';
