ALTER TABLE season
    ADD COLUMN demo_playable_days INT NULL,
    ADD COLUMN demo_skip_status VARCHAR(20) NOT NULL DEFAULT 'NONE';
