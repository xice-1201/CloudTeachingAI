ALTER TABLE notification
    ADD COLUMN external_event_id VARCHAR(100);

CREATE UNIQUE INDEX uk_notification_external_event_id
    ON notification (external_event_id)
    WHERE external_event_id IS NOT NULL;
