package com.neighbormarket.integration;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.neighbormarket.domain.Item;
import com.neighbormarket.service.ItemService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers(disabledWithoutDocker = true)
@Import(SqlStatementCounter.Configuration.class)
class ItemListQueryIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("neighbor_market_item_list_test")
            .withUsername("neighbor_list")
            .withPassword("test-only-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    ItemService itemService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SqlStatementCounter queryCounter;

    @BeforeEach
    void seedItems() {
        jdbc.update("DELETE FROM item");
        jdbc.update("DELETE FROM app_user");
        jdbc.update("""
                INSERT INTO app_user(id, username, password_hash, nickname)
                VALUES (1, 'owner_one', 'test-only-hash', '发布者一'),
                       (2, 'owner_two', 'test-only-hash', '发布者二')
                """);
        for (int id = 1; id <= 20; id++) {
            jdbc.update("""
                    INSERT INTO item(
                        id, publisher_id, title, description, category, trade_type, price,
                        image_url, status, published_at, last_confirmed_at, pickup_location
                    ) VALUES (?, ?, ?, ?, ?, 'FREE', 0, '/test.jpg', 'AVAILABLE',
                              TIMESTAMP('2026-01-01 00:00:00') + INTERVAL ? MINUTE,
                              '2026-01-02 00:00:00', '测试门口')
                    """, id, id % 2 == 0 ? 1 : 2, "物品 " + id,
                    id % 3 == 0 ? "含关键词的描述" : "普通描述",
                    id % 2 == 0 ? "书籍" : "其他", id);
        }
        queryCounter.reset();
    }

    @Test
    void marketPageQueryCountDoesNotGrowWithPageSize() {
        var one = itemService.market(new Page<Item>(1, 1), null, null, null, null);
        assertEquals(2, queryCounter.count(), "pagination should execute one count and one joined page query");
        assertEquals("发布者一", one.getRecords().get(0).publisher().nickname());

        queryCounter.reset();
        var twelve = itemService.market(new Page<Item>(1, 12), null, null, null, null);
        assertEquals(2, queryCounter.count(), "publisher mapping must not add one query per item");
        assertEquals(12, twelve.getRecords().size());
        assertTrue(twelve.getRecords().stream().allMatch(item -> item.publisher() != null));
    }

    @Test
    void marketFiltersAndStableOrderingRemainCompatible() {
        var page = itemService.market(new Page<Item>(1, 10), "关键词", "书籍", null, null);

        assertEquals(2, queryCounter.count());
        assertTrue(page.getRecords().stream().allMatch(item -> item.description().contains("关键词")));
        assertTrue(page.getRecords().stream().allMatch(item -> item.category().equals("书籍")));
        for (int index = 1; index < page.getRecords().size(); index++) {
            assertTrue(page.getRecords().get(index - 1).id() > page.getRecords().get(index).id());
        }
    }

    @Test
    void minePageUsesOneUserLookupAndTwoPaginationStatementsAtAnySize() {
        var one = itemService.mine(new Page<Item>(1, 1), "owner_one");
        assertEquals(3, queryCounter.count());
        assertEquals("发布者一", one.getRecords().get(0).publisher().nickname());

        queryCounter.reset();
        var ten = itemService.mine(new Page<Item>(1, 10), "owner_one");
        assertEquals(3, queryCounter.count(), "mine must stay constant instead of adding publisher lookups");
        assertEquals(10, ten.getRecords().size());
        assertTrue(ten.getRecords().stream().allMatch(item -> item.publisher().id().equals(1L)));
    }

}
