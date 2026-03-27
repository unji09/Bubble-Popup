package com.ssafy.S14P21A205.game.day.service;

import com.ssafy.S14P21A205.action.dto.ActionStatusResponse;
import com.ssafy.S14P21A205.action.entity.ActionLog;
import com.ssafy.S14P21A205.action.repository.ActionLogRepository;
import com.ssafy.S14P21A205.game.day.dto.GameDayStartResponse;
import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.debug.TickDebugActionNote;
import com.ssafy.S14P21A205.game.day.dto.GameDayStartResponse;
import com.ssafy.S14P21A205.game.day.dto.GameStateResponse;
import com.ssafy.S14P21A205.game.day.engine.EmergencyOrderEngine;
import com.ssafy.S14P21A205.game.day.engine.StockEngine;
import com.ssafy.S14P21A205.game.day.policy.CaptureRatePolicy;
import com.ssafy.S14P21A205.game.day.policy.CostPolicy;
import com.ssafy.S14P21A205.game.day.policy.CustomerScorePolicy;
import com.ssafy.S14P21A205.game.day.policy.PopulationPolicy;
import com.ssafy.S14P21A205.game.day.resolver.EventEffectResolver;
import com.ssafy.S14P21A205.game.day.resolver.EventScheduleResolver;
import com.ssafy.S14P21A205.game.day.resolver.TrafficDelayResolver;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.time.model.DayWindow;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
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
import java.util.ArrayList;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameDayStateService {

    private static final SeasonTimelineService SEASON_TIMELINE_SERVICE = new SeasonTimelineService();
    private static final EmergencyOrderEngine EMERGENCY_ORDER_ENGINE = new EmergencyOrderEngine();
    private static final StoreLocationTransitionSupport STORE_LOCATION_TRANSITION_SUPPORT = new StoreLocationTransitionSupport();

    private final UserService userService;
    private final StoreRepository storeRepository;
    private final ActionLogRepository actionLogRepository;
    private final OrderRepository orderRepository;
    private final EventEffectResolver eventEffectResolver;
    private final StockEngine stockEngine;
    private final PopulationPolicy populationPolicy;
    private final CustomerScorePolicy customerScorePolicy;
    private final CaptureRatePolicy captureRatePolicy;
    private final CostPolicy costPolicy;
    private final TrafficDelayResolver trafficDelayResolver;
    private final EventScheduleResolver eventScheduleResolver;
    private final GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;
    private final GameDayStartService gameDayStartService;
    private final Clock clock;

    @Transactional
    public GameStateResponse getGameState(Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        Store store = getActiveStore(user.getId());
        return refreshGameState(store)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public Optional<GameStateResponse> refreshGameState(Store store) {
        LocalDateTime serverTime = LocalDateTime.now(clock);
        STORE_LOCATION_TRANSITION_SUPPORT.applyPendingLocationIfDue(store, serverTime);
        SeasonTimePoint seasonTimePoint = SEASON_TIMELINE_SERVICE.resolve(store.getSeason(), serverTime);
        if (!seasonTimePoint.isPlayableDayPhase()) {
            log.debug(
                    "state-skip storeId={} seasonId={} now={} phase={}",
                    store.getId(),
                    store.getSeason().getId(),
                    serverTime,
                    seasonTimePoint.phase()
            );
            return Optional.empty();
        }
        int day = resolveCurrentDay(store.getSeason(), seasonTimePoint);

        GameDayLiveState rawState = gameDayStoreStateRedisRepository.find(store.getId(), day)
                .orElse(null);
        if (rawState == null || rawState.startResponse() == null) {
            rawState = gameDayStartService.ensureCurrentDayState(store, serverTime, seasonTimePoint).orElse(null);
            if (rawState == null || rawState.startResponse() == null) {
                return Optional.empty();
            }
        }

        Order dailyStartOrder = orderRepository.findDailyStartOrder(store.getId(), day).orElse(null);
        List<Order> emergencyOrders = orderRepository.findByStoreIdAndOrderTypeOrderByArrivedTimeAscIdAsc(
                store.getId(),
                OrderType.EMERGENCY
        );
        List<Order> currentDayEmergencyOrders = orderRepository.findByStoreIdAndOrderedDayAndOrderTypeOrderByArrivedTimeAscIdAsc(
                store.getId(),
                day,
                OrderType.EMERGENCY
        );
        GameDayLiveState state = normalizeState(rawState, dailyStartOrder);
        DayWindow currentTimeline = SEASON_TIMELINE_SERVICE.day(store.getSeason(), day);

        LocalDateTime effectiveNow = min(serverTime, currentTimeline.reportEnd());
        int tick = stockEngine.resolveCurrentTick(currentTimeline, effectiveNow);
        log.info(
                "state-timeline storeId={} seasonId={} now={} phase={} day={} gameTime={} tick={} effectiveNow={}",
                store.getId(),
                store.getSeason().getId(),
                serverTime,
                seasonTimePoint.phase(),
                day,
                seasonTimePoint.gameTime(),
                tick,
                effectiveNow
        );
        ActionStatusResponse actionStatus = resolveActionStatus(store.getId(), day);
        long actionTotalCost = resolveActionTotalCost(store.getId(), day);
        int regionStoreCount = resolveRegionStoreCount(store, state, serverTime);

        CalculatedGameState calculatedState = calculateGameState(
                store,
                state,
                currentTimeline,
                tick,
                actionTotalCost,
                effectiveNow,
                dailyStartOrder,
                emergencyOrders,
                day,
                regionStoreCount
        );

        if (calculatedState.currentMenu() != null
                && (store.getMenu() == null || !calculatedState.currentMenu().equals(store.getMenu()))) {
            store.changeMenu(calculatedState.currentMenu());
        }
        syncEmergencyOrderSalePrice(store, emergencyOrders, effectiveNow);

        gameDayStoreStateRedisRepository.saveStateAndTickLog(store.getId(), day, calculatedState.liveState());
        log.info(
                "state-updated storeId={} seasonId={} day={} tick={} populationPerStore={} customerCount={} cash={} stock={}",
                store.getId(),
                store.getSeason().getId(),
                day,
                tick,
                calculatedState.populationPerStore(),
                calculatedState.liveState().cumulativeCustomerCount(),
                calculatedState.cash(),
                calculatedState.totalStock()
        );
        logTickDebug(
                store,
                state,
                seasonTimePoint,
                day,
                currentTimeline,
                effectiveNow,
                tick,
                regionStoreCount,
                emergencyOrders,
                calculatedState
        );
        TrafficDelayResolver.ResolvedTraffic resolvedTraffic = trafficDelayResolver.resolve(
                store.getSeason().getId(),
                store.getLocation().getId(),
                day,
                store.getSeason().resolveRuntimePlayableDays(),
                currentTimeline.dayStart(),
                effectiveNow
        );

        EmergencyOrderState currentDayEmergencyOrderState = resolveEmergencyOrderState(currentDayEmergencyOrders, effectiveNow);

        return Optional.of(new GameStateResponse(
                serverTime,
                store.getSeason().getId(),
                day,
                populationPolicy.resolvePopulationLabel(calculatedState.baseFloatingPopulation()),
                new GameStateResponse.Traffic(
                        resolvedTraffic.trafficStatus(),
                        resolvedTraffic.trafficStatus() == null ? null : resolvedTraffic.trafficStatus().getValue(),
                        resolvedTraffic.resolvedHour(),
                        resolvedTraffic.delaySeconds()
                ),
                effectiveNow,
                calculatedState.cash(),
                calculatedState.liveState().cumulativeCustomerCount(),
                new GameStateResponse.CustomerTick(
                        calculatedState.liveState().tick(),
                        calculatedState.tickCustomerCount(),
                        calculatedState.liveState().salePrice(),
                        calculatedState.liveState().tickSoldUnits() == null ? List.of() : calculatedState.liveState().tickSoldUnits(),
                        calculatedState.baseFloatingPopulation(),
                        calculatedState.populationGrowthRate(),
                        calculatedState.currentFloatingPopulation(),
                        calculatedState.regionStoreCount(),
                        calculatedState.rValue()
                ),
                resolveTodayEventSchedule(store, day),
                new GameStateResponse.Inventory(calculatedState.totalStock()),
                new GameStateResponse.ActionStatus(
                        actionStatus.discountUsed(),
                        actionStatus.donationUsed(),
                        actionStatus.promotionUsed(),
                        actionStatus.emergencyUsed(),
                        currentDayEmergencyOrderState.pending(),
                        currentDayEmergencyOrderState.pending() ? currentDayEmergencyOrderState.arriveAt() : null
                ),
                calculatedState.appliedEvents()
        ));
    }

    @Transactional
    public Optional<GameDayLiveState> restoreClosedDayState(Store store, int day) {
        if (store == null || day < 1 || store.getSeason() == null) {
            return Optional.empty();
        }

        int totalDays = store.getSeason().resolveRuntimePlayableDays();
        if (day > totalDays) {
            return Optional.empty();
        }

        DayWindow currentTimeline = SEASON_TIMELINE_SERVICE.day(store.getSeason(), day);
        LocalDateTime effectiveNow = currentTimeline.businessEnd();
        STORE_LOCATION_TRANSITION_SUPPORT.applyPendingLocationIfDue(store, day);

        GameDayLiveState rawState = gameDayStartService.restoreDayState(store, day, effectiveNow).orElse(null);
        if (rawState == null || rawState.startResponse() == null) {
            log.warn(
                    "state-restore-skipped storeId={} seasonId={} day={} reason=missing_start_state",
                    store.getId(),
                    store.getSeason().getId(),
                    day
            );
            return Optional.empty();
        }

        Order dailyStartOrder = orderRepository.findDailyStartOrder(store.getId(), day).orElse(null);
        List<Order> emergencyOrders = orderRepository.findByStoreIdAndOrderTypeOrderByArrivedTimeAscIdAsc(
                store.getId(),
                OrderType.EMERGENCY
        );
        GameDayLiveState state = normalizeState(rawState, dailyStartOrder);
        int tick = stockEngine.resolveCurrentTick(currentTimeline, effectiveNow);
        long actionTotalCost = resolveActionTotalCost(store.getId(), day);
        int regionStoreCount = resolveRegionStoreCount(store, state, effectiveNow);

        CalculatedGameState calculatedState = calculateGameState(
                store,
                state,
                currentTimeline,
                tick,
                actionTotalCost,
                effectiveNow,
                dailyStartOrder,
                emergencyOrders,
                day,
                regionStoreCount
        );

        gameDayStoreStateRedisRepository.saveStateAndTickLog(store.getId(), day, calculatedState.liveState());
        log.info(
                "state-restored storeId={} seasonId={} day={} tick={} cash={} stock={}",
                store.getId(),
                store.getSeason().getId(),
                day,
                tick,
                calculatedState.cash(),
                calculatedState.totalStock()
        );
        return Optional.of(calculatedState.liveState());
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

    private int resolveRegionStoreCount(Store store, GameDayLiveState state, LocalDateTime now) {
        if (state.regionStoreCount() != null && state.regionStoreCount() > 0) {
            return state.regionStoreCount();
        }

        if (state.startResponse() != null
                && state.startResponse().marketSnapshot() != null
                && state.startResponse().marketSnapshot().regionStoreCount() != null
                && state.startResponse().marketSnapshot().regionStoreCount() > 0) {
            return state.startResponse().marketSnapshot().regionStoreCount();
        }

        List<Store> seasonStores = storeRepository.findBySeason_IdOrderByIdAsc(store.getSeason().getId());
        STORE_LOCATION_TRANSITION_SUPPORT.applyPendingLocationIfDue(seasonStores, now);
        long currentLocationId = store.getLocation().getId();
        int resolvedCount = Math.max(
                1,
                Math.toIntExact(
                        seasonStores.stream()
                                .filter(seasonStore -> seasonStore.getLocation() != null)
                                .filter(seasonStore -> currentLocationId == seasonStore.getLocation().getId())
                                .count()
                )
        );
        log.info(
                "state-region-store-count-backfill storeId={} seasonId={} locationId={} regionStoreCount={}",
                store.getId(),
                store.getSeason().getId(),
                store.getLocation().getId(),
                resolvedCount
        );
        return resolvedCount;
    }

    private GameDayLiveState normalizeState(GameDayLiveState state, Order dailyStartOrder) {
        LocalDateTime startedAt = state.startedAt();
        if (startedAt == null) {
            startedAt = dailyStartOrder != null && dailyStartOrder.getCreatedAt() != null
                    ? dailyStartOrder.getCreatedAt()
                    : LocalDateTime.now(clock);
        }

        LocalDateTime lastCalculatedAt = state.lastCalculatedAt();
        if (lastCalculatedAt == null || lastCalculatedAt.isBefore(startedAt)) {
            lastCalculatedAt = startedAt;
        }

        Integer regionStoreCount = state.regionStoreCount();
        if ((regionStoreCount == null || regionStoreCount <= 0)
                && state.startResponse() != null
                && state.startResponse().marketSnapshot() != null
                && state.startResponse().marketSnapshot().regionStoreCount() != null
                && state.startResponse().marketSnapshot().regionStoreCount() > 0) {
            regionStoreCount = state.startResponse().marketSnapshot().regionStoreCount();
        }

        int purchaseCursor = state.purchaseCursor() == null ? 0 : state.purchaseCursor();
        return new GameDayLiveState(
                startedAt,
                state.purchaseList(),
                purchaseCursor,
                state.startResponse(),
                state.tick() == null ? 0 : state.tick(),
                regionStoreCount,
                state.populationPerStore() == null ? 0 : state.populationPerStore(),
                state.captureRate(),
                state.salePrice() == null ? 0 : state.salePrice(),
                state.tickCustomerCount() == null ? 0 : state.tickCustomerCount(),
                state.tickSoldUnits() == null ? List.of() : state.tickSoldUnits(),
                state.tickPurchaseCount() == null ? 0 : state.tickPurchaseCount(),
                state.tickSales() == null ? 0L : state.tickSales(),
                state.cumulativeCustomerCount() == null ? 0 : state.cumulativeCustomerCount(),
                state.cumulativePurchaseCount() == null ? 0 : state.cumulativePurchaseCount(),
                state.cumulativeSales() == null ? 0L : state.cumulativeSales(),
                state.cumulativeTotalCost() == null ? 0L : state.cumulativeTotalCost(),
                state.locationChangeCost() == null ? 0L : state.locationChangeCost(),
                state.balance() == null ? 0L : state.balance(),
                normalizeStock(state.stock() == null ? initialStockOf(state) : state.stock()),
                lastCalculatedAt
        );
    }

    private ActionStatusResponse resolveActionStatus(Long storeId, int day) {
        return ActionStatusResponse.fromActionLogs(
                actionLogRepository.findByStore_IdAndGameDayAndIsUsedTrue(storeId, day)
        );
    }

    private long resolveActionTotalCost(Long storeId, int day) {
        List<ActionLog> actionLogs = actionLogRepository.findByStore_IdAndGameDayAndIsUsedTrue(storeId, day);
        long totalCost = 0L;

        for (ActionLog actionLog : actionLogs) {
            if (actionLog.getAction() == null) {
                continue;
            }

            totalCost += actionLog.getAction().getCost() == null ? 0L : actionLog.getAction().getCost();
        }
        return totalCost;
    }

    private EmergencyOrderState resolveEmergencyOrderState(List<Order> emergencyOrders, LocalDateTime effectiveNow) {
        EmergencyOrderEngine.EmergencyOrderState resolved = EMERGENCY_ORDER_ENGINE.resolve(emergencyOrders, effectiveNow);
        return new EmergencyOrderState(
                resolved.pending(),
                resolved.arriveAt()
        );
    }

    private void syncEmergencyOrderSalePrice(Store store, List<Order> emergencyOrders, LocalDateTime effectiveNow) {
        Order latestArrivedEmergency = EMERGENCY_ORDER_ENGINE.resolveLatestArrivedOrderAt(emergencyOrders, effectiveNow);
        if (latestArrivedEmergency == null || latestArrivedEmergency.getSalePrice() == null) {
            return;
        }
        if (!latestArrivedEmergency.getSalePrice().equals(store.getPrice())) {
            store.changePrice(latestArrivedEmergency.getSalePrice());
        }
    }

    private CalculatedGameState calculateGameState(
            Store store,
            GameDayLiveState state,
            DayWindow currentTimeline,
            int tick,
            long actionTotalCost,
            LocalDateTime effectiveNow,
            Order dailyStartOrder,
            List<Order> emergencyOrders,
            int day,
            int regionStoreCount
    ) {
        BigDecimal captureRate = resolveLiveCaptureRate(state);
        ProgressionState progressionState = progressStateByTick(
                store,
                state,
                currentTimeline,
                tick,
                effectiveNow,
                captureRate,
                emergencyOrders,
                day,
                regionStoreCount
        );
        EventEffectResolver.EventEffect eventEffect = progressionState.currentEventEffect();
        EmergencyOrderState emergencyOrderState = progressionState.currentEmergencyOrderState();
        CostPolicy.CostResult costResult = costPolicy.calculate(
                store,
                dailyStartOrder,
                state.startResponse(),
                actionTotalCost,
                EMERGENCY_ORDER_ENGINE.resolveOrderedDayTotalCost(emergencyOrders, day),
                state.locationChangeCost() == null ? 0L : state.locationChangeCost(),
                eventEffect.capitalChange(),
                progressionState.cumulativeSales(),
                state.startResponse().initialBalance()
        );

        return new CalculatedGameState(
                costResult.cash(),
                progressionState.stock(),
                progressionState.populationPerStore(),
                progressionState.tickCustomerCount(),
                progressionState.currentPopulationSnapshot().baseFloatingPopulation(),
                progressionState.currentPopulationSnapshot().populationGrowthRate(),
                progressionState.currentPopulationSnapshot().currentFloatingPopulation(),
                regionStoreCount,
                progressionState.currentCustomerScore().rValue(),
                emergencyOrderState,
                eventEffect.appliedEvents(),
                eventEffect,
                progressionState.currentMenu(),
                new GameDayLiveState(
                        state.startedAt(),
                        state.purchaseList(),
                        progressionState.purchaseCursor(),
                        state.startResponse(),
                        tick,
                        regionStoreCount,
                        progressionState.populationPerStore(),
                        captureRate,
                        progressionState.salePrice(),
                        progressionState.tickCustomerCount(),
                        progressionState.tickSoldUnits(),
                        progressionState.tickPurchaseCount(),
                        progressionState.tickSales(),
                        progressionState.cumulativeCustomerCount(),
                        progressionState.cumulativePurchaseCount(),
                        progressionState.cumulativeSales(),
                        costResult.cumulativeTotalCost(),
                        state.locationChangeCost(),
                        costResult.cash(),
                        progressionState.stock(),
                        effectiveNow
                )
        );
    }

    private ProgressionState progressStateByTick(
            Store store,
            GameDayLiveState state,
            DayWindow currentTimeline,
            int currentTick,
            LocalDateTime effectiveNow,
            BigDecimal captureRate,
            List<Order> emergencyOrders,
            int day,
            int regionStoreCount
    ) {
        int processedTick = state.tick() == null ? 0 : state.tick();
        int purchaseCursor = state.purchaseCursor() == null ? 0 : state.purchaseCursor();
        int tickCustomerCount = state.tickCustomerCount() == null ? 0 : state.tickCustomerCount();
        List<Integer> tickSoldUnits = state.tickSoldUnits() == null ? List.of() : state.tickSoldUnits();
        int tickPurchaseCount = state.tickPurchaseCount() == null ? 0 : state.tickPurchaseCount();
        long tickSales = state.tickSales() == null ? 0L : state.tickSales();
        int cumulativeCustomerCount = state.cumulativeCustomerCount() == null ? 0 : state.cumulativeCustomerCount();
        int cumulativePurchaseCount = state.cumulativePurchaseCount() == null ? 0 : state.cumulativePurchaseCount();
        long cumulativeSales = state.cumulativeSales() == null ? 0L : state.cumulativeSales();
        int stock = normalizeStock(state.stock() == null ? initialStockOf(state) : state.stock());
        PopulationPolicy.PopulationSnapshot currentPopulationSnapshot = PopulationPolicy.PopulationSnapshot.empty();
        CustomerScorePolicy.CustomerScoreResult currentCustomerScore = CustomerScorePolicy.CustomerScoreResult.empty();
        int salePrice = state.salePrice() == null ? 0 : state.salePrice();
        Menu currentMenu = store.getMenu();
        EmergencyOrderEngine.InventoryState currentInventory = new EmergencyOrderEngine.InventoryState(
                currentMenu,
                stock,
                salePrice
        );

        LocalDateTime baselineTime = state.lastCalculatedAt();
        EventEffectResolver.EventEffect baselineEffect = resolveEventEffect(store, day, baselineTime, currentMenu);

        for (int nextTick = processedTick + 1; nextTick <= currentTick; nextTick++) {
            LocalDateTime tickBoundary = stockEngine.resolveTickBoundary(currentTimeline, nextTick);
            EmergencyOrderEngine.InventoryState tickInventory = EMERGENCY_ORDER_ENGINE.applyArrivalsBetween(
                    currentInventory,
                    emergencyOrders,
                    baselineTime,
                    tickBoundary
            );
            currentMenu = tickInventory.menu() == null ? currentMenu : tickInventory.menu();
            salePrice = tickInventory.salePrice() == null ? salePrice : tickInventory.salePrice();
            EventEffectResolver.EventEffect tickEffect = resolveEventEffect(store, day, tickBoundary, currentMenu);

            PopulationPolicy.PopulationSnapshot populationSnapshot = resolvePopulationSnapshot(
                    state,
                    currentTimeline,
                    tickEffect.populationEventMultiplier(),
                    tickBoundary
            );
            CustomerScorePolicy.CustomerScoreResult customerScore = resolveCustomerScore(
                    store,
                    day,
                    nextTick,
                    populationSnapshot,
                    regionStoreCount,
                    captureRate
            );
            int desiredCustomerCount = customerScore.customerCount();
            int nextCursor = stockEngine.advancePurchaseCursor(state.purchaseList(), purchaseCursor, desiredCustomerCount);
            int actualCustomerCount = Math.max(0, nextCursor - purchaseCursor);
            int availableStock = Math.max(
                    0,
                    tickInventory.stock() + applyStockEventDelta(tickInventory.stock(), baselineEffect, tickEffect)
            );
            TickSoldUnitsManifest tickManifest = buildTickSoldUnitsManifest(
                    state.purchaseList(),
                    purchaseCursor,
                    nextCursor,
                    availableStock
            );
            int soldUnits = tickManifest.totalSoldUnits();

            tickCustomerCount = actualCustomerCount;
            tickSoldUnits = tickManifest.soldUnits();
            tickPurchaseCount = soldUnits;
            tickSales = Math.multiplyExact((long) soldUnits, valueOf(salePrice));
            cumulativeCustomerCount += actualCustomerCount;
            cumulativePurchaseCount += soldUnits;
            cumulativeSales += tickSales;
            stock = Math.max(0, availableStock - soldUnits);
            purchaseCursor = nextCursor;
            currentPopulationSnapshot = populationSnapshot;
            currentCustomerScore = customerScore;
            currentInventory = new EmergencyOrderEngine.InventoryState(currentMenu, stock, salePrice);
            baselineEffect = tickEffect;
            baselineTime = tickBoundary;
        }

        EmergencyOrderEngine.InventoryState currentInventoryAtNow = EMERGENCY_ORDER_ENGINE.applyArrivalsBetween(
                currentInventory,
                emergencyOrders,
                baselineTime,
                effectiveNow
        );
        currentMenu = currentInventoryAtNow.menu() == null ? currentMenu : currentInventoryAtNow.menu();
        salePrice = currentInventoryAtNow.salePrice() == null ? salePrice : currentInventoryAtNow.salePrice();
        EventEffectResolver.EventEffect currentEffect = resolveEventEffect(store, day, effectiveNow, currentMenu);
        EmergencyOrderState currentEmergency = resolveEmergencyOrderState(emergencyOrders, effectiveNow);
        int stockNow = Math.max(
                0,
                currentInventoryAtNow.stock()
                        + applyStockEventDelta(currentInventoryAtNow.stock(), baselineEffect, currentEffect)
        );
        currentPopulationSnapshot = resolvePopulationSnapshot(
                state,
                currentTimeline,
                currentEffect.populationEventMultiplier(),
                effectiveNow
        );
        currentCustomerScore = resolveCustomerScore(
                store,
                day,
                currentTick,
                currentPopulationSnapshot,
                regionStoreCount,
                captureRate
        );
        int currentPopulationPerStore = currentCustomerScore.populationPerStore();

        return new ProgressionState(
                purchaseCursor,
                tickCustomerCount,
                tickSoldUnits,
                tickPurchaseCount,
                tickSales,
                cumulativeCustomerCount,
                cumulativePurchaseCount,
                cumulativeSales,
                salePrice,
                currentMenu,
                stockNow,
                currentPopulationPerStore,
                currentPopulationSnapshot,
                currentCustomerScore,
                currentEffect,
                currentEmergency
        );
    }

    private EventEffectResolver.EventEffect resolveEventEffect(Store store, int day, LocalDateTime effectiveNow, Menu menu) {
        return eventEffectResolver.resolve(
                store.getSeason(),
                day,
                effectiveNow,
                store.getLocation().getId(),
                menu == null ? store.getMenu().getId() : menu.getId()
        );
    }

    private BigDecimal resolveLiveCaptureRate(GameDayLiveState state) {
        if (state.captureRate() != null) {
            return captureRatePolicy.normalizeCaptureRate(state.captureRate());
        }
        if (state.startResponse() != null && state.startResponse().captureRate() != null) {
            return captureRatePolicy.normalizeCaptureRate(state.startResponse().captureRate());
        }
        return captureRatePolicy.normalizeCaptureRate(BigDecimal.ZERO);
    }

    private PopulationPolicy.PopulationSnapshot resolvePopulationSnapshot(
            GameDayLiveState state,
            DayWindow currentTimeline,
            BigDecimal populationEventMultiplier,
            LocalDateTime effectiveNow
    ) {
        return populationPolicy.resolvePopulationSnapshot(
                state.startResponse(),
                currentTimeline,
                populationEventMultiplier,
                effectiveNow
        );
    }

    private CustomerScorePolicy.CustomerScoreResult resolveCustomerScore(
            Store store,
            int day,
            int tick,
            PopulationPolicy.PopulationSnapshot populationSnapshot,
            int regionStoreCount,
            BigDecimal captureRate
    ) {
        if (regionStoreCount <= 0) {
            log.warn(
                    "state-customer-score-invalid-region-store-count storeId={} day={} tick={} regionStoreCount={}",
                    store.getId(),
                    day,
                    tick,
                    regionStoreCount
            );
            return CustomerScorePolicy.CustomerScoreResult.empty();
        }
        return customerScorePolicy.calculate(populationSnapshot, regionStoreCount, captureRate);
    }

    private int applyStockEventDelta(
            int currentStock,
            EventEffectResolver.EventEffect previous,
            EventEffectResolver.EventEffect current
    ) {
        int adjustedStock = currentStock + (current.stockChange() - previous.stockChange());
        Set<Long> previouslyAppliedEventIds = new HashSet<>();
        for (EventEffectResolver.StockRateEvent stockRateEvent : previous.appliedStockRateEvents()) {
            if (stockRateEvent.dailyEventId() != null) {
                previouslyAppliedEventIds.add(stockRateEvent.dailyEventId());
            }
        }

        for (EventEffectResolver.StockRateEvent stockRateEvent : current.appliedStockRateEvents()) {
            Long dailyEventId = stockRateEvent.dailyEventId();
            if (dailyEventId != null && previouslyAppliedEventIds.contains(dailyEventId)) {
                continue;
            }
            adjustedStock = BigDecimal.valueOf(adjustedStock)
                    .multiply(stockRateEvent.stockRate())
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
        }
        return adjustedStock - currentStock;
    }

    private TickSoldUnitsManifest buildTickSoldUnitsManifest(
            List<Integer> purchaseList,
            int fromCursor,
            int toCursor,
            int availableStock
    ) {
        if (purchaseList == null || purchaseList.isEmpty() || toCursor <= fromCursor) {
            return TickSoldUnitsManifest.empty();
        }

        int start = Math.max(0, Math.min(fromCursor, purchaseList.size()));
        int end = Math.max(start, Math.min(toCursor, purchaseList.size()));
        if (start >= end) {
            return TickSoldUnitsManifest.empty();
        }

        int remainingStock = Math.max(0, availableStock);
        int totalSoldUnits = 0;
        List<Integer> soldUnits = new ArrayList<>(end - start);

        for (int index = start; index < end; index++) {
            Integer requestedUnits = purchaseList.get(index);
            int requested = requestedUnits == null ? 0 : Math.max(0, requestedUnits);
            int sold = Math.min(requested, remainingStock);
            soldUnits.add(sold);
            remainingStock -= sold;
            totalSoldUnits += sold;
        }

        return new TickSoldUnitsManifest(List.copyOf(soldUnits), totalSoldUnits);
    }

    private int initialStockOf(GameDayLiveState state) {
        return normalizeStock(
                state.startResponse() == null || state.startResponse().initialStock() == null
                        ? 0
                        : state.startResponse().initialStock()
        );
    }

    private int normalizeStock(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private List<GameDayStartResponse.EventSchedule> resolveTodayEventSchedule(Store store, int day) {
        if (store == null || store.getSeason() == null || store.getSeason().getId() == null || day < 1) {
            return List.of();
        }
        return eventScheduleResolver.resolveAll(store.getSeason().getId(), day);
    }

    private long valueOf(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private int safeToInt(long value) {
        return Math.toIntExact(Math.max(0L, value));
    }

    private LocalDateTime min(LocalDateTime left, LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }

    private void logTickDebug(
            Store store,
            GameDayLiveState previousState,
            SeasonTimePoint seasonTimePoint,
            int day,
            DayWindow currentTimeline,
            LocalDateTime effectiveNow,
            int tick,
            int regionStoreCount,
            List<Order> emergencyOrders,
            CalculatedGameState calculatedState
    ) {
        if (store == null
                || previousState == null
                || calculatedState == null
                || !shouldLogTickDebug(previousState, currentTimeline, tick)) {
            return;
        }

        List<TickDebugActionNote> actionNotes = Optional.ofNullable(
                        gameDayStoreStateRedisRepository.findTickDebugActionNotes(store.getId(), day, tick)
                )
                .orElse(List.of());
        String eventSummary = resolveCurrentTickEventSummary(previousState.lastCalculatedAt(), effectiveNow, calculatedState.appliedEvents());
        String actionSummary = resolveCurrentTickActionSummary(actionNotes);

        GameDayStartResponse.HourlySchedule currentSchedule = resolveCurrentHourlySchedule(
                previousState.startResponse(),
                currentTimeline,
                effectiveNow
        );

        long previousCash = previousState.balance() != null
                ? previousState.balance()
                : previousState.startResponse() == null || previousState.startResponse().initialBalance() == null
                ? 0L
                : previousState.startResponse().initialBalance().longValue();
        int previousStock = normalizeStock(
                previousState.stock() != null
                        ? previousState.stock()
                        : initialStockOf(previousState)
        );

        int emergencyArrivedQuantity = resolveEmergencyArrivedQuantity(previousState.lastCalculatedAt(), effectiveNow, emergencyOrders);
        int regularOrderQuantity = tick == 0 && previousState.startResponse() != null && previousState.startResponse().openingSummary() != null
                && previousState.startResponse().openingSummary().regularOrderQuantity() != null
                ? previousState.startResponse().openingSummary().regularOrderQuantity()
                : 0;
        int disposalQuantity = tick == 0 && previousState.startResponse() != null && previousState.startResponse().openingSummary() != null
                && previousState.startResponse().openingSummary().disposalQuantity() != null
                ? previousState.startResponse().openingSummary().disposalQuantity()
                : 0;

        long promotionCost = sumPromotionCost(actionNotes);
        long discountCost = sumDiscountCost(actionNotes);
        long emergencyOrderCost = sumEmergencyOrderCost(actionNotes);
        long donationCost = sumDonationCost(actionNotes);
        long moveCost = sumMoveCost(actionNotes);
        int donationStockDelta = sumDonationStockDelta(actionNotes);

        String customerFactors = joinFactors(
                "기본 유동인구=%d".formatted(calculatedState.baseFloatingPopulation()),
                "날씨 배수=%s".formatted(normalizeScale(previousState.startResponse() == null
                        ? BigDecimal.ONE
                        : previousState.startResponse().weatherMultiplier())),
                "교통 배수=%s".formatted(normalizeScale(currentSchedule == null ? BigDecimal.ONE : currentSchedule.trafficMultiplier())),
                "이벤트 배수=%s".formatted(normalizeScale(resolveEventMultiplier(currentSchedule, calculatedState.currentEventEffect()))),
                "지역 점포 나눗값=%d".formatted(Math.max(1, Math.min(regionStoreCount, 5))),
                "캡처율=%s".formatted(normalizeScale(resolveLiveCaptureRate(previousState))),
                "최종 반올림 결과=%d".formatted(calculatedState.tickCustomerCount())
        );
        String stockFactors = joinFactors(
                "직전 재고=%d".formatted(previousStock),
                "이번 틱 판매량=%d".formatted(-Math.max(0, calculatedState.liveState().tickPurchaseCount() == null
                        ? 0
                        : calculatedState.liveState().tickPurchaseCount())),
                "긴급발주 도착=%d".formatted(emergencyArrivedQuantity),
                "정규발주 반영=%d".formatted(regularOrderQuantity),
                "나눔 차감=%d".formatted(-Math.max(0, donationStockDelta)),
                "폐기 차감=%d".formatted(-Math.max(0, disposalQuantity))
        );
        String cashFactors = joinFactors(
                "직전 잔액=%d".formatted(previousCash),
                "이번 틱 판매금액=%+d".formatted(calculatedState.liveState().tickSales() == null ? 0L : calculatedState.liveState().tickSales()),
                "홍보 비용=%d".formatted(-promotionCost),
                "할인 비용=%d".formatted(-discountCost),
                "긴급발주 비용=%d".formatted(-emergencyOrderCost),
                "나눔 비용=%d".formatted(-donationCost),
                "이동 비용=%d".formatted(-moveCost)
        );

        log.info(
                "\n================ 틱 디버그 ================\n"
                        + "현재 day     : {}\n"
                        + "현재 phase   : {}\n"
                        + "현재 tick    : {}\n"
                        + "게임 시간    : {}\n"
                        + "\n"
                        + "이번 틱 이벤트 : {}\n"
                        + "이번 틱 액션   : {}\n"
                        + "\n"
                        + "손님\n"
                        + "- 최종값      : {}명\n"
                        + "- 영향요소    : {}\n"
                        + "\n"
                        + "재고\n"
                        + "- 최종값      : {}개\n"
                        + "- 영향요소    : {}\n"
                        + "\n"
                        + "잔액\n"
                        + "- 최종값      : {}원\n"
                        + "- 영향요소    : {}\n"
                        + "==========================================",
                day,
                seasonTimePoint == null || seasonTimePoint.phase() == null ? "-" : seasonTimePoint.phase().name(),
                tick,
                resolveTickDebugGameTime(currentTimeline, tick),
                eventSummary,
                actionSummary,
                calculatedState.tickCustomerCount(),
                customerFactors,
                calculatedState.totalStock(),
                stockFactors,
                calculatedState.cash(),
                cashFactors
        );
    }

    private boolean shouldLogTickDebug(GameDayLiveState previousState, DayWindow currentTimeline, int tick) {
        if (previousState == null || currentTimeline == null) {
            return false;
        }

        int previousTick = previousState.tick() == null ? 0 : previousState.tick();
        if (tick > previousTick) {
            return true;
        }

        if (tick != 0) {
            return false;
        }

        LocalDateTime previousCalculatedAt = previousState.lastCalculatedAt();
        return previousCalculatedAt == null || !previousCalculatedAt.isAfter(currentTimeline.businessStart());
    }

    private String resolveTickDebugGameTime(DayWindow currentTimeline, int tick) {
        if (currentTimeline == null || tick < 0) {
            return "-";
        }

        LocalDateTime tickBoundary = stockEngine.resolveTickBoundary(currentTimeline, tick);
        int offsetSeconds = Math.max(0, (int) Duration.between(currentTimeline.businessStart(), tickBoundary).toSeconds());
        return SEASON_TIMELINE_SERVICE.formatGameTime(offsetSeconds);
    }

    private String resolveCurrentTickEventSummary(
            LocalDateTime previousCalculatedAt,
            LocalDateTime effectiveNow,
            List<GameStateResponse.AppliedEvent> appliedEvents
    ) {
        if (appliedEvents == null || appliedEvents.isEmpty()) {
            return "없음";
        }
        List<String> currentTickEvents = appliedEvents.stream()
                .filter(event -> event != null && event.appliedAt() != null)
                .filter(event -> isWithinRange(previousCalculatedAt, effectiveNow, event.appliedAt()))
                .map(event -> event.eventName() == null || event.eventName().isBlank() ? event.eventType() : event.eventName())
                .distinct()
                .toList();
        return currentTickEvents.isEmpty() ? "없음" : String.join(", ", currentTickEvents);
    }

    private String resolveCurrentTickActionSummary(List<TickDebugActionNote> actionNotes) {
        if (actionNotes == null || actionNotes.isEmpty()) {
            return "없음";
        }
        List<String> labels = actionNotes.stream()
                .filter(note -> note != null && note.actionLabel() != null && !note.actionLabel().isBlank())
                .map(TickDebugActionNote::actionLabel)
                .distinct()
                .toList();
        return labels.isEmpty() ? "없음" : String.join(", ", labels);
    }

    private GameDayStartResponse.HourlySchedule resolveCurrentHourlySchedule(
            GameDayStartResponse startResponse,
            DayWindow currentTimeline,
            LocalDateTime effectiveNow
    ) {
        if (startResponse == null || startResponse.hourlySchedule() == null || startResponse.hourlySchedule().isEmpty()) {
            return null;
        }

        if (!effectiveNow.isAfter(currentTimeline.businessStart()) || !effectiveNow.isBefore(currentTimeline.businessEnd())) {
            return startResponse.hourlySchedule().values().stream().findFirst().orElse(null);
        }

        List<GameDayStartResponse.HourlySchedule> schedules = new ArrayList<>(startResponse.hourlySchedule().values());
        long totalMillis = SEASON_TIMELINE_SERVICE.businessDuration().toMillis();
        long elapsedMillis = java.time.Duration.between(currentTimeline.businessStart(), effectiveNow).toMillis();
        long boundedElapsedMillis = Math.max(0L, Math.min(elapsedMillis, totalMillis));
        int scheduleIndex = (int) Math.min(
                schedules.size() - 1L,
                (boundedElapsedMillis * schedules.size()) / totalMillis
        );
        return schedules.get(scheduleIndex);
    }

    private BigDecimal resolveEventMultiplier(
            GameDayStartResponse.HourlySchedule currentSchedule,
            EventEffectResolver.EventEffect currentEventEffect
    ) {
        BigDecimal scheduleMultiplier = currentSchedule == null || currentSchedule.eventMultiplier() == null
                ? BigDecimal.ONE
                : currentSchedule.eventMultiplier();
        BigDecimal eventMultiplier = currentEventEffect == null || currentEventEffect.populationEventMultiplier() == null
                ? BigDecimal.ONE
                : currentEventEffect.populationEventMultiplier();
        return normalizeScale(scheduleMultiplier.multiply(eventMultiplier));
    }

    private int resolveEmergencyArrivedQuantity(
            LocalDateTime previousCalculatedAt,
            LocalDateTime effectiveNow,
            List<Order> emergencyOrders
    ) {
        if (emergencyOrders == null || emergencyOrders.isEmpty()) {
            return 0;
        }
        return emergencyOrders.stream()
                .filter(order -> order != null && order.getArrivedTime() != null)
                .filter(order -> isWithinRange(previousCalculatedAt, effectiveNow, order.getArrivedTime()))
                .mapToInt(order -> order.getQuantity() == null ? 0 : order.getQuantity())
                .sum();
    }

    private boolean isWithinRange(LocalDateTime previousCalculatedAt, LocalDateTime effectiveNow, LocalDateTime targetTime) {
        if (targetTime == null || effectiveNow == null) {
            return false;
        }
        if (previousCalculatedAt == null) {
            return !targetTime.isAfter(effectiveNow);
        }
        return targetTime.isAfter(previousCalculatedAt) && !targetTime.isAfter(effectiveNow);
    }

    private long sumPromotionCost(List<TickDebugActionNote> actionNotes) {
        return sumLong(actionNotes, TickDebugActionNote::promotionCost);
    }

    private long sumDiscountCost(List<TickDebugActionNote> actionNotes) {
        return sumLong(actionNotes, TickDebugActionNote::discountCost);
    }

    private long sumEmergencyOrderCost(List<TickDebugActionNote> actionNotes) {
        return sumLong(actionNotes, TickDebugActionNote::emergencyOrderCost);
    }

    private long sumDonationCost(List<TickDebugActionNote> actionNotes) {
        return sumLong(actionNotes, TickDebugActionNote::donationCost);
    }

    private long sumMoveCost(List<TickDebugActionNote> actionNotes) {
        return sumLong(actionNotes, TickDebugActionNote::moveCost);
    }

    private int sumDonationStockDelta(List<TickDebugActionNote> actionNotes) {
        if (actionNotes == null || actionNotes.isEmpty()) {
            return 0;
        }
        return actionNotes.stream()
                .filter(note -> note != null && note.donationStockDelta() != null)
                .mapToInt(TickDebugActionNote::donationStockDelta)
                .sum();
    }

    private long sumLong(List<TickDebugActionNote> actionNotes, java.util.function.Function<TickDebugActionNote, Long> extractor) {
        if (actionNotes == null || actionNotes.isEmpty()) {
            return 0L;
        }
        return actionNotes.stream()
                .filter(note -> note != null)
                .map(extractor)
                .filter(value -> value != null)
                .mapToLong(Long::longValue)
                .sum();
    }

    private String joinFactors(String... factors) {
        List<String> values = new ArrayList<>();
        for (String factor : factors) {
            if (factor == null || factor.isBlank()) {
                continue;
            }
            values.add(factor);
        }
        return values.isEmpty() ? "-" : String.join(" / ", values);
    }

    private BigDecimal normalizeScale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record EmergencyOrderState(
            boolean pending,
            LocalDateTime arriveAt
    ) {
    }

    private record CalculatedGameState(
            long cash,
            int totalStock,
            int populationPerStore,
            int tickCustomerCount,
            int baseFloatingPopulation,
            BigDecimal populationGrowthRate,
            int currentFloatingPopulation,
            int regionStoreCount,
            BigDecimal rValue,
            EmergencyOrderState emergencyOrderState,
            List<GameStateResponse.AppliedEvent> appliedEvents,
            EventEffectResolver.EventEffect currentEventEffect,
            Menu currentMenu,
            GameDayLiveState liveState
    ) {
    }

    private record ProgressionState(
            int purchaseCursor,
            int tickCustomerCount,
            List<Integer> tickSoldUnits,
            int tickPurchaseCount,
            long tickSales,
            int cumulativeCustomerCount,
            int cumulativePurchaseCount,
            long cumulativeSales,
            int salePrice,
            Menu currentMenu,
            int stock,
            int populationPerStore,
            PopulationPolicy.PopulationSnapshot currentPopulationSnapshot,
            CustomerScorePolicy.CustomerScoreResult currentCustomerScore,
            EventEffectResolver.EventEffect currentEventEffect,
            EmergencyOrderState currentEmergencyOrderState
    ) {
    }

    private record TickSoldUnitsManifest(
            List<Integer> soldUnits,
            int totalSoldUnits
    ) {
        private static TickSoldUnitsManifest empty() {
            return new TickSoldUnitsManifest(List.of(), 0);
        }
    }
}

