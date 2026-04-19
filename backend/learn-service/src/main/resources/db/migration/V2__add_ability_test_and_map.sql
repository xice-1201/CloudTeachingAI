CREATE TABLE ability_test_session (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    root_knowledge_point_id BIGINT NOT NULL,
    root_knowledge_point_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    question_count INTEGER NOT NULL,
    answered_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ability_test_session_student_started
    ON ability_test_session(student_id, started_at DESC);

CREATE TABLE ability_test_question (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES ability_test_session(id) ON DELETE CASCADE,
    knowledge_point_id BIGINT NOT NULL,
    knowledge_point_name VARCHAR(255) NOT NULL,
    prompt TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    option_c TEXT NOT NULL,
    option_d TEXT NOT NULL,
    display_order INTEGER NOT NULL,
    answered BOOLEAN NOT NULL DEFAULT FALSE,
    selected_answer VARCHAR(1),
    score DOUBLE PRECISION,
    answered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_ability_test_question_session_order
    ON ability_test_question(session_id, display_order);

CREATE TABLE ability_map (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    knowledge_point_name VARCHAR(255) NOT NULL,
    knowledge_point_path VARCHAR(1000),
    mastery_level DOUBLE PRECISION NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    test_score DOUBLE PRECISION NOT NULL,
    progress_score DOUBLE PRECISION NOT NULL,
    resource_count INTEGER NOT NULL DEFAULT 0,
    source VARCHAR(32) NOT NULL,
    last_tested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_ability_map_student_knowledge_point
    ON ability_map(student_id, knowledge_point_id);

CREATE INDEX idx_ability_map_student_mastery
    ON ability_map(student_id, mastery_level DESC, updated_at DESC);
