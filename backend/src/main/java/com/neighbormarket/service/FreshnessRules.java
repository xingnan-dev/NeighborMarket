package com.neighbormarket.service;

import java.time.Duration;
import java.time.LocalDateTime;

public final class FreshnessRules {
    public static final Duration JUST_LISTED_WINDOW = Duration.ofHours(24);
    public static final Duration NEW_LISTING_WINDOW = Duration.ofHours(72);
    public static final Duration STALE_CONFIRMATION_THRESHOLD = Duration.ofDays(7);

    private FreshnessRules() {}

    public static String label(LocalDateTime publishedAt, LocalDateTime now) {
        Duration age = safeAge(publishedAt, now);
        if (age.compareTo(JUST_LISTED_WINDOW) < 0) return "刚上架";
        if (age.compareTo(NEW_LISTING_WINDOW) < 0) return "新上架";
        return "已上架" + Math.max(3, age.toDays()) + "天";
    }

    public static boolean isConfirmationStale(LocalDateTime confirmedAt, LocalDateTime now) {
        return safeAge(confirmedAt, now).compareTo(STALE_CONFIRMATION_THRESHOLD) > 0;
    }

    private static Duration safeAge(LocalDateTime start, LocalDateTime now) {
        Duration age = Duration.between(start, now);
        return age.isNegative() ? Duration.ZERO : age;
    }
}