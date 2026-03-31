package com.ssafy.S14P21A205.game.bot.service;

import com.ssafy.S14P21A205.action.dto.DiscountRequest;
import com.ssafy.S14P21A205.action.dto.DonationRequest;
import com.ssafy.S14P21A205.action.dto.EmergencyOrderRequest;
import com.ssafy.S14P21A205.action.dto.PromotionRequest;
import com.ssafy.S14P21A205.action.service.ActionService;
import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.game.day.dto.GameStateResponse;
import com.ssafy.S14P21A205.game.day.service.GameDayStartService;
import com.ssafy.S14P21A205.game.day.service.GameDayStateService;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.time.model.SeasonPhase;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import com.ssafy.S14P21A205.order.dto.RegularOrderRequest;
import com.ssafy.S14P21A205.order.repository.OrderRepository;
import com.ssafy.S14P21A205.order.service.OrderService;
import com.ssafy.S14P21A205.store.dto.UpdateStoreLocationRequest;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.store.service.StoreService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BotExecutionService {

    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ActionService actionService;
    private final StoreService storeService;
    private final GameDayStateService gameDayStateService;
    private final GameDayStartService gameDayStartService;
    private final BotDecisionService botDecisionService;
    private final Clock clock;

    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();

    @Transactional
    public void executeForSeason(Season season) {
        if (season == null || season.getId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        SeasonTimePoint timePoint = seasonTimelineService.resolve(season, now);
        if (timePoint.phase() != SeasonPhase.DAY_PREPARING && timePoint.phase() != SeasonPhase.DAY_BUSINESS) {
            return;
        }

        int currentDay = timePoint.currentDay() == null ? 1 : timePoint.currentDay();
        List<Store> botStores = storeRepository.findBySeason_IdOrderByIdAsc(season.getId()).stream()
                .filter(store -> store.getUser() != null && store.getUser().isBot())
                .toList();

        for (Store store : botStores) {
            try {
                if (timePoint.phase() == SeasonPhase.DAY_PREPARING) {
                    executePreparing(store, currentDay, now, timePoint);
                    continue;
                }
                executeBusiness(store);
            } catch (BaseException e) {
                log.debug("Bot action skipped. seasonId={} storeId={} reason={}", season.getId(), store.getId(), e.getMessage());
            } catch (Exception e) {
                log.warn("Bot action failed. seasonId={} storeId={}", season.getId(), store.getId(), e);
            }
        }
    }

    private void executePreparing(Store store, int day, LocalDateTime now, SeasonTimePoint timePoint) {
        gameDayStartService.synchronizeCurrentDayState(store, now, timePoint);
        BotPreparationDecision decision = botDecisionService.decidePreparation(store, day);

        if (decision.nextLocationId() != null) {
            try {
                storeService.updateStoreLocation(store.getUser().getId(), new UpdateStoreLocationRequest(decision.nextLocationId()));
            } catch (BaseException e) {
                log.debug("Bot move skipped. storeId={} reason={}", store.getId(), e.getMessage());
            }
        }

        if (orderRepository.findDailyStartOrder(store.getId(), day).isPresent()) {
            return;
        }

        orderService.createRegularOrder(
                store.getUser().getId(),
                new RegularOrderRequest(decision.menuId(), decision.quantity(), decision.price())
        );
    }

    private void executeBusiness(Store store) {
        GameStateResponse state = gameDayStateService.refreshGameState(store).orElse(null);
        BotPlayDecision decision = botDecisionService.decideBusinessAction(store, state);
        switch (decision.actionType()) {
            case NONE -> {
                return;
            }
            case DISCOUNT -> actionService.executeDiscount(
                    store.getUser().getId(),
                    new DiscountRequest(decision.discountValue())
            );
            case PROMOTION -> actionService.executePromotion(
                    store.getUser().getId(),
                    new PromotionRequest(decision.promotionType())
            );
            case DONATION -> actionService.executeDonation(
                    store.getUser().getId(),
                    new DonationRequest(decision.donationQuantity())
            );
            case EMERGENCY_ORDER -> actionService.executeEmergencyOrder(
                    store.getUser().getId(),
                    new EmergencyOrderRequest(decision.menuId(), decision.quantity(), decision.price())
            );
        }
    }
}
