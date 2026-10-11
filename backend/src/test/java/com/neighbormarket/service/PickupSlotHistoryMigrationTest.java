package com.neighbormarket.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickupSlotHistoryMigrationTest {
    @Test
    void migrationAddsActiveFlagAndQueryIndexWithoutDeletingRows() throws IOException {
        String sql = resource("/db/migration/V6__preserve_pickup_slot_history.sql").toLowerCase();

        assertTrue(sql.contains("add column active boolean not null default true"));
        assertTrue(sql.contains("item_id, active, start_at"));
        assertFalse(sql.contains("delete from"));
        assertFalse(sql.contains("drop table"));
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing migration " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
