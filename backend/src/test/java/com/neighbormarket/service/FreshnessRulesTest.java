package com.neighbormarket.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class FreshnessRulesTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 12, 0);

    @Test void freshnessBoundariesAreExact() {
        assertEquals("刚上架", FreshnessRules.label(NOW.minusHours(24).plusNanos(1), NOW));
        assertEquals("新上架", FreshnessRules.label(NOW.minusHours(24), NOW));
        assertEquals("新上架", FreshnessRules.label(NOW.minusHours(72).plusNanos(1), NOW));
        assertEquals("已上架3天", FreshnessRules.label(NOW.minusHours(72), NOW));
    }

    @Test void staleWarningStartsAfterSevenFullDays() {
        assertFalse(FreshnessRules.isConfirmationStale(NOW.minusDays(7), NOW));
        assertTrue(FreshnessRules.isConfirmationStale(NOW.minusDays(7).minusNanos(1), NOW));
    }
}