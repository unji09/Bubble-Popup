package com.ssafy.S14P21A205.game.season.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.game.day.generator.PurchaseListGenerator;
import com.ssafy.S14P21A205.game.season.dto.SeasonJoinRequest;
import com.ssafy.S14P21A205.game.season.dto.SeasonJoinResponse;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRankingRecordRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.LocationRepository;
import com.ssafy.S14P21A205.store.repository.MenuRepository;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.service.UserService;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeasonJoinServiceTests {

    @Mock
    private UserService userService;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private SeasonRankingRecordRepository seasonRankingRecordRepository;

    @Mock
    private DailyReportRepository dailyReportRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private PurchaseListGenerator purchaseListGenerator;

    private SeasonJoinService seasonJoinService;

    @BeforeEach
    void setUp() {
        seasonJoinService = new SeasonJoinService(
                userService,
                seasonRepository,
                seasonRankingRecordRepository,
                dailyReportRepository,
                storeRepository,
                locationRepository,
                menuRepository,
                purchaseListGenerator,
                Clock.fixed(Instant.parse("2026-03-18T01:00:30Z"), ZoneId.of("Asia/Seoul"))
        );
    }

    @Test
    void joinCurrentSeasonReturnsJoinBalanceForNewSeasonStore() {
        User user = user(7);
        Season season = season(11L);
        Location location = location(3L, 100_000, 200_000);
        Menu menu = menu(5L, 2_000);
        Store savedStore = store(21L, user, season, location, menu, 2_000);

        when(userService.getCurrentUser(any(Authentication.class))).thenReturn(user);
        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)).thenReturn(Optional.of(season));
        when(storeRepository.findFirstByUser_IdAndSeason_IdOrderByIdDesc(7, 11L)).thenReturn(Optional.empty());
        when(locationRepository.findById(3L)).thenReturn(Optional.of(location));
        when(storeRepository.findFirstByUser_IdOrderBySeason_IdDescIdDesc(7)).thenReturn(Optional.empty());
        when(menuRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(menu));
        when(storeRepository.save(any(Store.class))).thenReturn(savedStore);
        when(purchaseListGenerator.issueSeed()).thenReturn(9_876L);

        SeasonJoinResponse response = seasonJoinService.joinCurrentSeason(
                org.mockito.Mockito.mock(Authentication.class),
                new SeasonJoinRequest(3, "test store")
        );

        assertThat(response.storeId()).isEqualTo(21L);
        assertThat(response.balance()).isEqualTo(4_800_000);
        assertThat(response.playableFromDay()).isEqualTo(1);
        assertThat(savedStore.getPurchaseSeed()).isEqualTo(9_876L);
        assertThat(savedStore.getPurchaseCursor()).isZero();
    }

    @Test
    void joinCurrentSeasonAllowsRejoinAfterBankruptcyInSameSeason() {
        User user = user(7);
        Season season = season(11L);
        Location location = location(3L, 100_000, 200_000);
        Menu defaultMenu = menu(1L, 1_800);
        Menu previousMenu = menu(5L, 2_000);
        Store previousStore = store(20L, user, season, location, previousMenu, 4_500);

        when(userService.getCurrentUser(any(Authentication.class))).thenReturn(user);
        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)).thenReturn(Optional.of(season));
        when(storeRepository.findFirstByUser_IdAndSeason_IdOrderByIdDesc(7, 11L)).thenReturn(Optional.of(previousStore));
        when(seasonRankingRecordRepository.existsByStore_Id(20L)).thenReturn(false);
        when(dailyReportRepository.findFirstByStore_IdOrderByDayDesc(20L))
                .thenReturn(Optional.of(bankruptReport(previousStore)));
        when(locationRepository.findById(3L)).thenReturn(Optional.of(location));
        when(menuRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultMenu));
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            ReflectionTestUtils.setField(store, "id", 21L);
            return store;
        });
        when(purchaseListGenerator.issueSeed()).thenReturn(4_321L);

        SeasonJoinResponse response = seasonJoinService.joinCurrentSeason(
                org.mockito.Mockito.mock(Authentication.class),
                new SeasonJoinRequest(3, "rejoin store")
        );

        ArgumentCaptor<Store> storeCaptor = ArgumentCaptor.forClass(Store.class);
        verify(storeRepository).save(storeCaptor.capture());
        Store savedStore = storeCaptor.getValue();

        assertThat(response.storeId()).isEqualTo(21L);
        assertThat(response.balance()).isEqualTo(4_800_000);
        assertThat(savedStore.getMenu()).isSameAs(defaultMenu);
        assertThat(savedStore.getPrice()).isEqualTo(1_800);
        assertThat(savedStore.getPurchaseSeed()).isEqualTo(4_321L);
        assertThat(savedStore.getPurchaseCursor()).isZero();
    }

    @Test
    void joinCurrentSeasonRejectsWhenActiveStoreStillExists() {
        User user = user(7);
        Season season = season(11L);
        Location location = location(3L, 100_000, 200_000);
        Menu menu = menu(5L, 2_000);
        Store activeStore = store(20L, user, season, location, menu, 2_000);

        when(userService.getCurrentUser(any(Authentication.class))).thenReturn(user);
        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)).thenReturn(Optional.of(season));
        when(storeRepository.findFirstByUser_IdAndSeason_IdOrderByIdDesc(7, 11L)).thenReturn(Optional.of(activeStore));
        when(seasonRankingRecordRepository.existsByStore_Id(20L)).thenReturn(false);
        when(dailyReportRepository.findFirstByStore_IdOrderByDayDesc(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seasonJoinService.joinCurrentSeason(
                org.mockito.Mockito.mock(Authentication.class),
                new SeasonJoinRequest(3, "blocked join")
        ))
                .isInstanceOf(com.ssafy.S14P21A205.exception.BaseException.class)
                .hasMessageContaining("already joined");
    }

    @Test
    void joinCurrentSeasonRejectsLateJoinFromDaySix() {
        User user = user(7);
        Season season = season(11L);
        LocalDateTime daySixBusiness = season.getStartTime().plusSeconds(60 + 5L * 180L + 40L);

        seasonJoinService = new SeasonJoinService(
                userService,
                seasonRepository,
                seasonRankingRecordRepository,
                dailyReportRepository,
                storeRepository,
                locationRepository,
                menuRepository,
                purchaseListGenerator,
                Clock.fixed(daySixBusiness.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"))
        );

        when(userService.getCurrentUser(any(Authentication.class))).thenReturn(user);
        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)).thenReturn(Optional.of(season));
        when(storeRepository.findFirstByUser_IdAndSeason_IdOrderByIdDesc(7, 11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seasonJoinService.joinCurrentSeason(
                org.mockito.Mockito.mock(Authentication.class),
                new SeasonJoinRequest(3, "late join")
        ))
                .isInstanceOf(com.ssafy.S14P21A205.exception.BaseException.class)
                .hasMessageContaining("no longer available");
    }

    private User user(Integer id) {
        User user = new User("join-%d@test.com".formatted(id), "join-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Season season(Long id) {
        Season season = instantiate(Season.class);
        ReflectionTestUtils.setField(season, "id", id);
        ReflectionTestUtils.setField(season, "status", SeasonStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(season, "totalDays", 7);
        ReflectionTestUtils.setField(season, "startTime", LocalDateTime.of(2026, 3, 18, 10, 0, 0));
        return season;
    }

    private Location location(Long id, Integer rent, Integer interiorCost) {
        Location location = instantiate(Location.class);
        ReflectionTestUtils.setField(location, "id", id);
        ReflectionTestUtils.setField(location, "rent", rent);
        ReflectionTestUtils.setField(location, "interiorCost", interiorCost);
        return location;
    }

    private Menu menu(Long id, Integer originPrice) {
        Menu menu = instantiate(Menu.class);
        ReflectionTestUtils.setField(menu, "id", id);
        ReflectionTestUtils.setField(menu, "originPrice", originPrice);
        return menu;
    }

    private Store store(Long id, User user, Season season, Location location, Menu menu, Integer price) {
        Store store = instantiate(Store.class);
        ReflectionTestUtils.setField(store, "id", id);
        ReflectionTestUtils.setField(store, "user", user);
        ReflectionTestUtils.setField(store, "season", season);
        ReflectionTestUtils.setField(store, "location", location);
        ReflectionTestUtils.setField(store, "menu", menu);
        ReflectionTestUtils.setField(store, "storeName", "fixture-store");
        ReflectionTestUtils.setField(store, "price", price);
        ReflectionTestUtils.setField(store, "playableFromDay", 1);
        return store;
    }

    private com.ssafy.S14P21A205.game.season.entity.DailyReport bankruptReport(Store store) {
        return com.ssafy.S14P21A205.game.season.entity.DailyReport.create(
                store,
                3,
                "fixture-location",
                "fixture-menu",
                1_000,
                2_000,
                -1_000,
                10,
                5,
                0,
                3,
                true,
                5_000,
                new BigDecimal("0.10")
        );
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

