package com.ssafy.S14P21A205.game.day.policy;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.dto.GameDayStartResponse;
import com.ssafy.S14P21A205.game.day.model.OpeningState;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.event.entity.DailyEvent;
import com.ssafy.S14P21A205.game.event.entity.EventEndTime;
import com.ssafy.S14P21A205.game.event.entity.EventStartTime;
import com.ssafy.S14P21A205.game.event.entity.RandomEvent;
import com.ssafy.S14P21A205.game.event.repository.DailyEventRepository;
import com.ssafy.S14P21A205.game.support.StoreStateCarryOverSupport;
import com.ssafy.S14P21A205.game.season.entity.DailyReport;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.order.entity.Order;
import com.ssafy.S14P21A205.shop.entity.ItemCategory;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.shop.repository.ItemUserRepository;
import com.ssafy.S14P21A205.store.entity.Store;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentPolicy {

    private static final BigDecimal DECIMAL_ONE = new BigDecimal("1.00");
    private static final Set<Integer> REGULAR_ORDER_DAYS = Set.of(1, 3, 5, 7);
    private final DailyReportRepository dailyReportRepository;
    private final DailyEventRepository dailyEventRepository;
    private final GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;
    private final ItemUserRepository itemUserRepository;
    private final StoreRankingPolicy marketRankingPolicy;

    public OpeningState resolveStartingState(
            Store store,
            int day,
            List<Order> regularOrders,
            GameDayStartResponse.MarketSnapshot marketSnapshot
    ) {
        CarryOverState carryOverState = resolveCarryOverState(store, day);
        int carriedBalance = carryOverState.balance();
        int carriedStock = carryOverState.stock();
        String previousMenuName = carryOverState.previousMenuName();

        BigDecimal rentMultiplier = marketRankingPolicy.resolveRentMultiplier(
                marketSnapshot == null ? null : marketSnapshot.locationPopularityRank()
        );
        BigDecimal rentCouponMultiplier = itemUserRepository
                .findPurchasedDiscountRateByUserIdAndCategory(store.getUser().getId(), ItemCategory.RENT)
                .orElse(DECIMAL_ONE);
        BigDecimal ingredientDiscountMultiplier = itemUserRepository
                .findPurchasedDiscountRateByUserIdAndCategory(store.getUser().getId(), ItemCategory.INGREDIENT)
                .orElse(DECIMAL_ONE);
        BigDecimal trendCostMultiplier = marketSnapshot == null || marketSnapshot.trendMultiplier() == null
                ? DECIMAL_ONE
                : marketSnapshot.trendMultiplier();
        OpeningEventAdjustment openingEventAdjustment = resolveOpeningEventAdjustment(store, day);

        int dailyRentApplied = marketRankingPolicy.apply(store.getLocation().getRent(), rentMultiplier, rentCouponMultiplier);
        // Interior costs are already charged when the store joins or reserves a relocation.
        int interiorCost = 0;
        int regularOrderQuantity = regularOrders.stream()
                .map(Order::getQuantity)
                .filter(quantity -> quantity != null && quantity > 0)
                .mapToInt(Integer::intValue)
                .sum();
        int appliedUnitCost = resolveAppliedUnitCost(
                regularOrders.stream().findFirst().map(Order::getMenu).orElse(store.getMenu()),
                trendCostMultiplier,
                ingredientDiscountMultiplier,
                openingEventAdjustment
        );
        int regularOrderCost = regularOrders.stream()
                .mapToInt(order -> Math.multiplyExact(
                        resolveAppliedUnitCost(
                                order.getMenu(),
                                trendCostMultiplier,
                                ingredientDiscountMultiplier,
                                openingEventAdjustment
                        ),
                        order.getQuantity() == null ? 0 : order.getQuantity()
                ))
                .sum();

        boolean menuChanged = previousMenuName != null
                && !previousMenuName.isBlank()
                && store.getMenu() != null
                && store.getMenu().getMenuName() != null
                && !previousMenuName.equals(store.getMenu().getMenuName());
        boolean discardCarryOverStock = menuChanged || REGULAR_ORDER_DAYS.contains(day);
        // Disposed stock is removed from opening inventory, but no longer reduces cash.
        int disposalQuantity = discardCarryOverStock ? carriedStock : 0;
        int disposalLoss = 0;
        int openingAgedStock = discardCarryOverStock ? 0 : carriedStock;
        int openingFreshStock = regularOrderQuantity;
        int fixedCostTotal = Math.addExact(interiorCost, regularOrderCost);
        int initialBalance = carriedBalance - fixedCostTotal;

        if (initialBalance < 0) {
            int maxAffordableOrderCount = Math.max(
                    0,
                    (carriedBalance - interiorCost) / Math.max(1, appliedUnitCost)
            );
            throw new BaseException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "Insufficient balance for today's opening costs. "
                            + "maxOrderCount=%d, existingOrderCount=%d, balanceBeforeOrder=%d, interiorCost=%d, appliedUnitCost=%d"
                            .formatted(
                                    maxAffordableOrderCount,
                                    regularOrderQuantity,
                                    carriedBalance,
                                    interiorCost,
                                    appliedUnitCost
                            )
            );
        }

        return new OpeningState(
                initialBalance,
                Math.addExact(openingAgedStock, openingFreshStock),
                new GameDayStartResponse.OpeningSummary(
                        carriedBalance,
                        carriedStock,
                        regularOrderQuantity,
                        regularOrderCost,
                        dailyRentApplied,
                        interiorCost,
                        disposalQuantity,
                        disposalLoss,
                        openingEventAdjustment.governmentSupportCash(),
                        appliedUnitCost,
                        openingFreshStock,
                        openingAgedStock,
                        fixedCostTotal,
                        normalizeScale(rentMultiplier),
                        normalizeScale(rentCouponMultiplier),
                        normalizeScale(ingredientDiscountMultiplier),
                        normalizeScale(openingEventAdjustment.persistentCostMultiplier()),
                        normalizeScale(openingEventAdjustment.todayCostMultiplier()),
                        normalizeScale(trendCostMultiplier)
                )
        );
    }

    private CarryOverState resolveCarryOverState(Store store, int day) {
        if (day <= 1) {
            return new CarryOverState(
                    StoreStateCarryOverSupport.resolveInitialBalance(store),
                    normalizeStock(StoreStateCarryOverSupport.resolveInitialStock()),
                    null
            );
        }

        DailyReport previousDayReport = dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(
                        store.getId(),
                        day
                )
                .orElse(null);
        if (previousDayReport != null) {
            return new CarryOverState(
                    previousDayReport.getBalance(),
                    normalizeStock(previousDayReport.getStockRemaining()),
                    previousDayReport.getMenuName()
            );
        }

        GameDayLiveState previousDayState = gameDayStoreStateRedisRepository.find(store.getId(), day - 1)
                .orElse(null);
        if (previousDayState != null) {
            return new CarryOverState(
                    safeInt(previousDayState.balance()),
                    normalizeStock(previousDayState.stock()),
                    null
            );
        }

        return new CarryOverState(
                StoreStateCarryOverSupport.resolveInitialBalance(store),
                normalizeStock(StoreStateCarryOverSupport.resolveInitialStock()),
                null
        );
    }

    private int resolveAppliedUnitCost(
            Menu menu,
            BigDecimal trendCostMultiplier,
            BigDecimal ingredientDiscountMultiplier,
            OpeningEventAdjustment openingEventAdjustment
    ) {
        if (menu == null || menu.getOriginPrice() == null) {
            return 0;
        }
        return marketRankingPolicy.apply(
                menu.getOriginPrice(),
                trendCostMultiplier,
                ingredientDiscountMultiplier,
                openingEventAdjustment.persistentCostMultiplier(),
                openingEventAdjustment.todayCostMultiplier()
        );
    }

    private OpeningEventAdjustment resolveOpeningEventAdjustment(Store store, int day) {
        if (store.getSeason() == null || store.getSeason().getId() == null || day < 1) {
            return new OpeningEventAdjustment(DECIMAL_ONE, DECIMAL_ONE, 0);
        }

        BigDecimal persistentMultiplier = DECIMAL_ONE;
        BigDecimal todayMultiplier = DECIMAL_ONE;
        int governmentSupportCash = 0;

        List<DailyEvent> dailyEvents = dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(
                store.getSeason().getId(),
                1,
                day
        );
        for (DailyEvent dailyEvent : dailyEvents) {
            if (!matchesScope(dailyEvent, store)) {
                continue;
            }

            RandomEvent event = dailyEvent.getEvent();
            if (event == null) {
                continue;
            }

            int appliedDay = event.getStartTime() == EventStartTime.NEXT_DAY
                    ? dailyEvent.getDay() + 1
                    : dailyEvent.getDay();
            if (appliedDay > day) {
                continue;
            }

            BigDecimal costRate = normalizeScale(event.getCostRate());
            boolean appliesAtOpening = appliedDay < day
                    || (appliedDay == day && (dailyEvent.getApplyOffsetSeconds() == null || dailyEvent.getApplyOffsetSeconds() <= 0));

            if (!appliesAtOpening) {
                continue;
            }

            if (event.getCapitalFlat() != null && event.getCapitalFlat() > 0 && appliedDay == day) {
                governmentSupportCash += event.getCapitalFlat();
            }

            if (costRate.compareTo(DECIMAL_ONE) == 0) {
                continue;
            }

            if (appliedDay < day && event.getEndTime() == EventEndTime.SEASON_END) {
                persistentMultiplier = persistentMultiplier.multiply(costRate);
                continue;
            }

            todayMultiplier = todayMultiplier.multiply(costRate);
        }

        return new OpeningEventAdjustment(persistentMultiplier, todayMultiplier, governmentSupportCash);
    }

    private boolean matchesScope(DailyEvent dailyEvent, Store store) {
        if (dailyEvent.getTargetLocationId() != null && !dailyEvent.getTargetLocationId().equals(store.getLocation().getId())) {
            return false;
        }
        return dailyEvent.getTargetMenuId() == null || dailyEvent.getTargetMenuId().equals(store.getMenu().getId());
    }

    private BigDecimal normalizeScale(BigDecimal value) {
        if (value == null) {
            return DECIMAL_ONE.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private int safeInt(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }

    private int normalizeStock(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private record OpeningEventAdjustment(
            BigDecimal persistentCostMultiplier,
            BigDecimal todayCostMultiplier,
            int governmentSupportCash
    ) {
    }

    private record CarryOverState(
            int balance,
            int stock,
            String previousMenuName
    ) {
    }
}
