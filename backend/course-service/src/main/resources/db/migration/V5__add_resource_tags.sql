CREATE TABLE resource_tag (
    id BIGSERIAL PRIMARY KEY,
    resource_id BIGINT NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    label VARCHAR(255) NOT NULL,
    normalized_label VARCHAR(255) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 1,
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    knowledge_point_id BIGINT REFERENCES knowledge_point(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_resource_tag_label UNIQUE (resource_id, normalized_label)
);

CREATE INDEX idx_resource_tag_resource_id
    ON resource_tag(resource_id);

CREATE INDEX idx_resource_tag_normalized_label
    ON resource_tag(normalized_label);
