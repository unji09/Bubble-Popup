package com.ssafy.S14P21A205.game.day.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.dto.GameDayReportResponse;
import com.ssafy.S14P21A205.game.day.generator.PurchaseListGenerator;
import com.ssafy.S14P21A205.game.day.policy.BankruptcyPolicy;
import com.ssafy.S14P21A205.game.day.policy.CaptureRatePolicy;
import com.ssafy.S14P21A205.game.day.policy.ProfitPolicy;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.environment.entity.Weather;
import com.ssafy.S14P21A205.game.environment.entity.WeatherLocation;
import com.ssafy.S14P21A205.game.environment.entity.WeatherType;
import com.ssafy.S14P21A205.game.environment.repository.WeatherDayRedisRepository;
import com.ssafy.S14P21A205.game.environment.repository.WeatherLocationRepository;
import com.ssafy.S14P21A205.game.season.entity.DailyReport;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.shop.service.ShopService;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.service.UserService;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GameDayReportServiceTests {

    @Mock
    private UserService userService;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private DailyReportRepository dailyReportRepository;

    @Mock
    private GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;

    @Mock
    private WeatherDayRedisRepository weatherDayRedisRepository;

    @Mock
    private WeatherLocationRepository weatherLocationRepository;

    @Mock
    private PurchaseListGenerator purchaseListGenerator;

    @Mock
    private GameDayStateService gameDayStateService;

    @Mock
    private ShopService shopService;

    private GameDayReportService gameDayReportService;

    @BeforeEach
    void setUp() {
        ProfitPolicy profitPolicy = new ProfitPolicy();
        CaptureRatePolicy captureRatePolicy = new CaptureRatePolicy();
        BankruptcyPolicy bankruptcyPolicy = new BankruptcyPolicy();
        gameDayReportService = new GameDayReportService(
                userService,
                storeRepository,
                dailyReportRepository,
                gameDayStoreStateRedisRepository,
                weatherDayRedisRepository,
                weatherLocationRepository,
                profitPolicy,
                captureRatePolicy,
                bankruptcyPolicy,
                gameDayStateService,
                purchaseListGenerator,
                shopService,
                Clock.fixed(Instant.parse("2026-03-09T05:33:00Z"), ZoneId.of("Asia/Seoul"))
        );
        org.mockito.Mockito.lenient()
                .when(weatherDayRedisRepository.findLocation(anyLong(), anyLong(), anyInt()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient()
                .when(gameDayStoreStateRedisRepository.find(anyLong(), anyInt()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient()
                .when(dailyReportRepository.findFirstByStore_IdOrderByDayDesc(anyLong()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient()
                .when(purchaseListGenerator.advanceCursor(any(), anyInt()))
                .thenReturn(12);
        org.mockito.Mockito.lenient()
                .when(storeRepository.findFirstByUser_IdAndSeason_StatusOrderByIdDesc(anyInt(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void recordClosedDayReportSavesReportDuringReportPhase() {
        Store store = store(15L, 9L, 2, 7, "Seongsu", "Cookie", 300);
        GameDayLiveState state = state(
                LocalDateTime.of(2026, 3, 9, 14, 30, 0),
                new BigDecimal("0.10"),
                5_000L,
                1_300L,
                42,
                20,
                15_000L,
                12,
                8
        );

        when(dailyReportRepository.existsByStoreIdAndDay(15L, 2)).thenReturn(false);
        when(gameDayStateService.refreshGameState(store)).thenReturn(Optional.empty());
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.of(state));
        when(dailyReportRepository.findByStoreIdAndDay(15L, 1)).thenReturn(Optional.empty());

        gameDayReportService.recordClosedDayReport(store, 2);

        ArgumentCaptor<DailyReport> captor = ArgumentCaptor.forClass(DailyReport.class);
        verify(dailyReportRepository).save(captor.capture());

        DailyReport saved = captor.getValue();
        assertThat(saved.getDay()).isEqualTo(2);
        assertThat(saved.getLocationName()).isEqualTo("Seongsu");
        assertThat(saved.getMenuName()).isEqualTo("Cookie");
        assertThat(saved.getRevenue()).isEqualTo(5_000);
        assertThat(saved.getTotalCost()).isEqualTo(1_600);
        assertThat(saved.getNetProfit()).isEqualTo(3_400);
        assertThat(saved.getVisitors()).isEqualTo(42);
        assertThat(saved.getSalesCount()).isEqualTo(20);
        assertThat(saved.getStockRemaining()).isEqualTo(12);
        assertThat(saved.getBalance()).isEqualTo(14_700);
        assertThat(saved.getCaptureRate()).isEqualByComparingTo("0.10");
        assertThat(store.getPurchaseCursor()).isEqualTo(12);
        verify(gameDayStateService).refreshGameState(store);
        verify(gameDayStoreStateRedisRepository).updateField(15L, 2, "cumulative_total_cost", "1600");
        verify(gameDayStoreStateRedisRepository).saveBalance(15L, 2, 14_700L);
        verify(purchaseListGenerator).advanceCursor(4, 8);
    }

    @Test
    void recordClosedDayReportResetsPurchasedItemsWhenStoreBecomesBankrupt() {
        Store store = store(15L, 9L, 3, 7, "Seongsu", "Cookie", 300);
        GameDayLiveState state = state(
                LocalDateTime.of(2026, 3, 9, 14, 30, 0),
                new BigDecimal("0.10"),
                1_000L,
                5_000L,
                12,
                5,
                8_000L,
                10,
                4
        );
        DailyReport previousDayReport = dailyReport(
                store,
                2,
                "Seongsu",
                "Cookie",
                1_000,
                3_000,
                -2_000,
                10,
                5,
                4,
                2,
                false,
                9_000,
                new BigDecimal("0.05")
        );

        when(dailyReportRepository.existsByStoreIdAndDay(15L, 3)).thenReturn(false);
        when(gameDayStateService.refreshGameState(store)).thenReturn(Optional.empty());
        when(gameDayStoreStateRedisRepository.find(15L, 3)).thenReturn(Optional.of(state));
        when(dailyReportRepository.findByStoreIdAndDay(15L, 2)).thenReturn(Optional.of(previousDayReport));

        gameDayReportService.recordClosedDayReport(store, 3);

        ArgumentCaptor<DailyReport> captor = ArgumentCaptor.forClass(DailyReport.class);
        verify(dailyReportRepository).save(captor.capture());

        DailyReport saved = captor.getValue();
        assertThat(saved.getTotalCost()).isEqualTo(5_300);
        assertThat(saved.getNetProfit()).isEqualTo(-4_300);
        assertThat(saved.getBalance()).isEqualTo(7_700);
        assertThat(saved.getIsBankrupt()).isTrue();
        verify(gameDayStoreStateRedisRepository).updateField(15L, 3, "cumulative_total_cost", "5300");
        verify(shopService).resetPurchasedItems(1);
        verify(gameDayStoreStateRedisRepository).saveBalance(15L, 3, 0L);
        verify(gameDayStoreStateRedisRepository).updateField(15L, 3, "stock", "0");
    }

    @Test
    void recordClosedDayReportMarksStoreBankruptWhenClosingRentMakesBalanceNegative() {
        Store store = store(15L, 9L, 4, 7, "Seongsu", "Cookie", 300);
        GameDayLiveState state = state(
                LocalDateTime.of(2026, 3, 9, 14, 30, 0),
                new BigDecimal("0.12"),
                5_000L,
                1_000L,
                9,
                4,
                200L,
                6,
                2
        );

        when(dailyReportRepository.existsByStoreIdAndDay(15L, 4)).thenReturn(false);
        when(gameDayStateService.refreshGameState(store)).thenReturn(Optional.empty());
        when(gameDayStoreStateRedisRepository.find(15L, 4)).thenReturn(Optional.of(state));
        when(dailyReportRepository.findByStoreIdAndDay(15L, 3)).thenReturn(Optional.empty());

        gameDayReportService.recordClosedDayReport(store, 4);

        ArgumentCaptor<DailyReport> captor = ArgumentCaptor.forClass(DailyReport.class);
        verify(dailyReportRepository).save(captor.capture());

        DailyReport saved = captor.getValue();
        assertThat(saved.getTotalCost()).isEqualTo(1_300);
        assertThat(saved.getNetProfit()).isEqualTo(3_700);
        assertThat(saved.getBalance()).isZero();
        assertThat(saved.getConsecutiveDeficitDays()).isZero();
        assertThat(saved.getIsBankrupt()).isTrue();
        verify(gameDayStoreStateRedisRepository).updateField(15L, 4, "cumulative_total_cost", "1300");
        verify(shopService).resetPurchasedItems(1);
        verify(gameDayStoreStateRedisRepository).saveBalance(15L, 4, 0L);
        verify(gameDayStoreStateRedisRepository).updateField(15L, 4, "stock", "0");
    }

    @Test
    void recordClosedDayReportRestoresClosedStateWhenRedisStateIsMissing() {
        Store store = store(15L, 9L, 2, 7, "Seongsu", "Cookie", 300);
        GameDayLiveState restoredState = state(
                LocalDateTime.of(2026, 3, 9, 14, 30, 0),
                new BigDecimal("0.10"),
                5_000L,
                1_300L,
                42,
                20,
                15_000L,
                12,
                8
        );

        when(dailyReportRepository.existsByStoreIdAndDay(15L, 2)).thenReturn(false);
        when(gameDayStateService.refreshGameState(store)).thenReturn(Optional.empty());
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.empty());
        when(gameDayStateService.restoreClosedDayState(store, 2)).thenReturn(Optional.of(restoredState));
        when(dailyReportRepository.findByStoreIdAndDay(15L, 1)).thenReturn(Optional.empty());

        gameDayReportService.recordClosedDayReport(store, 2);

        ArgumentCaptor<DailyReport> captor = ArgumentCaptor.forClass(DailyReport.class);
        verify(dailyReportRepository).save(captor.capture());

        DailyReport saved = captor.getValue();
        assertThat(saved.getDay()).isEqualTo(2);
        assertThat(saved.getTotalCost()).isEqualTo(1_600);
        assertThat(saved.getNetProfit()).isEqualTo(3_400);
        assertThat(saved.getBalance()).isEqualTo(14_700);
        verify(gameDayStateService).restoreClosedDayState(store, 2);
    }

    @Test
    void recordClosedDayReportSkipsWhenStoreWasAlreadyBankrupt() {
        Store store = store(15L, 9L, 7, 7, "Current Location", "Current Menu", 300);
        DailyReport bankruptReport = dailyReport(
                store,
                3,
                "Current Location",
                "Current Menu",
                1_000,
                5_000,
                -4_000,
                12,
                5,
                0,
                3,
                true,
                0,
                new BigDecimal("0.05")
        );

        when(dailyReportRepository.existsByStoreIdAndDay(15L, 7)).thenReturn(false);
        when(dailyReportRepository.findFirstByStore_IdOrderByDayDesc(15L)).thenReturn(Optional.of(bankruptReport));

        gameDayReportService.recordClosedDayReport(store, 7);

        verify(dailyReportRepository, never()).save(any(DailyReport.class));
        verify(gameDayStateService, never()).restoreClosedDayState(any(Store.class), anyInt());
    }

    @Test
    void recordClosedDayReportClampsNegativeStockToZero() {
        Store store = store(15L, 9L, 2, 7, "Seongsu", "Cookie", 300);
        GameDayLiveState state = state(
                LocalDateTime.of(2026, 3, 9, 14, 30, 0),
                new BigDecimal("0.10"),
                5_000L,
                1_300L,
                42,
                20,
                15_000L,
                -12,
                8
        );

        when(dailyReportRepository.existsByStoreIdAndDay(15L, 2)).thenReturn(false);
        when(gameDayStateService.refreshGameState(store)).thenReturn(Optional.empty());
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.of(state));
        when(dailyReportRepository.findByStoreIdAndDay(15L, 1)).thenReturn(Optional.empty());

        gameDayReportService.recordClosedDayReport(store, 2);

        ArgumentCaptor<DailyReport> captor = ArgumentCaptor.forClass(DailyReport.class);
        verify(dailyReportRepository).save(captor.capture());

        DailyReport saved = captor.getValue();
        assertThat(saved.getStockRemaining()).isZero();
        verify(gameDayStoreStateRedisRepository).updateField(15L, 2, "stock", "0");
    }

    @Test
    void getDayReportReturnsComputedFields() {
        User user = user(1);
        Store store = store(15L, 9L, 2, 7, "Current Location", "Current Menu", 300);
        DailyReport dayTwo = dailyReport(
                store,
                2,
                "Seongsu",
                "Cookie",
                5_000,
                1_300,
                3_700,
                42,
                20,
                12,
                2,
                false,
                15_000,
                new BigDecimal("0.10")
        );

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(dailyReportRepository.findByStoreIdAndDay(15L, 2)).thenReturn(Optional.of(dayTwo));
        when(dailyReportRepository.findByStore_IdOrderByDayAsc(15L)).thenReturn(List.of(
                dailyReport(store, 1, "Seongsu", "Cookie", 4_000, 1_200, 2_800, 40, 18, 10, 0, false, 10_000, new BigDecimal("0.08")),
                dayTwo,
                dailyReport(store, 3, "Seongsu", "Cookie", 9_000, 2_000, 7_000, 55, 24, 8, 0, false, 20_000, new BigDecimal("0.11"))
        ));
        when(weatherLocationRepository.findByDayOrderByLocation_IdAsc(3)).thenReturn(List.of(
                weatherLocation(store.getLocation(), 3, weather(WeatherType.SNOW))
        ));

        GameDayReportResponse response = gameDayReportService.getDayReport(mock(Authentication.class), 2);

        assertThat(response.seasonId()).isEqualTo(9L);
        assertThat(response.day()).isEqualTo(2);
        assertThat(response.storeName()).isEqualTo("Ignored Store Name");
        assertThat(response.locationName()).isEqualTo("Seongsu");
        assertThat(response.menuName()).isEqualTo("Cookie");
        assertThat(response.revenue()).isEqualTo(5_000L);
        assertThat(response.totalCost()).isEqualTo(1_300L);
        assertThat(response.visitors()).isEqualTo(42);
        assertThat(response.salesCount()).isEqualTo(20);
        assertThat(response.stockRemaining()).isZero();
        assertThat(response.stockDisposedCount()).isEqualTo(12);
        assertThat(response.captureRate()).isEqualByComparingTo("0.10");
        assertThat(response.changeCaptureRate()).isEqualByComparingTo("0.0200");
        assertThat(response.dailyRevenue()).isEqualTo(new GameDayReportResponse.DailyRevenue(
                4_000L,
                5_000L,
                null,
                null,
                null,
                null,
                null
        ));
        assertThat(response.tomorrowWeather()).isNotNull();
        assertThat(response.tomorrowWeather().condition()).isEqualTo("SNOW");
        assertThat(response.isNextDayOrderDay()).isTrue();
        assertThat(response.consecutiveDeficitDays()).isEqualTo(2);
        assertThat(response.isBankrupt()).isFalse();
    }

    @Test
    void getDayReportMaterializesMissingReportOnRead() {
        User user = user(1);
        Store store = store(15L, 9L, 1, 7, "Seongsu", "Cookie", 300);
        GameDayLiveState state = state(
                LocalDateTime.of(2026, 3, 9, 14, 30, 0),
                new BigDecimal("0.10"),
                5_000L,
                1_300L,
                42,
                20,
                15_000L,
                12,
                8
        );
        AtomicReference<DailyReport> savedReport = new AtomicReference<>();

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(dailyReportRepository.findByStoreIdAndDay(15L, 1))
                .thenAnswer(invocation -> Optional.ofNullable(savedReport.get()));
        when(dailyReportRepository.existsByStoreIdAndDay(15L, 1)).thenReturn(false);
        when(dailyReportRepository.findFirstByStore_IdOrderByDayDesc(15L)).thenReturn(Optional.empty());
        when(gameDayStateService.refreshGameState(store)).thenReturn(Optional.empty());
        when(gameDayStoreStateRedisRepository.find(15L, 1)).thenReturn(Optional.of(state));
        when(dailyReportRepository.save(any(DailyReport.class))).thenAnswer(invocation -> {
            DailyReport report = invocation.getArgument(0);
            savedReport.set(report);
            return report;
        });
        when(dailyReportRepository.findByStore_IdOrderByDayAsc(15L))
                .thenAnswer(invocation -> savedReport.get() == null ? List.of() : List.of(savedReport.get()));
        when(weatherLocationRepository.findByDayOrderByLocation_IdAsc(2)).thenReturn(List.of(
                weatherLocation(store.getLocation(), 2, weather(WeatherType.SUNNY))
        ));

        GameDayReportResponse response = gameDayReportService.getDayReport(mock(Authentication.class), 1);

        assertThat(response.day()).isEqualTo(1);
        assertThat(response.revenue()).isEqualTo(5_000L);
        assertThat(response.totalCost()).isEqualTo(1_600L);
        assertThat(response.locationName()).isEqualTo("Seongsu");
        verify(dailyReportRepository).save(any(DailyReport.class));
    }

    @Test
    void getDayReportUsesReservedLocationForTomorrowWeather() {
        User user = user(1);
        Store store = store(15L, 9L, 2, 7, "Current Location", "Current Menu", 300);
        Location reservedLocation = location(9L, "Reserved Location", 500);
        ReflectionTestUtils.setField(store, "pendingLocation", reservedLocation);
        ReflectionTestUtils.setField(store, "pendingLocationApplyDay", 3);

        DailyReport dayTwo = dailyReport(
                store,
                2,
                "Current Location",
                "Current Menu",
                5_000,
                1_300,
                3_700,
                42,
                20,
                12,
                2,
                false,
                15_000,
                new BigDecimal("0.10")
        );

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(dailyReportRepository.findByStoreIdAndDay(15L, 2)).thenReturn(Optional.of(dayTwo));
        when(dailyReportRepository.findByStore_IdOrderByDayAsc(15L)).thenReturn(List.of(
                dailyReport(store, 1, "Current Location", "Current Menu", 3_000, 1_000, 2_000, 20, 10, 5, 0, false, 8_000, new BigDecimal("0.09")),
                dayTwo,
                dailyReport(store, 3, "Current Location", "Current Menu", 9_000, 2_000, 7_000, 50, 25, 9, 0, false, 20_000, new BigDecimal("0.11"))
        ));
        when(weatherLocationRepository.findByDayOrderByLocation_IdAsc(3)).thenReturn(List.of(
                weatherLocation(store.getLocation(), 3, weather(WeatherType.SNOW)),
                weatherLocation(reservedLocation, 3, weather(WeatherType.RAIN))
        ));

        GameDayReportResponse response = gameDayReportService.getDayReport(mock(Authentication.class), 2);

        assertThat(response.tomorrowWeather()).isNotNull();
        assertThat(response.tomorrowWeather().condition()).isEqualTo("RAIN");
    }

    @Test
    void getDayReportReturnsBankruptInProgressStoreReport() {
        User user = user(1);
        Store store = store(15L, 9L, 6, 7, "Current Location", "Current Menu", 300);
        DailyReport daySix = dailyReport(
                store,
                6,
                "Current Location",
                "Current Menu",
                2_000,
                5_000,
                -3_000,
                12,
                5,
                3,
                3,
                true,
                0,
                new BigDecimal("0.08")
        );

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(storeRepository.findFirstIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(dailyReportRepository.findByStoreIdAndDay(15L, 6)).thenReturn(Optional.of(daySix));
        when(dailyReportRepository.findByStore_IdOrderByDayAsc(15L)).thenReturn(List.of(daySix));
        when(weatherLocationRepository.findByDayOrderByLocation_IdAsc(7)).thenReturn(List.of());

        GameDayReportResponse response = gameDayReportService.getDayReport(mock(Authentication.class), 6);

        assertThat(response.day()).isEqualTo(6);
        assertThat(response.locationName()).isEqualTo("Current Location");
        assertThat(response.menuName()).isEqualTo("Current Menu");
        assertThat(response.consecutiveDeficitDays()).isEqualTo(3);
    }

    @Test
    void getDayReportThrowsWhenDayIsOutOfRange() {
        User user = user(1);
        Store store = store(15L, 9L, 2, 7, "Seongsu", "Cookie", 300);

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));

        assertThatThrownBy(() -> gameDayReportService.getDayReport(mock(Authentication.class), 8))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode()).isEqualTo(ErrorCode.INVALID_DAY));
    }

    @Test
    void getDayReportThrowsWhenUserHasNoActiveOrFinishedStore() {
        User user = user(1);

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(storeRepository.findFirstByUser_IdAndSeason_StatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(storeRepository.findFirstIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameDayReportService.getDayReport(mock(Authentication.class), 1))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_PARTICIPATING));
    }

    @Test
    void getDayReportThrowsWhenMissingReportCannotBeMaterialized() {
        User user = user(1);
        Store store = store(15L, 9L, 1, 7, "Seongsu", "Cookie", 300);

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(dailyReportRepository.findByStoreIdAndDay(15L, 1)).thenReturn(Optional.empty());
        when(dailyReportRepository.existsByStoreIdAndDay(15L, 1)).thenReturn(false);
        when(dailyReportRepository.findFirstByStore_IdOrderByDayDesc(15L)).thenReturn(Optional.empty());
        when(gameDayStateService.refreshGameState(store)).thenReturn(Optional.empty());
        when(gameDayStoreStateRedisRepository.find(15L, 1)).thenReturn(Optional.empty());
        when(gameDayStateService.restoreClosedDayState(store, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameDayReportService.getDayReport(mock(Authentication.class), 1))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.REPORT_NOT_FOUND));

        verify(dailyReportRepository, never()).save(any(DailyReport.class));
    }

    @Test
    void getDayReportAllowsBankruptInProgressStoreWhenActiveStoreIsUnavailable() {
        User user = user(1);
        Store store = store(15L, 9L, 3, 7, "Current Location", "Current Menu", 300);
        DailyReport bankruptReport = dailyReport(
                store,
                3,
                "Current Location",
                "Current Menu",
                1_000,
                5_000,
                -4_000,
                12,
                5,
                0,
                3,
                true,
                0,
                new BigDecimal("0.05")
        );

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(storeRepository.findFirstByUser_IdAndSeason_StatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(dailyReportRepository.findByStoreIdAndDay(15L, 3)).thenReturn(Optional.of(bankruptReport));
        when(dailyReportRepository.findByStore_IdOrderByDayAsc(15L)).thenReturn(List.of(
                dailyReport(store, 1, "Current Location", "Current Menu", 3_000, 1_000, 2_000, 20, 10, 5, 1, false, 8_000, new BigDecimal("0.09")),
                dailyReport(store, 2, "Current Location", "Current Menu", 1_000, 3_000, -2_000, 10, 5, 2, 2, false, 5_000, new BigDecimal("0.07")),
                bankruptReport
        ));
        when(weatherLocationRepository.findByDayOrderByLocation_IdAsc(4)).thenReturn(List.of(
                weatherLocation(store.getLocation(), 4, weather(WeatherType.RAIN))
        ));

        GameDayReportResponse response = gameDayReportService.getDayReport(mock(Authentication.class), 3);

        assertThat(response.day()).isEqualTo(3);
        assertThat(response.isBankrupt()).isTrue();
        assertThat(response.consecutiveDeficitDays()).isEqualTo(3);
        assertThat(response.tomorrowWeather()).isNotNull();
        assertThat(response.tomorrowWeather().condition()).isEqualTo("RAIN");
    }

    private User user(int id) {
        User user = new User("test@example.com", "tester");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Store store(
            Long storeId,
            Long seasonId,
            int currentDay,
            int totalDays,
            String locationName,
            String menuName,
            int rent
    ) {
        Location location = instantiate(Location.class);
        ReflectionTestUtils.setField(location, "id", 3L);
        ReflectionTestUtils.setField(location, "locationName", locationName);
        ReflectionTestUtils.setField(location, "rent", rent);

        Menu menu = instantiate(Menu.class);
        ReflectionTestUtils.setField(menu, "id", 5L);
        ReflectionTestUtils.setField(menu, "menuName", menuName);
        ReflectionTestUtils.setField(menu, "originPrice", 500);

        Season season = instantiate(Season.class);
        ReflectionTestUtils.setField(season, "id", seasonId);
        ReflectionTestUtils.setField(season, "status", SeasonStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(season, "currentDay", currentDay);
        ReflectionTestUtils.setField(season, "totalDays", totalDays);
        LocalDateTime reportPhaseAt = LocalDateTime.of(2026, 3, 9, 14, 33, 0);
        LocalDateTime seasonStartAt = reportPhaseAt.minusSeconds(60L + (currentDay - 1L) * 180L + 170L);
        ReflectionTestUtils.setField(season, "startTime", seasonStartAt);
        ReflectionTestUtils.setField(season, "endTime", seasonStartAt.plusSeconds(60L + totalDays * 180L + 180L));

        Store store = instantiate(Store.class);
        ReflectionTestUtils.setField(store, "id", storeId);
        ReflectionTestUtils.setField(store, "user", user(1));
        ReflectionTestUtils.setField(store, "location", location);
        ReflectionTestUtils.setField(store, "menu", menu);
        ReflectionTestUtils.setField(store, "season", season);
        ReflectionTestUtils.setField(store, "storeName", "Ignored Store Name");
        ReflectionTestUtils.setField(store, "purchaseCursor", 4);
        return store;
    }

    private Location location(Long id, String locationName, int rent) {
        Location location = instantiate(Location.class);
        ReflectionTestUtils.setField(location, "id", id);
        ReflectionTestUtils.setField(location, "locationName", locationName);
        ReflectionTestUtils.setField(location, "rent", rent);
        return location;
    }

    private GameDayLiveState state(
            LocalDateTime startedAt,
            BigDecimal captureRate,
            Long cumulativeSales,
            Long cumulativeTotalCost,
            Integer cumulativeCustomerCount,
            Integer cumulativePurchaseCount,
            Long balance,
            Integer stock,
            Integer purchaseCursor
    ) {
        return new GameDayLiveState(
                startedAt,
                List.of(),
                purchaseCursor,
                null,
                18,
                0,
                captureRate,
                500,
                0,
                List.of(),
                0,
                0L,
                cumulativeCustomerCount,
                cumulativePurchaseCount,
                cumulativeSales,
                cumulativeTotalCost,
                balance,
                stock,
                LocalDateTime.of(2026, 3, 9, 14, 33, 0)
        );
    }

    private DailyReport dailyReport(
            Store store,
            int day,
            String locationName,
            String menuName,
            int revenue,
            int totalCost,
            int netProfit,
            int visitors,
            int salesCount,
            int stockRemaining,
            int consecutiveDeficitDays,
            boolean isBankrupt,
            int balance,
            BigDecimal captureRate
    ) {
        return DailyReport.create(
                store,
                day,
                locationName,
                menuName,
                revenue,
                totalCost,
                netProfit,
                visitors,
                salesCount,
                stockRemaining,
                consecutiveDeficitDays,
                isBankrupt,
                balance,
                captureRate
        );
    }

    private Weather weather(WeatherType weatherType) {
        Weather weather = instantiate(Weather.class);
        ReflectionTestUtils.setField(weather, "id", 1L);
        ReflectionTestUtils.setField(weather, "weatherType", weatherType);
        ReflectionTestUtils.setField(weather, "populationPercent", BigDecimal.ONE);
        return weather;
    }

    private WeatherLocation weatherLocation(Location location, int day, Weather weather) {
        WeatherLocation weatherLocation = instantiate(WeatherLocation.class);
        ReflectionTestUtils.setField(weatherLocation, "location", location);
        ReflectionTestUtils.setField(weatherLocation, "day", day);
        ReflectionTestUtils.setField(weatherLocation, "weather", weather);
        return weatherLocation;
    }

    private <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
