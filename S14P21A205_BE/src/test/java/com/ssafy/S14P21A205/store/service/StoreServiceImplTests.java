package com.ssafy.S14P21A205.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.policy.StoreRankingPolicy;
import com.ssafy.S14P21A205.game.day.resolver.EventEffectResolver;
import com.ssafy.S14P21A205.game.day.resolver.NewsRankingResolver;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.shop.entity.ItemCategory;
import com.ssafy.S14P21A205.shop.repository.ItemUserRepository;
import com.ssafy.S14P21A205.store.dto.LocationListResponse;
import com.ssafy.S14P21A205.store.dto.MenuListResponse;
import com.ssafy.S14P21A205.store.dto.StoreResponse;
import com.ssafy.S14P21A205.store.dto.UpdateStoreLocationRequest;
import com.ssafy.S14P21A205.store.dto.UpdateStoreLocationResponse;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.store.repository.LocationRepository;
import com.ssafy.S14P21A205.store.repository.MenuRepository;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreServiceImplTests {

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private ItemUserRepository itemUserRepository;

    @Mock
    private GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;

    @Mock
    private NewsRankingResolver newsRankingResolver;

    @Mock
    private EventEffectResolver eventEffectResolver;

    private StoreServiceImpl storeService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-09T05:33:00Z"), ZoneId.of("Asia/Seoul"));
        storeService = new StoreServiceImpl(
                storeRepository,
                locationRepository,
                menuRepository,
                itemUserRepository,
                gameDayStoreStateRedisRepository,
                new StoreRankingPolicy(),
                newsRankingResolver,
                eventEffectResolver,
                fixedClock
        );
    }

    @Test
    void updateStoreLocationReservesNextDayMoveAndChargesDepositImmediately() {
        Store store = store(15L, 3L, 2, 7);
        Location targetLocation = location(4L, "Gangnam", 200_000, 120_000);
        GameDayLiveState state = new GameDayLiveState(0L, 0L, 50, LocalDateTime.of(2026, 3, 9, 14, 33, 0));

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(locationRepository.findById(4L)).thenReturn(Optional.of(targetLocation));
        when(gameDayStoreStateRedisRepository.findBalance(15L, 2)).thenReturn(Optional.of(1_000_000L));
        when(gameDayStoreStateRedisRepository.find(15L, 2)).thenReturn(Optional.of(state));

        UpdateStoreLocationResponse response = storeService.updateStoreLocation(1, new UpdateStoreLocationRequest(4L));

        assertThat(response.locationId()).isEqualTo(4L);
        assertThat(response.balance()).isEqualTo(800_000);
        assertThat(store.getLocation().getId()).isEqualTo(3L);
        assertThat(store.getPendingLocation().getId()).isEqualTo(4L);
        assertThat(store.getPendingLocationApplyDay()).isEqualTo(3);
        verify(gameDayStoreStateRedisRepository).saveBalance(15L, 2, 800_000L);
        verify(gameDayStoreStateRedisRepository).updateField(15L, 2, "location_change_cost", "200000");
    }

    @Test
    void updateStoreLocationRejectsWhenNextDayMoveIsAlreadyReserved() {
        Store store = store(15L, 3L, 2, 7);
        ReflectionTestUtils.setField(store, "pendingLocation", location(4L, "Gangnam", 200_000, 120_000));
        ReflectionTestUtils.setField(store, "pendingLocationApplyDay", 3);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));

        assertThatThrownBy(() -> storeService.updateStoreLocation(1, new UpdateStoreLocationRequest(5L)))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void getStoreReturnsPlayableDay() {
        Store store = store(15L, 3L, 3, 7);
        ReflectionTestUtils.setField(store, "storeName", "PulsePop Kitchen");
        ReflectionTestUtils.setField(store, "menu", menu(5L, "치킨 타코"));
        ReflectionTestUtils.setField(store, "playableFromDay", 3);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));

        StoreResponse response = storeService.getStore(1);

        assertThat(response.location()).isEqualTo("Seongsu");
        assertThat(response.popupName()).isEqualTo("PulsePop Kitchen");
        assertThat(response.menu()).isEqualTo("치킨 타코");
        assertThat(response.day()).isEqualTo(3);
        assertThat(response.playableDay()).isEqualTo(3);
    }

    @Test
    void getStoreReturnsBankruptStoreDuringReportPhase() {
        Store store = store(15L, 3L, 6, 7);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(storeRepository.findFirstIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));

        StoreResponse response = storeService.getStore(1);

        assertThat(response.location()).isEqualTo("Seongsu");
        assertThat(response.popupName()).isEqualTo("Default Store");
        assertThat(response.day()).isEqualTo(6);
    }

    @Test
    void getStoreRejectsBankruptStoreDuringBusinessPhase() {
        Store store = store(15L, 3L, 6, 7, 100L);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(storeRepository.findFirstIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));

        assertThatThrownBy(() -> storeService.getStore(1))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.STORE_NOT_FOUND));
    }

    @Test
    void getLocationsReturnsListWithoutCurrentSeasonStore() {
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.RENT))
                .thenReturn(Optional.of(new BigDecimal("0.80")));
        when(locationRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                location(3L, "Seongsu", 100_000, 150_000),
                location(4L, "Gangnam", 200_000, 120_000)
        ));

        LocationListResponse response = storeService.getLocations(1);

        assertThat(response.locations()).hasSize(2);
        assertThat(response.locations().get(0).locationId()).isEqualTo(3L);
        assertThat(response.locations().get(0).locationName()).isEqualTo("Seongsu");
        assertThat(response.locations().get(0).discount()).isEqualTo(0.8f);
        verify(storeRepository, never()).findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS);
    }

    @Test
    void getMenusReturnsIngredientPriceAdjustedByMenuRankAndEvent() {
        Store store = store(15L, 3L, 3, 7, 60L);
        Menu cookie = menu(5L, "Cookie", 1_200);
        Menu taco = menu(8L, "Taco", 2_000);

        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));
        when(itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(1, ItemCategory.INGREDIENT))
                .thenReturn(Optional.of(new BigDecimal("0.80")));
        when(menuRepository.findAllByOrderByIdAsc()).thenReturn(List.of(cookie, taco));
        when(storeRepository.findBySeason_IdOrderByIdAsc(9L)).thenReturn(List.of(
                storeWithMenu(31L, 3L, cookie, store.getSeason()),
                storeWithMenu(32L, 3L, cookie, store.getSeason()),
                storeWithMenu(33L, 3L, taco, store.getSeason())
        ));
        when(newsRankingResolver.resolveMenuEntryRank(9L, 3, cookie)).thenReturn(null);
        when(newsRankingResolver.resolveMenuEntryRank(9L, 3, taco)).thenReturn(9);
        when(eventEffectResolver.resolve(store.getSeason(), 3, LocalDateTime.of(2026, 3, 9, 14, 33), 3L, 5L))
                .thenReturn(eventEffect(new BigDecimal("1.05")));
        when(eventEffectResolver.resolve(store.getSeason(), 3, LocalDateTime.of(2026, 3, 9, 14, 33), 3L, 8L))
                .thenReturn(eventEffect(new BigDecimal("0.95")));

        MenuListResponse response = storeService.getMenus(1);

        assertThat(response.getMenus()).hasSize(2);
        assertThat(response.getMenus().get(0).getIngredientPrice()).isEqualTo(1_512);
        assertThat(response.getMenus().get(0).getDiscount()).isEqualTo(0.8f);
        assertThat(response.getMenus().get(1).getIngredientPrice()).isEqualTo(1_710);
        assertThat(response.getMenus().get(1).getDiscount()).isEqualTo(0.8f);
    }

    @Test
    void getMenusThrowsWhenNoActiveStoreExists() {
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.getMenus(1))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.STORE_NOT_FOUND));
    }

    private Store store(Long storeId, Long locationId, int currentDay, int totalDays) {
        return store(storeId, locationId, currentDay, totalDays, 170L);
    }

    private Store store(Long storeId, Long locationId, int currentDay, int totalDays, long currentDayElapsedSeconds) {
        Location location = location(locationId, "Seongsu", 100_000, 150_000);
        Menu menu = menu(5L, "Cookie");

        Season season = instantiate(Season.class);
        ReflectionTestUtils.setField(season, "id", 9L);
        ReflectionTestUtils.setField(season, "status", SeasonStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(season, "currentDay", currentDay);
        ReflectionTestUtils.setField(season, "totalDays", totalDays);
        LocalDateTime now = LocalDateTime.of(2026, 3, 9, 14, 33, 0);
        LocalDateTime seasonStartAt = now.minusSeconds(60L + (currentDay - 1L) * 180L + currentDayElapsedSeconds);
        ReflectionTestUtils.setField(season, "startTime", seasonStartAt);
        ReflectionTestUtils.setField(season, "endTime", seasonStartAt.plusSeconds(60L + totalDays * 180L + 180L));

        Store store = instantiate(Store.class);
        ReflectionTestUtils.setField(store, "id", storeId);
        ReflectionTestUtils.setField(store, "location", location);
        ReflectionTestUtils.setField(store, "menu", menu);
        ReflectionTestUtils.setField(store, "season", season);
        ReflectionTestUtils.setField(store, "storeName", "Default Store");
        ReflectionTestUtils.setField(store, "playableFromDay", 1);
        return store;
    }

    private Menu menu(Long id, String name) {
        return menu(id, name, 1_200);
    }

    private Menu menu(Long id, String name, int originPrice) {
        Menu menu = instantiate(Menu.class);
        ReflectionTestUtils.setField(menu, "id", id);
        ReflectionTestUtils.setField(menu, "menuName", name);
        ReflectionTestUtils.setField(menu, "originPrice", originPrice);
        return menu;
    }

    private Store storeWithMenu(Long storeId, Long locationId, Menu menu, Season season) {
        Store store = instantiate(Store.class);
        ReflectionTestUtils.setField(store, "id", storeId);
        ReflectionTestUtils.setField(store, "location", location(locationId, "Seongsu", 100_000, 150_000));
        ReflectionTestUtils.setField(store, "menu", menu);
        ReflectionTestUtils.setField(store, "season", season);
        ReflectionTestUtils.setField(store, "storeName", "Trend Store");
        ReflectionTestUtils.setField(store, "playableFromDay", 1);
        return store;
    }

    private EventEffectResolver.EventEffect eventEffect(BigDecimal ingredientCostMultiplier) {
        return new EventEffectResolver.EventEffect(
                0L,
                0,
                BigDecimal.ONE,
                ingredientCostMultiplier,
                List.of(),
                List.of()
        );
    }

    private Location location(Long id, String name, int interiorCost, int rent) {
        Location location = instantiate(Location.class);
        ReflectionTestUtils.setField(location, "id", id);
        ReflectionTestUtils.setField(location, "locationName", name);
        ReflectionTestUtils.setField(location, "interiorCost", interiorCost);
        ReflectionTestUtils.setField(location, "rent", rent);
        return location;
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
