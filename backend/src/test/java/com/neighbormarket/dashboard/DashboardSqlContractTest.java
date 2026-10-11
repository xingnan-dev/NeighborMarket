package com.neighbormarket.dashboard;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardSqlContractTest {
    @Test
    void fastestUsesCompletedHandoverDurationAndStableTieBreakers() throws Exception {
        String sql = sql("findFastestCompleted");

        assertTrue(sql.contains("timestampdiff(second, i.published_at, h.completed_at)"));
        assertTrue(sql.contains("h.status = 'completed'"));
        assertTrue(sql.contains("i.status = 'completed'"));
        assertTrue(sql.contains("order by duration_seconds asc, h.completed_at asc, i.id asc"));
    }

    @Test
    void wantedRankingDeduplicatesUsersAndExcludesCompletedItems() throws Exception {
        String sql = sql("findMostWantedUncompleted");

        assertTrue(sql.contains("count(distinct ii.user_id)"));
        assertTrue(sql.contains("ii.status in ('waiting', 'confirmed')"));
        assertTrue(sql.contains("group by ii.item_id"));
        assertTrue(sql.contains("ranked on ranked.item_id = i.id"));
        assertTrue(sql.contains("i.status <> 'completed'"));
    }

    @Test
    void wantedRankingHasStableOrderingForTies() throws Exception {
        String sql = sql("findMostWantedUncompleted");

        assertTrue(sql.contains("order by ranked.interest_count desc, i.published_at asc, i.id asc"));
    }

    private String sql(String methodName) throws Exception {
        Method method = java.util.Arrays.stream(DashboardMapper.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
