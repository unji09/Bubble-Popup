package com.ssafy.S14P21A205.game.bot.service;

import com.ssafy.S14P21A205.game.day.generator.PurchaseListGenerator;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.LocationRepository;
import com.ssafy.S14P21A205.store.repository.MenuRepository;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.user.entity.BotDifficulty;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BotSeasonFillService {

    private static final int MINIMUM_COMPETITORS = 3;

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final LocationRepository locationRepository;
    private final MenuRepository menuRepository;
    private final PurchaseListGenerator purchaseListGenerator;

    @Transactional
    public int fillBotsToMinimum(Season season) {
        if (season == null || season.getId() == null) {
            return 0;
        }

        long humanCount = storeRepository.countDistinctHumanUsersBySeasonId(season.getId());
        long existingBotCount = storeRepository.findAllBySeason_IdOrderByIdAsc(season.getId()).stream()
                .filter(store -> store.getUser() != null && store.getUser().isBot())
                .count();
        long targetBotCount = Math.max(0L, MINIMUM_COMPETITORS - humanCount);
        long missingBotCount = Math.max(0L, targetBotCount - existingBotCount);
        if (missingBotCount <= 0L) {
            return 0;
        }

        List<Location> locations = locationRepository.findAllByOrderByIdAsc();
        List<Menu> menus = menuRepository.findAllByOrderByIdAsc();
        if (locations.isEmpty() || menus.isEmpty()) {
            return 0;
        }

        for (int index = 0; index < missingBotCount; index++) {
            int botOrdinal = Math.toIntExact(existingBotCount + index);
            BotDifficulty difficulty = resolveDifficulty(Math.toIntExact(targetBotCount), botOrdinal);
            Location location = locations.get(botOrdinal % locations.size());
            Menu menu = menus.get(botOrdinal % menus.size());
            User botUser = userRepository.save(User.createBot(
                    "bot-season-%d-%02d@local.bot".formatted(season.getId(), botOrdinal + 1),
                    "BOT_%s_%02d".formatted(difficulty.name(), botOrdinal + 1),
                    difficulty
            ));
            Store botStore = Store.create(
                    botUser,
                    location,
                    menu,
                    season,
                    "BotStore-%s-%02d".formatted(difficulty.name(), botOrdinal + 1),
                    resolveInitialPrice(menu),
                    1
            );
            botStore.initializePurchaseQueue(purchaseListGenerator.issueSeed());
            storeRepository.save(botStore);
        }

        log.info(
                "Filled season bots. seasonId={} humanCount={} existingBotCount={} targetBotCount={} created={}",
                season.getId(),
                humanCount,
                existingBotCount,
                targetBotCount,
                missingBotCount
        );
        return Math.toIntExact(missingBotCount);
    }

    private BotDifficulty resolveDifficulty(int targetBotCount, int botOrdinal) {
        if (targetBotCount <= 1) {
            return BotDifficulty.MEDIUM;
        }
        if (targetBotCount == 2) {
            return botOrdinal % 2 == 0 ? BotDifficulty.LOW : BotDifficulty.HIGH;
        }
        return switch (botOrdinal % 3) {
            case 0 -> BotDifficulty.LOW;
            case 1 -> BotDifficulty.MEDIUM;
            default -> BotDifficulty.HIGH;
        };
    }

    private int resolveInitialPrice(Menu menu) {
        return BigDecimal.valueOf(menu.getOriginPrice())
                .multiply(new BigDecimal("2.5"))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }
}
