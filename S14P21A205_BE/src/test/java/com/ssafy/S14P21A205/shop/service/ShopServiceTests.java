package com.ssafy.S14P21A205.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.shop.dto.PurchaseItemsResponse;
import com.ssafy.S14P21A205.shop.entity.Item;
import com.ssafy.S14P21A205.shop.entity.ItemCategory;
import com.ssafy.S14P21A205.shop.repository.ItemRepository;
import com.ssafy.S14P21A205.shop.repository.ItemUserRepository;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.repository.UserRepository;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ShopServiceTests {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemUserRepository itemUserRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @Test
    void purchaseItemsAllowsPurchaseThroughDayFive() {
        ShopService shopService = new ShopService(
                itemRepository,
                itemUserRepository,
                userRepository,
                seasonRepository,
                fixedClock(LocalDateTime.of(2026, 3, 18, 10, 15, 50))
        );
        User user = user(7, 5_000);
        Item item = item(1L, ItemCategory.INGREDIENT, 1_000, new BigDecimal("0.90"));
        Season season = season(11L);

        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)).thenReturn(Optional.of(season));
        when(userRepository.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        when(itemRepository.findAllById(List.of(1L))).thenReturn(List.of(item));
        when(itemUserRepository.existsPurchasedCategoryItem(7, ItemCategory.INGREDIENT)).thenReturn(false);
        when(itemUserRepository.findAllByUser_IdAndItem_IdIn(7, List.of(1L))).thenReturn(List.of());
        when(itemUserRepository.saveAll(any())).thenReturn(List.of());

        PurchaseItemsResponse response = shopService.purchaseItems(7, List.of(1L));

        assertThat(response.usedPoints()).isEqualTo(1_000);
        assertThat(response.remainingPoints()).isEqualTo(4_000);
        assertThat(response.purchasedItems()).hasSize(1);
    }

    @Test
    void purchaseItemsRejectsLateJoinFromDaySix() {
        ShopService shopService = new ShopService(
                itemRepository,
                itemUserRepository,
                userRepository,
                seasonRepository,
                fixedClock(LocalDateTime.of(2026, 3, 18, 10, 17, 50))
        );
        Season season = season(11L);

        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)).thenReturn(Optional.of(season));

        assertThatThrownBy(() -> shopService.purchaseItems(7, List.of(1L)))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("no longer available");
        verifyNoInteractions(userRepository);
    }

    private Clock fixedClock(LocalDateTime dateTime) {
        return Clock.fixed(dateTime.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    }

    private User user(Integer id, Integer point) {
        User user = new User("shop-%d@test.com".formatted(id), "shop-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "point", point);
        return user;
    }

    private Season season(Long id) {
        Season season = instantiate(Season.class);
        ReflectionTestUtils.setField(season, "id", id);
        ReflectionTestUtils.setField(season, "status", SeasonStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(season, "totalDays", 7);
        ReflectionTestUtils.setField(season, "startTime", LocalDateTime.of(2026, 3, 18, 10, 0, 0));
        ReflectionTestUtils.setField(season, "endTime", LocalDateTime.of(2026, 3, 18, 10, 30, 0));
        return season;
    }

    private Item item(Long id, ItemCategory category, Integer point, BigDecimal discountRate) {
        Item item = instantiate(Item.class);
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "itemName", "fixture-item");
        ReflectionTestUtils.setField(item, "category", category);
        ReflectionTestUtils.setField(item, "point", point);
        ReflectionTestUtils.setField(item, "discountRate", discountRate);
        return item;
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
