package com.ssafy.S14P21A205.store.service;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.shop.entity.ItemCategory;
import com.ssafy.S14P21A205.shop.repository.ItemUserRepository;
import com.ssafy.S14P21A205.store.dto.LocationListResponse;
import com.ssafy.S14P21A205.store.dto.LocationResponse;
import com.ssafy.S14P21A205.store.dto.MenuListResponse;
import com.ssafy.S14P21A205.store.dto.StoreResponse;
import com.ssafy.S14P21A205.store.dto.UpdateStoreLocationRequest;
import com.ssafy.S14P21A205.store.dto.UpdateStoreLocationResponse;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.model.SeasonPhase;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.LocationRepository;
import com.ssafy.S14P21A205.store.repository.MenuRepository;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreServiceImpl implements StoreService {

    private static final StoreLocationTransitionSupport STORE_LOCATION_TRANSITION_SUPPORT = new StoreLocationTransitionSupport();

    private final StoreRepository storeRepository;
    private final LocationRepository locationRepository;
    private final MenuRepository menuRepository;
    private final ItemUserRepository itemUserRepository;
    private final GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;
    private final Clock clock;

    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();

    @Override
    public StoreResponse getStore(Integer userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Store store = getReadableStoreByUserId(userId, now);

        return new StoreResponse(
                store.getLocation().getLocationName(),
                store.getStoreName(),
                store.getMenu().getMenuName(),
                resolveCurrentDay(store, now),
                resolvePlayableDay(store)
        );
    }

    @Override
    @Transactional
    public UpdateStoreLocationResponse updateStoreLocation(Integer userId, UpdateStoreLocationRequest request) {
        Store store = getStoreByUserId(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        STORE_LOCATION_TRANSITION_SUPPORT.applyPendingLocationIfDue(store, now);
        Long storeId = store.getId();
        int currentDay = resolveCurrentDay(store, now);

        if (currentDay >= store.getSeason().getTotalDays()) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "Location changes are unavailable on the last day.");
        }
        if (STORE_LOCATION_TRANSITION_SUPPORT.hasFuturePendingLocationChange(store, currentDay)) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "A location change is already reserved for the next day.");
        }

        Location location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));

        if (store.getLocation().getId().equals(location.getId())) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "The store is already using this location.");
        }

        Integer updatedBalance = deductBalance(storeId, currentDay, location.getInteriorCost());
        recordLocationChangeCost(storeId, currentDay, location.getInteriorCost());
        store.reserveLocationChange(location, currentDay + 1);

        return new UpdateStoreLocationResponse(
                location.getId(),
                updatedBalance
        );
    }

    @Override
    public LocationListResponse getLocations(Integer userId) {
        float discount = getDisplayedRentDiscountRate(userId).floatValue();

        return new LocationListResponse(
                locationRepository.findAllByOrderByIdAsc().stream()
                        .map(location -> new LocationResponse(
                                location.getId(),
                                location.getLocationName(),
                                location.getRent(),
                                location.getInteriorCost(),
                                discount
                        ))
                        .toList()
        );
    }

    @Override
    public MenuListResponse getMenus(Integer userId) {
        Store store = getStoreByUserId(userId);
        float discount = getDisplayedIngredientDiscountRate(userId).floatValue();

        List<MenuListResponse.MenuInfo> menuInfos = menuRepository.findAllByOrderByIdAsc().stream()
                .map(menu -> MenuListResponse.MenuInfo.builder()
                        .menuId(Math.toIntExact(menu.getId()))
                        .menuName(menu.getMenuName())
                        .ingredientPrice(menu.getOriginPrice())
                        .discount(discount)
                        .build())
                .toList();

        return MenuListResponse.builder()
                .menus(menuInfos)
                .build();
    }

    private Integer deductBalance(Long storeId, int day, Integer amount) {
        long currentBalance = gameDayStoreStateRedisRepository.findBalance(storeId, day)
                .orElseThrow(() -> new BaseException(ErrorCode.GAME_STATE_NOT_FOUND));

        if (currentBalance < amount) {
            throw new BaseException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        long updatedBalance = currentBalance - amount;
        gameDayStoreStateRedisRepository.saveBalance(storeId, day, updatedBalance);
        return Math.toIntExact(updatedBalance);
    }

    private void recordLocationChangeCost(Long storeId, int day, Integer amount) {
        long normalizedAmount = amount == null ? 0L : amount.longValue();
        if (normalizedAmount <= 0L) {
            return;
        }

        GameDayLiveState state = gameDayStoreStateRedisRepository.find(storeId, day)
                .orElseThrow(() -> new BaseException(ErrorCode.GAME_STATE_NOT_FOUND));
        long currentCost = state.locationChangeCost() == null ? 0L : state.locationChangeCost();
        gameDayStoreStateRedisRepository.updateField(
                storeId,
                day,
                "location_change_cost",
                String.valueOf(currentCost + normalizedAmount)
        );
    }

    private BigDecimal getDisplayedRentDiscountRate(Integer userId) {
        return itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(userId, ItemCategory.RENT)
                .orElse(BigDecimal.ONE);
    }

    private BigDecimal getDisplayedIngredientDiscountRate(Integer userId) {
        return itemUserRepository.findPurchasedDiscountRateByUserIdAndCategory(userId, ItemCategory.INGREDIENT)
                .orElse(BigDecimal.ONE);
    }

    private int resolveCurrentDay(Store store, LocalDateTime now) {
        SeasonTimePoint seasonTimePoint = seasonTimelineService.resolve(store.getSeason(), now);
        Integer currentDay = seasonTimePoint.currentDay();
        if (currentDay != null && currentDay >= 1 && currentDay <= store.getSeason().getTotalDays()) {
            return currentDay;
        }
        Integer fallbackDay = store.getSeason().getCurrentDay();
        return fallbackDay == null || fallbackDay == 0 ? 1 : fallbackDay;
    }

    private int resolvePlayableDay(Store store) {
        Integer playableFromDay = store.getPlayableFromDay();
        return playableFromDay == null || playableFromDay <= 0 ? 1 : playableFromDay;
    }

    private Store getReadableStoreByUserId(Integer userId, LocalDateTime now) {
        return storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(userId, SeasonStatus.IN_PROGRESS)
                .or(() -> readableBankruptStore(userId, now))
                .map(store -> {
                    STORE_LOCATION_TRANSITION_SUPPORT.applyPendingLocationIfDue(store, now);
                    return store;
                })
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));
    }

    private java.util.Optional<Store> readableBankruptStore(Integer userId, LocalDateTime now) {
        return storeRepository.findFirstIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(userId, SeasonStatus.IN_PROGRESS)
                .filter(store -> {
                    SeasonPhase phase = seasonTimelineService.resolve(store.getSeason(), now).phase();
                    return phase == SeasonPhase.DAY_REPORT
                            || phase == SeasonPhase.SEASON_SUMMARY
                            || phase == SeasonPhase.NEXT_SEASON_WAITING;
                });
    }

    private Store getStoreByUserId(Integer userId) {
        Store store = storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(userId, SeasonStatus.IN_PROGRESS)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));
        STORE_LOCATION_TRANSITION_SUPPORT.applyPendingLocationIfDue(store, LocalDateTime.now(clock));
        return store;
    }
}



