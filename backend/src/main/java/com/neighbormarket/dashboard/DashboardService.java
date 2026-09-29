package com.neighbormarket.dashboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Service
public class DashboardService {
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

    private final DashboardMapper mapper;
    private final Clock clock;

    @Autowired
    public DashboardService(DashboardMapper mapper) {
        this(mapper, Clock.system(DISPLAY_ZONE));
    }

    DashboardService(DashboardMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public DashboardDto dashboard() {
        YearMonth month = YearMonth.from(clock.instant().atZone(DISPLAY_ZONE));
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();

        DashboardMapper.FastestCompletedRow fastestRow = mapper.findFastestCompleted();
        DashboardDto.FastestCompletedItem fastest = fastestRow == null ? null
                : new DashboardDto.FastestCompletedItem(
                        fastestRow.getItemId(),
                        fastestRow.getTitle(),
                        fastestRow.getDurationSeconds(),
                        fastestRow.getCompletedAt());

        List<DashboardDto.MostWantedItem> wanted = mapper.findMostWantedUncompleted().stream()
                .map(row -> new DashboardDto.MostWantedItem(
                        row.getItemId(),
                        row.getTitle(),
                        row.getInterestCount(),
                        row.getItemStatus()))
                .toList();

        return new DashboardDto(
                mapper.countPublishedBetween(start, end),
                mapper.countCompletedHandoversBetween(start, end),
                mapper.countAvailableItems(),
                fastest,
                wanted,
                DISPLAY_ZONE.getId());
    }
}
