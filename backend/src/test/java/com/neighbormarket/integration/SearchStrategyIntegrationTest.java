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
class SearchStrategyIntegrationTest {
    private static final int ITEM_COUNT = 30_000;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("neighbor_market_search_test")
            .withUsername("neighbor_search")
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
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO app_user(id, username, password_hash, nickname)
                        VALUES (1, 'search_owner', 'test-only-hash', '搜索测试用户')
                        """);
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO item(
                        id, publisher_id, title, description, category, trade_type, price,
                        image_url, status, published_at, last_confirmed_at
                    ) VALUES (?, 1, ?, ?, '测试', 'FREE', 0, '/search-test.jpg',
                              'AVAILABLE', ?, ?)
                    """)) {
                LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
                for (int id = 1; id <= ITEM_COUNT; id++) {
                    boolean lowFrequency = id % 100 == 0 || id % 333 == 0;
                    String title = lowFrequency ? "baseline-target durable drill " + id : "普通物品 " + id;
                    if (id == 29_999) title = "unique-needle collector item";
                    String description = lowFrequency ? "baseline-target search sample" : "社区闲置物品描述";
                    statement.setInt(1, id);
                    statement.setString(2, title);
                    statement.setString(3, description);
                    statement.setObject(4, base.plusSeconds(id));
                    statement.setObject(5, base.plusDays(1));
                    statement.addBatch();
                    if (id % 1_000 == 0) statement.executeBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE item ADD FULLTEXT INDEX ft_candidate_item_text(title, description)");
                statement.execute("ANALYZE TABLE item");
            }
        }
    }

    @Test
    void likeSearchPlansCoverHighLowAndZeroHitCases() throws Exception {
        try (Connection connection = connection()) {
            assertEquals(ITEM_COUNT, scalar(connection, "SELECT COUNT(*) FROM item"));
            for (Map.Entry<String, String> query : likeQueries().entrySet()) {
                Measurement result = measure(connection, query.getValue());
                assertFalse(result.plan().isBlank());
                assertTrue(result.plan().contains("actual time="));
                System.out.printf("[SEARCH_STRATEGY] query=%s median_ms=%.3f plan=%s%n",
                        query.getKey(), result.medianMillis(), singleLine(result.plan()));
            }
        }
    }

    @Test
    void fulltextCandidateIsMeasuredAndItsSubstringSemanticGapIsExplicit() throws Exception {
        try (Connection connection = connection()) {
            long likeWholeToken = scalar(connection, """
                    SELECT COUNT(*) FROM item
                    WHERE status = 'AVAILABLE'
                      AND (title LIKE '%baseline-target%' OR description LIKE '%baseline-target%')
                    """);
            long fulltextWholeToken = scalar(connection, """
                    SELECT COUNT(*) FROM item
                    WHERE status = 'AVAILABLE'
                      AND MATCH(title, description) AGAINST('+baseline +target' IN BOOLEAN MODE)
                    """);
            long likeInsideToken = scalar(connection, """
                    SELECT COUNT(*) FROM item
                    WHERE status = 'AVAILABLE'
                      AND (title LIKE '%seline%' OR description LIKE '%seline%')
                    """);
            long fulltextInsideToken = scalar(connection, """
                    SELECT COUNT(*) FROM item
                    WHERE status = 'AVAILABLE'
                      AND MATCH(title, description) AGAINST('seline' IN BOOLEAN MODE)
                    """);

            assertTrue(likeWholeToken > 0);
            assertEquals(likeWholeToken, fulltextWholeToken,
                    "controlled whole-token sample should be comparable");
            assertEquals(likeWholeToken, likeInsideToken,
                    "LIKE intentionally supports matching inside a token");
            assertEquals(0, fulltextInsideToken,
                    "FULLTEXT token matching is not a drop-in replacement for substring search");

            for (Map.Entry<String, String> query : fulltextQueries().entrySet()) {
                Measurement result = measure(connection, query.getValue());
                assertTrue(result.plan().contains("actual time="));
                System.out.printf("[SEARCH_STRATEGY] query=%s median_ms=%.3f plan=%s%n",
                        query.getKey(), result.medianMillis(), singleLine(result.plan()));
            }
        }
    }

    private static Map<String, String> likeQueries() {
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("like_high_hit_page", pageLike("普通"));
        queries.put("like_high_hit_count", countLike("普通"));
        queries.put("like_low_hit_page", pageLike("baseline-target"));
        queries.put("like_low_hit_count", countLike("baseline-target"));
        queries.put("like_single_hit_page", pageLike("unique-needle"));
        queries.put("like_single_hit_count", countLike("unique-needle"));
        queries.put("like_zero_hit_page", pageLike("definitely-absent-token"));
        queries.put("like_zero_hit_count", countLike("definitely-absent-token"));
        return queries;
    }

    private static Map<String, String> fulltextQueries() {
        Map<String, String> queries = new LinkedHashMap<>();
        String predicate = "MATCH(title, description) AGAINST('+baseline +target' IN BOOLEAN MODE)";
        queries.put("fulltext_low_hit_page", """
                SELECT id, title FROM item
                WHERE status = 'AVAILABLE' AND %s
                ORDER BY published_at DESC, id DESC LIMIT 12
                """.formatted(predicate));
        queries.put("fulltext_low_hit_count", """
                SELECT COUNT(*) FROM item
                WHERE status = 'AVAILABLE' AND %s
                """.formatted(predicate));
        return queries;
    }

    private static String pageLike(String keyword) {
        return """
                SELECT id, title FROM item
                WHERE status = 'AVAILABLE'
                  AND (title LIKE '%%%s%%' OR description LIKE '%%%s%%')
                ORDER BY published_at DESC, id DESC LIMIT 12
                """.formatted(keyword, keyword);
    }

    private static String countLike(String keyword) {
        return """
                SELECT COUNT(*) FROM item
                WHERE status = 'AVAILABLE'
                  AND (title LIKE '%%%s%%' OR description LIKE '%%%s%%')
                """.formatted(keyword, keyword);
    }

    private static Measurement measure(Connection connection, String sql) throws Exception {
        explain(connection, sql);
        List<Double> samples = new ArrayList<>();
        String plan = "";
        for (int run = 0; run < 5; run++) {
            long started = System.nanoTime();
            plan = explain(connection, sql);
            samples.add((System.nanoTime() - started) / 1_000_000.0);
        }
        samples.sort(Comparator.naturalOrder());
        return new Measurement(samples.get(samples.size() / 2), plan);
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

    private static long scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static String singleLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private record Measurement(double medianMillis, String plan) {}
}
