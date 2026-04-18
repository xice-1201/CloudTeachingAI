ALTER TABLE course
    ADD COLUMN visibility_type VARCHAR(40) NOT NULL DEFAULT 'PUBLIC';

UPDATE course
SET visibility_type = 'PUBLIC'
WHERE visibility_type IS NULL;

CREATE INDEX idx_course_visibility_type ON course(visibility_type);

CREATE TABLE course_visible_student (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_course_visible_student UNIQUE (course_id, student_id)
);

CREATE INDEX idx_course_visible_student_course_id
    ON course_visible_student(course_id);

CREATE INDEX idx_course_visible_student_student_id
    ON course_visible_student(student_id);
