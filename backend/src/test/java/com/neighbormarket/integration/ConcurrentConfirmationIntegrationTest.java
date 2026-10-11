package com.neighbormarket.integration;

import com.neighbormarket.service.ExchangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers(disabledWithoutDocker = true)
class ConcurrentConfirmationIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("neighbor_market_concurrency_test")
            .withUsername("neighbor_test")
            .withPassword("test-only-password")
            .withCommand("--log-bin-trust-function-creators=1");

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

    @BeforeEach
    void seedTwoWaitingInterests() {
        dropFailureTriggers();
        cleanBusinessData();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES (?,?,?)",
                "concurrent_owner", "test-hash", "发布者");
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES (?,?,?)",
                "concurrent_recipient_a", "test-hash", "领取人甲");
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES (?,?,?)",
                "concurrent_recipient_b", "test-hash", "领取人乙");
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES (?,?,?)",
                "concurrent_applicant", "test-hash", "重复申请人");
        long ownerId = userId("concurrent_owner");
        long recipientA = userId("concurrent_recipient_a");
        long recipientB = userId("concurrent_recipient_b");
        jdbc.update("""
                INSERT INTO item(publisher_id,title,description,category,trade_type,price,image_url,status,last_confirmed_at,pickup_location)
                VALUES (?, '并发确认测试物品', '仅存在于隔离测试库', '其他', 'FREE', 0, '/test.jpg', 'AVAILABLE', NOW(), '测试门口')
                """, ownerId);
        long itemId = jdbc.queryForObject(
                "SELECT id FROM item WHERE title = '并发确认测试物品'", Long.class);
        jdbc.update("""
                INSERT INTO pickup_slot(item_id,start_at,end_at,active)
                VALUES (?, DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY) + INTERVAL 1 HOUR, TRUE)
                """, itemId);
        long slotId = jdbc.queryForObject(
                "SELECT id FROM pickup_slot WHERE item_id = ? AND active = TRUE", Long.class, itemId);
        jdbc.update("INSERT INTO item_interest(item_id,user_id,slot_id,status) VALUES (?,?,?,'WAITING')",
                itemId, recipientA, slotId);
        jdbc.update("INSERT INTO item_interest(item_id,user_id,slot_id,status) VALUES (?,?,?,'WAITING')",
                itemId, recipientB, slotId);
    }

    @AfterEach
    void removeFailureTriggers() {
        dropFailureTriggers();
    }

    @Test
    void confirmationRollsBackItemReservationWhenHandoverInsertFails() {
        long interestId = firstInterestId();
        jdbc.execute("""
                CREATE TRIGGER test_fail_handover_insert
                BEFORE INSERT ON handover
                FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected handover insert failure'
                """);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> exchangeService.confirm(interestId, "concurrent_owner"));
        assertTrue(hasCauseMessage(failure, "injected handover insert failure"));

        assertUnconfirmedState(interestId);
    }

    @Test
    void confirmationRollsBackHandoverInsertWhenItemPointerUpdateFails() {
        long interestId = firstInterestId();
        jdbc.execute("""
                CREATE TRIGGER test_fail_item_pointer_update
                BEFORE UPDATE ON item
                FOR EACH ROW
                BEGIN
                    IF NEW.current_handover_id IS NOT NULL AND OLD.current_handover_id IS NULL THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected item pointer failure';
                    END IF;
                END
                """);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> exchangeService.confirm(interestId, "concurrent_owner"));
        assertTrue(hasCauseMessage(failure, "injected item pointer failure"));

        assertUnconfirmedState(interestId);
    }

    @Test
    void cancellationRollsBackHandoverAndItemWhenInterestUpdateFails() {
        long interestId = firstInterestId();
        exchangeService.confirm(interestId, "concurrent_owner");
        long handoverId = jdbc.queryForObject("SELECT id FROM handover", Long.class);
        jdbc.execute("""
                CREATE TRIGGER test_fail_interest_cancel
                BEFORE UPDATE ON item_interest
                FOR EACH ROW
                BEGIN
                    IF NEW.status = 'CANCELLED' AND OLD.status = 'CONFIRMED' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected interest cancellation failure';
                    END IF;
                END
                """);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> exchangeService.cancelHandover(handoverId, "concurrent_recipient_a"));
        assertTrue(hasCauseMessage(failure, "injected interest cancellation failure"));

        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM handover WHERE id = ?", String.class, handoverId));
        assertNull(jdbc.queryForObject(
                "SELECT cancelled_at FROM handover WHERE id = ?", Object.class, handoverId));
        assertEquals("RESERVED", itemStatus());
        assertEquals(handoverId, currentHandoverId());
        assertEquals("CONFIRMED", interestStatus(interestId));
    }

    @Test
    void completionRollsBackHandoverWhenItemCompletionFails() {
        long interestId = firstInterestId();
        exchangeService.confirm(interestId, "concurrent_owner");
        long handoverId = jdbc.queryForObject("SELECT id FROM handover", Long.class);
        jdbc.execute("""
                CREATE TRIGGER test_fail_item_completion
                BEFORE UPDATE ON item
                FOR EACH ROW
                BEGIN
                    IF NEW.status = 'COMPLETED' AND OLD.status = 'RESERVED' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected item completion failure';
                    END IF;
                END
                """);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> exchangeService.complete(handoverId, "concurrent_owner"));
        assertTrue(hasCauseMessage(failure, "injected item completion failure"));

        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM handover WHERE id = ?", String.class, handoverId));
        assertNull(jdbc.queryForObject(
                "SELECT completed_at FROM handover WHERE id = ?", Object.class, handoverId));
        assertEquals("RESERVED", itemStatus());
        assertEquals(handoverId, currentHandoverId());
        assertEquals("CONFIRMED", interestStatus(interestId));
    }

    @RepeatedTest(5)
    void concurrentFirstApplicationsAreIdempotentAndReturnTheSameInterest() throws Exception {
        long itemId = jdbc.queryForObject(
                "SELECT id FROM item WHERE title = '并发确认测试物品'", Long.class);
        long slotId = jdbc.queryForObject(
                "SELECT id FROM pickup_slot WHERE item_id = ? AND active = TRUE", Long.class, itemId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            Future<Long> first = executor.submit(() -> wantAfterBarrier(itemId, slotId, ready, start));
            Future<Long> second = executor.submit(() -> wantAfterBarrier(itemId, slotId, ready, start));
            assertEquals(true, ready.await(10, TimeUnit.SECONDS), "both requests should reach the start barrier");
            start.countDown();

            long firstId = first.get(10, TimeUnit.SECONDS);
            long secondId = second.get(10, TimeUnit.SECONDS);
            assertEquals(firstId, secondId);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM item_interest ii
                JOIN app_user u ON u.id = ii.user_id
                WHERE u.username = 'concurrent_applicant'
                  AND ii.status = 'WAITING'
                """));
    }

    @Test
    void retryCannotOverwriteConfirmedInterest() {
        long itemId = jdbc.queryForObject(
                "SELECT id FROM item WHERE title = '并发确认测试物品'", Long.class);
        long slotId = jdbc.queryForObject(
                "SELECT id FROM pickup_slot WHERE item_id = ? AND active = TRUE", Long.class, itemId);
        long applicantId = userId("concurrent_applicant");
        jdbc.update("INSERT INTO item_interest(item_id,user_id,slot_id,status) VALUES (?,?,?,'CONFIRMED')",
                itemId, applicantId, slotId);
        long interestId = jdbc.queryForObject(
                "SELECT id FROM item_interest WHERE item_id = ? AND user_id = ?", Long.class, itemId, applicantId);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> exchangeService.want(itemId, slotId, "concurrent_applicant"));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        assertEquals("CONFIRMED", jdbc.queryForObject(
                "SELECT status FROM item_interest WHERE id = ?", String.class, interestId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_interest WHERE id = ?", Integer.class, interestId));
    }

    @Test
    void concurrentCancellationAndCompletionEndInOneLegalTerminalState() throws Exception {
        long winningInterestId = jdbc.queryForObject(
                "SELECT id FROM item_interest ORDER BY id LIMIT 1", Long.class);
        exchangeService.confirm(winningInterestId, "concurrent_owner");
        long handoverId = jdbc.queryForObject("SELECT id FROM handover", Long.class);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            Future<TerminalOutcome> cancellation = executor.submit(
                    () -> cancelAfterBarrier(handoverId, ready, start));
            Future<TerminalOutcome> completion = executor.submit(
                    () -> completeAfterBarrier(handoverId, ready, start));
            assertEquals(true, ready.await(10, TimeUnit.SECONDS), "both requests should reach the start barrier");
            start.countDown();

            List<TerminalOutcome> outcomes = List.of(
                    cancellation.get(10, TimeUnit.SECONDS), completion.get(10, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(TerminalOutcome::successful).count());
            assertEquals(1, outcomes.stream().filter(outcome -> !outcome.successful()).count());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        String handoverStatus = jdbc.queryForObject(
                "SELECT status FROM handover WHERE id = ?", String.class, handoverId);
        String itemStatus = jdbc.queryForObject(
                "SELECT status FROM item WHERE title = '并发确认测试物品'", String.class);
        Long currentHandoverId = jdbc.queryForObject(
                "SELECT current_handover_id FROM item WHERE title = '并发确认测试物品'", Long.class);
        String winningInterestStatus = jdbc.queryForObject(
                "SELECT status FROM item_interest WHERE id = ?", String.class, winningInterestId);

        if ("CANCELLED".equals(handoverStatus)) {
            assertEquals("AVAILABLE", itemStatus);
            assertNull(currentHandoverId);
            assertEquals("CANCELLED", winningInterestStatus);
            assertEquals(1, count("SELECT COUNT(*) FROM handover WHERE cancelled_at IS NOT NULL AND completed_at IS NULL"));
        } else {
            assertEquals("COMPLETED", handoverStatus);
            assertEquals("COMPLETED", itemStatus);
            assertEquals(handoverId, currentHandoverId);
            assertEquals("CONFIRMED", winningInterestStatus);
            assertEquals(1, count("SELECT COUNT(*) FROM handover WHERE completed_at IS NOT NULL AND cancelled_at IS NULL"));
        }
        assertEquals(0, count("SELECT COUNT(*) FROM handover WHERE status = 'ACTIVE'"));
        assertEquals(1, count("SELECT COUNT(*) FROM item_interest WHERE status = 'WAITING'"));
    }

    @Test
    void twoConcurrentConfirmationsCreateExactlyOneConsistentHandover() throws Exception {
        List<Long> interestIds = jdbc.queryForList(
                "SELECT id FROM item_interest ORDER BY id", Long.class);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            Future<Outcome> first = executor.submit(
                    () -> confirmAfterBarrier(interestIds.get(0), ready, start));
            Future<Outcome> second = executor.submit(
                    () -> confirmAfterBarrier(interestIds.get(1), ready, start));
            assertEquals(true, ready.await(10, TimeUnit.SECONDS), "both requests should reach the start barrier");
            start.countDown();

            List<Outcome> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(outcome -> outcome == Outcome.SUCCESS).count());
            assertEquals(1, outcomes.stream().filter(outcome -> outcome == Outcome.CONFLICT).count());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertEquals(1, count("SELECT COUNT(*) FROM handover"));
        assertEquals(1, count("SELECT COUNT(*) FROM handover WHERE status = 'ACTIVE'"));
        assertEquals(1, count("SELECT COUNT(*) FROM item_interest WHERE status = 'CONFIRMED'"));
        assertEquals(1, count("SELECT COUNT(*) FROM item_interest WHERE status = 'WAITING'"));
        assertEquals(1, count("""
                SELECT COUNT(*)
                FROM item i
                JOIN handover h ON h.id = i.current_handover_id
                JOIN item_interest ii ON ii.id = h.interest_id
                WHERE i.status = 'RESERVED'
                  AND h.status = 'ACTIVE'
                  AND ii.status = 'CONFIRMED'
                  AND h.item_id = i.id
                  AND ii.item_id = i.id
                  AND h.recipient_id = ii.user_id
                """));
        assertNotNull(jdbc.queryForObject(
                "SELECT current_handover_id FROM item WHERE title = '并发确认测试物品'", Long.class));
    }

    private Outcome confirmAfterBarrier(long interestId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            exchangeService.confirm(interestId, "concurrent_owner");
            return Outcome.SUCCESS;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) return Outcome.CONFLICT;
            throw exception;
        }
    }

    private long wantAfterBarrier(long itemId, long slotId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return exchangeService.want(itemId, slotId, "concurrent_applicant").id();
    }

    private TerminalOutcome cancelAfterBarrier(long handoverId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            exchangeService.cancelHandover(handoverId, "concurrent_recipient_a");
            return new TerminalOutcome(true);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) return new TerminalOutcome(false);
            throw exception;
        }
    }

    private TerminalOutcome completeAfterBarrier(long handoverId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            exchangeService.complete(handoverId, "concurrent_owner");
            return new TerminalOutcome(true);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) return new TerminalOutcome(false);
            throw exception;
        }
    }

    private void cleanBusinessData() {
        jdbc.update("DELETE FROM item_comment");
        jdbc.update("UPDATE item SET current_handover_id = NULL WHERE current_handover_id IS NOT NULL");
        jdbc.update("DELETE FROM handover");
        jdbc.update("DELETE FROM item_interest");
        jdbc.update("DELETE FROM pickup_slot");
        jdbc.update("DELETE FROM item");
        jdbc.update("DELETE FROM app_user");
    }

    private void dropFailureTriggers() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_handover_insert");
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_item_pointer_update");
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_interest_cancel");
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_item_completion");
    }

    private long firstInterestId() {
        return jdbc.queryForObject("SELECT id FROM item_interest ORDER BY id LIMIT 1", Long.class);
    }

    private void assertUnconfirmedState(long interestId) {
        assertEquals("AVAILABLE", itemStatus());
        assertNull(currentHandoverId());
        assertEquals(0, count("SELECT COUNT(*) FROM handover"));
        assertEquals("WAITING", interestStatus(interestId));
    }

    private String itemStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM item WHERE title = '并发确认测试物品'", String.class);
    }

    private Long currentHandoverId() {
        return jdbc.queryForObject(
                "SELECT current_handover_id FROM item WHERE title = '并发确认测试物品'", Long.class);
    }

    private String interestStatus(long interestId) {
        return jdbc.queryForObject(
                "SELECT status FROM item_interest WHERE id = ?", String.class, interestId);
    }

    private boolean hasCauseMessage(Throwable failure, String expected) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) return true;
        }
        return false;
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private enum Outcome { SUCCESS, CONFLICT }

    private record TerminalOutcome(boolean successful) {}
}
