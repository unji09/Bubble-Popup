package com.ssafy.S14P21A205.game.season.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ssafy.S14P21A205.game.day.policy.ProfitPolicy;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTopRankingsResponse;
import com.ssafy.S14P21A205.game.season.entity.DailyReport;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRankingRedisRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.user.entity.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class RealtimeSeasonRankingTickTaskTests {

    private final SeasonRepository seasonRepository = mock(SeasonRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final DailyReportRepository dailyReportRepository = mock(DailyReportRepository.class);
    private final GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository = mock(GameDayStoreStateRedisRepository.class);
    private final SeasonRankingRedisRepository seasonRankingRedisRepository = mock(SeasonRankingRedisRepository.class);
    private final ProfitPolicy profitPolicy = new ProfitPolicy();

    @Test
    void refreshCurrentTopRankingsCombinesPastDailyReportsAndRedisState() {
        RealtimeSeasonRankingTickTask scheduler = createScheduler(Clock.fixed(
                Instant.parse("2026-03-13T15:00:00Z"),
                ZoneId.of("UTC")
        ));
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(3L);
        when(season.getStatus()).thenReturn(SeasonStatus.IN_PROGRESS);
        when(season.getCurrentDay()).thenReturn(2);
        when(season.getTotalDays()).thenReturn(7);
        when(season.resolveRuntimePlayableDays()).thenReturn(7);

        Store firstStore = createStore(101L, 1, "alpha", "Alpha Store", "Gangnam", "Salt Bread", 20);
        Store secondStore = createStore(102L, 2, "beta", "Beta Store", "Hongdae", "Mara", 25);
        Store thirdStore = createStore(103L, 3, "gamma", "Gamma Store", "Seongsu", "Cookie", 20);

        Instant fixedInstant = Instant.parse("2026-03-13T15:00:00Z");
        LocalDateTime refreshedAt = LocalDateTime.ofInstant(fixedInstant, ZoneId.of("UTC"));

        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)).thenReturn(Optional.of(season));
        when(storeRepository.findBySeason_IdOrderByIdAsc(3L)).thenReturn(List.of(firstStore, secondStore, thirdStore));
        when(dailyReportRepository.findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(3L, 2)).thenReturn(List.of(
                createDailyReport(firstStore, 1, 100, 50),
                createDailyReport(secondStore, 1, 60, 40),
                createDailyReport(thirdStore, 1, 20, 20)
        ));
        when(gameDayStoreStateRedisRepository.find(firstStore.getId(), 2))
                .thenReturn(Optional.of(new GameDayLiveState(120L, 50L, 11, refreshedAt.minusSeconds(10))));
        when(gameDayStoreStateRedisRepository.find(secondStore.getId(), 2))
                .thenReturn(Optional.of(new GameDayLiveState(90L, 60L, 11, refreshedAt)));
        when(gameDayStoreStateRedisRepository.find(thirdStore.getId(), 2))
                .thenReturn(Optional.of(new GameDayLiveState(30L, 40L, 11, refreshedAt.minusSeconds(20))));

        scheduler.refreshCurrentTopRankings();

        ArgumentCaptor<CurrentSeasonTopRankingsResponse> captor = ArgumentCaptor.forClass(CurrentSeasonTopRankingsResponse.class);
        verify(seasonRankingRedisRepository).saveCurrentTopRankings(captor.capture());

        CurrentSeasonTopRankingsResponse saved = captor.getValue();
        assertEquals(3L, saved.seasonId());
        assertEquals("2026-03-13T15:00:00", saved.refreshedAt());
        assertEquals(3, saved.rankings().size());
        assertEquals(1, saved.rankings().get(0).userId());
        assertEquals(220L, saved.rankings().get(0).totalRevenue());
        assertEquals(new BigDecimal("120.0"), saved.rankings().get(0).roi());
        assertEquals(30, saved.rankings().get(0).rewardPoints());
        assertEquals(2, saved.rankings().get(1).userId());
        assertEquals(150L, saved.rankings().get(1).totalRevenue());
        assertEquals(new BigDecimal("50.0"), saved.rankings().get(1).roi());
        assertEquals(20, saved.rankings().get(1).rewardPoints());
    }

    @Test
    void refreshCurrentTopRankingsAssignsCompetitionRankWhenRoiIsTied() {
        RealtimeSeasonRankingTickTask scheduler = createScheduler(Clock.fixed(
                Instant.parse("2026-03-13T15:00:00Z"),
                ZoneId.of("UTC")
        ));
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(3L);
        when(season.getStatus()).thenReturn(SeasonStatus.IN_PROGRESS);
        when(season.getCurrentDay()).thenReturn(1);
        when(season.getTotalDays()).thenReturn(7);
        when(season.resolveRuntimePlayableDays()).thenReturn(7);

        Store firstStore = createStore(101L, 1, "alpha", "Alpha Store", "Gangnam", "Salt Bread", 20);
        Store secondStore = createStore(102L, 2, "beta", "Beta Store", "Hongdae", "Mara", 20);
        Store thirdStore = createStore(103L, 3, "gamma", "Gamma Store", "Seongsu", "Cookie", 25);

        Instant fixedInstant = Instant.parse("2026-03-13T15:00:00Z");
        LocalDateTime refreshedAt = LocalDateTime.ofInstant(fixedInstant, ZoneId.of("UTC"));

        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)).thenReturn(Optional.of(season));
        when(storeRepository.findBySeason_IdOrderByIdAsc(3L)).thenReturn(List.of(firstStore, secondStore, thirdStore));
        when(dailyReportRepository.findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(3L, 1)).thenReturn(List.of());
        when(gameDayStoreStateRedisRepository.find(firstStore.getId(), 1))
                .thenReturn(Optional.of(new GameDayLiveState(120L, 50L, 10, refreshedAt.minusSeconds(10))));
        when(gameDayStoreStateRedisRepository.find(secondStore.getId(), 1))
                .thenReturn(Optional.of(new GameDayLiveState(120L, 50L, 10, refreshedAt)));
        when(gameDayStoreStateRedisRepository.find(thirdStore.getId(), 1))
                .thenReturn(Optional.of(new GameDayLiveState(90L, 60L, 10, refreshedAt.minusSeconds(20))));

        scheduler.refreshCurrentTopRankings();

        ArgumentCaptor<CurrentSeasonTopRankingsResponse> captor = ArgumentCaptor.forClass(CurrentSeasonTopRankingsResponse.class);
        verify(seasonRankingRedisRepository).saveCurrentTopRankings(captor.capture());

        CurrentSeasonTopRankingsResponse saved = captor.getValue();
        assertEquals(1, saved.rankings().get(0).rank());
        assertEquals(1, saved.rankings().get(1).rank());
        assertEquals(3, saved.rankings().get(2).rank());
        assertEquals(new BigDecimal("140.0"), saved.rankings().get(0).roi());
        assertEquals(new BigDecimal("140.0"), saved.rankings().get(1).roi());
        assertEquals(30, saved.rankings().get(0).rewardPoints());
        assertEquals(30, saved.rankings().get(1).rewardPoints());
        assertEquals(10, saved.rankings().get(2).rewardPoints());
    }

    @Test
    void refreshCurrentTopRankingsDeletesRedisWhenCurrentSeasonDoesNotExist() {
        RealtimeSeasonRankingTickTask scheduler = createScheduler(Clock.system(ZoneId.of("Asia/Seoul")));
        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)).thenReturn(Optional.empty());

        scheduler.refreshCurrentTopRankings();

        verify(seasonRankingRedisRepository).deleteCurrentTopRankings();
        verify(seasonRankingRedisRepository, never()).saveCurrentTopRankings(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshCurrentTopRankingsAggregatesMultiplePastDailyReportsIntoPastStats() {
        RealtimeSeasonRankingTickTask scheduler = createScheduler(Clock.system(ZoneId.of("Asia/Seoul")));
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(5L);
        when(season.getStatus()).thenReturn(SeasonStatus.IN_PROGRESS);
        when(season.getCurrentDay()).thenReturn(3);
        when(season.getTotalDays()).thenReturn(7);
        when(season.resolveRuntimePlayableDays()).thenReturn(7);

        Store store = createStore(201L, 11, "delta", "Delta Store", "Jamsil", "Bagel", 30);
        LocalDateTime liveTickAt = LocalDateTime.of(2026, 3, 14, 11, 0);

        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)).thenReturn(Optional.of(season));
        when(storeRepository.findBySeason_IdOrderByIdAsc(5L)).thenReturn(List.of(store));
        when(dailyReportRepository.findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(5L, 3)).thenReturn(List.of(
                createDailyReport(store, 1, 100, 50),
                createDailyReport(store, 2, 80, 40)
        ));
        when(gameDayStoreStateRedisRepository.find(store.getId(), 3))
                .thenReturn(Optional.of(new GameDayLiveState(20L, 10L, 33, liveTickAt)));

        ListAppender<ILoggingEvent> listAppender = attachLogAppender();
        try {
            scheduler.refreshCurrentTopRankings();
        } finally {
            detachLogAppender(listAppender);
        }

        ArgumentCaptor<CurrentSeasonTopRankingsResponse> captor = ArgumentCaptor.forClass(CurrentSeasonTopRankingsResponse.class);
        verify(seasonRankingRedisRepository).saveCurrentTopRankings(captor.capture());

        CurrentSeasonTopRankingsResponse saved = captor.getValue();
        assertEquals(5L, saved.seasonId());
        assertEquals("2026-03-14T11:00:00", saved.refreshedAt());
        assertEquals(1, saved.rankings().size());
        assertEquals(200L, saved.rankings().get(0).totalRevenue());
        assertEquals(new BigDecimal("100.0"), saved.rankings().get(0).roi());

        assertTrue(
                listAppender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .anyMatch(message -> message.contains("revenue=200") && message.contains("cost=100") && message.contains("roi=100.0"))
        );
    }

    @Test
    void refreshCurrentTopRankingsReflectsSequentialRedisCumulativeValuesAcrossTicks() {
        RealtimeSeasonRankingTickTask scheduler = createScheduler(Clock.system(ZoneId.of("Asia/Seoul")));
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(6L);
        when(season.getStatus()).thenReturn(SeasonStatus.IN_PROGRESS);
        when(season.getCurrentDay()).thenReturn(2);
        when(season.getTotalDays()).thenReturn(7);
        when(season.resolveRuntimePlayableDays()).thenReturn(7);

        Store store = createStore(301L, 21, "echo", "Echo Store", "Yeonnam", "Croffle", 35);

        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)).thenReturn(Optional.of(season));
        when(storeRepository.findBySeason_IdOrderByIdAsc(6L)).thenReturn(List.of(store));
        when(dailyReportRepository.findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(6L, 2)).thenReturn(List.of(
                createDailyReport(store, 1, 100, 50)
        ));
        when(gameDayStoreStateRedisRepository.find(store.getId(), 2)).thenReturn(
                Optional.of(new GameDayLiveState(20L, 10L, 11, LocalDateTime.of(2026, 3, 14, 11, 0))),
                Optional.of(new GameDayLiveState(50L, 20L, 12, LocalDateTime.of(2026, 3, 14, 12, 0))),
                Optional.of(new GameDayLiveState(90L, 40L, 13, LocalDateTime.of(2026, 3, 14, 13, 0)))
        );

        ListAppender<ILoggingEvent> listAppender = attachLogAppender();
        try {
            scheduler.refreshCurrentTopRankings();
            scheduler.refreshCurrentTopRankings();
            scheduler.refreshCurrentTopRankings();
        } finally {
            detachLogAppender(listAppender);
        }

        ArgumentCaptor<CurrentSeasonTopRankingsResponse> captor = ArgumentCaptor.forClass(CurrentSeasonTopRankingsResponse.class);
        verify(seasonRankingRedisRepository, times(3)).saveCurrentTopRankings(captor.capture());

        List<CurrentSeasonTopRankingsResponse> savedResponses = captor.getAllValues();
        assertEquals(3, savedResponses.size());

        assertRanking(savedResponses.get(0), "2026-03-14T11:00:00", 120L, "100.0");
        assertRanking(savedResponses.get(1), "2026-03-14T12:00:00", 150L, "114.3");
        assertRanking(savedResponses.get(2), "2026-03-14T13:00:00", 190L, "111.1");

        List<String> messages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("Realtime season rankings refreshed."))
                .toList();
        assertEquals(3, messages.size());
        assertTrue(messages.get(0).contains("revenue=120") && messages.get(0).contains("cost=60") && messages.get(0).contains("roi=100.0"));
        assertTrue(messages.get(1).contains("revenue=150") && messages.get(1).contains("cost=70") && messages.get(1).contains("roi=114.3"));
        assertTrue(messages.get(2).contains("revenue=190") && messages.get(2).contains("cost=90") && messages.get(2).contains("roi=111.1"));
    }

    private RealtimeSeasonRankingTickTask createScheduler(Clock clock) {
        return new RealtimeSeasonRankingTickTask(
                seasonRepository,
                storeRepository,
                dailyReportRepository,
                gameDayStoreStateRedisRepository,
                seasonRankingRedisRepository,
                profitPolicy,
                clock
        );
    }

    private Store createStore(
            Long storeId,
            Integer userId,
            String nickname,
            String storeName,
            String locationName,
            String menuName,
            Integer originPrice
    ) {
        User user = new User(userId + "@example.com", nickname);
        ReflectionTestUtils.setField(user, "id", userId);

        Location location = BeanUtils.instantiateClass(Location.class);
        ReflectionTestUtils.setField(location, "id", storeId + 1);
        ReflectionTestUtils.setField(location, "locationName", locationName);
        ReflectionTestUtils.setField(location, "rent", 1000);

        Menu menu = BeanUtils.instantiateClass(Menu.class);
        ReflectionTestUtils.setField(menu, "id", storeId + 10);
        ReflectionTestUtils.setField(menu, "menuName", menuName);
        ReflectionTestUtils.setField(menu, "originPrice", originPrice);

        Store store = BeanUtils.instantiateClass(Store.class);
        ReflectionTestUtils.setField(store, "id", storeId);
        ReflectionTestUtils.setField(store, "user", user);
        ReflectionTestUtils.setField(store, "location", location);
        ReflectionTestUtils.setField(store, "menu", menu);
        ReflectionTestUtils.setField(store, "storeName", storeName);
        ReflectionTestUtils.setField(store, "price", originPrice * 2);
        return store;
    }

    private DailyReport createDailyReport(Store store, Integer day, Integer revenue, Integer totalCost) {
        DailyReport dailyReport = BeanUtils.instantiateClass(DailyReport.class);
        ReflectionTestUtils.setField(dailyReport, "store", store);
        ReflectionTestUtils.setField(dailyReport, "day", day);
        ReflectionTestUtils.setField(dailyReport, "revenue", revenue);
        ReflectionTestUtils.setField(dailyReport, "totalCost", totalCost);
        return dailyReport;
    }

    private void assertRanking(
            CurrentSeasonTopRankingsResponse response,
            String refreshedAt,
            long totalRevenue,
            String roi
    ) {
        assertEquals(refreshedAt, response.refreshedAt());
        assertEquals(1, response.rankings().size());
        assertEquals(totalRevenue, response.rankings().get(0).totalRevenue());
        assertEquals(new BigDecimal(roi), response.rankings().get(0).roi());
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(RealtimeSeasonRankingTickTask.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        return listAppender;
    }

    private void detachLogAppender(ListAppender<ILoggingEvent> listAppender) {
        Logger logger = (Logger) LoggerFactory.getLogger(RealtimeSeasonRankingTickTask.class);
        logger.detachAppender(listAppender);
        listAppender.stop();
    }
}


