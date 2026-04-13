CREATE TABLE learning_progress (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    progress DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_position INTEGER,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    last_accessed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_learning_progress_student_resource
    ON learning_progress(student_id, resource_id);

CREATE INDEX idx_learning_progress_student_course
    ON learning_progress(student_id, course_id);

CREATE INDEX idx_learning_progress_student_last_accessed
    ON learning_progress(student_id, last_accessed_at DESC);

CREATE TABLE outbox_message (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_outbox_message_event_id
    ON outbox_message(event_id);
