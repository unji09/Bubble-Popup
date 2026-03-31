package com.ssafy.S14P21A205.game.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.game.day.generator.PurchaseListGenerator;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.LocationRepository;
import com.ssafy.S14P21A205.store.repository.MenuRepository;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class BotSeasonFillServiceTests {

    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final StoreRepository storeRepository = org.mockito.Mockito.mock(StoreRepository.class);
    private final LocationRepository locationRepository = org.mockito.Mockito.mock(LocationRepository.class);
    private final MenuRepository menuRepository = org.mockito.Mockito.mock(MenuRepository.class);
    private final PurchaseListGenerator purchaseListGenerator = org.mockito.Mockito.mock(PurchaseListGenerator.class);

    private final BotSeasonFillService botSeasonFillService = new BotSeasonFillService(
            userRepository,
            storeRepository,
            locationRepository,
            menuRepository,
            purchaseListGenerator
    );

    @Test
    void fillBotsToMinimumCreatesMissingBotsForSeason() {
        Season season = season(11L);
        List<Store> savedStores = new ArrayList<>();

        when(storeRepository.countDistinctHumanUsersBySeasonId(11L)).thenReturn(1L);
        when(storeRepository.findAllBySeason_IdOrderByIdAsc(11L)).thenReturn(List.of());
        when(locationRepository.findAllByOrderByIdAsc()).thenReturn(List.of(location(1L, "loc-1"), location(2L, "loc-2")));
        when(menuRepository.findAllByOrderByIdAsc()).thenReturn(List.of(menu(1L, "menu-1", 1000), menu(2L, "menu-2", 1200)));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 100 + savedStores.size());
            return user;
        });
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            savedStores.add(store);
            return store;
        });
        when(purchaseListGenerator.issueSeed()).thenReturn(101L, 202L);

        int created = botSeasonFillService.fillBotsToMinimum(season);

        assertThat(created).isEqualTo(2);
        assertThat(savedStores).hasSize(2);
        assertThat(savedStores)
                .extracting(store -> store.getUser().isBot())
                .containsOnly(true);
        assertThat(savedStores)
                .extracting(store -> store.getUser().getBotDifficulty().name())
                .containsExactly("LOW", "HIGH");
        assertThat(savedStores)
                .extracting(Store::getPurchaseSeed)
                .containsExactly(101L, 202L);
    }

    @Test
    void fillBotsToMinimumSkipsWhenSeasonAlreadyHasEnoughCompetitors() {
        Season season = season(11L);

        when(storeRepository.countDistinctHumanUsersBySeasonId(11L)).thenReturn(3L);
        when(storeRepository.findAllBySeason_IdOrderByIdAsc(11L)).thenReturn(List.of());

        int created = botSeasonFillService.fillBotsToMinimum(season);

        assertThat(created).isZero();
        verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
        verify(storeRepository, org.mockito.Mockito.never()).save(any(Store.class));
    }

    private Season season(Long seasonId) {
        Season season = BeanUtils.instantiateClass(Season.class);
        ReflectionTestUtils.setField(season, "id", seasonId);
        ReflectionTestUtils.setField(season, "status", SeasonStatus.SCHEDULED);
        ReflectionTestUtils.setField(season, "totalDays", 7);
        ReflectionTestUtils.setField(season, "startTime", LocalDateTime.of(2026, 3, 18, 10, 0));
        return season;
    }

    private Location location(Long id, String name) {
        Location location = BeanUtils.instantiateClass(Location.class);
        ReflectionTestUtils.setField(location, "id", id);
        ReflectionTestUtils.setField(location, "locationName", name);
        return location;
    }

    private Menu menu(Long id, String name, Integer price) {
        Menu menu = BeanUtils.instantiateClass(Menu.class);
        ReflectionTestUtils.setField(menu, "id", id);
        ReflectionTestUtils.setField(menu, "menuName", name);
        ReflectionTestUtils.setField(menu, "originPrice", price);
        return menu;
    }
}
