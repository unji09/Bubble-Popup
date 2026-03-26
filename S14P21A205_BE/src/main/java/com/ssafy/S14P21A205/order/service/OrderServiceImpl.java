package com.ssafy.S14P21A205.order.service;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.debug.TickDebugActionNote;
import com.ssafy.S14P21A205.game.day.policy.StoreRankingPolicy;
import com.ssafy.S14P21A205.game.day.resolver.EventEffectResolver;
import com.ssafy.S14P21A205.game.day.service.GameDayStartService;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.day.resolver.NewsRankingResolver;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.support.StoreStateCarryOverSupport;
import com.ssafy.S14P21A205.game.season.entity.DailyReport;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.game.time.model.SeasonPhase;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import com.ssafy.S14P21A205.order.dto.CurrentOrderResponse;
import com.ssafy.S14P21A205.order.dto.RegularOrderRequest;
import com.ssafy.S14P21A205.order.dto.RegularOrderResponse;
import com.ssafy.S14P21A205.order.entity.Order;
import com.ssafy.S14P21A205.order.entity.OrderType;
import com.ssafy.S14P21A205.order.repository.OrderRepository;
import com.ssafy.S14P21A205.shop.entity.ItemCategory;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.shop.repository.ItemUserRepository;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.MenuRepository;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private static final Set<Integer> REGULAR_ORDER_DAYS = Set.of(1, 3, 5, 7);
    private static final BigDecimal RECOMMENDED_PRICE_MULTIPLIER = new BigDecimal("2.5");

    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final DailyReportRepository dailyReportRepository;
    private final GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;
    private final ItemUserRepository itemUserRepository;
    private final StoreRankingPolicy marketRankingPolicy;
    private final NewsRankingResolver newsRankingResolver;
    private final EventEffectResolver eventEffectResolver;
    private final GameDayStartService gameDayStartService;
    private final Clock clock;

    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();

    @Override
    public CurrentOrderResponse getCurrentOrder(Integer userId, Integer menuId) {
        Store store = getStoreByUserId(userId);
        Long storeId = store.getId();
        int currentDay = resolveRegularOrderDay(store);
        Menu menu = resolvePreviewMenu(store, menuId);
        List<Store> seasonStores = storeRepository.findBySeason_IdOrderByIdAsc(store.getSeason().getId());

        BigDecimal ingredientDiscountRate = getIngredientDiscountRate(store.getUser().getId());
        int menuTrendRank = resolveMenuEntryRank(store, currentDay, menu, seasonStores);
        BigDecimal ingredientCostMultiplier = resolveRegularOrderIngredientCostMultiplier(store, currentDay, menu);
        PricingPolicy pricingPolicy = resolvePricingPolicy(
                menu,
                ingredientDiscountRate,
                ingredientCostMultiplier,
                menuTrendRank
        );
        Integer stock = resolveStock(store, currentDay);
        Integer baseSellingPrice = resolveBaseSellingPrice(store, currentDay);
        Integer sellingPrice = resolveSellingPrice(
                null,
                Objects.equals(store.getMenu().getId(), menu.getId()),
                baseSellingPrice,
                pricingPolicy
        );

        return CurrentOrderResponse.builder()
                .menuId(Math.toIntExact(menu.getId()))
                .menuName(menu.getMenuName())
                .costPrice(pricingPolicy.costPrice())
                .minimumSellingPrice(pricingPolicy.minimumSellingPrice())
                .recommendedPrice(pricingPolicy.recommendedPrice())
                .maxSellingPrice(pricingPolicy.maxSellingPrice())
                .sellingPrice(sellingPrice)
                .stock(stock)
                .build();
    }

    @Override
    @Transactional
    public RegularOrderResponse createRegularOrder(Integer userId, RegularOrderRequest request) {
        Store store = getStoreByUserId(userId);
        Long storeId = store.getId();
        LocalDateTime now = LocalDateTime.now(clock);
        SeasonTimePoint seasonTimePoint = seasonTimelineService.resolve(store.getSeason(), now);
        int regularOrderDay = resolveRegularOrderDay(store, seasonTimePoint);

        validateRegularOrderPhase(seasonTimePoint.phase());
        validateRegularOrderDay(regularOrderDay);
        validateQuantity(request.quantity());
        validateNoExistingOrder(storeId, regularOrderDay);

        Menu menu = getMenuById(request.menuId());
        boolean sameMenu = Objects.equals(store.getMenu().getId(), menu.getId());
        List<Store> seasonStores = storeRepository.findBySeason_IdOrderByIdAsc(store.getSeason().getId());

        BigDecimal discountRate = getIngredientDiscountRate(store.getUser().getId());
        int menuTrendRank = resolveMenuEntryRank(store, regularOrderDay, menu, seasonStores);
        BigDecimal ingredientCostMultiplier = resolveRegularOrderIngredientCostMultiplier(store, regularOrderDay, menu);
        PricingPolicy pricingPolicy = resolvePricingPolicy(
                menu,
                discountRate,
                ingredientCostMultiplier,
                menuTrendRank
        );
        Integer baseSellingPrice = resolveBaseSellingPrice(store, regularOrderDay);
        Integer sellingPrice = resolveSellingPrice(request.price(), sameMenu, baseSellingPrice, pricingPolicy);
        validateSellingPrice(sellingPrice, pricingPolicy);
        Integer totalCost = Math.multiplyExact(pricingPolicy.costPrice(), request.quantity());

        validateAffordableOrder(store, regularOrderDay, totalCost);

        if (!Objects.equals(store.getPrice(), sellingPrice)) {
            store.changePrice(sellingPrice);
        }

        if (!sameMenu) {
            store.changeMenu(menu);
        }

        Order savedOrder = orderRepository.save(
                Order.create(menu, store, request.quantity(), totalCost, sellingPrice, regularOrderDay)
        );
        gameDayStartService.synchronizeCurrentDayState(store, now, seasonTimePoint);
        gameDayStoreStateRedisRepository.appendTickDebugActionNote(
                storeId,
                regularOrderDay,
                resolveDebugTick(seasonTimePoint),
                new TickDebugActionNote(
                        "정규발주(%d개)".formatted(request.quantity()),
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0
                )
        );

        return RegularOrderResponse.builder()
                .orderId(savedOrder.getId())
                .menuId(Math.toIntExact(menu.getId()))
                .quantity(request.quantity())
                .costPrice(pricingPolicy.costPrice())
                .sellingPrice(sellingPrice)
                .totalCost(totalCost)
                .discount(discountRate.floatValue())
                .build();
    }

    private Store getStoreByUserId(Integer userId) {
        return storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(userId, SeasonStatus.IN_PROGRESS)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));
    }

    private Menu getMenuById(Integer menuId) {
        return menuRepository.findById(Long.valueOf(menuId))
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_NOT_FOUND));
    }

    private Menu resolvePreviewMenu(Store store, Integer menuId) {
        if (menuId == null || Objects.equals(store.getMenu().getId(), Long.valueOf(menuId))) {
            return store.getMenu();
        }
        return getMenuById(menuId);
    }

    private BigDecimal getIngredientDiscountRate(Integer userId) {
        return itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(userId, ItemCategory.INGREDIENT)
                .orElse(BigDecimal.ONE);
    }

    private Integer resolveCostPrice(
            Menu menu,
            BigDecimal discountRate,
            BigDecimal ingredientCostMultiplier,
            int menuTrendRank
    ) {
        return marketRankingPolicy.apply(
                menu.getOriginPrice(),
                marketRankingPolicy.resolveMenuEntryMultiplier(menuTrendRank),
                discountRate,
                ingredientCostMultiplier
        );
    }

    private Integer resolveMinimumSellingPrice(Menu menu, int menuTrendRank, BigDecimal ingredientCostMultiplier) {
        return marketRankingPolicy.apply(
                menu.getOriginPrice(),
                marketRankingPolicy.resolveMenuEntryMultiplier(menuTrendRank),
                ingredientCostMultiplier
        );
    }

    private PricingPolicy resolvePricingPolicy(
            Menu menu,
            BigDecimal discountRate,
            BigDecimal ingredientCostMultiplier,
            int menuTrendRank
    ) {
        int costPrice = resolveCostPrice(menu, discountRate, ingredientCostMultiplier, menuTrendRank);
        int minimumSellingPrice = resolveMinimumSellingPrice(menu, menuTrendRank, ingredientCostMultiplier);
        int recommendedPrice = BigDecimal.valueOf(minimumSellingPrice)
                .multiply(RECOMMENDED_PRICE_MULTIPLIER)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        int maxSellingPrice = Math.multiplyExact(recommendedPrice, 2);
        return new PricingPolicy(costPrice, minimumSellingPrice, recommendedPrice, maxSellingPrice);
    }

    private Integer resolveSellingPrice(
            Integer requestedPrice,
            boolean sameMenu,
            Integer currentStorePrice,
            PricingPolicy pricingPolicy
    ) {
        if (requestedPrice != null) {
            return requestedPrice;
        }
        if (sameMenu && currentStorePrice != null && isSellingPriceWithinRange(currentStorePrice, pricingPolicy)) {
            return currentStorePrice;
        }
        return pricingPolicy.recommendedPrice();
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

    private BigDecimal resolveRegularOrderIngredientCostMultiplier(Store store, int day, Menu menu) {
        LocalDateTime effectiveAtOpening = seasonTimelineService.day(store.getSeason(), day).businessStart();
        return eventEffectResolver.resolve(
                store.getSeason(),
                day,
                effectiveAtOpening,
                store.getLocation().getId(),
                menu.getId()
        ).ingredientCostMultiplier();
    }

    private void validateSellingPrice(Integer sellingPrice, PricingPolicy pricingPolicy) {
        if (sellingPrice == null || !isSellingPriceWithinRange(sellingPrice, pricingPolicy)) {
            throw new BaseException(
                    ErrorCode.ORDER_INVALID_SELLING_PRICE,
                    "Selling price must be between %d and %d."
                            .formatted(pricingPolicy.minimumSellingPrice(), pricingPolicy.maxSellingPrice())
            );
        }
    }

    private boolean isSellingPriceWithinRange(int sellingPrice, PricingPolicy pricingPolicy) {
        return sellingPrice >= pricingPolicy.minimumSellingPrice()
                && sellingPrice <= pricingPolicy.maxSellingPrice();
    }

    private void validateRegularOrderDay(int currentDay) {
        if (!REGULAR_ORDER_DAYS.contains(currentDay)) {
            throw new BaseException(
                    ErrorCode.ORDER_NOT_AVAILABLE_DAY,
                    "Regular orders are only available on eligible order days."
            );
        }
    }

    private void validateRegularOrderPhase(SeasonPhase phase) {
        if (phase != SeasonPhase.DAY_PREPARING) {
            throw new RuntimeException("Regular orders are only available during the preparing phase.");
        }
    }

    private int resolveRegularOrderDay(Store store) {
        return resolveRegularOrderDay(store, resolveSeasonTimePoint(store));
    }

    private int resolveRegularOrderDay(Store store, SeasonTimePoint seasonTimePoint) {
        Integer currentDay = seasonTimePoint.currentDay();
        if (currentDay == null || currentDay < 1 || currentDay > store.getSeason().resolveRuntimePlayableDays()) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "Current season day is out of range.");
        }
        return currentDay;
    }

    private Integer resolveDebugTick(SeasonTimePoint seasonTimePoint) {
        if (seasonTimePoint == null || seasonTimePoint.tick() == null) {
            return 0;
        }
        return Math.max(0, seasonTimePoint.tick());
    }

    private SeasonTimePoint resolveSeasonTimePoint(Store store) {
        return seasonTimelineService.resolve(store.getSeason(), LocalDateTime.now(clock));
    }

    private void validateNoExistingOrder(Long storeId, Integer orderedDay) {
        if (orderRepository.findDailyStartOrder(storeId, orderedDay).isPresent()) {
            throw new BaseException(ErrorCode.ORDER_ALREADY_EXISTS);
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 50 || quantity > 500) {
            throw new BaseException(ErrorCode.ORDER_INVALID_QUANTITY);
        }
    }

    private void validateAffordableOrder(Store store, int day, int totalCost) {
        int carriedBalance = resolveCarriedBalance(store, day);
        // Daily rent is settled at closing, so regular orders only need to fit within carried cash.
        if (carriedBalance < totalCost) {
            throw new BaseException(ErrorCode.ORDER_INSUFFICIENT_BALANCE);
        }
    }

    private int resolveAreaEntryRank(Store store, int day, List<Store> seasonStores) {
        Integer newsRank = newsRankingResolver.resolveAreaEntryRank(
                store.getSeason().getId(),
                day,
                store.getLocation()
        );
        if (newsRank != null) {
            return newsRank;
        }
        return marketRankingPolicy.resolveLocationPopularityRank(store.getLocation().getId(), seasonStores);
    }

    private int resolveMenuEntryRank(Store store, int day, Menu menu, List<Store> seasonStores) {
        Integer newsRank = newsRankingResolver.resolveMenuEntryRank(
                store.getSeason().getId(),
                day,
                menu
        );
        if (newsRank != null) {
            return newsRank;
        }
        return marketRankingPolicy.resolveMenuTrendRank(menu.getId(), seasonStores);
    }

    private int resolveCarriedBalance(Store store, int day) {
        if (day <= 1) {
            return StoreStateCarryOverSupport.resolveInitialBalance(store);
        }

        Integer reportedBalance = findLatestReportBefore(store.getId(), day)
                .map(report -> report.getBalance() == null ? 0 : report.getBalance())
                .orElse(null);
        if (reportedBalance != null) {
            return reportedBalance;
        }

        GameDayLiveState previousDayState = gameDayStoreStateRedisRepository.find(store.getId(), day - 1)
                .orElse(null);
        if (previousDayState != null && previousDayState.balance() != null) {
            return Math.toIntExact(previousDayState.balance());
        }

        return StoreStateCarryOverSupport.resolveInitialBalance(store);
    }

    private Integer resolveStock(Store store, int day) {
        Long storeId = store.getId();
        return gameDayStoreStateRedisRepository.find(storeId, day)
                .map(state -> normalizeStock(state.stock()))
                .orElseGet(() -> resolveStartingStock(store, day));
    }

    private Integer resolveStartingStock(Store store, int day) {
        Long storeId = store.getId();
        int carriedStock;
        if (day <= 1) {
            carriedStock = normalizeStock(StoreStateCarryOverSupport.resolveInitialStock());
        } else {
            Integer reportedStock = findLatestReportBefore(storeId, day)
                    .map(report -> normalizeStock(report.getStockRemaining()))
                    .orElse(null);
            if (reportedStock != null) {
                carriedStock = reportedStock;
            } else {
                carriedStock = gameDayStoreStateRedisRepository.find(storeId, day - 1)
                        .map(state -> normalizeStock(state.stock()))
                        .orElse(normalizeStock(StoreStateCarryOverSupport.resolveInitialStock()));
            }
        }
        if (REGULAR_ORDER_DAYS.contains(day)) {
            carriedStock = 0;
        }

        int orderedStock = orderRepository.findDailyStartOrder(storeId, day)
                .map(Order::getQuantity)
                .map(this::normalizeStock)
                .orElse(0);

        return normalizeStock(Math.addExact(carriedStock, orderedStock));
    }

    private int normalizeStock(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private Optional<DailyReport> findLatestReportBefore(Long storeId, int day) {
        if (storeId == null || day <= 1) {
            return Optional.empty();
        }
        return dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(storeId, day);
    }

    private record PricingPolicy(
            int costPrice,
            int minimumSellingPrice,
            int recommendedPrice,
            int maxSellingPrice
    ) {
    }
}
