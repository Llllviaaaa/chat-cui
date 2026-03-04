package com.chatcui.skill.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTurnSchemaCompatibilityTest {

    @Test
    void migrationDefinesTablesAndRequiredIndexes() throws IOException {
        String turnDdl = loadMigration("V1__skill_turn_tables.sql").toLowerCase(Locale.ROOT);
        assertTrue(turnDdl.contains("create table if not exists skill_turn_snapshot"));
        assertTrue(turnDdl.contains("create table if not exists skill_turn_delivery"));
        assertTrue(turnDdl.contains("unique key uk_session_turn_seq (session_id, turn_id, seq)"));
    }

    @Test
    void migrationDefinesAscendingHistoryReplayIndexes() throws IOException {
        String ddl = loadMigration("V1__skill_turn_tables.sql").toLowerCase(Locale.ROOT);
        assertTrue(ddl.contains("key idx_snapshot_session_created_turn (session_id, created_at, turn_id)"));
    }

    @Test
    void sendbackMigrationDefinesCorrelationTableAndIndexes() throws IOException {
        String ddl = loadMigration("V2__skill_sendback_record.sql").toLowerCase(Locale.ROOT);
        assertTrue(ddl.contains("create table if not exists skill_sendback_record"));
        assertTrue(ddl.contains("unique key uk_sendback_request_id (request_id)"));
        assertTrue(ddl.contains("key idx_sendback_session_turn_created (session_id, turn_id, created_at)"));
        assertTrue(ddl.contains("key idx_sendback_trace_id (trace_id)"));
    }

    @Test
    void sendbackIdempotencyMigrationAddsDeterministicReplayKey() throws IOException {
        String ddl = loadMigration("V3__sendback_idempotency_guard.sql").toLowerCase(Locale.ROOT);
        assertTrue(ddl.contains("alter table skill_sendback_record"));
        assertTrue(ddl.contains("add column idempotency_key varchar(128) default null"));
        assertTrue(ddl.contains("modify column idempotency_key varchar(128) not null"));
        assertTrue(ddl.contains("unique key uk_sendback_idempotency_key (idempotency_key)"));
        assertTrue(ddl.contains("key idx_sendback_session_turn_idempotency (session_id, turn_id, idempotency_key)"));
    }

    private String loadMigration(String fileName) throws IOException {
        Path migration = Path.of("src", "main", "resources", "db", "migration", fileName);
        return Files.readString(migration, StandardCharsets.UTF_8);
    }
}
