package com.neighbormarket.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PerformanceBaselineIntegrationTest {
    private static final int USER_COUNT = 100;
    private static final int ITEM_COUNT = 3_000;
    private static final int INTERESTS_PER_ITEM = 3;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("neighbor_market_performance_test")
            .withUsername("neighbor_perf")
            .withPassword("test-only-password");

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            seedUsers(connection);
            seedItems(connection);
            seedSlots(connection);
            seedInterests(connection);
            seedHandovers(connection);
            connection.commit();
            try (Statement statement = connection.createStatement()) {
                statement.execute("ANALYZE TABLE item, pickup_slot, item_interest, handover");
            }
        }
    }

    @Test
    void datasetIsDeterministicAndEveryBaselineQueryHasAnExecutedPlan() throws Exception {
        try (Connection connection = connection()) {
            assertEquals(USER_COUNT, count(connection, "SELECT COUNT(*) FROM app_user"));
            assertEquals(ITEM_COUNT, count(connection, "SELECT COUNT(*) FROM item"));
            assertEquals(ITEM_COUNT, count(connection, "SELECT COUNT(*) FROM pickup_slot"));
            assertEquals(ITEM_COUNT * INTERESTS_PER_ITEM,
                    count(connection, "SELECT COUNT(*) FROM item_interest"));
            assertEquals(1_200, count(connection, "SELECT COUNT(*) FROM handover"));
            assertEquals(1_800, count(connection, "SELECT COUNT(*) FROM item WHERE status = 'AVAILABLE'"));
            assertEquals(600, count(connection, "SELECT COUNT(*) FROM item WHERE status = 'RESERVED'"));
            assertEquals(600, count(connection, "SELECT COUNT(*) FROM item WHERE status = 'COMPLETED'"));
            assertTrue(count(connection, """
                    SELECT COUNT(*) FROM item
                    WHERE status = 'AVAILABLE'
                      AND (title LIKE '%baseline-target%' OR description LIKE '%baseline-target%')
                    """) > 0, "keyword baseline must contain matching AVAILABLE rows");

            for (Map.Entry<String, String> baseline : queries().entrySet()) {
                BaselineResult result = measure(connection, baseline.getValue());
                assertFalse(result.plan().isBlank(), baseline.getKey() + " must return an EXPLAIN ANALYZE plan");
                assertTrue(result.plan().contains("actual time="),
                        baseline.getKey() + " must execute rather than only estimate the plan");
                System.out.printf("[PERF_BASELINE] query=%s median_ms=%.3f plan=%s%n",
                        baseline.getKey(), result.medianMillis(), singleLine(result.plan()));
            }
        }
    }

    @Test
    void optimizedQueriesPreserveLegacyResultRowsAndOrdering() throws Exception {
        Map<String, String> sql = queries();
        try (Connection connection = connection()) {
            assertEquals(rows(connection, sql.get("legacy_my_handovers_join_order")),
                    rows(connection, sql.get("my_handovers")));
            assertEquals(rows(connection, sql.get("legacy_dashboard_most_wanted")),
                    rows(connection, sql.get("dashboard_most_wanted")));
        }
    }

    private static Map<String, String> queries() {
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("market_page", """
                SELECT i.*,
                       (SELECT u.nickname FROM app_user u WHERE u.id = i.publisher_id) AS publisher_nickname
                FROM item i
                WHERE i.status = 'AVAILABLE'
                ORDER BY i.published_at DESC, i.id DESC
                LIMIT 12
                """);
        queries.put("market_count", "SELECT COUNT(*) FROM item WHERE status = 'AVAILABLE'");
        queries.put("market_keyword_search", """
                SELECT i.*,
                       (SELECT u.nickname FROM app_user u WHERE u.id = i.publisher_id) AS publisher_nickname
                FROM item i
                WHERE i.status = 'AVAILABLE'
                  AND (i.title LIKE '%baseline-target%' OR i.description LIKE '%baseline-target%')
                ORDER BY i.published_at DESC, i.id DESC
                LIMIT 12
                """);
        queries.put("my_interests", """
                SELECT * FROM item_interest
                WHERE user_id = 1
                ORDER BY updated_at DESC
                """);
        queries.put("legacy_my_handovers_join_order", """
                SELECT h.*, i.title,
                       publisher.nickname AS publisher_name,
                       recipient.nickname AS recipient_name
                FROM handover h
                JOIN item i ON i.id = h.item_id
                JOIN app_user publisher ON publisher.id = h.publisher_id
                JOIN app_user recipient ON recipient.id = h.recipient_id
                WHERE h.publisher_id = 1 OR h.recipient_id = 1
                ORDER BY h.created_at DESC, h.id DESC
                """);
        queries.put("candidate_my_handovers_union", """
                (SELECT h.*, i.title,
                        publisher.nickname AS publisher_name,
                        recipient.nickname AS recipient_name
                 FROM handover h
                 JOIN item i ON i.id = h.item_id
                 JOIN app_user publisher ON publisher.id = h.publisher_id
                 JOIN app_user recipient ON recipient.id = h.recipient_id
                 WHERE h.publisher_id = 1)
                UNION ALL
                (SELECT h.*, i.title,
                        publisher.nickname AS publisher_name,
                        recipient.nickname AS recipient_name
                 FROM handover h
                 JOIN item i ON i.id = h.item_id
                 JOIN app_user publisher ON publisher.id = h.publisher_id
                 JOIN app_user recipient ON recipient.id = h.recipient_id
                 WHERE h.recipient_id = 1 AND h.publisher_id <> 1)
                ORDER BY created_at DESC, id DESC
                """);
        queries.put("my_handovers", """
                SELECT STRAIGHT_JOIN h.*, i.title,
                       publisher.nickname AS publisher_name,
                       recipient.nickname AS recipient_name
                FROM handover h
                JOIN item i ON i.id = h.item_id
                JOIN app_user publisher ON publisher.id = h.publisher_id
                JOIN app_user recipient ON recipient.id = h.recipient_id
                WHERE h.publisher_id = 1 OR h.recipient_id = 1
                ORDER BY h.created_at DESC, h.id DESC
                """);
        queries.put("dashboard_published", """
                SELECT COUNT(*) FROM item
                WHERE published_at >= '2026-01-01 00:00:00'
                  AND published_at < '2027-01-01 00:00:00'
                """);
        queries.put("dashboard_completed", """
                SELECT COUNT(*) FROM handover
                WHERE status = 'COMPLETED'
                  AND completed_at >= '2026-01-01 00:00:00'
                  AND completed_at < '2027-01-01 00:00:00'
                """);
        queries.put("dashboard_available", "SELECT COUNT(*) FROM item WHERE status = 'AVAILABLE'");
        queries.put("dashboard_fastest", """
                SELECT i.id AS item_id, i.title,
                       TIMESTAMPDIFF(SECOND, i.published_at, h.completed_at) AS duration_seconds,
                       h.completed_at
                FROM handover h
                JOIN item i ON i.id = h.item_id
                WHERE h.status = 'COMPLETED'
                  AND i.status = 'COMPLETED'
                  AND h.completed_at IS NOT NULL
                  AND h.completed_at >= i.published_at
                ORDER BY duration_seconds ASC, h.completed_at ASC, i.id ASC
                LIMIT 1
                """);
        queries.put("candidate_dashboard_fastest_item", """
                SELECT i.id AS item_id, i.title,
                       TIMESTAMPDIFF(SECOND, i.published_at, i.completed_at) AS duration_seconds,
                       i.completed_at
                FROM item i
                WHERE i.status = 'COMPLETED'
                  AND i.completed_at IS NOT NULL
                  AND i.completed_at >= i.published_at
                ORDER BY duration_seconds ASC, i.completed_at ASC, i.id ASC
                LIMIT 1
                """);
        queries.put("legacy_dashboard_most_wanted", """
                SELECT i.id AS item_id, i.title, i.status AS item_status,
                       COUNT(DISTINCT ii.user_id) AS interest_count
                FROM item i
                JOIN item_interest ii ON ii.item_id = i.id
                WHERE i.status <> 'COMPLETED'
                  AND ii.status IN ('WAITING', 'CONFIRMED')
                GROUP BY i.id, i.title, i.status, i.published_at
                ORDER BY interest_count DESC, i.published_at ASC, i.id ASC
                LIMIT 5
                """);
        queries.put("dashboard_most_wanted", """
                SELECT i.id AS item_id, i.title, i.status AS item_status,
                       ranked.interest_count
                FROM item i
                JOIN (
                    SELECT ii.item_id, COUNT(DISTINCT ii.user_id) AS interest_count
                    FROM item_interest ii
                    WHERE ii.status IN ('WAITING', 'CONFIRMED')
                    GROUP BY ii.item_id
                ) ranked ON ranked.item_id = i.id
                WHERE i.status <> 'COMPLETED'
                ORDER BY ranked.interest_count DESC, i.published_at ASC, i.id ASC
                LIMIT 5
                """);
        return queries;
    }

    private static BaselineResult measure(Connection connection, String sql) throws Exception {
        explain(connection, sql);
        List<Double> samples = new ArrayList<>();
        String plan = "";
        for (int run = 0; run < 5; run++) {
            long started = System.nanoTime();
            plan = explain(connection, sql);
            samples.add((System.nanoTime() - started) / 1_000_000.0);
        }
        samples.sort(Comparator.naturalOrder());
        return new BaselineResult(samples.get(samples.size() / 2), plan);
    }

    private static String explain(Connection connection, String sql) throws Exception {
        StringBuilder plan = new StringBuilder();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("EXPLAIN ANALYZE " + sql)) {
            while (result.next()) {
                if (!plan.isEmpty()) plan.append('\n');
                plan.append(result.getString(1));
            }
        }
        return plan.toString();
    }

    private static void seedUsers(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_user(id, username, password_hash, nickname, created_at)
                VALUES (?, ?, 'test-only-hash', ?, '2026-01-01 00:00:00')
                """)) {
            for (int id = 1; id <= USER_COUNT; id++) {
                statement.setInt(1, id);
                statement.setString(2, "perf_user_" + id);
                statement.setString(3, "性能用户" + id);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void seedItems(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO item(
                    id, publisher_id, title, description, category, trade_type, price,
                    image_url, status, published_at, last_confirmed_at, completed_at,
                    pickup_location, current_handover_id
                ) VALUES (?, ?, ?, ?, ?, 'FREE', 0, '/performance.jpg', ?, ?, ?, ?, '性能测试门口', NULL)
                """)) {
            LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
            for (int id = 1; id <= ITEM_COUNT; id++) {
                String status = id % 5 == 0 ? "COMPLETED" : id % 5 == 1 ? "RESERVED" : "AVAILABLE";
                LocalDateTime published = base.plusMinutes(id);
                statement.setInt(1, id);
                statement.setInt(2, publisherId(id));
                statement.setString(3, (id % 11 == 0 ? "baseline-target " : "普通物品 ") + id);
                statement.setString(4, (id % 17 == 0 ? "baseline-target " : "可复现性能数据 ") + id);
                statement.setString(5, "分类" + (id % 8));
                statement.setString(6, status);
                statement.setObject(7, published);
                statement.setObject(8, published.plusDays(1));
                statement.setObject(9, status.equals("COMPLETED") ? published.plusHours(6) : null);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void seedSlots(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO pickup_slot(id, item_id, start_at, end_at, active, created_at)
                VALUES (?, ?, '2027-01-01 10:00:00', '2027-01-01 11:00:00', TRUE, '2026-01-01 00:00:00')
                """)) {
            for (int id = 1; id <= ITEM_COUNT; id++) {
                statement.setInt(1, id);
                statement.setInt(2, id);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void seedInterests(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO item_interest(
                    id, item_id, user_id, slot_id, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            int interestId = 0;
            LocalDateTime base = LocalDateTime.of(2026, 2, 1, 0, 0);
            for (int itemId = 1; itemId <= ITEM_COUNT; itemId++) {
                for (int offset = 1; offset <= INTERESTS_PER_ITEM; offset++) {
                    interestId++;
                    String itemStatus = itemId % 5 == 0 ? "COMPLETED" : itemId % 5 == 1 ? "RESERVED" : "AVAILABLE";
                    statement.setInt(1, interestId);
                    statement.setInt(2, itemId);
                    statement.setInt(3, recipientId(itemId, offset));
                    statement.setInt(4, itemId);
                    statement.setString(5, offset == 1 && !itemStatus.equals("AVAILABLE") ? "CONFIRMED" : "WAITING");
                    statement.setObject(6, base.plusMinutes(interestId));
                    statement.setObject(7, base.plusMinutes(interestId));
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private static void seedHandovers(Connection connection) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO handover(
                    id, item_id, publisher_id, recipient_id, interest_id, start_at, end_at,
                    location_snapshot, status, created_at, cancelled_at, completed_at
                ) VALUES (?, ?, ?, ?, ?, '2027-01-01 10:00:00', '2027-01-01 11:00:00',
                          '性能测试门口', ?, ?, NULL, ?)
                """);
             PreparedStatement link = connection.prepareStatement(
                     "UPDATE item SET current_handover_id = ? WHERE id = ?")) {
            int handoverId = 0;
            LocalDateTime base = LocalDateTime.of(2026, 3, 1, 0, 0);
            for (int itemId = 1; itemId <= ITEM_COUNT; itemId++) {
                if (itemId % 5 != 0 && itemId % 5 != 1) continue;
                handoverId++;
                boolean completed = itemId % 5 == 0;
                LocalDateTime created = base.plusMinutes(handoverId);
                insert.setInt(1, handoverId);
                insert.setInt(2, itemId);
                insert.setInt(3, publisherId(itemId));
                insert.setInt(4, recipientId(itemId, 1));
                insert.setInt(5, firstInterestId(itemId));
                insert.setString(6, completed ? "COMPLETED" : "ACTIVE");
                insert.setObject(7, created);
                insert.setObject(8, completed ? created.plusHours(2) : null);
                insert.addBatch();
                link.setInt(1, handoverId);
                link.setInt(2, itemId);
                link.addBatch();
            }
            insert.executeBatch();
            link.executeBatch();
        }
    }

    private static int publisherId(int itemId) {
        return ((itemId - 1) % USER_COUNT) + 1;
    }

    private static int recipientId(int itemId, int offset) {
        return ((publisherId(itemId) - 1 + offset) % USER_COUNT) + 1;
    }

    private static int firstInterestId(int itemId) {
        return (itemId - 1) * INTERESTS_PER_ITEM + 1;
    }

    private static long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static List<List<String>> rows(Connection connection, String sql) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = result.getMetaData();
            while (result.next()) {
                List<String> row = new ArrayList<>();
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    row.add(result.getString(column));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static String singleLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private record BaselineResult(double medianMillis, String plan) {}
}
