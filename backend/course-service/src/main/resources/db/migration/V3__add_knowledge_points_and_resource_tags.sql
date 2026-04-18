CREATE TABLE knowledge_point (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT REFERENCES knowledge_point(id) ON DELETE RESTRICT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    keywords VARCHAR(1000),
    node_type VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    order_index INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_knowledge_point_parent_order
    ON knowledge_point(parent_id, order_index, id);

CREATE INDEX idx_knowledge_point_active
    ON knowledge_point(active);

CREATE TABLE resource_knowledge_point (
    id BIGSERIAL PRIMARY KEY,
    resource_id BIGINT NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    knowledge_point_id BIGINT NOT NULL REFERENCES knowledge_point(id) ON DELETE RESTRICT,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 1,
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_resource_knowledge_point UNIQUE (resource_id, knowledge_point_id)
);

CREATE INDEX idx_resource_knowledge_point_resource_id
    ON resource_knowledge_point(resource_id);

CREATE INDEX idx_resource_knowledge_point_knowledge_point_id
    ON resource_knowledge_point(knowledge_point_id);

ALTER TABLE resource
    ADD COLUMN tagging_status VARCHAR(20) NOT NULL DEFAULT 'UNTAGGED';

ALTER TABLE resource
    ADD COLUMN tagging_updated_at TIMESTAMPTZ;
