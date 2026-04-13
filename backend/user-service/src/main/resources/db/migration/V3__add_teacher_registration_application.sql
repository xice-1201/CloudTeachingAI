CREATE TABLE teacher_registration_application (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    review_note VARCHAR(500),
    reviewed_by BIGINT,
    created_user_id BIGINT,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP
);

CREATE INDEX idx_teacher_registration_application_email_status
    ON teacher_registration_application(email, status);

CREATE INDEX idx_teacher_registration_application_status_requested_at
    ON teacher_registration_application(status, requested_at DESC);
