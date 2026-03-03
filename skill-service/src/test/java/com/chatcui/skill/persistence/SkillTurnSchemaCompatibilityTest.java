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
        String ddl = loadMigration().toLowerCase(Locale.ROOT);
        assertTrue(ddl.contains("create table if not exists skill_turn_snapshot"));
        assertTrue(ddl.contains("create table if not exists skill_turn_delivery"));
        assertTrue(ddl.contains("unique key uk_session_turn_seq (session_id, turn_id, seq)"));
    }

    @Test
    void migrationDefinesAscendingHistoryReplayIndexes() throws IOException {
        String ddl = loadMigration().toLowerCase(Locale.ROOT);
        assertTrue(ddl.contains("key idx_snapshot_session_created_turn (session_id, created_at, turn_id)"));
    }

    private String loadMigration() throws IOException {
        Path migration = Path.of(
                "skill-service",
                "src",
                "main",
                "resources",
                "db",
                "migration",
                "V1__skill_turn_tables.sql"
        );
        return Files.readString(migration, StandardCharsets.UTF_8);
    }
}
