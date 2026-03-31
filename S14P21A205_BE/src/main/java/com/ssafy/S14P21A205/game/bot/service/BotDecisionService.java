package com.ssafy.S14P21A205.game.bot.service;

import com.ssafy.S14P21A205.action.entity.PromotionType;
import com.ssafy.S14P21A205.game.day.dto.GameStateResponse;
import com.ssafy.S14P21A205.order.dto.CurrentOrderResponse;
import com.ssafy.S14P21A205.order.service.OrderService;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.LocationRepository;
import com.ssafy.S14P21A205.store.repository.MenuRepository;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.user.entity.BotDifficulty;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BotDecisionService {

    private final OrderService orderService;
    private final MenuRepository menuRepository;
    private final LocationRepository locationRepository;
    private final StoreRepository storeRepository;

    public BotPreparationDecision decidePreparation(Store store, int day) {
        List<Menu> menus = menuRepository.findAllByOrderByIdAsc();
        if (menus.isEmpty()) {
            return new BotPreparationDecision(
                    Math.toIntExact(store.getMenu().getId()),
                    resolveQuantity(store.getUser().getBotDifficulty(), day),
                    store.getPrice(),
                    null
            );
        }

        BotDifficulty difficulty = normalizeDifficulty(store);
        Menu selectedMenu = switch (difficulty) {
            case LOW -> store.getMenu();
            case MEDIUM -> menus.stream()
                    .max(Comparator.comparingInt(menu -> preview(menu, store).recommendedPrice()))
                    .orElse(store.getMenu());
            case HIGH -> menus.stream()
                    .max(Comparator.comparingInt(menu -> margin(menu, store)))
                    .orElse(store.getMenu());
        };

        CurrentOrderResponse preview = preview(selectedMenu, store);
        Long nextLocationId = resolveNextLocationId(store, difficulty);
        return new BotPreparationDecision(
                preview.menuId(),
                resolveQuantity(difficulty, day),
                resolveSellingPrice(preview, difficulty),
                nextLocationId
        );
    }

    public BotPlayDecision decideBusinessAction(Store store, GameStateResponse state) {
        if (state == null || state.actionStatus() == null || state.customerTick() == null) {
            return BotPlayDecision.none();
        }

        BotDifficulty difficulty = normalizeDifficulty(store);
        if (difficulty == BotDifficulty.LOW) {
            return BotPlayDecision.none();
        }

        int tick = safe(state.customerTick().tick());
        int stock = state.inventory() == null ? 0 : safe(state.inventory().totalStock());
        long cash = state.cash() == null ? 0L : state.cash();

        if (difficulty == BotDifficulty.MEDIUM) {
            if (between(tick, 8, 12) && !Boolean.TRUE.equals(state.actionStatus().promotionUsed())) {
                return new BotPlayDecision(BotActionType.PROMOTION, null, null, null, null, null, PromotionType.SNS);
            }
            if (tick >= 18 && stock > 180 && !Boolean.TRUE.equals(state.actionStatus().discountUsed())) {
                return new BotPlayDecision(BotActionType.DISCOUNT, null, null, null, 200, null, null);
            }
            return BotPlayDecision.none();
        }

        if (!Boolean.TRUE.equals(state.actionStatus().emergencyOrderPending())
                && tick >= 12
                && stock < 30
                && !Boolean.TRUE.equals(state.actionStatus().emergencyUsed())) {
            CurrentOrderResponse preview = orderService.getCurrentOrder(store.getUser().getId(), Math.toIntExact(store.getMenu().getId()));
            return new BotPlayDecision(
                    BotActionType.EMERGENCY_ORDER,
                    preview.menuId(),
                    80,
                    Math.max(preview.recommendedPrice(), store.getPrice()),
                    null,
                    null,
                    null
            );
        }
        if (between(tick, 4, 8) && !Boolean.TRUE.equals(state.actionStatus().promotionUsed())) {
            return new BotPlayDecision(BotActionType.PROMOTION, null, null, null, null, null, PromotionType.INFLUENCER);
        }
        if (between(tick, 10, 18) && stock > 120 && !Boolean.TRUE.equals(state.actionStatus().discountUsed())) {
            return new BotPlayDecision(BotActionType.DISCOUNT, null, null, null, 300, null, null);
        }
        if (tick >= 24 && stock > 80 && cash > 100_000L && !Boolean.TRUE.equals(state.actionStatus().donationUsed())) {
            return new BotPlayDecision(BotActionType.DONATION, null, null, null, null, Math.min(30, Math.max(1, stock / 4)), null);
        }
        return BotPlayDecision.none();
    }

    private CurrentOrderResponse preview(Menu menu, Store store) {
        return orderService.getCurrentOrder(store.getUser().getId(), Math.toIntExact(menu.getId()));
    }

    private int margin(Menu menu, Store store) {
        CurrentOrderResponse preview = preview(menu, store);
        return preview.recommendedPrice() - preview.costPrice();
    }

    private int resolveQuantity(BotDifficulty difficulty, int day) {
        int baseQuantity = switch (difficulty) {
            case LOW -> 150;
            case MEDIUM -> 220;
            case HIGH -> 300;
        };
        int step = switch (difficulty) {
            case LOW -> 20;
            case MEDIUM -> 30;
            case HIGH -> 40;
        };
        return Math.max(50, Math.min(500, baseQuantity + Math.max(0, day - 1) * step));
    }

    private int resolveSellingPrice(CurrentOrderResponse preview, BotDifficulty difficulty) {
        double multiplier = switch (difficulty) {
            case LOW -> 1.00d;
            case MEDIUM -> 1.05d;
            case HIGH -> 1.12d;
        };
        int proposed = (int) Math.round(preview.recommendedPrice() * multiplier);
        return Math.max(preview.minimumSellingPrice(), Math.min(preview.maxSellingPrice(), proposed));
    }

    private Long resolveNextLocationId(Store store, BotDifficulty difficulty) {
        if (difficulty == BotDifficulty.LOW
                || store.getPendingLocation() != null
                || store.getSeason() == null
                || store.getSeason().resolveRuntimePlayableDays() <= 1) {
            return null;
        }

        List<Location> locations = locationRepository.findAllByOrderByIdAsc();
        if (locations.isEmpty()) {
            return null;
        }

        long currentCount = storeRepository.countBySeason_IdAndLocation_Id(store.getSeason().getId(), store.getLocation().getId());
        Location leastCrowded = locations.stream()
                .min(Comparator
                        .comparingLong((Location location) ->
                                storeRepository.countBySeason_IdAndLocation_Id(store.getSeason().getId(), location.getId()))
                        .thenComparing(Location::getId))
                .orElse(null);
        if (leastCrowded == null || leastCrowded.getId().equals(store.getLocation().getId())) {
            return null;
        }

        long targetCount = storeRepository.countBySeason_IdAndLocation_Id(store.getSeason().getId(), leastCrowded.getId());
        long requiredGap = difficulty == BotDifficulty.MEDIUM ? 2L : 1L;
        return currentCount - targetCount >= requiredGap ? leastCrowded.getId() : null;
    }

    private BotDifficulty normalizeDifficulty(Store store) {
        return store.getUser().getBotDifficulty() == null ? BotDifficulty.MEDIUM : store.getUser().getBotDifficulty();
    }

    private boolean between(int value, int startInclusive, int endInclusive) {
        return value >= startInclusive && value <= endInclusive;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
