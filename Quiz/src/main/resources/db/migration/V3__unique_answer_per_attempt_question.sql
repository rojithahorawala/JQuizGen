-- Remove duplicate answer rows, keeping only the highest-ID row per (attempt, question) pair
DELETE FROM answers
WHERE id NOT IN (
    SELECT MAX(id) FROM answers GROUP BY attempt_id, question_id
);

-- Prevent future duplicates
ALTER TABLE answers ADD CONSTRAINT uk_answers_attempt_question UNIQUE (attempt_id, question_id);
