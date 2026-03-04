ALTER TABLE skill_sendback_record
    ADD COLUMN idempotency_key VARCHAR(128) DEFAULT NULL AFTER request_id;

UPDATE skill_sendback_record
SET idempotency_key = CONCAT('legacy-', request_id)
WHERE idempotency_key IS NULL;

ALTER TABLE skill_sendback_record
    MODIFY COLUMN idempotency_key VARCHAR(128) NOT NULL,
    ADD UNIQUE KEY uk_sendback_idempotency_key (idempotency_key),
    ADD KEY idx_sendback_session_turn_idempotency (session_id, turn_id, idempotency_key);
