package com.neighbormarket.dashboard;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {
    @Test
    void emptyDatabaseReturnsZerosAndEmptyResults() {
        DashboardMapper mapper = mock(DashboardMapper.class);
        when(mapper.findMostWantedUncompleted()).thenReturn(List.of());

        DashboardDto result = service(mapper).dashboard();

        assertEquals(0, result.publishedThisMonth());
        assertEquals(0, result.completedThisMonth());
        assertEquals(0, result.availableNow());
        assertNull(result.fastestCompleted());
        assertTrue(result.mostWanted().isEmpty());
    }

    @Test
    void monthBoundaryUsesShanghaiNaturalMonthAndExclusiveNextMonth() {
        DashboardMapper mapper = mock(DashboardMapper.class);
        when(mapper.findMostWantedUncompleted()).thenReturn(List.of());
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T16:00:00Z"), ZoneOffset.UTC);

        new DashboardService(mapper, clock).dashboard();

        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).countPublishedBetween(start.capture(), end.capture());
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), start.getValue());
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), end.getValue());
        verify(mapper).countCompletedHandoversBetween(start.getValue(), end.getValue());
    }

    @Test
    void returnsAvailableCountAndAggregatedRankingRows() {
        DashboardMapper mapper = mock(DashboardMapper.class);
        when(mapper.countAvailableItems()).thenReturn(7L);
        DashboardMapper.FastestCompletedRow fastest = new DashboardMapper.FastestCompletedRow();
        fastest.setItemId(8L);
        fastest.setTitle("落地灯");
        fastest.setDurationSeconds(5400);
        fastest.setCompletedAt(LocalDateTime.of(2026, 9, 20, 18, 0));
        when(mapper.findFastestCompleted()).thenReturn(fastest);
        DashboardMapper.MostWantedRow wanted = new DashboardMapper.MostWantedRow();
        wanted.setItemId(9L);
        wanted.setTitle("工具箱");
        wanted.setInterestCount(4);
        wanted.setItemStatus("AVAILABLE");
        when(mapper.findMostWantedUncompleted()).thenReturn(List.of(wanted));

        DashboardDto result = service(mapper).dashboard();

        assertEquals(7, result.availableNow());
        assertEquals(5400, result.fastestCompleted().durationSeconds());
        assertEquals(4, result.mostWanted().get(0).interestCount());
        assertEquals("Asia/Shanghai", result.timeZone());
    }

    private DashboardService service(DashboardMapper mapper) {
        return new DashboardService(
                mapper,
                Clock.fixed(Instant.parse("2026-09-15T04:00:00Z"), ZoneOffset.UTC));
    }
}
