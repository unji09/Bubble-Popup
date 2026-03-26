package com.ssafy.S14P21A205.game.day.service;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.dto.GameDayStartResponse;
import com.ssafy.S14P21A205.game.day.engine.EmergencyOrderEngine;
import com.ssafy.S14P21A205.game.day.generator.PurchaseListGenerator;
import com.ssafy.S14P21A205.game.day.model.DaySchedule;
import com.ssafy.S14P21A205.game.day.model.OpeningState;
import com.ssafy.S14P21A205.game.day.policy.CaptureRatePolicy;
import com.ssafy.S14P21A205.game.day.policy.RentPolicy;
import com.ssafy.S14P21A205.game.day.policy.StoreRankingPolicy;
import com.ssafy.S14P21A205.game.day.resolver.EnvironmentScheduleResolver;
import com.ssafy.S14P21A205.game.day.resolver.EventScheduleResolver;
import com.ssafy.S14P21A205.game.day.resolver.NewsRankingResolver;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.environment.entity.WeatherType;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.time.model.DayWindow;
import com.ssafy.S14P21A205.game.time.model.SeasonPhase;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.policy.GameTimePolicy;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import com.ssafy.S14P21A205.order.entity.Order;
import com.ssafy.S14P21A205.order.entity.OrderType;
import com.ssafy.S14P21A205.order.repository.OrderRepository;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.store.service.StoreLocationTransitionSupport;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.service.UserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameDayStartService {

    private static final int BUSINESS_OPEN_HOUR = GameTimePolicy.BUSINESS_OPEN_HOUR;
    private static final int BUSINESS_CLOSE_HOUR = GameTimePolicy.BUSINESS_CLOSE_HOUR;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final EmergencyOrderEngine EMERGENCY_ORDER_ENGINE = new EmergencyOrderEngine();
    private static final StoreLocationTransitionSupport STORE_LOCATION_TRANSITION_SUPPORT = new StoreLocationTransitionSupport();

    private final UserService userService;
    private final StoreRepository storeRepository;
    private final EnvironmentScheduleResolver environmentScheduleResolver;
    private final OrderRepository orderRepository;
    private final RentPolicy rentPolicy;
    private final CaptureRatePolicy captureRatePolicy;
    private final StoreRankingPolicy marketRankingPolicy;
    private final NewsRankingResolver newsRankingResolver;
    private final EventScheduleResolver eventScheduleResolver;
    private final GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;
    private final PurchaseListGenerator purchaseListGenerator;
    private final Clock clock;

    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();

    @Transactional
    public GameDayStartResponse startDay(Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        Store store = getActiveStore(user.getId());
        LocalDateTime now = LocalDateTime.now(clock);
        STORE_LOCATION_TRANSITION_SUPPORT.applyPendingLocationIfDue(store, now);
        SeasonTimePoint seasonTimePoint = seasonTimelineService.resolve(store.getSeason(), now);
        int day = resolveCurrentDay(store.getSeason(), seasonTimePoint);
        log.info(
                "start-day-check storeId={} seasonId={} now={} phase={} day={} playableFromDay={} gameTime={} tick={}",
                store.getId(),
                store.getSeason().getId(),
                now,
                seasonTimePoint.phase(),
                day,
                store.getPlayableFromDay(),
                seasonTimePoint.gameTime(),
                seasonTimePoint.tick()
        );

        GameDayLiveState existingState = gameDayStoreStateRedisRepository.find(store.getId(), day).orElse(null);
        if (existingState != null && existingState.startResponse() != null) {
            log.info("start-day-check cache-hit storeId={} seasonId={} day={}", store.getId(), store.getSeason().getId(), day);
            return existingState.startResponse();
        }

        validatePlayableDay(store, day);
        if (seasonTimePoint.phase() == SeasonPhase.DAY_PREPARING) {
            return buildDayStartResponse(store, day, now, now).response();
        }

        return ensureCurrentDayState(store, now, seasonTimePoint)
                .map(GameDayLiveState::startResponse)
                .orElseThrow(() -> new BaseException(
                        ErrorCode.INVALID_INPUT_VALUE,
                        "Day start information is only available during active day phases."
                ));
    }

    @Transactional
    public Optional<GameDayLiveState> ensureCurrentDayState(Store store, LocalDateTime now, SeasonTimePoint seasonTimePoint) {
        return upsertCurrentDayState(store, now, seasonTimePoint, false);
    }

    @Transactional
    public Optional<GameDayLiveState> synchronizeCurrentDayState(Store store, LocalDateTime now, SeasonTimePoint seasonTimePoint) {
        return upsertCurrentDayState(store, now, seasonTimePoint, true);
    }

    @Transactional
    public Optional<GameDayLiveState> restoreDayState(Store store, int day, LocalDateTime requestedAt) {
        if (store == null || requestedAt == null || day < 1 || store.getSeason() == null) {
            return Optional.empty();
        }

        int totalDays = store.getSeason().resolveRuntimePlayableDays();
        if (day > totalDays) {
            return Optional.empty();
        }
        if (!isPlayableDay(store, day)) {
            return Optional.empty();
        }

        GameDayLiveState existingState = gameDayStoreStateRedisRepository.find(store.getId(), day).orElse(null);
        if (existingState != null
                && existingState.startedAt() != null
                && existingState.startResponse() != null) {
            return Optional.of(existingState);
        }

        ensurePurchaseQueueInitialized(store);
        DayWindow currentTimeline = seasonTimelineService.day(store.getSeason(), day);
        DayStartBuild build = buildDayStartResponse(store, day, requestedAt, currentTimeline.businessStart());
        writeInitialState(
                store,
                day,
                build.openingState(),
                build.response(),
                currentTimeline.dayStart(),
                currentTimeline.businessStart(),
                build.openingSalePrice(),
                build.openingStock()
        );
        log.info(
                "start-day-restored storeId={} seasonId={} day={} requestedAt={}",
                store.getId(),
                store.getSeason().getId(),
                day,
                requestedAt
        );
        return gameDayStoreStateRedisRepository.find(store.getId(), day);
    }

    private Optional<GameDayLiveState> upsertCurrentDayState(
            Store store,
            LocalDateTime now,
            SeasonTimePoint seasonTimePoint,
            boolean forceRefresh
    ) {
        if (store == null || now == null || seasonTimePoint == null || !seasonTimePoint.isPlayableDayPhase()) {
            return Optional.empty();
        }

        int day = resolveCurrentDay(store.getSeason(), seasonTimePoint);
        if (!isPlayableDay(store, day)) {
            return Optional.empty();
        }

        GameDayLiveState existingState = gameDayStoreStateRedisRepository.find(store.getId(), day).orElse(null);
        if (!forceRefresh && existingState != null && existingState.startResponse() != null) {
            return Optional.of(existingState);
        }

        ensurePurchaseQueueInitialized(store);
        DayWindow currentTimeline = seasonTimelineService.day(store.getSeason(), day);
        LocalDateTime openingReferenceTime = seasonTimePoint.phase() == SeasonPhase.DAY_PREPARING
                ? now
                : currentTimeline.businessStart();
        LocalDateTime lastCalculatedAt = seasonTimePoint.phase() == SeasonPhase.DAY_PREPARING
                ? now
                : currentTimeline.businessStart();
        DayStartBuild build = buildDayStartResponse(store, day, now, openingReferenceTime);
        writeInitialState(
                store,
                day,
                build.openingState(),
                build.response(),
                currentTimeline.dayStart(),
                lastCalculatedAt,
                build.openingSalePrice(),
                build.openingStock()
        );
        return gameDayStoreStateRedisRepository.find(store.getId(), day);
    }

    private Store getActiveStore(Integer userId) {
        return storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(userId, SeasonStatus.IN_PROGRESS)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private int resolveCurrentDay(Season season, SeasonTimePoint seasonTimePoint) {
        Integer currentDay = seasonTimePoint.currentDay();
        if (currentDay == null || currentDay < 1 || currentDay > season.resolveRuntimePlayableDays()) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "Current season day is out of range.");
        }
        return currentDay;
    }

    private void validatePlayableDay(Store store, int day) {
        if (!isPlayableDay(store, day)) {
            int playableFromDay = store.getPlayableFromDay() == null ? 1 : store.getPlayableFromDay();
            throw new BaseException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "This store can join from day %d.".formatted(playableFromDay)
            );
        }
    }

    private boolean isPlayableDay(Store store, int day) {
        int playableFromDay = store.getPlayableFromDay() == null ? 1 : store.getPlayableFromDay();
        return day >= playableFromDay;
    }

    private DayStartBuild buildDayStartResponse(
            Store store,
            int day,
            LocalDateTime now,
            LocalDateTime openingReferenceTime
    ) {
        STORE_LOCATION_TRANSITION_SUPPORT.applyPendingLocationIfDue(store, openingReferenceTime);
        Menu plannedMenu = store.getMenu();
        Integer plannedPrice = resolveBaseSellingPrice(store, day);
        if (!Objects.equals(store.getPrice(), plannedPrice)) {
            store.changePrice(plannedPrice);
        }

        EnvironmentScheduleResolver.ResolvedEnvironment resolvedEnvironment = environmentScheduleResolver.resolve(
                store.getSeason().getId(),
                store.getLocation().getId(),
                day
        );
        DaySchedule daySchedule = resolvedEnvironment.daySchedule();
        List<Order> emergencyOrders = orderRepository.findByStoreIdAndOrderTypeOrderByArrivedTimeAscIdAsc(
                store.getId(),
                OrderType.EMERGENCY
        );
        applyArrivedEmergencyMenuAndPrice(store, emergencyOrders, openingReferenceTime);
        List<Store> seasonStores = storeRepository.findBySeason_IdOrderByIdAsc(store.getSeason().getId());
        STORE_LOCATION_TRANSITION_SUPPORT.applyPendingLocationIfDue(seasonStores, openingReferenceTime);
        NewsRankingResolver.PreviousDayRanking previousDayRanking = newsRankingResolver.resolve(store, day);
        List<GameDayStartResponse.EventSchedule> eventSchedule = eventScheduleResolver.resolve(
                store.getSeason().getId(),
                day,
                store.getLocation().getId(),
                store.getMenu().getId()
        );
        FestivalSummary festivalSummary = resolveFestivalSummary(eventSchedule, store.getLocation().getId());
        GameDayStartResponse.MarketSnapshot marketSnapshot = marketRankingPolicy.resolveSnapshot(
                store,
                seasonStores,
                daySchedule,
                festivalSummary.name(),
                festivalSummary.populationMultiplier(),
                previousDayRanking.areaEntryRank(),
                previousDayRanking.menuEntryRank()
        );
        List<Order> existingOrders = orderRepository.findDailyStartOrders(store.getId(), day);
        OpeningState openingState = rentPolicy.resolveStartingState(store, day, existingOrders, marketSnapshot);
        EmergencyOrderEngine.InventoryState openingInventory = resolveOpeningInventory(
                store,
                day,
                openingState,
                emergencyOrders,
                openingReferenceTime,
                plannedMenu,
                plannedPrice
        );
        if (openingInventory.menu() != null && !openingInventory.menu().equals(store.getMenu())) {
            store.changeMenu(openingInventory.menu());
        }
        int openingSalePrice = openingInventory.salePrice() == null ? store.getPrice() : openingInventory.salePrice();
        if (!Integer.valueOf(openingSalePrice).equals(store.getPrice())) {
            store.changePrice(openingSalePrice);
        }
        BigDecimal captureRate = captureRatePolicy.resolveStartingCaptureRate(
                marketSnapshot.priceBandMultiplier(),
                marketRankingPolicy.resolveTrendKeywordCaptureMultiplier(previousDayRanking.trendKeywordRank())
        );

        GameDayStartResponse response = new GameDayStartResponse(
                formatHour(BUSINESS_OPEN_HOUR),
                formatHour(BUSINESS_CLOSE_HOUR),
                daySchedule.hourlySchedule(),
                toWeatherLabel(resolvedEnvironment.weatherType()),
                resolvedEnvironment.weatherMultiplier(),
                daySchedule.dailyTrafficMultiplier(),
                captureRate,
                eventSchedule,
                openingState.initialBalance(),
                openingInventory.stock(),
                openingState.openingSummary(),
                marketSnapshot
        );

        log.info(
                "start-day-built storeId={} seasonId={} day={} weather={} captureRate={} initialBalance={} initialStock={} referenceTime={} requestedAt={}",
                store.getId(),
                store.getSeason().getId(),
                day,
                resolvedEnvironment.weatherType(),
                response.captureRate(),
                response.initialBalance(),
                response.initialStock(),
                openingReferenceTime,
                now
        );
        return new DayStartBuild(response, openingState, openingSalePrice, openingInventory.stock());
    }

    private Integer resolveBaseSellingPrice(Store store, int day) {
        return orderRepository
                .findFirstByStore_IdAndOrderedDayLessThanEqualAndSalePriceIsNotNullOrderByOrderedDayDescIdDesc(
                        store.getId(),
                        day
                )
                .map(Order::getSalePrice)
                .orElseGet(() -> store.getPrice() != null ? store.getPrice() : store.getMenu().getOriginPrice());
    }

    private void writeInitialState(
            Store store,
            int day,
            OpeningState openingState,
            GameDayStartResponse response,
            LocalDateTime startedAt,
            LocalDateTime lastCalculatedAt,
            int openingSalePrice,
            int openingStock
    ) {
        List<Integer> purchaseList = purchaseListGenerator.generate(
                response.hourlySchedule(),
                store.getPurchaseSeed(),
                store.getPurchaseCursor()
        );
        gameDayStoreStateRedisRepository.save(
                store.getId(),
                day,
                new GameDayLiveState(
                        startedAt,
                        purchaseList,
                        0,
                        response,
                        0,
                        response.marketSnapshot() == null ? null : response.marketSnapshot().regionStoreCount(),
                        0,
                        captureRatePolicy.normalizeCaptureRate(response.captureRate()),
                        openingSalePrice,
                        0,
                        List.of(),
                        0,
                        0L,
                        0,
                        0,
                        0L,
                        response.openingSummary() == null || response.openingSummary().fixedCostTotal() == null
                                ? 0L
                                : response.openingSummary().fixedCostTotal().longValue(),
                        0L,
                        (long) openingState.initialBalance(),
                        openingStock,
                        lastCalculatedAt
                )
        );
    }

    private void applyArrivedEmergencyMenuAndPrice(Store store, List<Order> emergencyOrders, LocalDateTime now) {
        Order latestArrivedEmergency = EMERGENCY_ORDER_ENGINE.resolveLatestArrivedOrderAt(emergencyOrders, now);
        if (latestArrivedEmergency == null) {
            return;
        }
        if (latestArrivedEmergency.getMenu() != null && !latestArrivedEmergency.getMenu().equals(store.getMenu())) {
            store.changeMenu(latestArrivedEmergency.getMenu());
        }
        if (latestArrivedEmergency.getSalePrice() != null && !latestArrivedEmergency.getSalePrice().equals(store.getPrice())) {
            store.changePrice(latestArrivedEmergency.getSalePrice());
        }
    }

    private EmergencyOrderEngine.InventoryState resolveOpeningInventory(
            Store store,
            int day,
            OpeningState openingState,
            List<Order> emergencyOrders,
            LocalDateTime now,
            Menu plannedMenu,
            Integer plannedPrice
    ) {
        if (emergencyOrders.isEmpty() || day <= 1) {
            return new EmergencyOrderEngine.InventoryState(
                    store.getMenu(),
                    openingState.initialStock(),
                    store.getPrice()
            );
        }

        LocalDateTime previousBusinessEnd = seasonTimelineService.day(store.getSeason(), day - 1).businessEnd();
        return EMERGENCY_ORDER_ENGINE.applyArrivalsBetween(
                new EmergencyOrderEngine.InventoryState(plannedMenu, openingState.initialStock(), plannedPrice),
                emergencyOrders,
                previousBusinessEnd,
                now
        );
    }

    private void ensurePurchaseQueueInitialized(Store store) {
        if (store.getPurchaseSeed() == null) {
            store.initializePurchaseQueue(purchaseListGenerator.issueSeed());
            return;
        }
        store.changePurchaseCursor(purchaseListGenerator.normalizeCursor(store.getPurchaseCursor()));
    }

    private FestivalSummary resolveFestivalSummary(
            List<GameDayStartResponse.EventSchedule> eventSchedule,
            Long locationId
    ) {
        for (GameDayStartResponse.EventSchedule schedule : eventSchedule) {
            if (schedule.scope() == null) {
                continue;
            }
            if (!locationId.equals(schedule.scope().region()) || schedule.scope().menu() != null) {
                continue;
            }
            return new FestivalSummary(schedule.type(), schedule.populationMultiplier());
        }
        return new FestivalSummary(null, null);
    }

    private String toWeatherLabel(WeatherType weatherType) {
        return weatherType.name();
    }

    private String formatHour(int hour) {
        return LocalTime.of(hour % 24, 0).format(TIME_FORMATTER);
    }

    private record FestivalSummary(
            String name,
            BigDecimal populationMultiplier
    ) {
    }

    private record DayStartBuild(
            GameDayStartResponse response,
            OpeningState openingState,
            int openingSalePrice,
            int openingStock
    ) {
    }
}
