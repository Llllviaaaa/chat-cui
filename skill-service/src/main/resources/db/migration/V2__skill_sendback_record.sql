CREATE TABLE IF NOT EXISTS skill_sendback_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    turn_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    selected_text MEDIUMTEXT NOT NULL,
    message_text MEDIUMTEXT NOT NULL,
    send_status VARCHAR(32) NOT NULL,
    im_message_id VARCHAR(128) DEFAULT NULL,
    error_code VARCHAR(64) DEFAULT NULL,
    error_message VARCHAR(512) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sendback_request_id (request_id),
    KEY idx_sendback_session_turn_created (session_id, turn_id, created_at),
    KEY idx_sendback_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

