CREATE TABLE course_announcement (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_course_announcement_course_published
    ON course_announcement(course_id, pinned DESC, published_at DESC, id DESC);

CREATE TABLE course_discussion_post (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    resource_id BIGINT REFERENCES resource(id) ON DELETE CASCADE,
    parent_id BIGINT REFERENCES course_discussion_post(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL,
    title VARCHAR(255),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_course_discussion_course_parent_created
    ON course_discussion_post(course_id, parent_id, created_at DESC, id DESC);

CREATE INDEX idx_course_discussion_resource_parent_created
    ON course_discussion_post(resource_id, parent_id, created_at DESC, id DESC);
