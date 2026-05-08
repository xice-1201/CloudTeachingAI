ALTER TABLE ability_test_question
    ADD COLUMN correct_answer VARCHAR(1) NOT NULL DEFAULT 'A';

ALTER TABLE ability_test_question
    ADD COLUMN explanation TEXT;
