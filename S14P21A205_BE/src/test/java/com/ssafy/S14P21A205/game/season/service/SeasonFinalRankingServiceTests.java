package com.ssafy.S14P21A205.game.season.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.game.day.policy.ProfitPolicy;
import com.ssafy.S14P21A205.game.day.service.GameDayReportService;
import com.ssafy.S14P21A205.game.season.entity.DailyReport;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonRankingRecord;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRankingRecordRepository;
import com.ssafy.S14P21A205.shop.service.ShopService;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class SeasonFinalRankingServiceTests {

    private final StoreRepository storeRepository = org.mockito.Mockito.mock(StoreRepository.class);
    private final DailyReportRepository dailyReportRepository = org.mockito.Mockito.mock(DailyReportRepository.class);
    private final SeasonRankingRecordRepository seasonRankingRecordRepository = org.mockito.Mockito.mock(SeasonRankingRecordRepository.class);
    private final GameDayReportService gameDayReportService = org.mockito.Mockito.mock(GameDayReportService.class);
    private final ProfitPolicy profitPolicy = new ProfitPolicy();
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final ShopService shopService = org.mockito.Mockito.mock(ShopService.class);

    private final SeasonFinalRankingService seasonFinalRankingService = new SeasonFinalRankingService(
            storeRepository,
            dailyReportRepository,
            seasonRankingRecordRepository,
            gameDayReportService,
            profitPolicy,
            userRepository,
            shopService
    );

    @Test
    @SuppressWarnings("unchecked")
    void saveFinalRankingsPersistsRankedStoresSeparatelyAndGrantsRewardPoints() {
        Season season = org.mockito.Mockito.mock(Season.class);
        when(season.getId()).thenReturn(11L);
        when(season.getTotalDays()).thenReturn(7);
        when(season.resolveRuntimePlayableDays()).thenReturn(7);

        List<Store> stores = new ArrayList<>();
        List<DailyReport> reports = new ArrayList<>();
        Map<Integer, User> usersById = new LinkedHashMap<>();
        int[] rois = {200, 190, 180, 170, 160, 150, 140, 130, 120, 110, 110, 100};
        for (int index = 0; index < rois.length; index++) {
            Store store = createStore((long) (100 + index), index + 1, "user-" + (index + 1));
            stores.add(store);
            usersById.put(store.getUser().getId(), store.getUser());
            boolean bankrupt = index == 10;
            reports.add(createDailyReport(store, 7, 100 + rois[index], 100, 10 + index, new BigDecimal("0.12"), bankrupt));
        }

        when(seasonRankingRecordRepository.existsByStore_Season_Id(11L)).thenReturn(false);
        when(storeRepository.findAllBySeason_IdOrderByIdAsc(11L)).thenReturn(stores);
        when(dailyReportRepository.findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(11L, 8)).thenReturn(reports);
        when(userRepository.findByIdForUpdate(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> java.util.Optional.ofNullable(usersById.get(invocation.getArgument(0))));

        seasonFinalRankingService.saveFinalRankings(season);

        verify(gameDayReportService, times(12)).recordClosedDayReport(any(Store.class), eq(7));
        ArgumentCaptor<List> recordsCaptor = ArgumentCaptor.forClass(List.class);
        verify(seasonRankingRecordRepository).saveAll(recordsCaptor.capture());

        List<SeasonRankingRecord> savedRecords = (List<SeasonRankingRecord>) recordsCaptor.getValue();
        assertThat(savedRecords).hasSize(12);
        assertThat(savedRecords)
                .extracting(SeasonRankingRecord::getFinalRank)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 0);
        assertThat(savedRecords)
                .extracting(record -> record.getStore().getUser().getId())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 11);
        assertThat(savedRecords)
                .extracting(SeasonRankingRecord::getRewardPoints)
                .containsExactly(30, 20, 10, 5, 5, 5, 5, 5, 5, 5, 5, 0);
        assertThat(savedRecords.get(10).getIsBankruptcy()).isFalse();
        assertThat(savedRecords.get(11).getFinalRank()).isEqualTo(0);
        assertThat(savedRecords.get(11).getRewardPoints()).isEqualTo(0);
        assertThat(usersById.get(1).getPoint()).isEqualTo(30);
        assertThat(usersById.get(2).getPoint()).isEqualTo(20);
        assertThat(usersById.get(3).getPoint()).isEqualTo(10);
        assertThat(usersById.get(4).getPoint()).isEqualTo(5);
        assertThat(usersById.get(10).getPoint()).isEqualTo(5);
        assertThat(usersById.get(11).getPoint()).isZero();
        assertThat(usersById.get(12).getPoint()).isEqualTo(5);
        verify(shopService, times(12)).resetPurchasedItems(anyInt());
        verify(shopService).resetPurchasedItems(1);
        verify(shopService).resetPurchasedItems(12);
    }

    @Test
    void saveFinalRankingsSkipsWhenFinalRecordsAlreadyExist() {
        Season season = org.mockito.Mockito.mock(Season.class);
        when(season.getId()).thenReturn(22L);

        when(seasonRankingRecordRepository.existsByStore_Season_Id(22L)).thenReturn(true);

        seasonFinalRankingService.saveFinalRankings(season);

        verify(storeRepository, never()).findAllBySeason_IdOrderByIdAsc(anyLong());
        verify(dailyReportRepository, never()).findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(anyLong(), anyInt());
        verify(gameDayReportService, never()).recordClosedDayReport(any(Store.class), anyInt());
        verify(seasonRankingRecordRepository, never()).saveAll(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveFinalRankingsKeepsStoreBankruptWhenLaterReportIsNonBankrupt() {
        Season season = org.mockito.Mockito.mock(Season.class);
        when(season.getId()).thenReturn(33L);
        when(season.getTotalDays()).thenReturn(7);
        when(season.resolveRuntimePlayableDays()).thenReturn(7);

        Store store = createStore(301L, 1, "user-1");
        DailyReport bankruptDayThree = createDailyReport(store, 3, 100, 200, 10, new BigDecimal("0.10"), true);
        DailyReport incorrectLateReport = createDailyReport(store, 7, 0, 0, 0, new BigDecimal("0.10"), false);

        when(seasonRankingRecordRepository.existsByStore_Season_Id(33L)).thenReturn(false);
        when(storeRepository.findAllBySeason_IdOrderByIdAsc(33L)).thenReturn(List.of(store));
        when(dailyReportRepository.findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(33L, 8))
                .thenReturn(List.of(bankruptDayThree, incorrectLateReport));

        seasonFinalRankingService.saveFinalRankings(season);

        ArgumentCaptor<List> recordsCaptor = ArgumentCaptor.forClass(List.class);
        verify(seasonRankingRecordRepository).saveAll(recordsCaptor.capture());

        List<SeasonRankingRecord> savedRecords = (List<SeasonRankingRecord>) recordsCaptor.getValue();
        assertThat(savedRecords).hasSize(1);
        assertThat(savedRecords.get(0).getIsBankruptcy()).isTrue();
        assertThat(savedRecords.get(0).getFinalRank()).isZero();
    }

    private Store createStore(Long storeId, Integer userId, String nickname) {
        User user = new User(userId + "@example.com", nickname);
        ReflectionTestUtils.setField(user, "id", userId);

        Location location = BeanUtils.instantiateClass(Location.class);
        ReflectionTestUtils.setField(location, "id", storeId + 1);
        ReflectionTestUtils.setField(location, "locationName", "location-" + storeId);
        ReflectionTestUtils.setField(location, "rent", 1000);
        ReflectionTestUtils.setField(location, "interiorCost", 100);

        Menu menu = BeanUtils.instantiateClass(Menu.class);
        ReflectionTestUtils.setField(menu, "id", storeId + 10);
        ReflectionTestUtils.setField(menu, "menuName", "menu-" + storeId);
        ReflectionTestUtils.setField(menu, "originPrice", 50);

        Store store = BeanUtils.instantiateClass(Store.class);
        ReflectionTestUtils.setField(store, "id", storeId);
        ReflectionTestUtils.setField(store, "user", user);
        ReflectionTestUtils.setField(store, "location", location);
        ReflectionTestUtils.setField(store, "menu", menu);
        ReflectionTestUtils.setField(store, "storeName", "store-" + storeId);
        ReflectionTestUtils.setField(store, "price", 100);
        return store;
    }

    private DailyReport createDailyReport(
            Store store,
            Integer day,
            Integer revenue,
            Integer totalCost,
            Integer visitors,
            BigDecimal captureRate,
            boolean bankrupt
    ) {
        DailyReport report = BeanUtils.instantiateClass(DailyReport.class);
        ReflectionTestUtils.setField(report, "store", store);
        ReflectionTestUtils.setField(report, "day", day);
        ReflectionTestUtils.setField(report, "revenue", revenue);
        ReflectionTestUtils.setField(report, "totalCost", totalCost);
        ReflectionTestUtils.setField(report, "netProfit", revenue - totalCost);
        ReflectionTestUtils.setField(report, "visitors", visitors);
        ReflectionTestUtils.setField(report, "captureRate", captureRate);
        ReflectionTestUtils.setField(report, "isBankrupt", bankrupt);
        return report;
    }
}
