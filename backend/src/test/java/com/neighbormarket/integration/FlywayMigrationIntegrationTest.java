package com.neighbormarket.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("neighbor_market_test")
            .withUsername("neighbor_test")
            .withPassword("test-only-password");

    @BeforeEach
    void cleanDatabase() {
        flyway(null).clean();
    }

    @Test
    void emptyDatabaseMigratesThroughV7WithExpectedConstraintsAndIndexes() throws Exception {
        var result = flyway(null).migrate();

        assertEquals(7, result.migrationsExecuted);
        assertEquals("7", result.targetSchemaVersion);
        assertEquals(0, flyway(null).migrate().migrationsExecuted,
                "re-running Flyway on an up-to-date schema must be a no-op");
        try (Connection connection = connection()) {
            assertEquals(1, count(connection, """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 'pickup_slot'
                      AND column_name = 'active'
                      AND is_nullable = 'NO'
                    """));
            assertEquals(1, count(connection, """
                    SELECT COUNT(DISTINCT index_name)
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'pickup_slot'
                      AND index_name = 'idx_pickup_slot_item_active_start'
                    """));
            assertEquals(1, count(connection, """
                    SELECT COUNT(*)
                    FROM information_schema.referential_constraints
                    WHERE constraint_schema = DATABASE()
                      AND constraint_name = 'fk_interest_slot'
                    """));
            assertEquals(1, count(connection, """
                    SELECT COUNT(DISTINCT index_name)
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'item_interest'
                      AND index_name = 'uq_item_interest_user'
                      AND non_unique = 0
                    """));
            assertEquals(3, count(connection, """
                    SELECT COUNT(*)
                    FROM information_schema.table_constraints
                    WHERE constraint_schema = DATABASE()
                      AND constraint_name IN (
                          'chk_item_status',
                          'chk_item_interest_status',
                          'chk_handover_status'
                      )
                      AND constraint_type = 'CHECK'
                    """));
            assertEquals(1, count(connection, """
                    SELECT COUNT(DISTINCT index_name)
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'handover'
                      AND index_name = 'uq_handover_active_item'
                      AND non_unique = 0
                    """));
            assertEquals(1, count(connection, """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 'handover'
                      AND column_name = 'active_item_id'
                      AND generation_expression <> ''
                    """));
            assertEquals(4, count(connection, """
                    SELECT COUNT(*)
                    FROM information_schema.referential_constraints
                    WHERE constraint_schema = DATABASE()
                      AND constraint_name IN (
                          'fk_interest_slot_same_item',
                          'fk_handover_interest_same_item_user',
                          'fk_handover_publisher_same_item',
                          'fk_item_current_handover_same_item'
                      )
                    """));
        }
    }

    @Test
    void v5ToV6UpgradePreservesHistoricalInterestAndMakesOldSlotActive() throws Exception {
        flyway(MigrationVersion.fromVersion("5")).migrate();
        long slotId;
        long interestId;
        try (Connection connection = connection()) {
            long userId = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('migration_user','test-hash','迁移用户')");
            long publisherId = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('migration_owner','test-hash','发布者')");
            long itemId = insertAndReturnId(connection, """
                    INSERT INTO item(publisher_id,title,description,category,trade_type,price,image_url,status,last_confirmed_at,pickup_location)
                    VALUES (?, '迁移测试物品', '用于验证 V6', '其他', 'FREE', 0, '/test.jpg', 'AVAILABLE', NOW(), '测试门口')
                    """, publisherId);
            slotId = insertAndReturnId(connection, """
                    INSERT INTO pickup_slot(item_id,start_at,end_at)
                    VALUES (?, '2030-01-01 10:00:00', '2030-01-01 11:00:00')
                    """, itemId);
            interestId = insertAndReturnId(connection, """
                    INSERT INTO item_interest(item_id,user_id,slot_id,status)
                    VALUES (?, ?, ?, 'CANCELLED')
                    """, itemId, userId, slotId);
        }

        var result = flyway(MigrationVersion.fromVersion("6")).migrate();

        assertEquals(1, result.migrationsExecuted);
        assertEquals("6", result.targetSchemaVersion);
        try (Connection connection = connection()) {
            assertEquals(1, count(connection,
                    "SELECT COUNT(*) FROM pickup_slot WHERE id = ? AND active = TRUE", slotId));
            assertEquals(1, count(connection,
                    "SELECT COUNT(*) FROM item_interest WHERE id = ? AND slot_id = ?", interestId, slotId));
        }
    }

    @Test
    void v6ToV7UpgradePreservesAConsistentActiveHandover() throws Exception {
        flyway(MigrationVersion.fromVersion("6")).migrate();
        long itemId;
        long interestId;
        long handoverId;
        try (Connection connection = connection()) {
            long ownerId = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('v7_owner','test-hash','发布者')");
            long recipientId = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('v7_recipient','test-hash','领取人')");
            itemId = insertItem(connection, ownerId, "V7 合法升级物品");
            long slotId = insertSlot(connection, itemId);
            interestId = insertAndReturnId(connection, """
                    INSERT INTO item_interest(item_id,user_id,slot_id,status)
                    VALUES (?, ?, ?, 'CONFIRMED')
                    """, itemId, recipientId, slotId);
            handoverId = insertAndReturnId(connection, """
                    INSERT INTO handover(
                        item_id,publisher_id,recipient_id,interest_id,start_at,end_at,
                        location_snapshot,status
                    ) VALUES (?, ?, ?, ?, '2030-01-01 10:00:00', '2030-01-01 11:00:00',
                              '测试门口', 'ACTIVE')
                    """, itemId, ownerId, recipientId, interestId);
            executeUpdate(connection,
                    "UPDATE item SET status = 'RESERVED', current_handover_id = ? WHERE id = ?",
                    handoverId, itemId);
        }

        var result = flyway(null).migrate();

        assertEquals(1, result.migrationsExecuted);
        assertEquals("7", result.targetSchemaVersion);
        try (Connection connection = connection()) {
            assertEquals(1, count(connection, """
                    SELECT COUNT(*)
                    FROM item i
                    JOIN handover h ON h.id = i.current_handover_id AND h.item_id = i.id
                    JOIN item_interest ii
                      ON ii.id = h.interest_id
                     AND ii.item_id = h.item_id
                     AND ii.user_id = h.recipient_id
                    WHERE i.id = ? AND h.id = ? AND ii.id = ?
                    """, itemId, handoverId, interestId));
        }
    }

    @Test
    void v7RejectsInvalidStatesDuplicateActiveHandoversAndCrossItemReferences() throws Exception {
        flyway(null).migrate();
        try (Connection connection = connection()) {
            long ownerId = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('constraint_owner','test-hash','发布者')");
            long recipientA = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('constraint_a','test-hash','领取人甲')");
            long recipientB = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('constraint_b','test-hash','领取人乙')");
            long firstItem = insertItem(connection, ownerId, "约束测试物品一");
            long secondItem = insertItem(connection, ownerId, "约束测试物品二");
            long firstSlot = insertSlot(connection, firstItem);
            long secondSlot = insertSlot(connection, secondItem);
            long firstInterest = insertAndReturnId(connection, """
                    INSERT INTO item_interest(item_id,user_id,slot_id,status)
                    VALUES (?, ?, ?, 'CONFIRMED')
                    """, firstItem, recipientA, firstSlot);
            long secondInterest = insertAndReturnId(connection, """
                    INSERT INTO item_interest(item_id,user_id,slot_id,status)
                    VALUES (?, ?, ?, 'WAITING')
                    """, firstItem, recipientB, firstSlot);
            long handoverId = insertAndReturnId(connection, """
                    INSERT INTO handover(
                        item_id,publisher_id,recipient_id,interest_id,start_at,end_at,
                        location_snapshot,status
                    ) VALUES (?, ?, ?, ?, '2030-01-01 10:00:00', '2030-01-01 11:00:00',
                              '测试门口', 'ACTIVE')
                    """, firstItem, ownerId, recipientA, firstInterest);

            assertSqlRejected(connection,
                    "UPDATE item SET status = 'UNKNOWN' WHERE id = ?", firstItem);
            assertSqlRejected(connection, """
                    INSERT INTO item_interest(item_id,user_id,slot_id,status)
                    VALUES (?, ?, ?, 'WAITING')
                    """, firstItem, recipientB, secondSlot);
            assertSqlRejected(connection, """
                    INSERT INTO handover(
                        item_id,publisher_id,recipient_id,interest_id,start_at,end_at,
                        location_snapshot,status
                    ) VALUES (?, ?, ?, ?, '2030-01-02 10:00:00', '2030-01-02 11:00:00',
                              '测试门口', 'ACTIVE')
                    """, firstItem, ownerId, recipientB, secondInterest);
            assertSqlRejected(connection, """
                    INSERT INTO handover(
                        item_id,publisher_id,recipient_id,interest_id,start_at,end_at,
                        location_snapshot,status,cancelled_at
                    ) VALUES (?, ?, ?, ?, '2030-01-03 10:00:00', '2030-01-03 11:00:00',
                              '测试门口', 'CANCELLED', NOW())
                    """, firstItem, ownerId, recipientB, firstInterest);
            assertSqlRejected(connection,
                    "UPDATE item SET current_handover_id = ? WHERE id = ?", handoverId, secondItem);
            assertSqlRejected(connection, """
                    INSERT INTO handover(
                        item_id,publisher_id,recipient_id,interest_id,start_at,end_at,
                        location_snapshot,status
                    ) VALUES (?, ?, ?, ?, '2030-01-04 10:00:00', '2030-01-04 11:00:00',
                              '测试门口', 'CANCELLED')
                    """, firstItem, ownerId, recipientA, firstInterest);
        }
    }

    @Test
    void v7CompatibilityGuardRejectsInconsistentV6DataBeforeAnyDdl() throws Exception {
        flyway(MigrationVersion.fromVersion("6")).migrate();
        long inconsistentInterestId;
        long compatibleSlotId;
        try (Connection connection = connection()) {
            long ownerId = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('guard_owner','test-hash','发布者')");
            long recipientId = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('guard_recipient','test-hash','领取人')");
            long firstItem = insertItem(connection, ownerId, "守卫测试物品一");
            long secondItem = insertItem(connection, ownerId, "守卫测试物品二");
            compatibleSlotId = insertSlot(connection, firstItem);
            long secondSlot = insertSlot(connection, secondItem);
            inconsistentInterestId = insertAndReturnId(connection, """
                    INSERT INTO item_interest(item_id,user_id,slot_id,status)
                    VALUES (?, ?, ?, 'WAITING')
                    """, firstItem, recipientId, secondSlot);
        }

        FlywayException failure = assertThrows(FlywayException.class, () -> flyway(null).migrate());
        assertTrue(hasCauseMessage(failure, "nm_v7_blocked_inconsistent_existing_data"));

        try (Connection connection = connection()) {
            assertEquals(1, count(connection,
                    "SELECT COUNT(*) FROM item_interest WHERE id = ?", inconsistentInterestId));
            assertEquals(0, count(connection, """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 'handover'
                      AND column_name = 'active_item_id'
                    """));
            executeUpdate(connection,
                    "UPDATE item_interest SET slot_id = ? WHERE id = ?",
                    compatibleSlotId, inconsistentInterestId);
        }

        FlywayValidateException validationFailure = assertThrows(
                FlywayValidateException.class, () -> flyway(null).migrate());
        assertTrue(validationFailure.getMessage().contains("Detected failed migration to version 7"));
        flyway(null).repair();
        var retry = flyway(null).migrate();
        assertEquals(1, retry.migrationsExecuted,
                "after data is corrected and Flyway history repaired, V7 must apply cleanly");
        assertEquals("7", retry.targetSchemaVersion);
    }

    @Test
    void consistencyAuditReturnsNoRowsForAValidActiveHandover() throws Exception {
        flyway(null).migrate();
        try (Connection connection = connection()) {
            long ownerId = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('audit_owner','test-hash','发布者')");
            long recipientId = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('audit_recipient','test-hash','领取人')");
            long itemId = insertItem(connection, ownerId, "审计健康物品");
            long slotId = insertSlot(connection, itemId);
            long interestId = insertAndReturnId(connection, """
                    INSERT INTO item_interest(item_id,user_id,slot_id,status)
                    VALUES (?, ?, ?, 'CONFIRMED')
                    """, itemId, recipientId, slotId);
            long handoverId = insertAndReturnId(connection, """
                    INSERT INTO handover(
                        item_id,publisher_id,recipient_id,interest_id,start_at,end_at,
                        location_snapshot,status
                    ) VALUES (?, ?, ?, ?, '2030-01-01 10:00:00', '2030-01-01 11:00:00',
                              '测试门口', 'ACTIVE')
                    """, itemId, ownerId, recipientId, interestId);
            executeUpdate(connection,
                    "UPDATE item SET status = 'RESERVED', current_handover_id = ? WHERE id = ?",
                    handoverId, itemId);

            assertTrue(auditIssueCodes(connection).isEmpty(),
                    "a consistent V7 workflow must produce an empty audit result");
        }
    }

    @Test
    void consistencyAuditFindsConstructedV6Anomalies() throws Exception {
        flyway(MigrationVersion.fromVersion("6")).migrate();
        try (Connection connection = connection()) {
            long ownerId = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('audit_bad_owner','test-hash','发布者')");
            long recipientA = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('audit_bad_a','test-hash','领取人甲')");
            long recipientB = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('audit_bad_b','test-hash','领取人乙')");
            long recipientC = insertAndReturnId(connection,
                    "INSERT INTO app_user(username,password_hash,nickname) VALUES ('audit_bad_c','test-hash','领取人丙')");
            long firstItem = insertItem(connection, ownerId, "审计异常物品一");
            long secondItem = insertItem(connection, ownerId, "审计异常物品二");
            long firstSlot = insertSlot(connection, firstItem);
            long secondSlot = insertSlot(connection, secondItem);
            insertAndReturnId(connection, """
                    INSERT INTO item_interest(item_id,user_id,slot_id,status)
                    VALUES (?, ?, ?, 'WAITING')
                    """, firstItem, recipientA, secondSlot);
            long confirmedInterest = insertAndReturnId(connection, """
                    INSERT INTO item_interest(item_id,user_id,slot_id,status)
                    VALUES (?, ?, ?, 'CONFIRMED')
                    """, firstItem, recipientB, firstSlot);
            long waitingInterest = insertAndReturnId(connection, """
                    INSERT INTO item_interest(item_id,user_id,slot_id,status)
                    VALUES (?, ?, ?, 'WAITING')
                    """, firstItem, recipientC, firstSlot);
            insertAndReturnId(connection, """
                    INSERT INTO item_interest(item_id,user_id,slot_id,status)
                    VALUES (?, ?, ?, 'WAITING')
                    """, secondItem, ownerId, secondSlot);
            executeUpdate(connection,
                    "UPDATE item SET status = 'RESERVED' WHERE id = ?", firstItem);
            executeUpdate(connection,
                    "UPDATE item SET status = 'UNKNOWN' WHERE id = ?", secondItem);
            insertAndReturnId(connection, """
                    INSERT INTO handover(
                        item_id,publisher_id,recipient_id,interest_id,start_at,end_at,
                        location_snapshot,status
                    ) VALUES (?, ?, ?, ?, '2030-01-01 10:00:00', '2030-01-01 11:00:00',
                              '测试门口', 'ACTIVE')
                    """, firstItem, ownerId, recipientA, confirmedInterest);
            insertAndReturnId(connection, """
                    INSERT INTO handover(
                        item_id,publisher_id,recipient_id,interest_id,start_at,end_at,
                        location_snapshot,status
                    ) VALUES (?, ?, ?, ?, '2030-01-02 10:00:00', '2030-01-02 11:00:00',
                              '测试门口', 'ACTIVE')
                    """, firstItem, ownerId, recipientC, waitingInterest);
            executeUpdate(connection, "SET FOREIGN_KEY_CHECKS = 0");
            try {
                insertAndReturnId(connection, """
                        INSERT INTO item_comment(item_id,user_id,parent_id,content)
                        VALUES (?, ?, 999999999, '孤立父留言测试')
                        """, firstItem, recipientA);
            } finally {
                executeUpdate(connection, "SET FOREIGN_KEY_CHECKS = 1");
            }

            Set<String> issues = auditIssueCodes(connection);
            assertTrue(issues.containsAll(Set.of(
                    "ITEM_INVALID_STATUS",
                    "INTEREST_SLOT_ITEM_MISMATCH",
                    "HANDOVER_INTEREST_MISMATCH",
                    "MULTIPLE_ACTIVE_HANDOVERS",
                    "ITEM_HANDOVER_STATE_MISMATCH",
                    "ACTIVE_HANDOVER_NOT_CURRENT",
                    "ACTIVE_OR_COMPLETED_INTEREST_MISMATCH",
                    "ORPHAN_COMMENT_REFERENCE",
                    "PUBLISHER_SELF_INTEREST"
            )), "audit must identify every constructed anomaly; actual=" + issues);
        }
    }

    @Test
    void consistencyAuditScriptContainsOnlyOneReadOnlyStatement() throws Exception {
        String sql = auditSql();
        assertEquals(1, sql.chars().filter(character -> character == ';').count());
        assertTrue(sql.stripLeading().startsWith("-- NeighborMarket read-only data consistency audit."));
        assertTrue(!sql.matches("(?is).*\\n\\s*(INSERT|UPDATE|DELETE|ALTER|DROP|CREATE|TRUNCATE|CALL|SET)\\s+.*"),
                "audit script must not contain mutating statements");
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private long insertAndReturnId(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next(), "insert should return a generated key");
                return keys.getLong(1);
            }
        }
    }

    private int count(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "count query should return one row");
                return result.getInt(1);
            }
        }
    }

    private long insertItem(Connection connection, long publisherId, String title) throws Exception {
        return insertAndReturnId(connection, """
                INSERT INTO item(
                    publisher_id,title,description,category,trade_type,price,image_url,status,
                    last_confirmed_at,pickup_location
                ) VALUES (?, ?, '约束测试', '其他', 'FREE', 0, '/test.jpg', 'AVAILABLE', NOW(), '测试门口')
                """, publisherId, title);
    }

    private long insertSlot(Connection connection, long itemId) throws Exception {
        return insertAndReturnId(connection, """
                INSERT INTO pickup_slot(item_id,start_at,end_at,active)
                VALUES (?, '2030-01-01 10:00:00', '2030-01-01 11:00:00', TRUE)
                """, itemId);
    }

    private void executeUpdate(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }

    private void assertSqlRejected(Connection connection, String sql, Object... parameters) {
        assertThrows(SQLException.class, () -> executeUpdate(connection, sql, parameters));
    }

    private boolean hasCauseMessage(Throwable failure, String expected) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) return true;
        }
        return false;
    }

    private Set<String> auditIssueCodes(Connection connection) throws Exception {
        Set<String> issues = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(auditSql())) {
            while (result.next()) issues.add(result.getString("issue_code"));
        }
        return issues;
    }

    private String auditSql() throws Exception {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Path direct = workingDirectory.resolve("scripts/audit/data_consistency_audit.sql");
        Path fromBackend = workingDirectory.resolve("../scripts/audit/data_consistency_audit.sql").normalize();
        Path script = Files.exists(direct) ? direct : fromBackend;
        return Files.readString(script, StandardCharsets.UTF_8);
    }
}
