package com.ssafy.S14P21A205.game.season.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonRankingsResponse;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTopRankingItemResponse;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTopRankingsResponse;
import com.ssafy.S14P21A205.game.season.entity.DailyReport;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonRankingRecord;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRankingRecordRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRankingRedisRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.service.UserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

class SeasonRankingServiceTests {

    private final SeasonRankingRedisRepository seasonRankingRedisRepository = mock(SeasonRankingRedisRepository.class);
    private final SeasonRepository seasonRepository = mock(SeasonRepository.class);
    private final SeasonRankingRecordRepository seasonRankingRecordRepository = mock(SeasonRankingRecordRepository.class);
    private final DailyReportRepository dailyReportRepository = mock(DailyReportRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final UserService userService = mock(UserService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-24T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final SeasonRankingService seasonRankingService = new SeasonRankingService(
            seasonRankingRedisRepository,
            seasonRepository,
            seasonRankingRecordRepository,
            dailyReportRepository,
            storeRepository,
            userService,
            clock
    );

    @Test
    void getCurrentTopRankingsReturnsTopTenFromRedis() {
        Integer myUserId = 3;
        Authentication authentication = authenticate(myUserId, "me");
        CurrentSeasonTopRankingsResponse cachedResponse = new CurrentSeasonTopRankingsResponse(
                3L,
                buildRankingItems(12),
                "2026-03-13T15:00:00"
        );

        when(seasonRankingRedisRepository.findCurrentTopRankings()).thenReturn(Optional.of(cachedResponse));

        CurrentSeasonTopRankingsResponse response = seasonRankingService.getCurrentTopRankings(authentication);

        assertEquals(3L, response.seasonId());
        assertEquals(10, response.rankings().size());
        assertEquals(1, response.rankings().get(0).rank());
        assertTrue(response.rankings().get(2).isMine());
        assertEquals(1, response.rankings().stream().filter(ranking -> Boolean.TRUE.equals(ranking.isMine())).count());
        assertEquals("2026-03-13T15:00:00", response.refreshedAt());
    }


    @Test
    void getCurrentTopRankingsMarksOnlyMatchingUserAsMineWhenNicknamesAreDuplicated() {
        Integer myUserId = 200;
        Authentication authentication = authenticate(myUserId, "same-nickname");
        CurrentSeasonTopRankingsResponse cachedResponse = new CurrentSeasonTopRankingsResponse(
                3L,
                List.of(
                        new CurrentSeasonTopRankingItemResponse(
                                1,
                                100,
                                "same-nickname",
                                "store-100",
                                new BigDecimal("30.0"),
                                300_000L,
                                30,
                                false
                        ),
                        new CurrentSeasonTopRankingItemResponse(
                                2,
                                200,
                                "same-nickname",
                                "store-200",
                                new BigDecimal("20.0"),
                                200_000L,
                                20,
                                false
                        )
                ),
                "2026-03-13T15:00:00"
        );

        when(seasonRankingRedisRepository.findCurrentTopRankings()).thenReturn(Optional.of(cachedResponse));

        CurrentSeasonTopRankingsResponse response = seasonRankingService.getCurrentTopRankings(authentication);

        assertFalse(response.rankings().get(0).isMine());
        assertTrue(response.rankings().get(1).isMine());
        assertEquals(1, response.rankings().stream().filter(ranking -> Boolean.TRUE.equals(ranking.isMine())).count());
    }

    @Test
    void getCurrentFinalRankingsReturnsTopTenAndAdditionalMyStores() {
        Integer myUserId = 200;
        Authentication authentication = authenticate(myUserId, "me");
        Season season = finishedSeason(12L);

        List<SeasonRankingRecord> finalizedRecords = new ArrayList<>();
        for (int rank = 1; rank <= 11; rank++) {
            finalizedRecords.add(createFinalizedRecord(
                    (long) rank,
                    rank,
                    rank,
                    "user-" + rank,
                    rank * 100_000,
                    100 - rank,
                    rank == 1 ? 30 : rank == 2 ? 20 : rank == 3 ? 10 : 5,
                    false
            ));
        }
        SeasonRankingRecord myRankedStore = createFinalizedRecord(1000L, 12, myUserId, "me", 50_000, 10, 5, false);
        SeasonRankingRecord myBankruptStore = createFinalizedRecord(1001L, 0, myUserId, "me", 0, 0, 0, true);
        finalizedRecords.add(myRankedStore);
        finalizedRecords.add(myBankruptStore);
        Store myBankruptStoreEntity = myBankruptStore.getStore();

        when(seasonRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeDescIdDesc(
                eq(SeasonStatus.IN_PROGRESS),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(seasonRepository.findByStatusAndStartTimeLessThanEqualAndEndTimeAfterOrderByEndTimeDescIdDesc(
                eq(SeasonStatus.FINISHED),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(season));
        when(seasonRankingRecordRepository.findByStore_Season_IdOrderByFinalRankAsc(12L)).thenReturn(finalizedRecords);
        when(dailyReportRepository.findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(12L, 8))
                .thenReturn(List.of(createDailyReport(myBankruptStoreEntity, 3, true)));

        CurrentSeasonRankingsResponse response = seasonRankingService.getCurrentFinalRankings(authentication);

        assertEquals(12L, response.seasonId());
        assertEquals(10, response.rankings().size());
        assertEquals(2, response.myRankings().size());
        assertEquals(12, response.myRankings().get(0).rank());
        assertFalse(response.myRankings().get(0).isBankrupt());
        assertNull(response.myRankings().get(1).rank());
        assertTrue(response.myRankings().get(1).isBankrupt());
        assertTrue(response.myRankings().stream().allMatch(ranking -> Boolean.TRUE.equals(ranking.isMine())));
        assertFalse(response.rankings().stream().anyMatch(ranking -> ranking.userId().equals(myUserId)));
        assertTrue(response.rankings().stream().noneMatch(ranking -> Boolean.TRUE.equals(ranking.isMine())));
    }

    @Test
    void getCurrentFinalRankingsIncludesAllTiedUsersWithinTopTenRanks() {
        Integer myUserId = 999;
        Authentication authentication = authenticate(myUserId, "me");
        Season season = finishedSeason(12L);

        List<SeasonRankingRecord> finalizedRecords = new ArrayList<>();
        for (int rank = 1; rank <= 7; rank++) {
            finalizedRecords.add(createFinalizedRecord(
                    (long) rank,
                    rank,
                    rank,
                    "user-" + rank,
                    rank * 100_000,
                    100 - rank,
                    rank == 1 ? 30 : rank == 2 ? 20 : rank == 3 ? 10 : 5,
                    false
            ));
        }
        for (int userId = 8; userId <= 12; userId++) {
            finalizedRecords.add(createFinalizedRecord(
                    100L + userId,
                    8,
                    userId,
                    "user-" + userId,
                    userId * 100_000,
                    92,
                    5,
                    false
            ));
        }
        finalizedRecords.add(createFinalizedRecord(999L, 13, myUserId, "me", 50_000, 50, 5, false));

        when(seasonRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeDescIdDesc(
                eq(SeasonStatus.IN_PROGRESS),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(seasonRepository.findByStatusAndStartTimeLessThanEqualAndEndTimeAfterOrderByEndTimeDescIdDesc(
                eq(SeasonStatus.FINISHED),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(season));
        when(seasonRankingRecordRepository.findByStore_Season_IdOrderByFinalRankAsc(12L)).thenReturn(finalizedRecords);
        when(dailyReportRepository.findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(12L, 8))
                .thenReturn(List.of());

        CurrentSeasonRankingsResponse response = seasonRankingService.getCurrentFinalRankings(authentication);

        assertEquals(12L, response.seasonId());
        assertEquals(12, response.rankings().size());
        assertEquals(8, response.rankings().get(7).rank());
        assertEquals(8, response.rankings().get(11).rank());
        assertTrue(response.rankings().stream().allMatch(ranking -> ranking.rank() != null && ranking.rank() <= 10));
        assertEquals(13, response.myRankings().get(0).rank());
        assertTrue(response.myRankings().get(0).isMine());
    }

    @Test
    void getCurrentFinalRankingsAppendsBankruptStoresAtBottomWhenTotalStoreCountIsBelowTen() {
        Integer myUserId = 77;
        Authentication authentication = authenticate(myUserId, "me");
        Season season = finishedSeason(15L);

        SeasonRankingRecord rankedFirst = createFinalizedRecord(1L, 1, 1, "alpha", 100_000, 120, 30, false);
        SeasonRankingRecord rankedSecond = createFinalizedRecord(2L, 2, 2, "beta", 90_000, 90, 20, false);
        SeasonRankingRecord rankedThird = createFinalizedRecord(3L, 2, 3, "gamma", 80_000, 90, 20, false);
        SeasonRankingRecord bankruptEarly = createFinalizedRecord(11L, 0, myUserId, "me", 0, 0, 0, true);
        SeasonRankingRecord bankruptLate = createFinalizedRecord(12L, 0, 88, "delta", 0, 0, 0, true);
        Store bankruptEarlyStore = bankruptEarly.getStore();
        Store bankruptLateStore = bankruptLate.getStore();

        when(seasonRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeDescIdDesc(
                eq(SeasonStatus.IN_PROGRESS),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(seasonRepository.findByStatusAndStartTimeLessThanEqualAndEndTimeAfterOrderByEndTimeDescIdDesc(
                eq(SeasonStatus.FINISHED),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(season));
        when(seasonRankingRecordRepository.findByStore_Season_IdOrderByFinalRankAsc(15L))
                .thenReturn(List.of(bankruptLate, rankedThird, rankedFirst, bankruptEarly, rankedSecond));
        when(dailyReportRepository.findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(15L, 8))
                .thenReturn(List.of(
                        createDailyReport(bankruptLateStore, 5, true),
                        createDailyReport(bankruptEarlyStore, 3, true)
                ));

        CurrentSeasonRankingsResponse response = seasonRankingService.getCurrentFinalRankings(authentication);

        assertEquals(5, response.rankings().size());
        assertEquals(1, response.rankings().get(0).rank());
        assertEquals(2, response.rankings().get(1).rank());
        assertEquals(2, response.rankings().get(2).rank());
        assertNull(response.rankings().get(3).rank());
        assertNull(response.rankings().get(4).rank());
        assertEquals("me-store", response.rankings().get(3).storeName());
        assertEquals("delta-store", response.rankings().get(4).storeName());
        assertTrue(response.rankings().get(3).isBankrupt());
        assertTrue(response.rankings().get(4).isBankrupt());
        assertTrue(response.rankings().get(3).isMine());
        assertFalse(response.rankings().get(4).isMine());
        assertTrue(response.myRankings().isEmpty());
    }

    @Test
    void getCurrentFinalRankingsReturnsCurrentSeasonWhenFinalRankingIsAlreadyPreparedBeforeFinished() {
        Integer myUserId = 200;
        Authentication authentication = authenticate(myUserId, "me");
        Season inProgressSeason = inProgressSeason(13L);
        SeasonRankingRecord rankedStore = createFinalizedRecord(1300L, 1, myUserId, "me", 120_000, 30, 30, false);

        when(seasonRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeDescIdDesc(
                eq(SeasonStatus.IN_PROGRESS),
                any(LocalDateTime.class)
        )).thenReturn(List.of(inProgressSeason));
        when(seasonRankingRecordRepository.findByStore_Season_IdOrderByFinalRankAsc(13L)).thenReturn(List.of(rankedStore));
        when(dailyReportRepository.findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(13L, 8))
                .thenReturn(List.of());

        CurrentSeasonRankingsResponse response = seasonRankingService.getCurrentFinalRankings(authentication);

        assertEquals(13L, response.seasonId());
        assertEquals(1, response.rankings().size());
        assertTrue(response.rankings().get(0).isMine());
        assertTrue(response.myRankings().isEmpty());
    }

    @Test
    void getCurrentFinalRankingsReturnsEmptyArraysWhenSeasonHasNoParticipants() {
        Integer myUserId = 200;
        Authentication authentication = authenticate(myUserId, "me");
        Season inProgressSeason = inProgressSeason(13L);

        when(seasonRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeDescIdDesc(
                eq(SeasonStatus.IN_PROGRESS),
                any(LocalDateTime.class)
        )).thenReturn(List.of(inProgressSeason));
        when(seasonRankingRecordRepository.findByStore_Season_IdOrderByFinalRankAsc(13L)).thenReturn(List.of());
        when(storeRepository.findAllBySeason_IdOrderByIdAsc(13L)).thenReturn(List.of());
        when(dailyReportRepository.findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(13L, 8))
                .thenReturn(List.of());

        CurrentSeasonRankingsResponse response = seasonRankingService.getCurrentFinalRankings(authentication);

        assertEquals(13L, response.seasonId());
        assertTrue(response.rankings().isEmpty());
        assertTrue(response.myRankings().isEmpty());
    }

    @Test
    void getCurrentFinalRankingsDoesNotFallBackToPreviousFinishedSeasonWhenCurrentSeasonIsNotReady() {
        Integer myUserId = 200;
        Authentication authentication = authenticate(myUserId, "me");
        Season inProgressSeason = inProgressSeason(13L);
        Season previousFinishedSeason = finishedSeason(12L);

        when(seasonRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeDescIdDesc(
                eq(SeasonStatus.IN_PROGRESS),
                any(LocalDateTime.class)
        )).thenReturn(List.of(inProgressSeason));
        when(seasonRepository.findByStatusAndStartTimeLessThanEqualAndEndTimeAfterOrderByEndTimeDescIdDesc(
                eq(SeasonStatus.FINISHED),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(previousFinishedSeason));
        when(seasonRankingRecordRepository.findByStore_Season_IdOrderByFinalRankAsc(13L)).thenReturn(List.of());
        when(storeRepository.findAllBySeason_IdOrderByIdAsc(13L)).thenReturn(List.of(createStore(
                13L,
                13,
                "user-13",
                "store-13",
                "location-13",
                "menu-13",
                10
        )));

        BaseException exception = assertThrows(
                BaseException.class,
                () -> seasonRankingService.getCurrentFinalRankings(authentication)
        );

        assertEquals(ErrorCode.FINAL_RANKING_NOT_READY, exception.getErrorCode());
        verify(seasonRankingRecordRepository, never()).findByStore_Season_IdOrderByFinalRankAsc(12L);
    }

    @Test
    void getCurrentTopRankingsReturnsEmptyWhenTopCacheDoesNotExist() {
        Authentication authentication = authenticate(77, "me");
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(3L);
        when(seasonRankingRedisRepository.findCurrentTopRankings()).thenReturn(Optional.empty());
        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)).thenReturn(Optional.of(season));

        CurrentSeasonTopRankingsResponse response = seasonRankingService.getCurrentTopRankings(authentication);

        assertEquals(3L, response.seasonId());
        assertTrue(response.rankings().isEmpty());
        assertNull(response.refreshedAt());
    }

    private Season finishedSeason(Long seasonId) {
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(seasonId);
        when(season.getStatus()).thenReturn(SeasonStatus.FINISHED);
        when(season.getTotalDays()).thenReturn(7);
        when(season.resolveRuntimePlayableDays()).thenReturn(7);
        return season;
    }

    private Season inProgressSeason(Long seasonId) {
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(seasonId);
        when(season.getStatus()).thenReturn(SeasonStatus.IN_PROGRESS);
        when(season.getTotalDays()).thenReturn(7);
        when(season.resolveRuntimePlayableDays()).thenReturn(7);
        return season;
    }

    private Authentication authenticate(Integer userId, String nickname) {
        Authentication authentication = mock(Authentication.class);
        when(userService.getCurrentUser(authentication)).thenReturn(createUser(userId, nickname));
        return authentication;
    }

    private List<CurrentSeasonTopRankingItemResponse> buildRankingItems(int count) {
        List<CurrentSeasonTopRankingItemResponse> rankings = new ArrayList<>();
        for (int rank = 1; rank <= count; rank++) {
            rankings.add(new CurrentSeasonTopRankingItemResponse(
                    rank,
                    rank,
                    "user-" + rank,
                    "store-" + rank,
                    BigDecimal.valueOf(100 - rank).setScale(1),
                    rank * 100000L,
                    rank == 1 ? 30 : rank == 2 ? 20 : rank == 3 ? 10 : 5,
                    false
            ));
        }
        return rankings;
    }

    private SeasonRankingRecord createFinalizedRecord(
            Long storeId,
            Integer finalRank,
            Integer userId,
            String nickname,
            int totalRevenue,
            float roi,
            int rewardPoints,
            boolean bankrupt
    ) {
        SeasonRankingRecord record = mock(SeasonRankingRecord.class);
        Store store = createStore(
                storeId,
                userId,
                nickname,
                nickname + "-store",
                "location-" + storeId,
                "menu-" + storeId,
                10
        );

        when(record.getFinalRank()).thenReturn(finalRank);
        when(record.getStore()).thenReturn(store);
        when(record.getRoi()).thenReturn(roi);
        when(record.getTotalRevenue()).thenReturn(totalRevenue);
        when(record.getRewardPoints()).thenReturn(rewardPoints);
        when(record.getIsBankruptcy()).thenReturn(bankrupt);
        return record;
    }

    private DailyReport createDailyReport(Store store, int day, boolean bankrupt) {
        DailyReport report = BeanUtils.instantiateClass(DailyReport.class);
        ReflectionTestUtils.setField(report, "store", store);
        ReflectionTestUtils.setField(report, "day", day);
        ReflectionTestUtils.setField(report, "isBankrupt", bankrupt);
        return report;
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
        User user = createUser(userId, nickname);

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

    private User createUser(Integer userId, String nickname) {
        User user = new User(userId + "@example.com", nickname);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
