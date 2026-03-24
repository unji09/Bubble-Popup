package com.ssafy.S14P21A205.action.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.action.dto.ActionStatusResponse;
import com.ssafy.S14P21A205.action.dto.DiscountRequest;
import com.ssafy.S14P21A205.action.dto.DiscountResponse;
import com.ssafy.S14P21A205.action.dto.DonationRequest;
import com.ssafy.S14P21A205.action.dto.DonationResponse;
import com.ssafy.S14P21A205.action.dto.EmergencyOrderRequest;
import com.ssafy.S14P21A205.action.dto.EmergencyOrderResponse;
import com.ssafy.S14P21A205.action.entity.Action;
import com.ssafy.S14P21A205.action.entity.ActionCategory;
import com.ssafy.S14P21A205.action.entity.PromotionType;
import com.ssafy.S14P21A205.action.repository.ActionLogRepository;
import com.ssafy.S14P21A205.action.repository.ActionRepository;
import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.dto.GameDayStartResponse;
import com.ssafy.S14P21A205.game.day.policy.CaptureRatePolicy;
import com.ssafy.S14P21A205.game.day.policy.StoreRankingPolicy;
import com.ssafy.S14P21A205.game.day.resolver.EventEffectResolver;
import com.ssafy.S14P21A205.game.day.resolver.NewsRankingResolver;
import com.ssafy.S14P21A205.game.day.resolver.TrafficDelayResolver;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.environment.entity.TrafficStatus;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.order.entity.Order;
import com.ssafy.S14P21A205.order.repository.OrderRepository;
import com.ssafy.S14P21A205.shop.entity.ItemCategory;
import com.ssafy.S14P21A205.shop.repository.ItemUserRepository;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.MenuRepository;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.support.GameDayTestFixtures;
import com.ssafy.S14P21A205.user.entity.User;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ActionServiceImplTests {

    @Mock
    private GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private ActionLogRepository actionLogRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ItemUserRepository itemUserRepository;

    @Mock
    private EventEffectResolver eventEffectResolver;

    @Mock
    private TrafficDelayResolver trafficDelayResolver;

    @Mock
    private NewsRankingResolver newsRankingResolver;

    private ActionServiceImpl actionService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-03-17T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        actionService = new ActionServiceImpl(
                gameDayStoreStateRedisRepository,
                actionRepository,
                actionLogRepository,
                storeRepository,
                menuRepository,
                orderRepository,
                itemUserRepository,
                eventEffectResolver,
                trafficDelayResolver,
                new StoreRankingPolicy(),
                newsRankingResolver,
                new CaptureRatePolicy(),
                fixedClock
        );
    }

    @Test
    void getActionStatusTreatsLegacyPromotionKeysAsPromotionUsed() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 2_000);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(gameDayStoreStateRedisRepository.getActions(15L, 2)).thenReturn(Map.of("sns", true));

        ActionStatusResponse response = actionService.getActionStatus(1);

        assertThat(response.discountUsed()).isFalse();
        assertThat(response.donationUsed()).isFalse();
        assertThat(response.emergencyUsed()).isFalse();
        assertThat(response.promotionUsed()).isTrue();
    }

    @Test
    void executePromotionBlocksWhenAnyLegacyPromotionKeyIsAlreadyUsed() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 2_000);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(gameDayStoreStateRedisRepository.getActions(15L, 2)).thenReturn(Map.of("leaflet", true));

        assertThatThrownBy(() -> actionService.executePromotion(
                1,
                new com.ssafy.S14P21A205.action.dto.PromotionRequest(PromotionType.SNS)
        ))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.ACTION_ALREADY_USED));
    }

    @Test
    void executePromotionMarksSharedPromotionFlagAndLegacyTypeFlag() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 2_000);
        Action promotionAction = promotionAction(PromotionType.SNS, 500, new BigDecimal("0.10"));
        GameDayLiveState state = state(500_000L);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(gameDayStoreStateRedisRepository.getActions(15L, 2)).thenReturn(Map.of());
        when(actionRepository.findByCategoryAndPromotionType(ActionCategory.PROMOTION, PromotionType.SNS))
                .thenReturn(Optional.of(promotionAction));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.of(state));

        actionService.executePromotion(
                1,
                new com.ssafy.S14P21A205.action.dto.PromotionRequest(PromotionType.SNS)
        );

        verify(gameDayStoreStateRedisRepository).updateField(15L, 2, "capture_rate", "0.5500");
        verify(gameDayStoreStateRedisRepository).markActionUsed(15L, 2, "promotion");
        verify(gameDayStoreStateRedisRepository).markActionUsed(15L, 2, "sns");
        verify(gameDayStoreStateRedisRepository).saveBalance(15L, 2, 499_500L);
    }

    @Test
    void executeDiscountAppliesTwoHundredPercentBandMultiplier() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 2_000, 5_000);
        Action discountAction = action(ActionCategory.DISCOUNT, 500);
        GameDayLiveState state = state(500_000L);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(gameDayStoreStateRedisRepository.getActions(15L, 2)).thenReturn(Map.of());
        when(actionRepository.findByCategory(ActionCategory.DISCOUNT)).thenReturn(List.of(discountAction));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.of(state));
        when(storeRepository.findAveragePriceBySeasonIdAndMenuId(9L, 7L)).thenReturn(2_000);

        DiscountResponse response = actionService.executeDiscount(1, new DiscountRequest(1_000));

        assertThat(response.previousPrice()).isEqualTo(5_000);
        assertThat(response.newPrice()).isEqualTo(4_000);
        assertThat(response.priceRange()).isEqualTo("ABOVE");
        assertThat(response.priceRangeMultiplier()).isEqualByComparingTo("0.01");
        verify(gameDayStoreStateRedisRepository).updateField(15L, 2, "sale_price", "4000");
        verify(gameDayStoreStateRedisRepository).updateField(15L, 2, "capture_rate", "0.0050");
        verify(gameDayStoreStateRedisRepository).markActionUsed(15L, 2, "discount");
        verify(gameDayStoreStateRedisRepository).saveBalance(15L, 2, 499_500L);
    }

    @Test
    void executeDiscountTreatsElevenToTwentyPercentPremiumAsOneTwentyBand() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 2_000, 4_700);
        Action discountAction = action(ActionCategory.DISCOUNT, 500);
        GameDayLiveState state = state(500_000L);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(gameDayStoreStateRedisRepository.getActions(15L, 2)).thenReturn(Map.of());
        when(actionRepository.findByCategory(ActionCategory.DISCOUNT)).thenReturn(List.of(discountAction));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.of(state));
        when(storeRepository.findAveragePriceBySeasonIdAndMenuId(9L, 7L)).thenReturn(4_000);

        DiscountResponse response = actionService.executeDiscount(1, new DiscountRequest(100));

        assertThat(response.newPrice()).isEqualTo(4_600);
        assertThat(response.priceRange()).isEqualTo("ABOVE");
        assertThat(response.priceRangeMultiplier()).isEqualByComparingTo("0.80");
        verify(gameDayStoreStateRedisRepository).updateField(15L, 2, "capture_rate", "0.4000");
    }

    @Test
    void executeDiscountKeepsAverageBandWithinTenPercent() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 2_000, 4_500);
        Action discountAction = action(ActionCategory.DISCOUNT, 500);
        GameDayLiveState state = state(500_000L);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(gameDayStoreStateRedisRepository.getActions(15L, 2)).thenReturn(Map.of());
        when(actionRepository.findByCategory(ActionCategory.DISCOUNT)).thenReturn(List.of(discountAction));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.of(state));
        when(storeRepository.findAveragePriceBySeasonIdAndMenuId(9L, 7L)).thenReturn(4_000);

        DiscountResponse response = actionService.executeDiscount(1, new DiscountRequest(500));

        assertThat(response.newPrice()).isEqualTo(4_000);
        assertThat(response.priceRange()).isEqualTo("AVERAGE");
        assertThat(response.priceRangeMultiplier()).isEqualByComparingTo("1.00");
        verify(gameDayStoreStateRedisRepository).updateField(15L, 2, "capture_rate", "0.5000");
    }

    @Test
    void executeDiscountTreatsEightyToNinetyPercentDiscountAsEightyBand() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 2_000, 4_000);
        Action discountAction = action(ActionCategory.DISCOUNT, 500);
        GameDayLiveState state = state(500_000L);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(gameDayStoreStateRedisRepository.getActions(15L, 2)).thenReturn(Map.of());
        when(actionRepository.findByCategory(ActionCategory.DISCOUNT)).thenReturn(List.of(discountAction));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.of(state));
        when(storeRepository.findAveragePriceBySeasonIdAndMenuId(9L, 7L)).thenReturn(4_000);

        DiscountResponse response = actionService.executeDiscount(1, new DiscountRequest(600));

        assertThat(response.newPrice()).isEqualTo(3_400);
        assertThat(response.priceRange()).isEqualTo("BELOW");
        assertThat(response.priceRangeMultiplier()).isEqualByComparingTo("1.20");
        verify(gameDayStoreStateRedisRepository).updateField(15L, 2, "capture_rate", "0.6000");
    }

    @Test
    void executeDiscountAppliesSixtyPercentBandMultiplier() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 900, 2_500);
        Action discountAction = action(ActionCategory.DISCOUNT, 500);
        GameDayLiveState state = state(500_000L);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(gameDayStoreStateRedisRepository.getActions(15L, 2)).thenReturn(Map.of());
        when(actionRepository.findByCategory(ActionCategory.DISCOUNT)).thenReturn(List.of(discountAction));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.of(state));
        when(storeRepository.findAveragePriceBySeasonIdAndMenuId(9L, 7L)).thenReturn(2_500);

        DiscountResponse response = actionService.executeDiscount(1, new DiscountRequest(1_000));

        assertThat(response.previousPrice()).isEqualTo(2_500);
        assertThat(response.newPrice()).isEqualTo(1_500);
        assertThat(response.priceRange()).isEqualTo("BELOW");
        assertThat(response.priceRangeMultiplier()).isEqualByComparingTo("1.30");
        verify(gameDayStoreStateRedisRepository).updateField(15L, 2, "sale_price", "1500");
        verify(gameDayStoreStateRedisRepository).updateField(15L, 2, "capture_rate", "0.6500");
        verify(gameDayStoreStateRedisRepository).markActionUsed(15L, 2, "discount");
    }

    @Test
    void executeDiscountThrowsWhenNewPriceFallsBelowOriginPrice() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 2_000, 2_500);
        Action discountAction = action(ActionCategory.DISCOUNT, 500);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(gameDayStoreStateRedisRepository.getActions(15L, 2)).thenReturn(Map.of());
        when(actionRepository.findByCategory(ActionCategory.DISCOUNT)).thenReturn(List.of(discountAction));

        assertThatThrownBy(() -> actionService.executeDiscount(1, new DiscountRequest(600)))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void executeDonationAppliesFixedOnePointOneMultiplierAndReducesStock() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 2_000, 4_000);
        Action donationAction = action(ActionCategory.DONATION, 0);
        GameDayLiveState state = state(500_000L);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(gameDayStoreStateRedisRepository.getActions(15L, 2)).thenReturn(Map.of());
        when(actionRepository.findByCategory(ActionCategory.DONATION)).thenReturn(List.of(donationAction));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.of(state));

        DonationResponse response = actionService.executeDonation(1, new DonationRequest(25));

        assertThat(response.quantity()).isEqualTo(25);
        assertThat(response.captureRateBonus()).isEqualByComparingTo("0.10");
        verify(gameDayStoreStateRedisRepository).updateField(15L, 2, "stock", "25");
        verify(gameDayStoreStateRedisRepository).updateField(15L, 2, "capture_rate", "0.5500");
        verify(gameDayStoreStateRedisRepository).markActionUsed(15L, 2, "donation");
        verify(gameDayStoreStateRedisRepository).saveBalance(15L, 2, 500_000L);
    }

    @Test
    void executeEmergencyOrderAppliesIngredientDiscountTrendMultiplierAndCostMultiplierFromActiveEvent() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 2_000);
        com.ssafy.S14P21A205.shop.entity.Menu emergencyMenu = menu(8L, 3_000, "emergency-cookie");
        Season season = store.getSeason();
        Action emergencyAction = action(ActionCategory.EMERGENCY_ORDER, 500);
        GameDayLiveState state = state(500_000L);
        LocalDateTime now = LocalDateTime.of(2026, 3, 17, 10, 0);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(menuRepository.findById(8L)).thenReturn(Optional.of(emergencyMenu));
        when(gameDayStoreStateRedisRepository.getActions(15L, 2)).thenReturn(Map.of());
        when(actionRepository.findByCategory(ActionCategory.EMERGENCY_ORDER)).thenReturn(List.of(emergencyAction));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.of(state));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(
                store,
                store(16L, 2, 3L, 8L, 2, 7, 3_000),
                store(17L, 3, 3L, 8L, 2, 7, 3_000)
        ));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(new BigDecimal("0.80")));
        when(eventEffectResolver.resolve(
                eq(season),
                eq(2),
                eq(now),
                eq(3L),
                eq(8L)
        )).thenReturn(new EventEffectResolver.EventEffect(
                0L,
                0,
                BigDecimal.ONE,
                new BigDecimal("1.05"),
                List.of(),
                List.of()
        ));
        when(trafficDelayResolver.resolve(
                eq(9L),
                eq(3L),
                eq(2),
                eq(7),
                eq(state.startedAt()),
                eq(now)
        )).thenReturn(resolvedTraffic(2, 10, TrafficStatus.NORMAL, 20));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmergencyOrderResponse response = actionService.executeEmergencyOrder(1, new EmergencyOrderRequest(8, 10, 4_300));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getMenu().getId()).isEqualTo(8L);
        assertThat(orderCaptor.getValue().getTotalCost()).isEqualTo(45_360);
        assertThat(orderCaptor.getValue().getSalePrice()).isEqualTo(4_300);
        assertThat(orderCaptor.getValue().getArrivedTime()).isEqualTo(LocalDateTime.of(2026, 3, 17, 10, 0, 20));
        assertThat(response.totalCost()).isEqualTo(45_360);
        assertThat(response.quantity()).isEqualTo(10);
        verify(gameDayStoreStateRedisRepository).markActionUsed(15L, 2, "emergency");
        verify(gameDayStoreStateRedisRepository).saveBalance(15L, 2, 454_140L);
    }

    @Test
    void executeEmergencyOrderThrowsWhenLiveStateIsMissing() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 2_000);
        Action emergencyAction = action(ActionCategory.EMERGENCY_ORDER, 500);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(menuRepository.findById(7L)).thenReturn(Optional.of(store.getMenu()));
        when(gameDayStoreStateRedisRepository.getActions(15L, 2)).thenReturn(Map.of());
        when(actionRepository.findByCategory(ActionCategory.EMERGENCY_ORDER)).thenReturn(List.of(emergencyAction));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actionService.executeEmergencyOrder(1, new EmergencyOrderRequest(7, 10, 4_000)))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.GAME_STATE_NOT_FOUND));
    }

    @Test
    void executeEmergencyOrderThrowsWhenSalePriceFallsBelowOriginPrice() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 2_000);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(menuRepository.findById(7L)).thenReturn(Optional.of(store.getMenu()));

        assertThatThrownBy(() -> actionService.executeEmergencyOrder(1, new EmergencyOrderRequest(7, 10, 1_900)))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void executeEmergencyOrderThrowsWhenMenuDoesNotExist() {
        Store store = store(15L, 1, 3L, 7L, 2, 7, 2_000);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(menuRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actionService.executeEmergencyOrder(1, new EmergencyOrderRequest(8, 10, 4_300)))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MENU_NOT_FOUND));
    }

    @Test
    void executeEmergencyOrderUsesSharedDayFourFixtureCost() {
        fixedClock = GameDayTestFixtures.fixedClockAtStart();
        actionService = new ActionServiceImpl(
                gameDayStoreStateRedisRepository,
                actionRepository,
                actionLogRepository,
                storeRepository,
                menuRepository,
                orderRepository,
                itemUserRepository,
                eventEffectResolver,
                trafficDelayResolver,
                new StoreRankingPolicy(),
                newsRankingResolver,
                new CaptureRatePolicy(),
                fixedClock
        );

        User user = GameDayTestFixtures.user(GameDayTestFixtures.USER_ID);
        Location location = GameDayTestFixtures.location();
        com.ssafy.S14P21A205.shop.entity.Menu menu = GameDayTestFixtures.menu();
        Season season = GameDayTestFixtures.season();
        Store store = GameDayTestFixtures.store(user, season, location, menu);
        Action emergencyAction = action(ActionCategory.EMERGENCY_ORDER, 0);
        GameDayLiveState state = GameDayTestFixtures.liveState(
                GameDayTestFixtures.startResponse(List.of()),
                GameDayTestFixtures.fixedPurchaseList(),
                GameDayTestFixtures.DAY4_STARTED_AT
        );

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(
                GameDayTestFixtures.USER_ID,
                SeasonStatus.IN_PROGRESS
        )).thenReturn(Optional.of(store));
        when(menuRepository.findById(GameDayTestFixtures.MENU_ID)).thenReturn(Optional.of(menu));
        when(gameDayStoreStateRedisRepository.getActions(
                GameDayTestFixtures.STORE_ID,
                GameDayTestFixtures.CURRENT_DAY
        )).thenReturn(Map.of());
        when(actionRepository.findByCategory(ActionCategory.EMERGENCY_ORDER)).thenReturn(List.of(emergencyAction));
        when(gameDayStoreStateRedisRepository.find(GameDayTestFixtures.STORE_ID, GameDayTestFixtures.CURRENT_DAY))
                .thenReturn(Optional.of(state));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(
                GameDayTestFixtures.USER_ID,
                ItemCategory.INGREDIENT
        )).thenReturn(Optional.empty());
        when(eventEffectResolver.resolve(
                eq(season),
                eq(GameDayTestFixtures.CURRENT_DAY),
                eq(GameDayTestFixtures.DAY4_STARTED_AT),
                eq(GameDayTestFixtures.LOCATION_ID),
                eq(GameDayTestFixtures.MENU_ID)
        )).thenReturn(new EventEffectResolver.EventEffect(
                0L,
                0,
                BigDecimal.ONE,
                new BigDecimal("1.05"),
                List.of(),
                List.of()
        ));
        when(trafficDelayResolver.resolve(
                eq(GameDayTestFixtures.SEASON_ID),
                eq(GameDayTestFixtures.LOCATION_ID),
                eq(GameDayTestFixtures.CURRENT_DAY),
                eq(GameDayTestFixtures.TOTAL_DAYS),
                eq(GameDayTestFixtures.DAY4_STARTED_AT),
                eq(GameDayTestFixtures.DAY4_STARTED_AT)
        )).thenReturn(resolvedTraffic(GameDayTestFixtures.CURRENT_DAY, 10, TrafficStatus.NORMAL, 20));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmergencyOrderResponse response = actionService.executeEmergencyOrder(
                GameDayTestFixtures.USER_ID,
                new EmergencyOrderRequest(Math.toIntExact(GameDayTestFixtures.MENU_ID), 20, 3_300)
        );

        assertThat(response.totalCost()).isEqualTo(75_600);
        assertThat(response.arrivedTime()).isEqualTo(LocalDateTime.of(2026, 3, 17, 10, 0, 20));
        verify(gameDayStoreStateRedisRepository).markActionUsed(
                GameDayTestFixtures.STORE_ID,
                GameDayTestFixtures.CURRENT_DAY,
                "emergency"
        );
        verify(gameDayStoreStateRedisRepository).saveBalance(
                GameDayTestFixtures.STORE_ID,
                GameDayTestFixtures.CURRENT_DAY,
                4_914_400L
        );
    }

    private TrafficDelayResolver.ResolvedTraffic resolvedTraffic(
            Integer resolvedDay,
            Integer resolvedHour,
            TrafficStatus trafficStatus,
            int delaySeconds
    ) {
        return new TrafficDelayResolver.ResolvedTraffic(resolvedDay, resolvedHour, trafficStatus, delaySeconds);
    }

    private GameDayLiveState state(long balance) {
        return new GameDayLiveState(
                LocalDateTime.of(2026, 3, 17, 10, 0),
                List.of(1, 2, 3),
                0,
                new GameDayStartResponse(
                        "10:00",
                        "22:00",
                        Map.of("10", new GameDayStartResponse.HourlySchedule(100, BigDecimal.ONE, BigDecimal.ONE, 100)),
                        "SUNNY",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        new BigDecimal("0.50"),
                        List.of(),
                        5_000_000,
                        50,
                        null,
                        null
                ),
                0,
                0,
                new BigDecimal("0.50"),
                4_000,
                0,
                List.of(),
                0,
                0L,
                0,
                0,
                0L,
                0L,
                balance,
                50,
                LocalDateTime.of(2026, 3, 17, 10, 0)
        );
    }

    private Action action(ActionCategory category, int cost) {
        Action action = instantiate(Action.class);
        ReflectionTestUtils.setField(action, "id", 1L);
        ReflectionTestUtils.setField(action, "category", category);
        ReflectionTestUtils.setField(action, "cost", cost);
        ReflectionTestUtils.setField(action, "captureRate", BigDecimal.ZERO);
        return action;
    }

    private Action promotionAction(PromotionType promotionType, int cost, BigDecimal captureRate) {
        Action action = action(ActionCategory.PROMOTION, cost);
        ReflectionTestUtils.setField(action, "promotionType", promotionType);
        ReflectionTestUtils.setField(action, "captureRate", captureRate);
        return action;
    }

    private Store store(
            Long storeId,
            Integer userId,
            Long locationId,
            Long menuId,
            int currentDay,
            int totalDays,
            int originPrice
    ) {
        return store(storeId, userId, locationId, menuId, currentDay, totalDays, originPrice, 4_000);
    }

    private Store store(
            Long storeId,
            Integer userId,
            Long locationId,
            Long menuId,
            int currentDay,
            int totalDays,
            int originPrice,
            int currentPrice
    ) {
        User user = new User("action@test.com", "tester");
        ReflectionTestUtils.setField(user, "id", userId);

        Location location = instantiate(Location.class);
        ReflectionTestUtils.setField(location, "id", locationId);

        com.ssafy.S14P21A205.shop.entity.Menu menu = instantiate(com.ssafy.S14P21A205.shop.entity.Menu.class);
        ReflectionTestUtils.setField(menu, "id", menuId);
        ReflectionTestUtils.setField(menu, "originPrice", originPrice);

        Season season = instantiate(Season.class);
        ReflectionTestUtils.setField(season, "id", 9L);
        ReflectionTestUtils.setField(season, "status", SeasonStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(season, "currentDay", currentDay);
        ReflectionTestUtils.setField(season, "totalDays", totalDays);
        LocalDateTime businessStartAt = LocalDateTime.ofInstant(fixedClock.instant(), fixedClock.getZone()).withSecond(0).withNano(0);
        LocalDateTime seasonStartAt = businessStartAt.minusSeconds(120L + (currentDay - 1L) * 180L + 40L);
        ReflectionTestUtils.setField(season, "startTime", seasonStartAt);
        ReflectionTestUtils.setField(season, "endTime", seasonStartAt.plusSeconds(120L + totalDays * 180L + 120L));

        Store store = instantiate(Store.class);
        ReflectionTestUtils.setField(store, "id", storeId);
        ReflectionTestUtils.setField(store, "user", user);
        ReflectionTestUtils.setField(store, "location", location);
        ReflectionTestUtils.setField(store, "menu", menu);
        ReflectionTestUtils.setField(store, "season", season);
        ReflectionTestUtils.setField(store, "price", currentPrice);
        return store;
    }

    private com.ssafy.S14P21A205.shop.entity.Menu menu(Long menuId, int originPrice, String menuName) {
        com.ssafy.S14P21A205.shop.entity.Menu menu = instantiate(com.ssafy.S14P21A205.shop.entity.Menu.class);
        ReflectionTestUtils.setField(menu, "id", menuId);
        ReflectionTestUtils.setField(menu, "originPrice", originPrice);
        ReflectionTestUtils.setField(menu, "menuName", menuName);
        return menu;
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
