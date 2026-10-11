package com.neighbormarket.integration;

import com.neighbormarket.domain.HandoverStatus;
import com.neighbormarket.domain.InterestStatus;
import com.neighbormarket.service.ExchangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers(disabledWithoutDocker = true)
@Import(SqlStatementCounter.Configuration.class)
class ExchangeReadQueryIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("neighbor_market_exchange_read_test")
            .withUsername("neighbor_exchange")
            .withPassword("test-only-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    ExchangeService exchangeService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SqlStatementCounter queryCounter;

    @BeforeEach
    void seedExchangeHistory() {
        jdbc.update("UPDATE item SET current_handover_id = NULL");
        jdbc.update("DELETE FROM item_comment WHERE parent_id IS NOT NULL");
        jdbc.update("DELETE FROM item_comment");
        jdbc.update("DELETE FROM handover");
        jdbc.update("DELETE FROM item_interest");
        jdbc.update("DELETE FROM pickup_slot");
        jdbc.update("DELETE FROM item");
        jdbc.update("DELETE FROM app_user");

        jdbc.update("""
                INSERT INTO app_user(id, username, password_hash, nickname)
                VALUES (1, 'owner', 'test-only-hash', '发布者'),
                       (2, 'recipient', 'test-only-hash', '领取人'),
                       (3, 'neighbor_three', 'test-only-hash', '邻居三'),
                       (4, 'neighbor_four', 'test-only-hash', '邻居四')
                """);
        jdbc.update("""
                INSERT INTO item(
                    id, publisher_id, title, description, category, trade_type, price,
                    image_url, status, published_at, last_confirmed_at, completed_at,
                    pickup_location, current_handover_id
                ) VALUES
                    (10, 1, '可领取书籍', '测试', '书籍', 'FREE', 0, '/test.jpg', 'AVAILABLE',
                     '2026-01-01 10:00:00', '2026-01-01 10:00:00', NULL, '社区门口', NULL),
                    (11, 1, '预约中的桌子', '测试', '家具', 'FREE', 0, '/test.jpg', 'RESERVED',
                     '2026-01-02 10:00:00', '2026-01-02 10:00:00', NULL, '社区门口', NULL),
                    (12, 1, '已完成台灯', '测试', '家具', 'FREE', 0, '/test.jpg', 'COMPLETED',
                     '2026-01-03 10:00:00', '2026-01-03 10:00:00', '2026-02-03 12:00:00', '社区门口', NULL)
                """);
        jdbc.update("""
                INSERT INTO pickup_slot(id, item_id, start_at, end_at, created_at, active)
                VALUES (100, 10, '2027-01-10 10:00:00', '2027-01-10 11:00:00', '2026-01-01 10:00:00', TRUE),
                       (110, 11, '2027-01-11 10:00:00', '2027-01-11 11:00:00', '2026-01-02 10:00:00', FALSE),
                       (120, 12, '2027-01-12 10:00:00', '2027-01-12 11:00:00', '2026-01-03 10:00:00', FALSE)
                """);
        jdbc.update("""
                INSERT INTO item_interest(id, item_id, user_id, slot_id, status, created_at, updated_at)
                VALUES (1000, 10, 2, 100, 'WAITING', '2026-02-01 09:00:00', '2026-02-01 09:00:00'),
                       (1001, 10, 3, 100, 'WAITING', '2026-02-01 09:01:00', '2026-02-01 09:01:00'),
                       (1002, 10, 4, 100, 'CANCELLED', '2026-02-01 09:02:00', '2026-02-01 09:02:00'),
                       (1010, 11, 2, 110, 'CONFIRMED', '2026-02-02 09:00:00', '2026-02-02 09:00:00'),
                       (1020, 12, 2, 120, 'CONFIRMED', '2026-02-03 09:00:00', '2026-02-03 09:00:00')
                """);
        jdbc.update("""
                INSERT INTO handover(
                    id, item_id, publisher_id, recipient_id, interest_id, start_at, end_at,
                    location_snapshot, status, created_at, cancelled_at, completed_at
                ) VALUES
                    (200, 11, 1, 2, 1010, '2027-01-11 10:00:00', '2027-01-11 11:00:00',
                     '社区门口', 'ACTIVE', '2026-02-02 10:00:00', NULL, NULL),
                    (201, 12, 1, 2, 1020, '2027-01-12 10:00:00', '2027-01-12 11:00:00',
                     '社区门口', 'COMPLETED', '2026-02-03 10:00:00', NULL, '2026-02-03 12:00:00')
                """);
        jdbc.update("UPDATE item SET current_handover_id = 200 WHERE id = 11");
        jdbc.update("UPDATE item SET current_handover_id = 201 WHERE id = 12");
        jdbc.update("""
                INSERT INTO item_comment(id, item_id, user_id, parent_id, content, created_at)
                VALUES (300, 10, 2, NULL, '请问还在吗', '2026-02-01 10:00:00'),
                       (301, 10, 1, 300, '还在的', '2026-02-01 10:01:00'),
                       (302, 10, 3, NULL, '我也感兴趣', '2026-02-01 10:02:00')
                """);
        queryCounter.reset();
    }

    @Test
    void publisherInterestListUsesThreeStatementsAndKeepsEnrichedOrdering() {
        var views = exchangeService.itemInterests(10L, "owner");

        assertEquals(3, queryCounter.count());
        assertEquals(3, views.size());
        assertEquals(1002L, views.get(0).id());
        assertEquals("可领取书籍", views.get(0).itemTitle());
        assertEquals("邻居四", views.get(0).nickname());
        assertEquals(InterestStatus.CANCELLED, views.get(0).status());
        assertEquals(1000L, views.get(2).id());
    }

    @Test
    void singleInterestUsesTwoStatementsAndIncludesHistoricalSlot() {
        var view = exchangeService.myInterest(11L, "recipient");

        assertEquals(2, queryCounter.count());
        assertEquals(1010L, view.id());
        assertEquals("预约中的桌子", view.itemTitle());
        assertEquals("领取人", view.nickname());
        assertEquals(InterestStatus.CONFIRMED, view.status());
    }

    @Test
    void handoverListAndDetailHaveFixedStatementCounts() {
        var handovers = exchangeService.myHandovers("recipient");

        assertEquals(2, queryCounter.count());
        assertEquals(2, handovers.size());
        assertEquals(201L, handovers.get(0).id());
        assertEquals("已完成台灯", handovers.get(0).itemTitle());
        assertEquals("发布者", handovers.get(0).publisherName());
        assertEquals("领取人", handovers.get(0).recipientName());
        assertEquals(HandoverStatus.COMPLETED, handovers.get(0).status());

        queryCounter.reset();
        var detail = exchangeService.handover(200L, "owner");
        assertEquals(2, queryCounter.count());
        assertEquals("预约中的桌子", detail.itemTitle());
        assertEquals(HandoverStatus.ACTIVE, detail.status());
        assertNull(detail.completedAt());
    }

    @Test
    void commentsUseTwoStatementsAndPreserveThreadFields() {
        var views = exchangeService.comments(10L);

        assertEquals(2, queryCounter.count());
        assertEquals(3, views.size());
        assertEquals(300L, views.get(0).id());
        assertEquals("领取人", views.get(0).nickname());
        assertEquals(300L, views.get(1).parentId());
        assertEquals("发布者", views.get(1).nickname());
    }

    @Test
    void dashboardUsesThreeStatementsForAllEnrichedRows() {
        var dashboard = exchangeService.dashboard("recipient");

        assertEquals(3, queryCounter.count());
        assertEquals(3, dashboard.interests().size());
        assertEquals(2, dashboard.handovers().size());
        assertEquals("已完成台灯", dashboard.interests().get(0).itemTitle());
        assertEquals("已完成台灯", dashboard.handovers().get(0).itemTitle());
    }
}
