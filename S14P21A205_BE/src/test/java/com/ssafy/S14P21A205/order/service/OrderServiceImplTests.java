package com.ssafy.S14P21A205.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.policy.StoreRankingPolicy;
import com.ssafy.S14P21A205.game.day.resolver.EventEffectResolver;
import com.ssafy.S14P21A205.game.day.resolver.NewsRankingResolver;
import com.ssafy.S14P21A205.game.day.service.GameDayStartService;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.order.dto.RegularOrderRequest;
import com.ssafy.S14P21A205.order.dto.RegularOrderResponse;
import com.ssafy.S14P21A205.order.entity.Order;
import com.ssafy.S14P21A205.order.repository.OrderRepository;
import com.ssafy.S14P21A205.shop.entity.ItemCategory;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.shop.repository.ItemUserRepository;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.MenuRepository;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.user.entity.User;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTests {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DailyReportRepository dailyReportRepository;

    @Mock
    private GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;

    @Mock
    private ItemUserRepository itemUserRepository;

    @Mock
    private NewsRankingResolver newsRankingResolver;

    @Mock
    private EventEffectResolver eventEffectResolver;

    @Mock
    private GameDayStartService gameDayStartService;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                storeRepository,
                menuRepository,
                orderRepository,
                dailyReportRepository,
                gameDayStoreStateRedisRepository,
                itemUserRepository,
                new StoreRankingPolicy(),
                newsRankingResolver,
                eventEffectResolver,
                gameDayStartService,
                Clock.fixed(Instant.parse("2026-03-17T01:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
        org.mockito.Mockito.lenient()
                .when(gameDayStartService.synchronizeCurrentDayState(any(), any(), any()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient()
                .when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(any(), any()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient()
                .when(orderRepository.findFirstByStore_IdAndOrderedDayLessThanEqualAndSalePriceIsNotNullOrderByOrderedDayDescIdDesc(
                        any(),
                        any()
                ))
                .thenReturn(Optional.empty());
    }

    @Test
    void getCurrentOrderAppliesIngredientCostEventMultiplierToCostPrice() {
        Store store = store(15L, 1, 3L, 7L, 2_500, 4_000);
        Menu menu = store.getMenu();

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 1, menu)).thenReturn(null);
        when(eventEffectResolver.resolve(any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new EventEffectResolver.EventEffect(
                        0L,
                        0,
                        BigDecimal.ONE,
                        new BigDecimal("1.05"),
                        Collections.emptyList(),
                        Collections.emptyList()
                ));

        assertThat(orderService.getCurrentOrder(1, null).costPrice()).isEqualTo(3_150);
    }

    @Test
    void getCurrentOrderUsesLatestRegularOrderSalePriceAsBaseSellingPrice() {
        Store store = store(15L, 1, 3L, 7L, 2_500, 2_375);
        Menu menu = store.getMenu();

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 1, menu)).thenReturn(5);
        when(eventEffectResolver.resolve(any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new EventEffectResolver.EventEffect(
                        0L,
                        0,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        Collections.emptyList(),
                        Collections.emptyList()
                ));
        when(orderRepository.findFirstByStore_IdAndOrderedDayLessThanEqualAndSalePriceIsNotNullOrderByOrderedDayDescIdDesc(
                15L,
                1
        )).thenReturn(Optional.of(Order.create(menu, store, 50, 150_000, 2_500, 1)));

        assertThat(orderService.getCurrentOrder(1, null).sellingPrice()).isEqualTo(2_500);
    }

    @Test
    void getCurrentOrderFallsBackToRecommendedSellingPriceWhenBasePriceIsOutOfRange() {
        Store store = store(15L, 1, 3L, 7L, 2_400, 15_600);
        Menu menu = store.getMenu();

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 1, menu)).thenReturn(5);
        when(eventEffectResolver.resolve(any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new EventEffectResolver.EventEffect(
                        0L,
                        0,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        Collections.emptyList(),
                        Collections.emptyList()
                ));

        var response = orderService.getCurrentOrder(1, null);
        assertThat(response.minimumSellingPrice()).isEqualTo(2_400);
        assertThat(response.recommendedPrice()).isEqualTo(6_000);
        assertThat(response.maxSellingPrice()).isEqualTo(12_000);
        assertThat(response.sellingPrice()).isEqualTo(6_000);
    }

    @Test
    void getCurrentOrderUsesRequestedMenuPricingWhenMenuIdIsProvided() {
        Store store = store(15L, 1, 3L, 7L, 2_500, 15_600);
        Menu requestedMenu = instantiate(Menu.class);
        ReflectionTestUtils.setField(requestedMenu, "id", 8L);
        ReflectionTestUtils.setField(requestedMenu, "menuName", "requested-menu");
        ReflectionTestUtils.setField(requestedMenu, "originPrice", 2_000);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(menuRepository.findById(8L)).thenReturn(Optional.of(requestedMenu));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 1, requestedMenu)).thenReturn(1);
        when(eventEffectResolver.resolve(any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new EventEffectResolver.EventEffect(
                        0L,
                        0,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        Collections.emptyList(),
                        Collections.emptyList()
                ));

        var response = orderService.getCurrentOrder(1, 8);
        assertThat(response.menuId()).isEqualTo(8);
        assertThat(response.minimumSellingPrice()).isEqualTo(2_400);
        assertThat(response.recommendedPrice()).isEqualTo(6_000);
        assertThat(response.maxSellingPrice()).isEqualTo(12_000);
        assertThat(response.sellingPrice()).isEqualTo(6_000);
    }

    @Test
    void createRegularOrderPersistsSellingPriceToOrderSalePrice() {
        Store store = store(15L, 1, 3L, 7L, 2_500, 4_000);
        Menu menu = store.getMenu();

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(orderRepository.findDailyStartOrder(15L, 1)).thenReturn(Optional.empty());
        when(menuRepository.findById(7L)).thenReturn(Optional.of(menu));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 1, menu)).thenReturn(null);
        when(eventEffectResolver.resolve(any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new EventEffectResolver.EventEffect(
                        0L,
                        0,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        Collections.emptyList(),
                        Collections.emptyList()
                ));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 101L);
            return saved;
        });

        RegularOrderResponse response = orderService.createRegularOrder(1, new RegularOrderRequest(7, 50, 7_000));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        verify(gameDayStartService).synchronizeCurrentDayState(any(), any(), any());
        assertThat(orderCaptor.getValue().getSalePrice()).isEqualTo(7_000);
        assertThat(orderCaptor.getValue().getTotalCost()).isEqualTo(150_000);
        assertThat(response.orderId()).isEqualTo(101L);
        assertThat(response.sellingPrice()).isEqualTo(7_000);
        assertThat(response.totalCost()).isEqualTo(150_000);
    }

    @Test
    void createRegularOrderUsesLatestRegularOrderSalePriceWhenPriceIsOmitted() {
        Store store = store(15L, 1, 3L, 7L, 2_500, 2_375);
        Menu menu = store.getMenu();
        ReflectionTestUtils.setField(store.getSeason(), "currentDay", 3);
        ReflectionTestUtils.setField(store.getSeason(), "startTime", LocalDateTime.of(2026, 3, 17, 9, 53, 0));

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(orderRepository.findDailyStartOrder(15L, 3)).thenReturn(Optional.empty());
        when(menuRepository.findById(7L)).thenReturn(Optional.of(menu));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 3, menu)).thenReturn(5);
        when(eventEffectResolver.resolve(any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new EventEffectResolver.EventEffect(
                        0L,
                        0,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        Collections.emptyList(),
                        Collections.emptyList()
                ));
        when(orderRepository.findFirstByStore_IdAndOrderedDayLessThanEqualAndSalePriceIsNotNullOrderByOrderedDayDescIdDesc(
                eq(15L),
                anyInt()
        )).thenReturn(Optional.of(Order.create(menu, store, 50, 150_000, 2_500, 1)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 151L);
            return saved;
        });

        RegularOrderResponse response = orderService.createRegularOrder(1, new RegularOrderRequest(7, 50, null));

        assertThat(response.sellingPrice()).isEqualTo(2_500);
        assertThat(store.getPrice()).isEqualTo(2_500);
    }

    @Test
    void createRegularOrderThrowsWhenSellingPriceIsBelowAllowedRange() {
        Store store = store(15L, 1, 3L, 7L, 2_500, 4_000);
        Menu menu = store.getMenu();

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(orderRepository.findDailyStartOrder(15L, 1)).thenReturn(Optional.empty());
        when(menuRepository.findById(7L)).thenReturn(Optional.of(menu));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 1, menu)).thenReturn(null);
        when(eventEffectResolver.resolve(any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new EventEffectResolver.EventEffect(
                        0L,
                        0,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        Collections.emptyList(),
                        Collections.emptyList()
                ));

        assertThatThrownBy(() -> orderService.createRegularOrder(1, new RegularOrderRequest(7, 50, 2_000)))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_INVALID_SELLING_PRICE));
    }

    @Test
    void createRegularOrderAppliesIngredientCostEventMultiplierToTotalCost() {
        Store store = store(15L, 1, 3L, 7L, 2_500, 4_000);
        Menu menu = store.getMenu();

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(orderRepository.findDailyStartOrder(15L, 1)).thenReturn(Optional.empty());
        when(menuRepository.findById(7L)).thenReturn(Optional.of(menu));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 1, menu)).thenReturn(null);
        when(eventEffectResolver.resolve(any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new EventEffectResolver.EventEffect(
                        0L,
                        0,
                        BigDecimal.ONE,
                        new BigDecimal("1.05"),
                        Collections.emptyList(),
                        Collections.emptyList()
                ));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 201L);
            return saved;
        });

        RegularOrderResponse response = orderService.createRegularOrder(1, new RegularOrderRequest(7, 50, 7_000));

        assertThat(response.totalCost()).isEqualTo(157_500);
        assertThat(response.costPrice()).isEqualTo(3_150);
    }

    @Test
    void createRegularOrderAllowsUsingAllCarriedCashBeforeClosingRentSettlement() {
        Store store = store(15L, 1, 3L, 7L, 2_500, 4_000);
        Menu menu = store.getMenu();
        ReflectionTestUtils.setField(store.getSeason(), "currentDay", 3);
        ReflectionTestUtils.setField(store.getSeason(), "startTime", LocalDateTime.of(2026, 3, 17, 9, 53, 0));

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(orderRepository.findDailyStartOrder(15L, 3)).thenReturn(Optional.empty());
        when(menuRepository.findById(7L)).thenReturn(Optional.of(menu));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(15L, 3))
                .thenReturn(Optional.of(previousDailyReport(store, 2, 150_000)));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 3, menu)).thenReturn(null);
        when(eventEffectResolver.resolve(any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new EventEffectResolver.EventEffect(
                        0L,
                        0,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        Collections.emptyList(),
                        Collections.emptyList()
                ));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 301L);
            return saved;
        });

        RegularOrderResponse response = orderService.createRegularOrder(1, new RegularOrderRequest(7, 50, 7_000));

        assertThat(response.orderId()).isEqualTo(301L);
        assertThat(response.totalCost()).isEqualTo(150_000);
    }

    @Test
    void createRegularOrderThrowsWhenCarriedBalanceCannotCoverOrderCost() {
        Store store = store(15L, 1, 3L, 7L, 2_500, 4_000);
        Menu menu = store.getMenu();
        ReflectionTestUtils.setField(store.getSeason(), "currentDay", 3);
        ReflectionTestUtils.setField(store.getSeason(), "startTime", LocalDateTime.of(2026, 3, 17, 9, 53, 0));

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(orderRepository.findDailyStartOrder(15L, 3)).thenReturn(Optional.empty());
        when(menuRepository.findById(7L)).thenReturn(Optional.of(menu));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(15L, 3))
                .thenReturn(Optional.of(previousDailyReport(store, 2, 149_999)));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 3, menu)).thenReturn(null);
        when(eventEffectResolver.resolve(any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new EventEffectResolver.EventEffect(
                        0L,
                        0,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        Collections.emptyList(),
                        Collections.emptyList()
                ));

        assertThatThrownBy(() -> orderService.createRegularOrder(1, new RegularOrderRequest(7, 50, 7_000)))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_INSUFFICIENT_BALANCE));
    }

    @Test
    void createRegularOrderUsesLatestEarlierReportWhenPreviousDayReportIsMissing() {
        Store store = store(15L, 1, 3L, 7L, 2_500, 4_000);
        Menu menu = store.getMenu();
        ReflectionTestUtils.setField(store.getSeason(), "currentDay", 3);
        ReflectionTestUtils.setField(store.getSeason(), "startTime", LocalDateTime.of(2026, 3, 17, 9, 53, 0));

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(orderRepository.findDailyStartOrder(15L, 3)).thenReturn(Optional.empty());
        when(menuRepository.findById(7L)).thenReturn(Optional.of(menu));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(15L, 3))
                .thenReturn(Optional.of(previousDailyReport(store, 1, 150_000)));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 3, menu)).thenReturn(null);
        when(eventEffectResolver.resolve(any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new EventEffectResolver.EventEffect(
                        0L,
                        0,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        Collections.emptyList(),
                        Collections.emptyList()
                ));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 401L);
            return saved;
        });

        RegularOrderResponse response = orderService.createRegularOrder(1, new RegularOrderRequest(7, 50, 7_000));

        assertThat(response.orderId()).isEqualTo(401L);
        assertThat(response.totalCost()).isEqualTo(150_000);
    }

    @Test
    void getCurrentOrderClampsNegativeCarryOverStockFromPreviousReport() {
        Store store = store(15L, 1, 3L, 7L, 2_500, 4_000);
        Menu menu = store.getMenu();
        ReflectionTestUtils.setField(store.getSeason(), "currentDay", 3);
        ReflectionTestUtils.setField(store.getSeason(), "startTime", LocalDateTime.of(2026, 3, 17, 9, 53, 0));

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 3, menu)).thenReturn(null);
        when(eventEffectResolver.resolve(any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new EventEffectResolver.EventEffect(
                        0L,
                        0,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        Collections.emptyList(),
                        Collections.emptyList()
                ));
        when(gameDayStoreStateRedisRepository.find(15L, 3)).thenReturn(Optional.empty());
        when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(15L, 3))
                .thenReturn(Optional.of(previousDailyReport(store, 2, 150_000, -15)));
        when(orderRepository.findDailyStartOrder(15L, 3))
                .thenReturn(Optional.of(Order.create(menu, store, 50, 150_000, 7_000, 3)));

        assertThat(orderService.getCurrentOrder(1, null).stock()).isEqualTo(50);
    }

    @Test
    void getCurrentOrderDisposesCarryOverStockOnRegularOrderDay() {
        Store store = store(15L, 1, 3L, 7L, 2_500, 4_000);
        Menu menu = store.getMenu();
        ReflectionTestUtils.setField(store.getSeason(), "currentDay", 3);
        ReflectionTestUtils.setField(store.getSeason(), "startTime", LocalDateTime.of(2026, 3, 17, 9, 53, 0));

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(BigDecimal.ONE));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 3, menu)).thenReturn(null);
        when(eventEffectResolver.resolve(any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new EventEffectResolver.EventEffect(
                        0L,
                        0,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        Collections.emptyList(),
                        Collections.emptyList()
                ));
        when(gameDayStoreStateRedisRepository.find(15L, 3)).thenReturn(Optional.empty());
        when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(15L, 3))
                .thenReturn(Optional.of(previousDailyReport(store, 2, 150_000, 15)));
        when(orderRepository.findDailyStartOrder(15L, 3)).thenReturn(Optional.empty());

        assertThat(orderService.getCurrentOrder(1, null).stock()).isZero();
    }

    private Store store(Long storeId, Integer userId, Long locationId, Long menuId, int originPrice, int currentPrice) {
        User user = new User("order@test.com", "tester");
        ReflectionTestUtils.setField(user, "id", userId);

        Location location = instantiate(Location.class);
        ReflectionTestUtils.setField(location, "id", locationId);
        ReflectionTestUtils.setField(location, "locationName", "fixture-location");
        ReflectionTestUtils.setField(location, "rent", 100_000);
        ReflectionTestUtils.setField(location, "interiorCost", 50_000);

        Menu menu = instantiate(Menu.class);
        ReflectionTestUtils.setField(menu, "id", menuId);
        ReflectionTestUtils.setField(menu, "menuName", "fixture-menu");
        ReflectionTestUtils.setField(menu, "originPrice", originPrice);

        Season season = instantiate(Season.class);
        ReflectionTestUtils.setField(season, "id", 9L);
        ReflectionTestUtils.setField(season, "status", SeasonStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(season, "currentDay", 1);
        ReflectionTestUtils.setField(season, "totalDays", 7);
        ReflectionTestUtils.setField(season, "startTime", LocalDateTime.of(2026, 3, 17, 9, 58, 50));
        ReflectionTestUtils.setField(season, "endTime", LocalDateTime.of(2026, 3, 17, 10, 28, 50));

        Store store = instantiate(Store.class);
        ReflectionTestUtils.setField(store, "id", storeId);
        ReflectionTestUtils.setField(store, "user", user);
        ReflectionTestUtils.setField(store, "location", location);
        ReflectionTestUtils.setField(store, "menu", menu);
        ReflectionTestUtils.setField(store, "season", season);
        ReflectionTestUtils.setField(store, "storeName", "fixture-store");
        ReflectionTestUtils.setField(store, "price", currentPrice);
        return store;
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

    private com.ssafy.S14P21A205.game.season.entity.DailyReport previousDailyReport(Store store, int day, int balance) {
        return previousDailyReport(store, day, balance, 0);
    }

    private com.ssafy.S14P21A205.game.season.entity.DailyReport previousDailyReport(
            Store store,
            int day,
            int balance,
            int stockRemaining
    ) {
        return com.ssafy.S14P21A205.game.season.entity.DailyReport.create(
                store,
                day,
                store.getLocation().getLocationName(),
                store.getMenu().getMenuName(),
                0,
                0,
                0,
                0,
                0,
                stockRemaining,
                0,
                false,
                balance,
                BigDecimal.ZERO
        );
    }
}
