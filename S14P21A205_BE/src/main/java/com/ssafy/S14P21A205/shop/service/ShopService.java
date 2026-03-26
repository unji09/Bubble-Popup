package com.ssafy.S14P21A205.shop.service;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import com.ssafy.S14P21A205.shop.dto.PurchaseItemResponse;
import com.ssafy.S14P21A205.shop.dto.PurchaseItemsResponse;
import com.ssafy.S14P21A205.shop.dto.PurchasedItemListResponse;
import com.ssafy.S14P21A205.shop.dto.PurchasedItemResponse;
import com.ssafy.S14P21A205.shop.dto.ShopItemListResponse;
import com.ssafy.S14P21A205.shop.dto.ShopItemResponse;
import com.ssafy.S14P21A205.shop.entity.Item;
import com.ssafy.S14P21A205.shop.entity.ItemCategory;
import com.ssafy.S14P21A205.shop.entity.ItemUser;
import com.ssafy.S14P21A205.shop.repository.ItemRepository;
import com.ssafy.S14P21A205.shop.repository.ItemUserRepository;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService {

    private static final String PURCHASE_AVAILABLE_MESSAGE = "Item purchase for the current season is no longer available.";

    private final ItemRepository itemRepository;
    private final ItemUserRepository itemUserRepository;
    private final UserRepository userRepository;
    private final SeasonRepository seasonRepository;
    private final Clock clock;

    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();

    public ShopItemListResponse getShopItems() {
        List<Item> items = itemRepository.findAllByOrderByIdAsc();

        List<ShopItemResponse> itemResponses = items.stream()
                .map(ShopItemResponse::from)
                .toList();

        return ShopItemListResponse.of(itemResponses);
    }

    public PurchasedItemListResponse getPurchasedItems(Integer userId) {
        List<ItemUser> purchasedItems = itemUserRepository.findPurchasedItemsByUserId(userId);

        List<PurchasedItemResponse> responses = purchasedItems.stream()
                .map(itemUser -> PurchasedItemResponse.of(
                        itemUser.getItem().getId(),
                        itemUser.getItem().getDiscountRate()
                ))
                .toList();

        return PurchasedItemListResponse.of(responses);
    }

    @Transactional
    public PurchaseItemsResponse purchaseItems(Integer userId, List<Long> itemIds) {
        Season currentSeason = requirePurchasableSeason(userId, itemIds);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));

        if (itemIds == null || itemIds.isEmpty()) {
            PurchaseItemsResponse emptyResponse = PurchaseItemsResponse.of(List.of(), 0, user.getPoint());
            logPurchaseResult("SUCCESS", userId, currentSeason, itemIds, emptyResponse);
            return emptyResponse;
        }

        List<Item> items = getOrderedItems(itemIds);
        validateCategories(userId, items);

        int totalUsedPoints = items.stream()
                .mapToInt(Item::getPoint)
                .sum();

        if (user.getPoint() < totalUsedPoints) {
            throw new BaseException(ErrorCode.SHOP_INSUFFICIENT_POINTS);
        }

        user.usePoints(totalUsedPoints);
        savePurchasedItems(user, items);

        List<PurchaseItemResponse> purchasedItems = items.stream()
                .map(item -> PurchaseItemResponse.of(item, item.getDiscountRate()))
                .toList();

        PurchaseItemsResponse response = PurchaseItemsResponse.of(
                purchasedItems,
                totalUsedPoints,
                user.getPoint()
        );
        logPurchaseResult("SUCCESS", userId, currentSeason, itemIds, response);
        return response;
    }

    @Transactional
    public void resetPurchasedItems(Integer userId) {
        itemUserRepository.resetPurchasedByUserId(userId);
    }

    private Season requirePurchasableSeason(Integer userId, List<Long> itemIds) {
        Season currentSeason = seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)
                .orElseThrow(() -> new BaseException(ErrorCode.SEASON_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        Integer playableFromDay = seasonTimelineService.resolveJoinPlayableFromDay(currentSeason, now);
        if (playableFromDay == null) {
            logPurchaseBlocked(userId, itemIds, currentSeason, now);
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, PURCHASE_AVAILABLE_MESSAGE);
        }
        return currentSeason;
    }

    private void logPurchaseBlocked(Integer userId, List<Long> itemIds, Season season, LocalDateTime now) {
        SeasonTimePoint timePoint = seasonTimelineService.resolve(season, now);
        log.info(
                "\n================ SHOP PURCHASE ================\n"
                        + "now={} userId={} result=BLOCKED\n"
                        + "seasonId={} phase={} day={}\n"
                        + "joinEnabled={} joinPlayableFromDay={}\n"
                        + "itemIds={} usedPoints=- remainingPoints=-\n"
                        + "==================================================",
                now,
                userId,
                season.getId(),
                timePoint.phase(),
                formatDay(timePoint.currentDay()),
                timePoint.joinEnabled(),
                formatValue(timePoint.joinPlayableFromDay()),
                itemIds == null ? List.of() : itemIds
        );
    }

    private void logPurchaseResult(
            String result,
            Integer userId,
            Season season,
            List<Long> itemIds,
            PurchaseItemsResponse response
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        SeasonTimePoint timePoint = seasonTimelineService.resolve(season, now);
        log.info(
                "\n================ SHOP PURCHASE ================\n"
                        + "now={} userId={} result={}\n"
                        + "seasonId={} phase={} day={}\n"
                        + "joinEnabled={} joinPlayableFromDay={}\n"
                        + "itemIds={} usedPoints={} remainingPoints={}\n"
                        + "==================================================",
                now,
                userId,
                result,
                season.getId(),
                timePoint.phase(),
                formatDay(timePoint.currentDay()),
                timePoint.joinEnabled(),
                formatValue(timePoint.joinPlayableFromDay()),
                itemIds == null ? List.of() : itemIds,
                formatValue(response.usedPoints()),
                formatValue(response.remainingPoints())
        );
    }

    private List<Item> getOrderedItems(List<Long> itemIds) {
        Map<Long, Item> itemMap = itemRepository.findAllById(itemIds).stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);

        if (itemMap.size() != itemIds.size()) {
            throw new BaseException(ErrorCode.SHOP_ITEM_NOT_FOUND);
        }

        return itemIds.stream()
                .map(itemMap::get)
                .toList();
    }

    private void savePurchasedItems(User user, List<Item> items) {
        Map<Long, ItemUser> existingItemsByItemId = new HashMap<>();
        itemUserRepository.findAllByUser_IdAndItem_IdIn(
                        user.getId(),
                        items.stream().map(Item::getId).toList()
                )
                .forEach(itemUser -> existingItemsByItemId.put(itemUser.getItem().getId(), itemUser));

        List<ItemUser> itemUsersToSave = items.stream()
                .map(item -> {
                    ItemUser existingItemUser = existingItemsByItemId.get(item.getId());
                    if (existingItemUser != null) {
                        existingItemUser.markPurchased();
                        return existingItemUser;
                    }
                    return ItemUser.purchase(item, user);
                })
                .toList();

        itemUserRepository.saveAll(itemUsersToSave);
    }

    private void validateCategories(Integer userId, List<Item> items) {
        Set<ItemCategory> requestCategories = new LinkedHashSet<>();

        for (Item item : items) {
            if (!requestCategories.add(item.getCategory())) {
                throw new BaseException(
                        ErrorCode.INVALID_INPUT_VALUE,
                        "Only one item can be purchased per category in a single request."
                );
            }

            if (itemUserRepository.existsPurchasedCategoryItem(userId, item.getCategory())) {
                throw new BaseException(
                        ErrorCode.SHOP_ITEM_ALREADY_PURCHASED,
                        "An item in this category has already been purchased."
                );
            }
        }
    }

    private String formatDay(Integer day) {
        return day == null ? "-" : "DAY " + day;
    }

    private String formatValue(Object value) {
        return value == null ? "-" : value.toString();
    }
}
