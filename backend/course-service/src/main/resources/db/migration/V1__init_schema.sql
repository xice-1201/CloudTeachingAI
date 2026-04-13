CREATE TABLE course (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    cover_key VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_course_teacher_id ON course(teacher_id);
CREATE INDEX idx_course_status ON course(status);
CREATE INDEX idx_course_updated_at ON course(updated_at DESC);

CREATE TABLE chapter (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    order_index INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chapter_course_id_order ON chapter(course_id, order_index, id);

CREATE TABLE resource (
    id BIGSERIAL PRIMARY KEY,
    chapter_id BIGINT NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    storage_key VARCHAR(1000) NOT NULL,
    file_size BIGINT,
    duration_seconds INT,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    order_index INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_resource_chapter_id_order ON resource(chapter_id, order_index, id);

CREATE TABLE enrollment (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_enrollment_student_course UNIQUE (student_id, course_id)
);

CREATE INDEX idx_enrollment_student_id ON enrollment(student_id);
CREATE INDEX idx_enrollment_course_id ON enrollment(course_id);

CREATE TABLE outbox_message (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_outbox_event_id ON outbox_message(event_id);
