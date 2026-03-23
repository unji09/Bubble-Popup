package com.ssafy.S14P21A205.game.day.service;

import com.ssafy.S14P21A205.action.dto.ActionStatusResponse;
import com.ssafy.S14P21A205.action.entity.ActionLog;
import com.ssafy.S14P21A205.action.repository.ActionLogRepository;
import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.dto.GameStateResponse;
import com.ssafy.S14P21A205.game.day.engine.EmergencyOrderEngine;
import com.ssafy.S14P21A205.game.day.engine.StockEngine;
import com.ssafy.S14P21A205.game.day.policy.CaptureRatePolicy;
import com.ssafy.S14P21A205.game.day.policy.CostPolicy;
import com.ssafy.S14P21A205.game.day.policy.CustomerScorePolicy;
import com.ssafy.S14P21A205.game.day.policy.PopulationPolicy;
import com.ssafy.S14P21A205.game.day.resolver.EventEffectResolver;
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

        if (calculatedState.liveState().salePrice() != null
                && !calculatedState.liveState().salePrice().equals(store.getPrice())) {
            store.changePrice(calculatedState.liveState().salePrice());
        }
        if (calculatedState.currentMenu() != null
                && (store.getMenu() == null || !calculatedState.currentMenu().equals(store.getMenu()))) {
            store.changeMenu(calculatedState.currentMenu());
        }

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
        TrafficDelayResolver.ResolvedTraffic resolvedTraffic = trafficDelayResolver.resolve(
                store.getSeason().getId(),
                store.getLocation().getId(),
                day,
                store.getSeason().getTotalDays(),
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

    private Store getActiveStore(Integer userId) {
        return storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(userId, SeasonStatus.IN_PROGRESS)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private int resolveCurrentDay(Season season, SeasonTimePoint seasonTimePoint) {
        Integer currentDay = seasonTimePoint.currentDay();
        if (currentDay == null || currentDay < 1 || currentDay > season.getTotalDays()) {
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
                state.stock() == null ? initialStockOf(state) : state.stock(),
                lastCalculatedAt
        );
    }

    private ActionStatusResponse resolveActionStatus(Long storeId, int day) {
        java.util.Map<String, Boolean> actions = gameDayStoreStateRedisRepository.getActions(storeId, day);
        return ActionStatusResponse.from(actions == null ? java.util.Map.of() : actions);
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
        int stock = state.stock() == null ? initialStockOf(state) : state.stock();
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
            stock = availableStock - soldUnits;
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
        return state.startResponse() == null || state.startResponse().initialStock() == null
                ? 0
                : state.startResponse().initialStock();
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

