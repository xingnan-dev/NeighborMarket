package com.neighbormarket.dashboard;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DashboardMapper {
    @Select("""
            SELECT COUNT(*)
            FROM item
            WHERE published_at >= #{start}
              AND published_at < #{end}
            """)
    long countPublishedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
            SELECT COUNT(*)
            FROM handover
            WHERE status = 'COMPLETED'
              AND completed_at >= #{start}
              AND completed_at < #{end}
            """)
    long countCompletedHandoversBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM item WHERE status = 'AVAILABLE'")
    long countAvailableItems();

    @Select("""
            SELECT i.id AS item_id,
                   i.title,
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
            """)
    FastestCompletedRow findFastestCompleted();

    @Select("""
            SELECT i.id AS item_id,
                   i.title,
                   i.status AS item_status,
                   ranked.interest_count
            FROM item i
            JOIN (
                SELECT ii.item_id,
                       COUNT(DISTINCT ii.user_id) AS interest_count
                FROM item_interest ii
                WHERE ii.status IN ('WAITING', 'CONFIRMED')
                GROUP BY ii.item_id
            ) ranked ON ranked.item_id = i.id
            WHERE i.status <> 'COMPLETED'
            ORDER BY ranked.interest_count DESC, i.published_at ASC, i.id ASC
            LIMIT 5
            """)
    List<MostWantedRow> findMostWantedUncompleted();

    class FastestCompletedRow {
        private Long itemId;
        private String title;
        private long durationSeconds;
        private LocalDateTime completedAt;

        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public long getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    }

    class MostWantedRow {
        private Long itemId;
        private String title;
        private long interestCount;
        private String itemStatus;

        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public long getInterestCount() { return interestCount; }
        public void setInterestCount(long interestCount) { this.interestCount = interestCount; }
        public String getItemStatus() { return itemStatus; }
        public void setItemStatus(String itemStatus) { this.itemStatus = itemStatus; }
    }
}
