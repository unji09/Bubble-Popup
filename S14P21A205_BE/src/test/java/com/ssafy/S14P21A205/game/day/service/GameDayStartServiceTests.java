package com.ssafy.S14P21A205.game.day.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.dto.GameDayStartResponse;
import com.ssafy.S14P21A205.game.day.generator.PurchaseListGenerator;
import com.ssafy.S14P21A205.game.day.policy.CaptureRatePolicy;
import com.ssafy.S14P21A205.game.day.policy.PopulationPolicy;
import com.ssafy.S14P21A205.game.day.policy.RentPolicy;
import com.ssafy.S14P21A205.game.day.policy.StoreRankingPolicy;
import com.ssafy.S14P21A205.game.day.resolver.EnvironmentScheduleResolver;
import com.ssafy.S14P21A205.game.day.resolver.EventScheduleResolver;
import com.ssafy.S14P21A205.game.day.resolver.NewsRankingResolver;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.environment.entity.Population;
import com.ssafy.S14P21A205.game.environment.entity.Traffic;
import com.ssafy.S14P21A205.game.environment.entity.TrafficStatus;
import com.ssafy.S14P21A205.game.environment.repository.PopulationRepository;
import com.ssafy.S14P21A205.game.environment.repository.TrafficRepository;
import com.ssafy.S14P21A205.game.environment.repository.WeatherDayRedisRepository;
import com.ssafy.S14P21A205.game.environment.repository.WeatherLocationRepository;
import com.ssafy.S14P21A205.game.event.entity.DailyEvent;
import com.ssafy.S14P21A205.game.event.entity.EventCategory;
import com.ssafy.S14P21A205.game.event.entity.EventEndTime;
import com.ssafy.S14P21A205.game.event.entity.EventStartTime;
import com.ssafy.S14P21A205.game.event.entity.RandomEvent;
import com.ssafy.S14P21A205.game.event.repository.DailyEventRepository;
import com.ssafy.S14P21A205.game.news.entity.NewsReport;
import com.ssafy.S14P21A205.game.news.repository.NewsReportRepository;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.order.entity.Order;
import com.ssafy.S14P21A205.order.entity.OrderType;
import com.ssafy.S14P21A205.order.repository.OrderRepository;
import com.ssafy.S14P21A205.shop.entity.ItemCategory;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.shop.repository.ItemUserRepository;
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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GameDayStartServiceTests {

    @Mock
    private UserService userService;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private DailyReportRepository dailyReportRepository;

    @Mock
    private PopulationRepository populationRepository;

    @Mock
    private TrafficRepository trafficRepository;

    @Mock
    private DailyEventRepository dailyEventRepository;

    @Mock
    private NewsReportRepository newsReportRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;

    @Mock
    private PurchaseListGenerator purchaseListGenerator;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ItemUserRepository itemUserRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private WeatherLocationRepository weatherLocationRepository;

    private GameDayStartService gameDayStartService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-03-09T05:32:10Z"), ZoneId.of("Asia/Seoul"));
        gameDayStartService = createService(fixedClock);
        org.mockito.Mockito.lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.lenient().when(valueOperations.get(any())).thenReturn(null);
        org.mockito.Mockito.lenient()
                .when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.RENT))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient()
                .when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient()
                .when(orderRepository.findByStoreIdAndOrderTypeOrderByArrivedTimeAscIdAsc(any(), eq(OrderType.EMERGENCY)))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(orderRepository.findFirstByStore_IdAndOrderedDayLessThanEqualAndSalePriceIsNotNullOrderByOrderedDayDescIdDesc(
                        any(),
                        any()
                ))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient()
                .when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(any(), any()))
                .thenReturn(Optional.empty());
    }

    private GameDayStartService createService(Clock clock) {
        StoreRankingPolicy storeRankingPolicy = new StoreRankingPolicy();
        RentPolicy rentPolicy = new RentPolicy(
                dailyReportRepository,
                dailyEventRepository,
                gameDayStoreStateRedisRepository,
                itemUserRepository,
                storeRankingPolicy
        );
        PopulationPolicy populationPolicy = new PopulationPolicy(populationRepository, trafficRepository);
        CaptureRatePolicy captureRatePolicy = new CaptureRatePolicy();
        ObjectMapper objectMapper = new ObjectMapper();
        WeatherDayRedisRepository weatherDayRedisRepository =
                new WeatherDayRedisRepository(stringRedisTemplate, objectMapper);
        EnvironmentScheduleResolver environmentScheduleResolver =
                new EnvironmentScheduleResolver(populationPolicy, weatherDayRedisRepository, weatherLocationRepository);
        NewsRankingResolver newsRankingResolver = new NewsRankingResolver(newsReportRepository, objectMapper);
        EventScheduleResolver eventScheduleResolver = new EventScheduleResolver(dailyEventRepository);
        return new GameDayStartService(
                userService,
                storeRepository,
                environmentScheduleResolver,
                orderRepository,
                rentPolicy,
                captureRatePolicy,
                storeRankingPolicy,
                newsRankingResolver,
                eventScheduleResolver,
                gameDayStoreStateRedisRepository,
                purchaseListGenerator,
                clock
        );
    }

    @Test
    void startDayCreatesInitialStateAndFiltersScopedEvents() {
        User user = user(1);
        Store store = store(user, 15L, 3L, 1L, 9L, 1, 7, 4_500, 100_000, 2_000);
        DailyEvent globalEvent = dailyEvent(store.getSeason(), 1, "celebrity", "1.15", 200_000, null, null);
        DailyEvent locationEvent = dailyEvent(store.getSeason(), 1, "local-festival", "1.05", 0, 3L, null);
        DailyEvent ignoredMenuEvent = dailyEvent(store.getSeason(), 1, "other-menu-sale", "1.50", 999_999, null, 99L);
        List<Integer> fixedPurchaseList = List.of(1, 0, 2, 1, 1);

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(gameDayStoreStateRedisRepository.find(15L, 1)).thenReturn(Optional.empty());
        when(orderRepository.findDailyStartOrders(15L, 1)).thenReturn(List.of());
        when(valueOperations.get("season:9:weather:day:1")).thenReturn(
                """
                [
                  {"locationId":3,"day":1,"weatherType":"SUNNY","populationMultiplier":1.10}
                ]
                """
        );
        when(populationRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                population(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), 500),
                population(store.getLocation(), LocalDateTime.of(2026, 3, 9, 11, 0), 650)
        ));
        when(trafficRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), TrafficStatus.CONGESTED),
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 9, 11, 0), TrafficStatus.NORMAL)
        ));
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(9L, 1, 1))
                .thenReturn(List.of(globalEvent, locationEvent, ignoredMenuEvent));
        GameDayStartResponse response = gameDayStartService.startDay(mock(Authentication.class));

        assertThat(response.startTime()).isEqualTo("10:00");
        assertThat(response.endTime()).isEqualTo("22:00");
        assertThat(response.weatherMultiplier()).isEqualByComparingTo("1.10");
        assertThat(response.captureRate()).isEqualByComparingTo("0.50");
        assertThat(response.hourlySchedule().get("10").population()).isEqualTo(500);
        assertThat(response.hourlySchedule().get("11").trafficMultiplier()).isEqualByComparingTo("0.86");
        assertThat(response.hourlySchedule().get("10").effectivePopulation()).isEqualTo(627);
        assertThat(response.hourlySchedule().get("11").effectivePopulation()).isEqualTo(615);
        assertThat(response.initialStock()).isEqualTo(0);
        assertThat(response.initialBalance()).isEqualTo(4_800_000);
        assertThat(response.eventSchedule()).hasSize(2);
        assertThat(response.eventSchedule().get(0).type()).isEqualTo("celebrity");
        assertThat(response.eventSchedule().get(0).scope()).isNull();
        assertThat(response.eventSchedule().get(1).type()).isEqualTo("local-festival");
        assertThat(response.eventSchedule().get(1).scope()).isNotNull();
        assertThat(response.eventSchedule().get(1).scope().region()).isEqualTo(3L);
        assertThat(response.eventSchedule().get(1).scope().menu()).isNull();
        assertThat(response.marketSnapshot().avgMenuPrice()).isEqualTo(4_500);
        assertThat(response.marketSnapshot().regionStoreCount()).isEqualTo(1);
        assertThat(response.openingSummary().dailyRentApplied()).isEqualTo(130_000);
        assertThat(response.openingSummary().interiorCost()).isZero();
        assertThat(response.openingSummary().fixedCostTotal()).isZero();
        assertThat(response.openingSummary().appliedUnitCost()).isEqualTo(2_400);

        verify(gameDayStoreStateRedisRepository, never()).save(any(), any(), any());
    }

    @Test
    void startDayReturnsExistingStateResponseWhenSameDayAlreadyStarted() {
        User user = user(1);
        Store store = store(user, 15L, 3L, 1L, 9L, 1, 7, 5_000, 100_000, 2_000);
        GameDayStartResponse existingResponse = new GameDayStartResponse(
                "10:00",
                "22:00",
                Map.of(),
                "SUNNY",
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                List.of(),
                9_100_000,
                100,
                null,
                null
        );
        GameDayLiveState existingState = new GameDayLiveState(
                LocalDateTime.of(2026, 3, 9, 14, 30, 0),
                List.of(1, 2, 3),
                0,
                existingResponse,
                0,
                0,
                BigDecimal.ZERO,
                5_000,
                0,
                List.of(),
                0,
                0L,
                0,
                0,
                0L,
                0L,
                9_100_000L,
                100,
                LocalDateTime.of(2026, 3, 9, 14, 30, 0)
        );

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(gameDayStoreStateRedisRepository.find(15L, 1)).thenReturn(Optional.of(existingState));

        GameDayStartResponse response = gameDayStartService.startDay(mock(Authentication.class));

        assertThat(response).isEqualTo(existingResponse);
        verify(gameDayStoreStateRedisRepository, never()).save(any(), any(), any());
    }

    @Test
    void startDayUsesPreviousLiveStateCarryOverWhenPreviousReportIsMissingOnLaterDay() {
        User user = user(1);
        Store store = store(user, 15L, 3L, 1L, 9L, 2, 7, 5_000, 100_000, 2_000);

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.empty());
        when(gameDayStoreStateRedisRepository.find(15L, 1))
                .thenReturn(Optional.of(previousLiveState(9_700_000L, 15)));
        when(orderRepository.findDailyStartOrders(15L, 2)).thenReturn(List.of());
        when(valueOperations.get("season:9:weather:day:2")).thenReturn(
                """
                [
                  {"locationId":3,"day":2,"weatherType":"SUNNY","populationMultiplier":1.00}
                ]
                """
        );
        when(populationRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                population(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), 500),
                population(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), 500)
        ));
        when(trafficRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), TrafficStatus.NORMAL),
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), TrafficStatus.NORMAL)
        ));
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(9L, 1, 2)).thenReturn(List.of());
        GameDayStartResponse response = gameDayStartService.startDay(mock(Authentication.class));

        assertThat(response.initialBalance()).isEqualTo(9_700_000);
        assertThat(response.initialStock()).isEqualTo(15);
        assertThat(response.openingSummary().previousClosingBalance()).isEqualTo(9_700_000);
        assertThat(response.openingSummary().previousClosingStock()).isEqualTo(15);
        assertThat(response.openingSummary().interiorCost()).isZero();
    }

    @Test
    void startDayDisposesCarryOverStockOnRegularOrderDayWhenPreviousDayReportIsMissing() {
        User user = user(1);
        Store store = store(user, 15L, 3L, 1L, 9L, 3, 7, 5_000, 100_000, 2_000);

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(gameDayStoreStateRedisRepository.find(15L, 3)).thenReturn(Optional.empty());
        when(orderRepository.findDailyStartOrders(15L, 3)).thenReturn(List.of());
        when(valueOperations.get("season:9:weather:day:3")).thenReturn(
                """
                [
                  {"locationId":3,"day":3,"weatherType":"SUNNY","populationMultiplier":1.00}
                ]
                """
        );
        when(populationRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                population(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), 500),
                population(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), 500),
                population(store.getLocation(), LocalDateTime.of(2026, 3, 11, 10, 0), 500)
        ));
        when(trafficRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), TrafficStatus.NORMAL),
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), TrafficStatus.NORMAL),
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 11, 10, 0), TrafficStatus.NORMAL)
        ));
        when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(eq(15L), anyInt()))
                .thenReturn(Optional.of(previousDailyReport(store, 9_000_000, 10)));
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(9L, 2, 3)).thenReturn(List.of());

        GameDayStartResponse response = gameDayStartService.startDay(mock(Authentication.class));

        assertThat(response.initialBalance()).isEqualTo(9_000_000);
        assertThat(response.initialStock()).isZero();
        assertThat(response.openingSummary().previousClosingBalance()).isEqualTo(9_000_000);
        assertThat(response.openingSummary().previousClosingStock()).isEqualTo(10);
        assertThat(response.openingSummary().disposalQuantity()).isEqualTo(10);
        assertThat(response.openingSummary().openingAgedStock()).isZero();
        assertThat(response.openingSummary().openingFreshStock()).isZero();
    }

    @Test
    void startDayResetsDiscountedStorePriceToLatestRegularOrderSalePrice() {
        User user = user(1);
        Store store = store(user, 15L, 3L, 1L, 9L, 2, 7, 2_375, 100_000, 2_000);
        Order latestRegularOrder = Order.create(store.getMenu(), store, 50, 100_000, 2_500, 1);

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.empty());
        when(orderRepository.findDailyStartOrders(15L, 2)).thenReturn(List.of());
        when(orderRepository.findFirstByStore_IdAndOrderedDayLessThanEqualAndSalePriceIsNotNullOrderByOrderedDayDescIdDesc(
                15L,
                2
        )).thenReturn(Optional.of(latestRegularOrder));
        when(valueOperations.get("season:9:weather:day:2")).thenReturn(
                """
                [
                  {"locationId":3,"day":2,"weatherType":"SUNNY","populationMultiplier":1.00}
                ]
                """
        );
        when(populationRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                population(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), 500),
                population(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), 500)
        ));
        when(trafficRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), TrafficStatus.NORMAL),
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), TrafficStatus.NORMAL)
        ));
        when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(15L, 2))
                .thenReturn(Optional.of(previousDailyReport(store, 9_000_000, 10)));
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(9L, 1, 2)).thenReturn(List.of());

        GameDayStartResponse response = gameDayStartService.startDay(mock(Authentication.class));

        assertThat(store.getPrice()).isEqualTo(2_500);
        assertThat(response.marketSnapshot().avgMenuPrice()).isEqualTo(2_500);
    }

    @Test
    void startDayCarriesEmergencyStockAndSalePriceIntoNextDay() {
        User user = user(1);
        Store store = store(user, 15L, 3L, 1L, 9L, 2, 7, 5_000, 100_000, 2_000);
        Order overnightEmergencyOrder = Order.createEmergency(
                store.getMenu(),
                store,
                20,
                63_000,
                6_000,
                1,
                LocalDateTime.of(2026, 3, 9, 14, 32, 5)
        );

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.empty());
        when(orderRepository.findDailyStartOrders(15L, 2)).thenReturn(List.of());
        when(orderRepository.findByStoreIdAndOrderTypeOrderByArrivedTimeAscIdAsc(15L, OrderType.EMERGENCY))
                .thenReturn(List.of(overnightEmergencyOrder));
        when(valueOperations.get("season:9:weather:day:2")).thenReturn(
                """
                [
                  {"locationId":3,"day":2,"weatherType":"SUNNY","populationMultiplier":1.00}
                ]
                """
        );
        when(populationRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                population(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), 500),
                population(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), 500)
        ));
        when(trafficRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), TrafficStatus.NORMAL),
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), TrafficStatus.NORMAL)
        ));
        when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(15L, 2))
                .thenReturn(Optional.of(previousDailyReport(store, 9_000_000, 10)));
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(9L, 1, 2)).thenReturn(List.of());

        GameDayStartResponse response = gameDayStartService.startDay(mock(Authentication.class));

        assertThat(response.initialStock()).isEqualTo(30);
        assertThat(store.getPrice()).isEqualTo(6_000);

        verify(gameDayStoreStateRedisRepository, never()).save(any(), any(), any());
    }

    @Test
    void startDayReplacesOpeningStockWhenOvernightEmergencyOrderChangesMenu() {
        User user = user(1);
        Store store = store(user, 15L, 3L, 1L, 9L, 2, 7, 5_000, 100_000, 2_000);
        Menu emergencyMenu = menu(8L, "bagel", 3_000);
        Order overnightEmergencyOrder = Order.createEmergency(
                emergencyMenu,
                store,
                20,
                94_500,
                6_500,
                1,
                LocalDateTime.of(2026, 3, 9, 14, 32, 5)
        );

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.empty());
        when(orderRepository.findDailyStartOrders(15L, 2)).thenReturn(List.of());
        when(orderRepository.findByStoreIdAndOrderTypeOrderByArrivedTimeAscIdAsc(15L, OrderType.EMERGENCY))
                .thenReturn(List.of(overnightEmergencyOrder));
        when(valueOperations.get("season:9:weather:day:2")).thenReturn(
                """
                [
                  {"locationId":3,"day":2,"weatherType":"SUNNY","populationMultiplier":1.00}
                ]
                """
        );
        when(populationRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                population(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), 500),
                population(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), 500)
        ));
        when(trafficRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), TrafficStatus.NORMAL),
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), TrafficStatus.NORMAL)
        ));
        when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(15L, 2))
                .thenReturn(Optional.of(previousDailyReport(store, 9_000_000, 10)));
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(9L, 1, 2)).thenReturn(List.of());
        GameDayStartResponse response = gameDayStartService.startDay(mock(Authentication.class));

        assertThat(response.initialStock()).isEqualTo(20);
        assertThat(store.getMenu().getId()).isEqualTo(8L);
        assertThat(store.getPrice()).isEqualTo(6_500);

        verify(gameDayStoreStateRedisRepository, never()).save(any(), any(), any());
    }

    @Test
    void startDayUsesInitialJoinBalanceAndSeasonAdjustedRegularOrderCost() {
        User user = user(1);
        Store store = store(user, 15L, 3L, 1L, 9L, 1, 7, 4_500, 100_000, 2_000);
        Order existingOrder = Order.create(store.getMenu(), store, 120, 1_000_000, 1);

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(gameDayStoreStateRedisRepository.find(15L, 1)).thenReturn(Optional.empty());
        when(orderRepository.findDailyStartOrders(15L, 1)).thenReturn(List.of(existingOrder));
        when(valueOperations.get("season:9:weather:day:1")).thenReturn(
                """
                [
                  {"locationId":3,"day":1,"weatherType":"SUNNY","populationMultiplier":1.00}
                ]
                """
        );
        when(populationRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                population(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), 500)
        ));
        when(trafficRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), TrafficStatus.NORMAL)
        ));
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(9L, 1, 1)).thenReturn(List.of());
        GameDayStartResponse response = gameDayStartService.startDay(mock(Authentication.class));

        assertThat(response.initialBalance()).isEqualTo(4_512_000);
        assertThat(response.initialStock()).isEqualTo(120);
        assertThat(response.openingSummary().regularOrderCost()).isEqualTo(288_000);
        assertThat(response.openingSummary().fixedCostTotal()).isEqualTo(288_000);
    }

    @Test
    void startDayUsesPreviousNewsReportRanksForRentAndIngredientCostFromNameBasedPayload() {
        User user = user(1);
        Store store = store(user, 15L, 3L, 1L, 9L, 2, 7, 4_500, 100_000, 2_000);
        ReflectionTestUtils.setField(store.getLocation(), "locationName", "hongdae");
        ReflectionTestUtils.setField(store.getMenu(), "menuName", "hotdog");

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.empty());
        when(orderRepository.findDailyStartOrders(15L, 2)).thenReturn(List.of());
        when(valueOperations.get("season:9:weather:day:2")).thenReturn(
                """
                [
                  {"locationId":3,"day":2,"weatherType":"RAIN","populationMultiplier":0.90}
                ]
                """
        );
        when(populationRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                population(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), 500),
                population(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), 400)
        ));
        when(trafficRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), TrafficStatus.NORMAL),
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), TrafficStatus.NORMAL)
        ));
        when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(15L, 2))
                .thenReturn(Optional.of(previousDailyReport(store, 9_000_000, 10)));
        when(newsReportRepository.findFirstBySeason_IdAndDay(9L, 1)).thenReturn(Optional.of(newsReport(
                store.getSeason(),
                1,
                """
                [{"name":"gangnam","storeCount":2},{"name":"hongdae","storeCount":2},{"name":"myeongdong","storeCount":1}]
                """,
                """
                [{"menuName":"taco","storeCount":3},{"menuName":"hotdog","storeCount":2},{"menuName":"tteokbokki","storeCount":1}]
                """,
                """
                [{"menuName":"taco","mentionCount":19},{"menuName":"hotdog","mentionCount":7},{"menuName":"tteokbokki","mentionCount":6},{"menuName":"bread","mentionCount":6},{"menuName":"hamburger","mentionCount":5},{"menuName":"icecream","mentionCount":2},{"menuName":"dakgangjeong","mentionCount":1},{"menuName":"bubbletea","mentionCount":1}]
                """
        )));
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(9L, 1, 2)).thenReturn(List.of());
        NewsRankingResolver.PreviousDayRanking previousDayRanking =
                new NewsRankingResolver(newsReportRepository, new ObjectMapper()).resolve(store, 2);
        assertThat(previousDayRanking.areaEntryRank()).isEqualTo(2);
        assertThat(previousDayRanking.menuEntryRank()).isEqualTo(2);
        assertThat(previousDayRanking.trendKeywordRank()).isEqualTo(2);

        GameDayStartResponse response = gameDayStartService.startDay(mock(Authentication.class));

        assertThat(response.marketSnapshot().locationPopularityRank()).isEqualTo(2);
        assertThat(response.marketSnapshot().menuTrendRank()).isEqualTo(2);
        assertThat(response.captureRate()).isEqualByComparingTo("0.5500");
        assertThat(response.openingSummary().dailyRentApplied()).isEqualTo(120_000);
        assertThat(response.openingSummary().interiorCost()).isZero();
        assertThat(response.openingSummary().appliedUnitCost()).isEqualTo(2_200);
        assertThat(response.openingSummary().trendCostMultiplier()).isEqualByComparingTo("1.10");
        assertThat(response.initialBalance()).isEqualTo(9_000_000);
    }

    @Test
    void startDayDiscardsPreviousStockWithoutReducingBalanceWhenMenuChanged() {
        User user = user(1);
        Store store = store(user, 15L, 3L, 1L, 9L, 2, 7, 5_000, 100_000, 2_000);
        com.ssafy.S14P21A205.game.season.entity.DailyReport previousReport = previousDailyReport(store, 130_000, 10);
        ReflectionTestUtils.setField(previousReport, "menuName", "old-menu");

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.empty());
        when(orderRepository.findDailyStartOrders(15L, 2)).thenReturn(List.of());
        when(valueOperations.get("season:9:weather:day:2")).thenReturn(
                """
                [
                  {"locationId":3,"day":2,"weatherType":"SUNNY","populationMultiplier":1.00}
                ]
                """
        );
        when(populationRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                population(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), 500),
                population(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), 500)
        ));
        when(trafficRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), TrafficStatus.NORMAL),
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), TrafficStatus.NORMAL)
        ));
        when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(15L, 2))
                .thenReturn(Optional.of(previousReport));
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(9L, 1, 2)).thenReturn(List.of());

        GameDayStartResponse response = gameDayStartService.startDay(mock(Authentication.class));

        assertThat(response.initialBalance()).isEqualTo(130_000);
        assertThat(response.initialStock()).isZero();
        assertThat(response.openingSummary().disposalQuantity()).isEqualTo(10);
        assertThat(response.openingSummary().disposalLoss()).isZero();
        assertThat(response.openingSummary().openingAgedStock()).isZero();
        assertThat(response.openingSummary().openingFreshStock()).isZero();
    }

    @Test
    void startDayAllowsEnteringDayWhenBalanceIsBelowDailyRent() {
        User user = user(1);
        Store store = store(user, 15L, 3L, 1L, 9L, 2, 7, 5_000, 100_000, 2_000);
        com.ssafy.S14P21A205.game.season.entity.DailyReport previousReport = previousDailyReport(store, 129_999, 10);
        ReflectionTestUtils.setField(previousReport, "menuName", "old-menu");

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(store));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.empty());
        when(orderRepository.findDailyStartOrders(15L, 2)).thenReturn(List.of());
        when(valueOperations.get("season:9:weather:day:2")).thenReturn(
                """
                [
                  {"locationId":3,"day":2,"weatherType":"SUNNY","populationMultiplier":1.00}
                ]
                """
        );
        when(populationRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                population(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), 500),
                population(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), 500)
        ));
        when(trafficRepository.findByLocationIdOrderByDateAsc(3L)).thenReturn(List.of(
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 9, 10, 0), TrafficStatus.NORMAL),
                traffic(store.getLocation(), LocalDateTime.of(2026, 3, 10, 10, 0), TrafficStatus.NORMAL)
        ));
        when(dailyReportRepository.findFirstByStore_IdAndDayLessThanOrderByDayDesc(15L, 2))
                .thenReturn(Optional.of(previousReport));
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(9L, 1, 2)).thenReturn(List.of());

        GameDayStartResponse response = gameDayStartService.startDay(mock(Authentication.class));

        assertThat(response.initialBalance()).isEqualTo(129_999);
        assertThat(response.openingSummary().dailyRentApplied()).isEqualTo(130_000);
        assertThat(response.openingSummary().fixedCostTotal()).isZero();
    }

    @Test
    void startDayThrowsWhenWeatherScheduleIsMissingInRedis() {
        User user = user(1);
        Store store = store(user, 15L, 3L, 1L, 9L, 1, 7, 4_500, 100_000, 2_000);

        when(userService.getCurrentUser(any())).thenReturn(user);
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(gameDayStoreStateRedisRepository.find(15L, 1)).thenReturn(Optional.empty());
        when(valueOperations.get("season:9:weather:day:1")).thenReturn(null);

        assertThatThrownBy(() -> gameDayStartService.startDay(mock(Authentication.class)))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private User user(int id) {
        User user = new User("test@example.com", "tester");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Store store(
            User user,
            Long storeId,
            Long locationId,
            Long menuId,
            Long seasonId,
            int currentDay,
            int totalDays,
            int price,
            int rent,
            int originPrice
    ) {
        Location location = instantiate(Location.class);
        ReflectionTestUtils.setField(location, "id", locationId);
        ReflectionTestUtils.setField(location, "rent", rent);
        ReflectionTestUtils.setField(location, "locationName", "loc-" + locationId);
        ReflectionTestUtils.setField(location, "interiorCost", 200_000);

        Menu menu = instantiate(Menu.class);
        ReflectionTestUtils.setField(menu, "id", menuId);
        ReflectionTestUtils.setField(menu, "originPrice", originPrice);
        ReflectionTestUtils.setField(menu, "menuName", "cookie");

                Season season = instantiate(Season.class);
        ReflectionTestUtils.setField(season, "id", seasonId);
        ReflectionTestUtils.setField(season, "status", SeasonStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(season, "currentDay", currentDay);
        ReflectionTestUtils.setField(season, "totalDays", totalDays);
        LocalDateTime currentPrepAt = LocalDateTime.ofInstant(fixedClock.instant(), fixedClock.getZone());
        LocalDateTime seasonStartAt = currentPrepAt.minusSeconds(60L + (currentDay - 1L) * 180L);
        ReflectionTestUtils.setField(season, "startTime", seasonStartAt);
        ReflectionTestUtils.setField(season, "endTime", seasonStartAt.plusSeconds(60L + totalDays * 180L + 180L));

        Store store = instantiate(Store.class);
        ReflectionTestUtils.setField(store, "id", storeId);
        ReflectionTestUtils.setField(store, "user", user);
        ReflectionTestUtils.setField(store, "location", location);
        ReflectionTestUtils.setField(store, "menu", menu);
        ReflectionTestUtils.setField(store, "season", season);
        ReflectionTestUtils.setField(store, "price", price);
        ReflectionTestUtils.setField(store, "purchaseSeed", 111L);
        ReflectionTestUtils.setField(store, "purchaseCursor", 0);
        return store;
    }

    private com.ssafy.S14P21A205.game.season.entity.DailyReport previousDailyReport(Store store, int balance, int stock) {
        com.ssafy.S14P21A205.game.season.entity.DailyReport report =
                instantiate(com.ssafy.S14P21A205.game.season.entity.DailyReport.class);
        ReflectionTestUtils.setField(report, "id", 5L);
        ReflectionTestUtils.setField(report, "store", store);
        ReflectionTestUtils.setField(report, "day", 1);
        ReflectionTestUtils.setField(report, "menuName", store.getMenu().getMenuName());
        ReflectionTestUtils.setField(report, "balance", balance);
        ReflectionTestUtils.setField(report, "stockRemaining", stock);
        return report;
    }

    private GameDayLiveState previousLiveState(long balance, int stock) {
        return new GameDayLiveState(
                LocalDateTime.of(2026, 3, 9, 12, 34, 0),
                List.of(1, 1, 1),
                3,
                null,
                12,
                0,
                new BigDecimal("0.10"),
                5_000,
                0,
                List.of(),
                0,
                0L,
                0,
                0,
                0L,
                0L,
                balance,
                stock,
                LocalDateTime.of(2026, 3, 9, 12, 34, 0)
        );
    }

    private Menu menu(Long menuId, String menuName, int originPrice) {
        Menu menu = instantiate(Menu.class);
        ReflectionTestUtils.setField(menu, "id", menuId);
        ReflectionTestUtils.setField(menu, "menuName", menuName);
        ReflectionTestUtils.setField(menu, "originPrice", originPrice);
        return menu;
    }

    private NewsReport newsReport(
            Season season,
            int day,
            String areaEntryRanking,
            String menuEntryRanking,
            String trendKeywordRanking
    ) {
        NewsReport newsReport = instantiate(NewsReport.class);
        ReflectionTestUtils.setField(newsReport, "id", 11L);
        ReflectionTestUtils.setField(newsReport, "season", season);
        ReflectionTestUtils.setField(newsReport, "day", day);
        ReflectionTestUtils.setField(newsReport, "areaRevenueRanking", "[]");
        ReflectionTestUtils.setField(newsReport, "areaPopulationRanking", "[]");
        ReflectionTestUtils.setField(newsReport, "menuEntryRanking", menuEntryRanking);
        ReflectionTestUtils.setField(newsReport, "trendKeywordRanking", trendKeywordRanking);
        ReflectionTestUtils.setField(newsReport, "areaEntryRanking", areaEntryRanking);
        return newsReport;
    }

    private DailyEvent dailyEvent(
            Season season,
            int day,
            String eventType,
            String populationRate,
            int capitalFlat,
            Long targetLocationId,
            Long targetMenuId
    ) {
        RandomEvent randomEvent = instantiate(RandomEvent.class);
        ReflectionTestUtils.setField(randomEvent, "id", 2L);
        ReflectionTestUtils.setField(randomEvent, "eventCategory", EventCategory.CELEBRITY_APPEARANCE);
        ReflectionTestUtils.setField(randomEvent, "eventName", eventType);
        ReflectionTestUtils.setField(randomEvent, "startTime", EventStartTime.IMMEDIATE);
        ReflectionTestUtils.setField(randomEvent, "endTime", EventEndTime.SAME_DAY);
        ReflectionTestUtils.setField(randomEvent, "populationRate", new BigDecimal(populationRate));
        ReflectionTestUtils.setField(randomEvent, "capitalFlat", capitalFlat);

        DailyEvent dailyEvent = instantiate(DailyEvent.class);
        ReflectionTestUtils.setField(dailyEvent, "id", 3L);
        ReflectionTestUtils.setField(dailyEvent, "season", season);
        ReflectionTestUtils.setField(dailyEvent, "event", randomEvent);
        ReflectionTestUtils.setField(dailyEvent, "day", day);
        ReflectionTestUtils.setField(dailyEvent, "applyOffsetSeconds", 40);
        ReflectionTestUtils.setField(dailyEvent, "expireOffsetSeconds", 120);
        ReflectionTestUtils.setField(dailyEvent, "targetLocationId", targetLocationId);
        ReflectionTestUtils.setField(dailyEvent, "targetMenuId", targetMenuId);
        return dailyEvent;
    }

    private Population population(Location location, LocalDateTime dateTime, int floatingPopulation) {
        Population population = instantiate(Population.class);
        ReflectionTestUtils.setField(population, "location", location);
        ReflectionTestUtils.setField(population, "date", dateTime);
        ReflectionTestUtils.setField(population, "floatingPopulation", floatingPopulation);
        return population;
    }

    private Traffic traffic(Location location, LocalDateTime dateTime, TrafficStatus trafficStatus) {
        Traffic traffic = instantiate(Traffic.class);
        ReflectionTestUtils.setField(traffic, "location", location);
        ReflectionTestUtils.setField(traffic, "date", dateTime);
        ReflectionTestUtils.setField(traffic, "trafficStatus", trafficStatus);
        return traffic;
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
