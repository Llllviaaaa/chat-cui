CREATE TABLE IF NOT EXISTS skill_turn_snapshot (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    turn_id VARCHAR(64) NOT NULL,
    seq BIGINT NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    actor VARCHAR(16) NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    payload MEDIUMTEXT NULL,
    turn_status VARCHAR(32) NOT NULL DEFAULT 'processing',
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot_session_turn_seq (session_id, turn_id, seq),
    KEY idx_snapshot_session_created_turn (session_id, created_at, turn_id),
    KEY idx_snapshot_tenant_client_created (tenant_id, client_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS skill_turn_delivery (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    turn_id VARCHAR(64) NOT NULL,
    seq BIGINT NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    delivered_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_turn_seq (session_id, turn_id, seq),
    KEY idx_delivery_session_created (session_id, created_at),
    KEY idx_delivery_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
