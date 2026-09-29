package com.neighbormarket.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardDto(
        long publishedThisMonth,
        long completedThisMonth,
        long availableNow,
        FastestCompletedItem fastestCompleted,
        List<MostWantedItem> mostWanted,
        String timeZone
) {
    public record FastestCompletedItem(
            Long itemId,
            String title,
            long durationSeconds,
            LocalDateTime completedAt
    ) {}

    public record MostWantedItem(
            Long itemId,
            String title,
            long interestCount,
            String itemStatus
    ) {}
}
