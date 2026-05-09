CREATE TABLE mentor_relation (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    mentor_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    review_note VARCHAR(500),
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP
);

CREATE INDEX idx_mentor_relation_student_status
    ON mentor_relation(student_id, status, requested_at DESC);

CREATE INDEX idx_mentor_relation_mentor_status
    ON mentor_relation(mentor_id, status, requested_at DESC);

CREATE INDEX idx_mentor_relation_student_mentor_status
    ON mentor_relation(student_id, mentor_id, status);
