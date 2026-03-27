package com.ssafy.S14P21A205.action.service;

import com.ssafy.S14P21A205.action.dto.ActionResponse;
import com.ssafy.S14P21A205.action.dto.ActionStatusResponse;
import com.ssafy.S14P21A205.action.dto.DiscountRequest;
import com.ssafy.S14P21A205.action.dto.DiscountResponse;
import com.ssafy.S14P21A205.action.dto.DonationRequest;
import com.ssafy.S14P21A205.action.dto.DonationResponse;
import com.ssafy.S14P21A205.action.dto.EmergencyOrderRequest;
import com.ssafy.S14P21A205.action.dto.EmergencyOrderResponse;
import com.ssafy.S14P21A205.action.dto.PromotionPriceResponse;
import com.ssafy.S14P21A205.action.dto.PromotionRequest;
import com.ssafy.S14P21A205.action.entity.Action;
import com.ssafy.S14P21A205.action.entity.ActionCategory;
import com.ssafy.S14P21A205.action.entity.ActionLog;
import com.ssafy.S14P21A205.action.repository.ActionLogRepository;
import com.ssafy.S14P21A205.action.repository.ActionRepository;
import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.debug.TickDebugActionNote;
import com.ssafy.S14P21A205.game.day.service.GameDayStateService;
import com.ssafy.S14P21A205.game.day.service.GameDayStartService;
import com.ssafy.S14P21A205.game.day.policy.CaptureRatePolicy;
import com.ssafy.S14P21A205.game.day.policy.StoreRankingPolicy;
import com.ssafy.S14P21A205.game.day.resolver.EventEffectResolver;
import com.ssafy.S14P21A205.game.day.resolver.NewsRankingResolver;
import com.ssafy.S14P21A205.game.day.resolver.TrafficDelayResolver;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.time.model.SeasonPhase;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import com.ssafy.S14P21A205.order.entity.Order;
import com.ssafy.S14P21A205.order.repository.OrderRepository;
import com.ssafy.S14P21A205.shop.entity.ItemCategory;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.shop.repository.ItemUserRepository;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.MenuRepository;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.store.service.StoreLocationTransitionSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActionServiceImpl implements ActionService {

    private static final BigDecimal EMERGENCY_COST_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal RECOMMENDED_PRICE_MULTIPLIER = new BigDecimal("2.5");
    private static final BigDecimal PRICE_RANGE_UPPER = new BigDecimal("1.10");
    private static final BigDecimal PRICE_RANGE_LOWER = new BigDecimal("0.90");
    private static final BigDecimal MULTIPLIER_AVERAGE = new BigDecimal("1.00");
    private static final BigDecimal MULTIPLIER_ABOVE_200 = new BigDecimal("0.01");
    private static final BigDecimal MULTIPLIER_ABOVE_190 = new BigDecimal("0.10");
    private static final BigDecimal MULTIPLIER_ABOVE_180 = new BigDecimal("0.20");
    private static final BigDecimal MULTIPLIER_ABOVE_170 = new BigDecimal("0.30");
    private static final BigDecimal MULTIPLIER_ABOVE_160 = new BigDecimal("0.40");
    private static final BigDecimal MULTIPLIER_ABOVE_150 = new BigDecimal("0.50");
    private static final BigDecimal MULTIPLIER_ABOVE_140 = new BigDecimal("0.60");
    private static final BigDecimal MULTIPLIER_ABOVE_130 = new BigDecimal("0.70");
    private static final BigDecimal MULTIPLIER_ABOVE_120 = new BigDecimal("0.80");
    private static final BigDecimal MULTIPLIER_BELOW_80 = new BigDecimal("1.20");
    private static final BigDecimal MULTIPLIER_BELOW_70 = new BigDecimal("1.30");
    private static final BigDecimal MULTIPLIER_BELOW_60 = new BigDecimal("1.40");
    private static final BigDecimal RATIO_200 = new BigDecimal("2.00");
    private static final BigDecimal RATIO_190 = new BigDecimal("1.90");
    private static final BigDecimal RATIO_180 = new BigDecimal("1.80");
    private static final BigDecimal RATIO_170 = new BigDecimal("1.70");
    private static final BigDecimal RATIO_160 = new BigDecimal("1.60");
    private static final BigDecimal RATIO_150 = new BigDecimal("1.50");
    private static final BigDecimal RATIO_140 = new BigDecimal("1.40");
    private static final BigDecimal RATIO_130 = new BigDecimal("1.30");
    private static final BigDecimal RATIO_120 = new BigDecimal("1.20");
    private static final BigDecimal RATIO_80 = new BigDecimal("0.80");
    private static final BigDecimal RATIO_70 = new BigDecimal("0.70");
    private static final BigDecimal RATIO_60 = new BigDecimal("0.60");
    private static final BigDecimal DONATION_CAPTURE_RATE_BONUS = new BigDecimal("0.10");
    private static final StoreLocationTransitionSupport STORE_LOCATION_TRANSITION_SUPPORT = new StoreLocationTransitionSupport();
    private static final String ACTION_FIELD_DISCOUNT = "discount";
    private static final String ACTION_FIELD_DONATION = "donation";
    private static final String ACTION_FIELD_EMERGENCY = "emergency";
    private static final String ACTION_FIELD_PROMOTION = "promotion";

    private final GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;
    private final ActionRepository actionRepository;
    private final ActionLogRepository actionLogRepository;
    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final ItemUserRepository itemUserRepository;
    private final EventEffectResolver eventEffectResolver;
    private final TrafficDelayResolver trafficDelayResolver;
    private final StoreRankingPolicy storeRankingPolicy;
    private final NewsRankingResolver newsRankingResolver;
    private final CaptureRatePolicy captureRatePolicy;
    private final GameDayStateService gameDayStateService;
    private final GameDayStartService gameDayStartService;
    private final Clock clock;

    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();

    @Override
    public ActionStatusResponse getActionStatus(Integer userId) {
        Store store = findStore(userId);
        int day = resolveCurrentDay(store);
        return resolveActionStatus(store.getId(), day);
    }

    @Override
    public PromotionPriceResponse getPromotionPrices() {
        List<Action> promotions = actionRepository.findByCategory(ActionCategory.PROMOTION);
        return PromotionPriceResponse.from(promotions);
    }

    @Override
    @Transactional
    public ActionResponse executePromotion(Integer userId, PromotionRequest request) {
        LocalDateTime actionTime = LocalDateTime.now(clock);
        Store store = findStore(userId, actionTime);
        ActionExecutionContext context = resolveActionExecutionContext(store, actionTime);
        int day = context.day();
        String legacyField = request.promotionType().name().toLowerCase();

        validateNotUsed(store.getId(), day, ACTION_FIELD_PROMOTION);

        Action action = actionRepository
                .findByCategoryAndPromotionType(ActionCategory.PROMOTION, request.promotionType())
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        GameDayLiveState state = resolveCurrentLiveState(store, context);
        long updatedBalance = resolveUpdatedBalance("PROMOTION", userId, store, day, valueOf(action.getCost()), state);

        BigDecimal multiplier = action.getCaptureRate();
        BigDecimal newCaptureRate = resolveAppliedCaptureRate(state, multiplier);

        actionLogRepository.save(new ActionLog(action, store, day, null));
        afterCommit(() -> synchronizeActionState("PROMOTION", store.getId(), day, () -> {
            gameDayStoreStateRedisRepository.updateField(
                    store.getId(),
                    day,
                    "capture_rate",
                    newCaptureRate.toPlainString()
            );
            gameDayStoreStateRedisRepository.markActionUsed(store.getId(), day, ACTION_FIELD_PROMOTION);
            gameDayStoreStateRedisRepository.markActionUsed(store.getId(), day, legacyField);
            gameDayStoreStateRedisRepository.saveBalance(store.getId(), day, updatedBalance);
            gameDayStoreStateRedisRepository.appendTickDebugActionNote(
                    store.getId(),
                    day,
                    resolveDebugTick(store, actionTime),
                    new TickDebugActionNote(
                            "홍보(%s)".formatted(request.promotionType().name()),
                            valueOf(action.getCost()),
                            0L,
                            0L,
                            0L,
                            0L,
                            0
                    )
            );
        }));

        logActionExecution(
                "PROMOTION",
                userId,
                store,
                day,
                "promotionType=%s actionCost=%s captureRateMultiplier=%s"
                        .formatted(request.promotionType(), action.getCost(), multiplier.toPlainString()),
                updatedBalance
        );
        return new ActionResponse(
                "PROMOTION_" + request.promotionType().name(),
                action.getCost(),
                "Promotion executed."
        );
    }

    @Override
    @Transactional
    public DiscountResponse executeDiscount(Integer userId, DiscountRequest request) {
        LocalDateTime actionTime = LocalDateTime.now(clock);
        Store store = findStore(userId, actionTime);
        ActionExecutionContext context = resolveActionExecutionContext(store, actionTime);
        int day = context.day();

        validateNotUsed(store.getId(), day, ACTION_FIELD_DISCOUNT);

        Action action = findSingleAction(ActionCategory.DISCOUNT);
        int previousPrice = store.getPrice();
        int newPrice = previousPrice - request.discountValue();

        if (newPrice < store.getMenu().getOriginPrice()) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }

        GameDayLiveState state = resolveCurrentLiveState(store, context);
        long updatedBalance = resolveUpdatedBalance("DISCOUNT", userId, store, day, valueOf(action.getCost()), state);

        int averagePrice = storeRepository.findAveragePriceBySeasonIdAndMenuId(
                store.getSeason().getId(),
                store.getMenu().getId()
        );
        if (averagePrice <= 0) {
            averagePrice = store.getMenu().getOriginPrice();
        }
        PriceRange priceRange = determinePriceRange(newPrice, averagePrice);

        BigDecimal newCaptureRate = resolveAppliedCaptureRate(state, priceRange.multiplier());

        actionLogRepository.save(new ActionLog(action, store, day, priceRange.multiplier()));
        afterCommit(() -> synchronizeActionState("DISCOUNT", store.getId(), day, () -> {
            gameDayStoreStateRedisRepository.updateField(store.getId(), day, "sale_price", String.valueOf(newPrice));
            gameDayStoreStateRedisRepository.updateField(
                    store.getId(),
                    day,
                    "capture_rate",
                    newCaptureRate.toPlainString()
            );
            gameDayStoreStateRedisRepository.markActionUsed(store.getId(), day, ACTION_FIELD_DISCOUNT);
            gameDayStoreStateRedisRepository.saveBalance(store.getId(), day, updatedBalance);
            gameDayStoreStateRedisRepository.appendTickDebugActionNote(
                    store.getId(),
                    day,
                    resolveDebugTick(store, actionTime),
                    new TickDebugActionNote(
                            "할인(%d->%d)".formatted(previousPrice, newPrice),
                            0L,
                            valueOf(action.getCost()),
                            0L,
                            0L,
                            0L,
                            0
                    )
            );
        }));

        logActionExecution(
                "DISCOUNT",
                userId,
                store,
                day,
                "discountValue=%s previousPrice=%s newPrice=%s priceRange=%s priceRangeMultiplier=%s"
                        .formatted(
                                request.discountValue(),
                                previousPrice,
                                newPrice,
                                priceRange.label(),
                                priceRange.multiplier().toPlainString()
                        ),
                updatedBalance
        );
        return new DiscountResponse(
                previousPrice,
                newPrice,
                priceRange.label(),
                priceRange.multiplier(),
                "Discount applied."
        );
    }

    @Override
    @Transactional
    public DonationResponse executeDonation(Integer userId, DonationRequest request) {
        LocalDateTime actionTime = LocalDateTime.now(clock);
        Store store = findStore(userId, actionTime);
        ActionExecutionContext context = resolveActionExecutionContext(store, actionTime);
        int day = context.day();

        validateNotUsed(store.getId(), day, ACTION_FIELD_DONATION);

        Action action = findSingleAction(ActionCategory.DONATION);
        GameDayLiveState state = resolveCurrentLiveState(store, context);
        int currentStock = normalizeStock(state.stock());
        if (currentStock < request.quantity()) {
            throw new BaseException(ErrorCode.INSUFFICIENT_STOCK);
        }

        long updatedBalance = resolveUpdatedBalance("DONATION", userId, store, day, valueOf(action.getCost()), state);
        BigDecimal captureRateBonus = DONATION_CAPTURE_RATE_BONUS.setScale(2, RoundingMode.HALF_UP);

        int newStock = normalizeStock(currentStock - request.quantity());
        BigDecimal multiplier = BigDecimal.ONE.add(captureRateBonus);
        BigDecimal newCaptureRate = resolveAppliedCaptureRate(state, multiplier);

        actionLogRepository.save(new ActionLog(action, store, day, captureRateBonus));
        afterCommit(() -> synchronizeActionState("DONATION", store.getId(), day, () -> {
            gameDayStoreStateRedisRepository.updateField(store.getId(), day, "stock", String.valueOf(newStock));
            gameDayStoreStateRedisRepository.updateField(
                    store.getId(),
                    day,
                    "capture_rate",
                    newCaptureRate.toPlainString()
            );
            gameDayStoreStateRedisRepository.markActionUsed(store.getId(), day, ACTION_FIELD_DONATION);
            gameDayStoreStateRedisRepository.saveBalance(store.getId(), day, updatedBalance);
            gameDayStoreStateRedisRepository.appendTickDebugActionNote(
                    store.getId(),
                    day,
                    resolveDebugTick(store, actionTime),
                    new TickDebugActionNote(
                            "나눔(%d개)".formatted(request.quantity()),
                            0L,
                            0L,
                            0L,
                            valueOf(action.getCost()),
                            0L,
                            request.quantity()
                    )
            );
        }));

        logActionExecution(
                "DONATION",
                userId,
                store,
                day,
                "quantity=%s stockAfter=%s captureRateBonus=%s"
                        .formatted(request.quantity(), newStock, captureRateBonus.toPlainString()),
                updatedBalance
        );
        return new DonationResponse(
                request.quantity(),
                captureRateBonus,
                "Donation executed."
        );
    }

    @Override
    @Transactional
    public EmergencyOrderResponse executeEmergencyOrder(Integer userId, EmergencyOrderRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        Store store = findStore(userId, now);
        ActionExecutionContext context = resolveActionExecutionContext(store, now);
        int day = context.day();
        Menu menu = getMenuById(request.menuId());

        if (request.salePrice() < menu.getOriginPrice()) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }

        validateNotUsed(store.getId(), day, ACTION_FIELD_EMERGENCY);

        Action action = findSingleAction(ActionCategory.EMERGENCY_ORDER);
        GameDayLiveState state = resolveCurrentLiveState(store, context);

        BigDecimal ingredientCostMultiplier = resolveIngredientCostMultiplier(store, menu, day, state, now);
        int menuTrendRank = resolveMenuEntryRank(store, day, menu);
        int minimumSellingPrice = storeRankingPolicy.apply(
                menu.getOriginPrice(),
                storeRankingPolicy.resolveMenuEntryMultiplier(menuTrendRank),
                ingredientCostMultiplier
        );
        int recommendedPrice = BigDecimal.valueOf(minimumSellingPrice)
                .multiply(RECOMMENDED_PRICE_MULTIPLIER)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        int maxSellingPrice = Math.multiplyExact(recommendedPrice, 2);

        if (request.salePrice() < minimumSellingPrice || request.salePrice() > maxSellingPrice) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }
        int adjustedOriginPrice = storeRankingPolicy.apply(
                menu.getOriginPrice(),
                storeRankingPolicy.resolveMenuEntryMultiplier(menuTrendRank),
                resolveIngredientDiscountRate(store.getUser().getId()),
                ingredientCostMultiplier
        );
        int totalCost = BigDecimal.valueOf(adjustedOriginPrice)
                .multiply(BigDecimal.valueOf(request.quantity()))
                .multiply(EMERGENCY_COST_MULTIPLIER)
                .intValue();

        long updatedBalance = resolveUpdatedBalance(
                "EMERGENCY_ORDER",
                userId,
                store,
                day,
                valueOf(action.getCost()) + totalCost,
                state
        );
        int deliverySeconds = trafficDelayResolver.resolve(
                store.getSeason().getId(),
                store.getLocation().getId(),
                day,
                store.getSeason().resolveRuntimePlayableDays(),
                state.startedAt(),
                now
        ).delaySeconds();
        LocalDateTime arrivedTime = now.plusSeconds(deliverySeconds);

        if (!Objects.equals(store.getPrice(), request.salePrice())) {
            store.changePrice(request.salePrice());
        }

        actionLogRepository.save(new ActionLog(action, store, day, null));
        Order order = orderRepository.save(
                Order.createEmergency(
                        menu,
                        store,
                        request.quantity(),
                        totalCost,
                        request.salePrice(),
                        day,
                        arrivedTime
                )
        );

        afterCommit(() -> synchronizeActionState("EMERGENCY_ORDER", store.getId(), day, () -> {
            gameDayStoreStateRedisRepository.markActionUsed(store.getId(), day, ACTION_FIELD_EMERGENCY);
            gameDayStoreStateRedisRepository.saveBalance(store.getId(), day, updatedBalance);
            gameDayStoreStateRedisRepository.appendTickDebugActionNote(
                    store.getId(),
                    day,
                    resolveDebugTick(store, now),
                    new TickDebugActionNote(
                            "긴급발주(%d개)".formatted(request.quantity()),
                            0L,
                            0L,
                            valueOf(action.getCost()) + totalCost,
                            0L,
                            0L,
                            0
                    )
            );
        }));

        logActionExecution(
                "EMERGENCY_ORDER",
                userId,
                store,
                day,
                "orderId=%s quantity=%s totalCost=%s deliverySeconds=%s arrivedTime=%s"
                        .formatted(order.getId(), request.quantity(), totalCost, deliverySeconds, arrivedTime),
                updatedBalance
        );
        return new EmergencyOrderResponse(
                order.getId(),
                request.quantity(),
                totalCost,
                arrivedTime,
                "Emergency order accepted."
        );
    }

    private Store findStore(Integer userId) {
        return findStore(userId, LocalDateTime.now(clock));
    }

    private Store findStore(Integer userId, LocalDateTime now) {
        Store store = storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(userId, SeasonStatus.IN_PROGRESS)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));
        STORE_LOCATION_TRANSITION_SUPPORT.applyPendingLocationIfDue(store, now);
        return store;
    }

    private Menu getMenuById(Integer menuId) {
        return menuRepository.findById(Long.valueOf(menuId))
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_NOT_FOUND));
    }

    private int resolveCurrentDay(Store store) {
        SeasonTimePoint seasonTimePoint = seasonTimelineService.resolve(store.getSeason(), LocalDateTime.now(clock));
        Integer currentDay = seasonTimePoint.currentDay();
        if (currentDay == null || currentDay < 1 || currentDay > store.getSeason().resolveRuntimePlayableDays()) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "Current season day is out of range.");
        }
        return currentDay;
    }

    private Action findSingleAction(ActionCategory category) {
        return actionRepository.findByCategory(category)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void validateNotUsed(Long storeId, int day, String field) {
        ActionStatusResponse actionStatus = resolveActionStatus(storeId, day);
        boolean alreadyUsed = switch (field) {
            case ACTION_FIELD_DISCOUNT -> actionStatus.discountUsed();
            case ACTION_FIELD_EMERGENCY -> actionStatus.emergencyUsed();
            case ACTION_FIELD_DONATION -> actionStatus.donationUsed();
            case ACTION_FIELD_PROMOTION -> actionStatus.promotionUsed();
            default -> false;
        };
        if (alreadyUsed) {
            throw new BaseException(ErrorCode.ACTION_ALREADY_USED);
        }
    }

    private ActionStatusResponse resolveActionStatus(Long storeId, int day) {
        return ActionStatusResponse.fromActionLogs(
                actionLogRepository.findByStore_IdAndGameDayAndIsUsedTrue(storeId, day)
        );
    }

    private long resolveUpdatedBalance(
            String actionType,
            Integer userId,
            Store store,
            int day,
            long requiredAmount,
            GameDayLiveState state
    ) {
        long currentBalance = state.balance() == null ? 0L : state.balance();
        long normalizedRequiredAmount = Math.max(requiredAmount, 0L);
        if (currentBalance < normalizedRequiredAmount) {
            logActionInsufficientBalance(actionType, userId, store, day, currentBalance, normalizedRequiredAmount);
            throw new BaseException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        return currentBalance - normalizedRequiredAmount;
    }

    private BigDecimal resolveAppliedCaptureRate(GameDayLiveState state, BigDecimal multiplier) {
        BigDecimal currentRate = state.captureRate() != null
                ? state.captureRate()
                : state.startResponse() != null && state.startResponse().captureRate() != null
                ? state.startResponse().captureRate()
                : BigDecimal.ZERO;
        return captureRatePolicy.applyMultiplier(currentRate, multiplier);
    }

    private ActionExecutionContext resolveActionExecutionContext(Store store, LocalDateTime now) {
        SeasonTimePoint seasonTimePoint = seasonTimelineService.resolve(store.getSeason(), now);
        validateActionExecutionPhase(seasonTimePoint.phase());

        Integer currentDay = seasonTimePoint.currentDay();
        if (currentDay == null || currentDay < 1 || currentDay > store.getSeason().resolveRuntimePlayableDays()) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "Current season day is out of range.");
        }

        return new ActionExecutionContext(now, seasonTimePoint, currentDay);
    }

    private void validateActionExecutionPhase(SeasonPhase phase) {
        if (phase != SeasonPhase.DAY_BUSINESS) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "Actions are only available during business hours.");
        }
    }

    private GameDayLiveState resolveLiveState(Store store, ActionExecutionContext context) {
        return gameDayStoreStateRedisRepository.find(store.getId(), context.day())
                .or(() -> gameDayStartService.ensureCurrentDayState(store, context.now(), context.seasonTimePoint()))
                .orElseThrow(() -> new BaseException(ErrorCode.GAME_STATE_NOT_FOUND));
    }

    private GameDayLiveState resolveCurrentLiveState(Store store, ActionExecutionContext context) {
        gameDayStateService.refreshGameState(store);
        return resolveLiveState(store, context);
    }

    private PriceRange determinePriceRange(int sellingPrice, int averagePrice) {
        BigDecimal ratio = BigDecimal.valueOf(sellingPrice)
                .divide(BigDecimal.valueOf(averagePrice), 4, RoundingMode.HALF_UP);

        if (ratio.compareTo(PRICE_RANGE_UPPER) > 0) {
            return new PriceRange("ABOVE", resolveAboveMultiplier(ratio));
        }
        if (ratio.compareTo(PRICE_RANGE_LOWER) < 0) {
            return new PriceRange("BELOW", resolveBelowMultiplier(ratio));
        }
        return new PriceRange("AVERAGE", MULTIPLIER_AVERAGE);
    }

    private BigDecimal resolveAboveMultiplier(BigDecimal ratio) {
        if (ratio.compareTo(RATIO_200) >= 0) {
            return MULTIPLIER_ABOVE_200;
        }
        if (ratio.compareTo(RATIO_190) >= 0) {
            return MULTIPLIER_ABOVE_190;
        }
        if (ratio.compareTo(RATIO_180) >= 0) {
            return MULTIPLIER_ABOVE_180;
        }
        if (ratio.compareTo(RATIO_170) >= 0) {
            return MULTIPLIER_ABOVE_170;
        }
        if (ratio.compareTo(RATIO_160) >= 0) {
            return MULTIPLIER_ABOVE_160;
        }
        if (ratio.compareTo(RATIO_150) >= 0) {
            return MULTIPLIER_ABOVE_150;
        }
        if (ratio.compareTo(RATIO_140) >= 0) {
            return MULTIPLIER_ABOVE_140;
        }
        if (ratio.compareTo(RATIO_130) >= 0) {
            return MULTIPLIER_ABOVE_130;
        }
        return MULTIPLIER_ABOVE_120;
    }

    private BigDecimal resolveBelowMultiplier(BigDecimal ratio) {
        if (ratio.compareTo(RATIO_60) < 0) {
            return MULTIPLIER_BELOW_60;
        }
        if (ratio.compareTo(RATIO_70) < 0) {
            return MULTIPLIER_BELOW_70;
        }
        if (ratio.compareTo(RATIO_80) < 0) {
            return MULTIPLIER_BELOW_80;
        }
        return MULTIPLIER_BELOW_80;
    }

    private BigDecimal resolveIngredientCostMultiplier(
            Store store,
            Menu menu,
            int day,
            GameDayLiveState state,
            LocalDateTime now
    ) {
        if (state.startedAt() == null) {
            return BigDecimal.ONE;
        }

        LocalDateTime effectiveAt = seasonTimelineService.day(store.getSeason(), day).businessStart();
        return eventEffectResolver.resolve(
                store.getSeason(),
                day,
                effectiveAt,
                store.getLocation().getId(),
                menu.getId()
        ).ingredientCostMultiplier();
    }

    private BigDecimal resolveIngredientDiscountRate(Integer userId) {
        return itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(userId, ItemCategory.INGREDIENT)
                .filter(rate -> rate.signum() > 0)
                .orElse(BigDecimal.ONE);
    }

    private void logActionInsufficientBalance(
            String actionType,
            Integer userId,
            Store store,
            int day,
            long currentBalance,
            long requiredAmount
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        SeasonTimePoint timePoint = seasonTimelineService.resolve(store.getSeason(), now);
        log.info(
                "\n================ ACTION EXECUTION ================\n"
                        + "now={} userId={} storeId={} seasonId={}\n"
                        + "actionType={} result=BLOCKED reason=INSUFFICIENT_BALANCE\n"
                        + "phase={} day={} gameTime={} tick={}\n"
                        + "currentBalance={} requiredAmount={}\n"
                        + "==================================================",
                now,
                userId,
                store.getId(),
                store.getSeason().getId(),
                actionType,
                timePoint.phase(),
                formatDay(day),
                formatValue(timePoint.gameTime()),
                formatValue(timePoint.tick()),
                currentBalance,
                requiredAmount
        );
    }

    private void logActionExecution(
            String actionType,
            Integer userId,
            Store store,
            int day,
            String detail,
            Long balanceAfter
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        SeasonTimePoint timePoint = seasonTimelineService.resolve(store.getSeason(), now);
        log.info(
                "\n================ ACTION EXECUTION ================\n"
                        + "now={} userId={} storeId={} seasonId={}\n"
                        + "actionType={} phase={} day={} gameTime={} tick={}\n"
                        + "balanceAfter={} detail={}\n"
                        + "==================================================",
                now,
                userId,
                store.getId(),
                store.getSeason().getId(),
                actionType,
                timePoint.phase(),
                formatDay(day),
                formatValue(timePoint.gameTime()),
                formatValue(timePoint.tick()),
                formatValue(balanceAfter),
                detail
        );
    }

    private String formatDay(Integer day) {
        return day == null || day <= 0 ? "-" : "DAY " + day;
    }

    private String formatValue(Object value) {
        return value == null ? "-" : value.toString();
    }

    private int resolveMenuEntryRank(Store store, int day, Menu menu) {
        Integer newsRank = newsRankingResolver.resolveMenuEntryRank(
                store.getSeason().getId(),
                day,
                menu
        );
        if (newsRank != null && newsRank > 0) {
            return newsRank;
        }

        List<Store> seasonStores = storeRepository.findBySeason_IdOrderByIdAsc(store.getSeason().getId());
        if (seasonStores == null || seasonStores.isEmpty()) {
            return 1;
        }
        return storeRankingPolicy.resolveMenuTrendRank(menu.getId(), seasonStores);
    }

    private long valueOf(Integer amount) {
        return amount == null ? 0L : amount.longValue();
    }

    private int normalizeStock(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private Integer resolveDebugTick(Store store, LocalDateTime recordedAt) {
        if (store == null || store.getSeason() == null || recordedAt == null) {
            return 0;
        }
        try {
            Integer tick = seasonTimelineService.resolve(store.getSeason(), recordedAt).tick();
            return tick == null ? 0 : Math.max(0, tick);
        } catch (Exception e) {
            return 0;
        }
    }

    private void synchronizeActionState(String actionType, Long storeId, int day, Runnable syncAction) {
        try {
            syncAction.run();
        } catch (Exception e) {
            log.error(
                    "action-state-sync-failed actionType={} storeId={} day={} message={}",
                    actionType,
                    storeId,
                    day,
                    e.getMessage(),
                    e
            );
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private record PriceRange(String label, BigDecimal multiplier) {
    }

    private record ActionExecutionContext(
            LocalDateTime now,
            SeasonTimePoint seasonTimePoint,
            int day
    ) {
    }
}
