package com.ssafy.S14P21A205.game.day.service;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.dto.GameDayReportResponse;
import com.ssafy.S14P21A205.game.day.generator.PurchaseListGenerator;
import com.ssafy.S14P21A205.game.day.policy.BankruptcyPolicy;
import com.ssafy.S14P21A205.game.day.policy.CaptureRatePolicy;
import com.ssafy.S14P21A205.game.day.policy.ProfitPolicy;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.environment.entity.WeatherLocation;
import com.ssafy.S14P21A205.game.environment.repository.WeatherDayRedisRepository;
import com.ssafy.S14P21A205.game.environment.repository.WeatherLocationRepository;
import com.ssafy.S14P21A205.game.season.entity.DailyReport;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.game.time.model.DayWindow;
import com.ssafy.S14P21A205.game.time.model.SeasonPhase;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import com.ssafy.S14P21A205.shop.service.ShopService;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.store.service.StoreLocationTransitionSupport;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.service.UserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameDayReportService {

    private static final int MAX_SUPPORTED_DAY = 7;
    private static final int STOCK_DISPOSED_COUNT = 0;
    private static final Set<Integer> REGULAR_ORDER_DAYS = Set.of(1, 3, 5, 7);
    private static final SeasonTimelineService SEASON_TIMELINE_SERVICE = new SeasonTimelineService();
    private static final StoreLocationTransitionSupport STORE_LOCATION_TRANSITION_SUPPORT = new StoreLocationTransitionSupport();

    private final UserService userService;
    private final StoreRepository storeRepository;
    private final DailyReportRepository dailyReportRepository;
    private final GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;
    private final WeatherDayRedisRepository weatherDayRedisRepository;
    private final WeatherLocationRepository weatherLocationRepository;
    private final ProfitPolicy profitPolicy;
    private final CaptureRatePolicy captureRatePolicy;
    private final BankruptcyPolicy bankruptcyPolicy;
    private final GameDayStateService gameDayStateService;
    private final PurchaseListGenerator purchaseListGenerator;
    private final ShopService shopService;
    private final Clock clock;

    @Transactional
    public void recordClosedDayReport(Store store) {
        LocalDateTime now = LocalDateTime.now(clock);
        SeasonTimePoint seasonTimePoint = SEASON_TIMELINE_SERVICE.resolve(store.getSeason(), now);
        Integer day = resolveReportDay(store.getSeason(), seasonTimePoint);
        if (day == null) {
            return;
        }
        recordClosedDayReport(store, day, now, seasonTimePoint);
    }

    @Transactional
    public void recordClosedDayReport(Store store, int day) {
        if (store == null || day < 1) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        SeasonTimePoint seasonTimePoint = SEASON_TIMELINE_SERVICE.resolve(store.getSeason(), now);
        recordClosedDayReport(store, day, now, seasonTimePoint);
    }

    private void recordClosedDayReport(
            Store store,
            int day,
            LocalDateTime now,
            SeasonTimePoint seasonTimePoint
    ) {
        int totalDays = store.getSeason().resolveRuntimePlayableDays();
        if (day > totalDays) {
            log.warn("[DayReport] Skipped: day {} > totalDays {}. storeId={}", day, totalDays, store.getId());
            return;
        }
        if (dailyReportRepository.existsByStoreIdAndDay(store.getId(), day)) {
            log.debug("[DayReport] Already exists. storeId={} day={}", store.getId(), day);
            return;
        }
        DailyReport latestReport = dailyReportRepository.findFirstByStore_IdOrderByDayDesc(store.getId())
                .orElse(null);
        if (latestReport != null
                && latestReport.getDay() != null
                && latestReport.getDay() < day
                && Boolean.TRUE.equals(latestReport.getIsBankrupt())) {
            log.info(
                    "[DayReport] Skipped: store already bankrupt. storeId={} latestBankruptDay={} requestedDay={}",
                    store.getId(),
                    latestReport.getDay(),
                    day
            );
            return;
        }

        if (shouldRefreshCurrentDayState(day, seasonTimePoint)) {
            gameDayStateService.refreshGameState(store);
        }

        GameDayLiveState state = gameDayStoreStateRedisRepository.find(store.getId(), day).orElse(null);
        if (state == null || state.startedAt() == null) {
            state = restoreClosedDayState(store, day);
        }
        if (state == null || state.startedAt() == null) {
            log.warn("[DayReport] Skipped: Redis state missing. storeId={} day={} state={}", store.getId(), day, state);
            return;
        }

        DayWindow timeline = SEASON_TIMELINE_SERVICE.day(store.getSeason(), day);
        if (now.isBefore(timeline.businessEnd())) {
            log.warn("[DayReport] Skipped: before businessEnd. storeId={} day={} now={} businessEnd={}", store.getId(), day, now, timeline.businessEnd());
            return;
        }

        long settledRent = resolveClosingRent(store, state, day);
        long finalTotalCost = valueOf(state.cumulativeTotalCost()) + settledRent;
        long closingBalanceBeforeBankruptcy = valueOf(state.balance()) - settledRent;
        long reportBalance = Math.max(0L, closingBalanceBeforeBankruptcy);

        ProfitPolicy.ProfitResult profitResult =
                profitPolicy.calculate(state.cumulativeSales(), finalTotalCost);
        DailyReport previousDayReport = day == 1
                ? null
                : dailyReportRepository.findByStoreIdAndDay(store.getId(), day - 1).orElse(null);
        BankruptcyPolicy.BankruptcyResult deficitBankruptcyResult =
                bankruptcyPolicy.resolve(previousDayReport, profitResult.netProfit());
        boolean bankruptByClosingBalance = closingBalanceBeforeBankruptcy < 0L;
        boolean isBankrupt = deficitBankruptcyResult.bankrupt() || bankruptByClosingBalance;

        if (bankruptByClosingBalance) {
            log.info(
                    "[DayReport] Closing settlement triggered bankruptcy. storeId={} day={} balanceBeforeSettlement={} rent={}",
                    store.getId(),
                    day,
                    valueOf(state.balance()),
                    settledRent
            );
        }

        Location reportLocation = STORE_LOCATION_TRANSITION_SUPPORT.resolveLocationForDay(store, day);
        String reportLocationName = reportLocation == null || reportLocation.getLocationName() == null
                ? store.getLocation().getLocationName()
                : reportLocation.getLocationName();

        int stockRemaining = normalizeStock(state.stock());

        dailyReportRepository.save(DailyReport.create(
                store,
                day,
                reportLocationName,
                store.getMenu().getMenuName(),
                safeToInt(profitResult.revenue()),
                safeToInt(profitResult.totalCost()),
                safeToInt(profitResult.netProfit()),
                defaultInt(state.cumulativeCustomerCount()),
                defaultInt(state.cumulativePurchaseCount()),
                stockRemaining,
                deficitBankruptcyResult.consecutiveDeficitDays(),
                isBankrupt,
                safeToInt(reportBalance),
                resolveCaptureRate(state)
        ));
        store.changePurchaseCursor(
                purchaseListGenerator.advanceCursor(store.getPurchaseCursor(), defaultInt(state.purchaseCursor()))
        );
        gameDayStoreStateRedisRepository.updateField(
                store.getId(),
                day,
                "cumulative_total_cost",
                String.valueOf(finalTotalCost)
        );
        gameDayStoreStateRedisRepository.updateField(
                store.getId(),
                day,
                "stock",
                String.valueOf(stockRemaining)
        );
        if (isBankrupt) {
            shopService.resetPurchasedItems(store.getUser().getId());
            gameDayStoreStateRedisRepository.saveBalance(store.getId(), day, 0L);
            gameDayStoreStateRedisRepository.updateField(store.getId(), day, "stock", "0");
            return;
        }
        gameDayStoreStateRedisRepository.saveBalance(store.getId(), day, reportBalance);
    }

    @Transactional
    public GameDayReportResponse getDayReport(Authentication authentication, int day) {
        User user = userService.getCurrentUser(authentication);
        Store store = getReportStore(user.getId());
        validateDay(day, store.getSeason());

        DailyReport report = findOrCreateReport(store, day);
        List<DailyReport> storeReports = dailyReportRepository.findByStore_IdOrderByDayAsc(store.getId());

        int stockRemaining = normalizeStock(report.getStockRemaining());
        int stockDisposed = STOCK_DISPOSED_COUNT;
        boolean nextDayIsOrderDay = Boolean.TRUE.equals(
                resolveIsNextDayOrderDay(report.getDay(), store.getSeason().resolveRuntimePlayableDays()));
        if (nextDayIsOrderDay && stockRemaining > 0) {
            stockDisposed = stockRemaining;
            stockRemaining = 0;
        }

        return new GameDayReportResponse(
                report.getStore().getSeason().getId(),
                report.getDay(),
                resolveStoreName(report),
                resolveLocationName(report),
                resolveMenuName(report),
                valueOf(report.getRevenue()),
                valueOf(report.getTotalCost()),
                defaultInt(report.getVisitors()),
                defaultInt(report.getSalesCount()),
                stockRemaining,
                stockDisposed,
                captureRatePolicy.normalizeCaptureRate(report.getCaptureRate()),
                resolveChangeCaptureRate(report, storeReports),
                resolveDailyRevenue(storeReports, report.getDay()),
                resolveTomorrowWeather(
                        store.getSeason().getId(),
                        resolveTomorrowLocationId(store, report.getDay()),
                        report.getDay(),
                        store.getSeason().resolveRuntimePlayableDays()
                ),
                resolveIsNextDayOrderDay(report.getDay(), store.getSeason().resolveRuntimePlayableDays()),
                defaultInt(report.getConsecutiveDeficitDays()),
                Boolean.TRUE.equals(report.getIsBankrupt())
        );
    }

    private DailyReport findOrCreateReport(Store store, int day) {
        Optional<DailyReport> existingReport = dailyReportRepository.findByStoreIdAndDay(store.getId(), day);
        if (existingReport.isPresent()) {
            return existingReport.get();
        }

        log.info("[DayReport] Missing report on read. attempting materialization. storeId={} day={}", store.getId(), day);
        recordClosedDayReport(store, day);
        return dailyReportRepository.findByStoreIdAndDay(store.getId(), day)
                .orElseThrow(() -> new BaseException(ErrorCode.REPORT_NOT_FOUND));
    }

    private Store getReportStore(Integer userId) {
        Optional<Store> activeStore = storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(
                userId,
                SeasonStatus.IN_PROGRESS
        );
        if (activeStore.isPresent()) {
            return applyPendingLocationIfDue(activeStore.get());
        }

        Optional<Store> inProgressStore = storeRepository.findFirstByUser_IdAndSeason_StatusOrderByIdDesc(
                userId,
                SeasonStatus.IN_PROGRESS
        );
        if (inProgressStore.isPresent()) {
            return applyPendingLocationIfDue(inProgressStore.get());
        }

        Optional<Store> bankruptInProgressStore = storeRepository.findFirstIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(
                userId,
                SeasonStatus.IN_PROGRESS
        );
        if (bankruptInProgressStore.isPresent()) {
            STORE_LOCATION_TRANSITION_SUPPORT.applyPendingLocationIfDue(bankruptInProgressStore.get(), LocalDateTime.now(clock));
            return bankruptInProgressStore.get();
        }

        throw new BaseException(ErrorCode.NOT_PARTICIPATING);
    }

    private Store applyPendingLocationIfDue(Store store) {
        STORE_LOCATION_TRANSITION_SUPPORT.applyPendingLocationIfDue(store, LocalDateTime.now(clock));
        return store;
    }

    private void validateDay(int day, Season season) {
        int totalDays = season.resolveRuntimePlayableDays() <= 0 ? MAX_SUPPORTED_DAY : season.resolveRuntimePlayableDays();
        if (day < 1 || day > MAX_SUPPORTED_DAY || day > totalDays) {
            throw new BaseException(
                    ErrorCode.INVALID_DAY,
                    "day must be between 1 and %d.".formatted(Math.min(MAX_SUPPORTED_DAY, totalDays))
            );
        }
    }

    private Integer resolveReportDay(Season season, SeasonTimePoint seasonTimePoint) {
        Integer currentDay = seasonTimePoint.currentDay();
        return switch (seasonTimePoint.phase()) {
            case LOCATION_SELECTION -> null;
            case DAY_PREPARING, DAY_BUSINESS -> currentDay == null || currentDay <= 1 ? null : currentDay - 1;
            case DAY_REPORT -> currentDay;
            case SEASON_SUMMARY, NEXT_SEASON_WAITING, CLOSED -> season.resolveRuntimePlayableDays();
        };
    }

    private boolean shouldRefreshCurrentDayState(Integer reportDay, SeasonTimePoint seasonTimePoint) {
        return reportDay != null
                && seasonTimePoint.phase() == SeasonPhase.DAY_REPORT
                && seasonTimePoint.currentDay() != null
                && reportDay.equals(seasonTimePoint.currentDay());
    }

    private GameDayLiveState restoreClosedDayState(Store store, int day) {
        log.info("[DayReport] Redis state missing. attempting restore. storeId={} day={}", store.getId(), day);
        GameDayLiveState restoredState = gameDayStateService.restoreClosedDayState(store, day).orElse(null);
        if (restoredState != null) {
            log.info("[DayReport] Redis state restored. storeId={} day={}", store.getId(), day);
        }
        return restoredState;
    }

    private long resolveClosingRent(Store store, GameDayLiveState state, int day) {
        if (state != null
                && state.startResponse() != null
                && state.startResponse().openingSummary() != null
                && state.startResponse().openingSummary().dailyRentApplied() != null) {
            return state.startResponse().openingSummary().dailyRentApplied().longValue();
        }
        Location reportLocation = STORE_LOCATION_TRANSITION_SUPPORT.resolveLocationForDay(store, day);
        if (reportLocation == null || reportLocation.getRent() == null) {
            return 0L;
        }
        return reportLocation.getRent().longValue();
    }

    private GameDayReportResponse.TomorrowWeather resolveTomorrowWeather(
            Long seasonId,
            Long locationId,
            int day,
            int totalDays
    ) {
        if (day >= totalDays) {
            return null;
        }

        return weatherDayRedisRepository.findLocation(seasonId, locationId, day + 1)
                .or(() -> loadAndCacheTomorrow(seasonId, locationId, day + 1))
                .map(entry -> new GameDayReportResponse.TomorrowWeather(entry.weatherType().name()))
                .orElse(null);
    }

    private Optional<WeatherDayRedisRepository.WeatherDayEntry> loadAndCacheTomorrow(Long seasonId, Long locationId, int day) {
        java.util.List<WeatherLocation> dayEntries = weatherLocationRepository.findByDayOrderByLocation_IdAsc(day);
        if (dayEntries.isEmpty()) {
            return Optional.empty();
        }

        java.util.List<WeatherDayRedisRepository.WeatherDayEntry> cachedEntries = dayEntries.stream()
                .map(entry -> new WeatherDayRedisRepository.WeatherDayEntry(
                        entry.getLocation().getId(),
                        entry.getDay(),
                        entry.getWeather().getWeatherType(),
                        entry.getWeather().getPopulationPercent()
                ))
                .toList();
        weatherDayRedisRepository.saveDay(seasonId, day, cachedEntries);
        return cachedEntries.stream()
                .filter(entry -> locationId.equals(entry.locationId()))
                .findFirst();
    }

    private Boolean resolveIsNextDayOrderDay(int day, int totalDays) {
        if (day >= totalDays) {
            return null;
        }
        return REGULAR_ORDER_DAYS.contains(day + 1);
    }

    private Long resolveTomorrowLocationId(Store store, int day) {
        if (store == null || store.getLocation() == null) {
            return null;
        }
        if (day >= (store.getSeason() == null || store.getSeason().resolveRuntimePlayableDays() <= 0
                ? MAX_SUPPORTED_DAY
                : store.getSeason().resolveRuntimePlayableDays())) {
            return store.getLocation().getId();
        }
        return STORE_LOCATION_TRANSITION_SUPPORT.resolveLocationForDay(store, day + 1).getId();
    }

    private String resolveLocationName(DailyReport report) {
        if (report.getLocationName() != null && !report.getLocationName().isBlank()) {
            return report.getLocationName();
        }
        return report.getStore().getLocation().getLocationName();
    }

    private String resolveStoreName(DailyReport report) {
        if (report.getStore().getStoreName() != null && !report.getStore().getStoreName().isBlank()) {
            return report.getStore().getStoreName();
        }
        return null;
    }

    private String resolveMenuName(DailyReport report) {
        if (report.getMenuName() != null && !report.getMenuName().isBlank()) {
            return report.getMenuName();
        }
        return report.getStore().getMenu().getMenuName();
    }

    private BigDecimal resolveChangeCaptureRate(DailyReport currentReport, List<DailyReport> storeReports) {
        if (currentReport == null || currentReport.getDay() == null || currentReport.getDay() <= 1) {
            return null;
        }

        DailyReport previousReport = storeReports.stream()
                .filter(report -> report.getDay() != null)
                .filter(report -> report.getDay().equals(currentReport.getDay() - 1))
                .findFirst()
                .orElse(null);
        if (previousReport == null || previousReport.getCaptureRate() == null || currentReport.getCaptureRate() == null) {
            return null;
        }

        return captureRatePolicy.normalizeCaptureRate(currentReport.getCaptureRate())
                .subtract(captureRatePolicy.normalizeCaptureRate(previousReport.getCaptureRate()))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private GameDayReportResponse.DailyRevenue resolveDailyRevenue(List<DailyReport> storeReports, Integer reportDay) {
        Long[] profits = new Long[MAX_SUPPORTED_DAY];
        for (DailyReport report : storeReports) {
            if (report.getDay() == null || report.getDay() < 1 || report.getDay() > MAX_SUPPORTED_DAY) {
                continue;
            }
            if (reportDay != null && report.getDay() > reportDay) {
                continue;
            }
            profits[report.getDay() - 1] = valueOf(report.getRevenue());
        }
        return new GameDayReportResponse.DailyRevenue(
                profits[0],
                profits[1],
                profits[2],
                profits[3],
                profits[4],
                profits[5],
                profits[6]
        );
    }

    private java.math.BigDecimal resolveCaptureRate(GameDayLiveState state) {
        if (state.captureRate() != null) {
            return captureRatePolicy.normalizeCaptureRate(state.captureRate());
        }
        if (state.startResponse() != null && state.startResponse().captureRate() != null) {
            return captureRatePolicy.normalizeCaptureRate(state.startResponse().captureRate());
        }
        return captureRatePolicy.normalizeCaptureRate(java.math.BigDecimal.ZERO);
    }

    private int safeToInt(long value) {
        return Math.toIntExact(value);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int normalizeStock(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private long valueOf(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private long valueOf(Long value) {
        return value == null ? 0L : value;
    }
}
