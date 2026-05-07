ALTER TABLE notification
    ADD COLUMN target_type VARCHAR(50),
    ADD COLUMN target_id BIGINT,
    ADD COLUMN target_url VARCHAR(500);

CREATE INDEX idx_notification_target
    ON notification (target_type, target_id);
