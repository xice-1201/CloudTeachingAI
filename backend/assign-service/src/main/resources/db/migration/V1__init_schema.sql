CREATE TABLE assignment (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    course_title VARCHAR(255) NOT NULL,
    teacher_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    grading_criteria TEXT,
    submit_type VARCHAR(20) NOT NULL,
    max_score DOUBLE PRECISION NOT NULL,
    deadline TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_assignment_course_deadline
    ON assignment(course_id, deadline DESC);

CREATE INDEX idx_assignment_teacher_created
    ON assignment(teacher_id, created_at DESC);

CREATE TABLE submission (
    id BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES assignment(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    attachments_json TEXT,
    status VARCHAR(30) NOT NULL,
    ai_score DOUBLE PRECISION,
    ai_feedback TEXT,
    final_score DOUBLE PRECISION,
    final_feedback TEXT,
    submitted_at TIMESTAMPTZ NOT NULL,
    graded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_submission_assignment_student
    ON submission(assignment_id, student_id);

CREATE INDEX idx_submission_assignment_status
    ON submission(assignment_id, status);

CREATE INDEX idx_submission_student_submitted
    ON submission(student_id, submitted_at DESC);

CREATE TABLE outbox_message (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_assign_outbox_event_id
    ON outbox_message(event_id);
