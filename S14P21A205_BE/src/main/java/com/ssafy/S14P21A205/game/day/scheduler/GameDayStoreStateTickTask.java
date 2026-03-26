package com.ssafy.S14P21A205.game.day.scheduler;

import com.ssafy.S14P21A205.game.day.service.GameDayStateService;
import com.ssafy.S14P21A205.game.day.dto.GameStateResponse;
import com.ssafy.S14P21A205.game.scheduler.GameTickTask;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.news.service.NewsService;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@org.springframework.core.annotation.Order(200)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameDayStoreStateTickTask implements GameTickTask {

    private static final Logger log = LoggerFactory.getLogger(GameDayStoreStateTickTask.class);

    private final SeasonRepository seasonRepository;
    private final StoreRepository storeRepository;
    private final GameDayStateService gameDayStateService;
    private final NewsService newsService;

    /** 영업 중 뉴스가 이미 생성 요청된 day를 추적 (시즌 내 중복 방지) */
    private final AtomicInteger openingNewsGeneratedDay = new AtomicInteger(-1);

    @Override
    public String taskName() {
        return "gameDayStoreState";
    }

    @Override
    @Transactional
    public void execute() {
        Season season = seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS).orElse(null);
        if (season == null) {
            openingNewsGeneratedDay.set(-1);
            return;
        }

        List<Store> stores = storeRepository.findBySeason_IdOrderByIdAsc(season.getId());
        for (Store store : stores) {
            try {
                gameDayStateService.refreshGameState(store)
                        .ifPresent(response -> log.info(
                                "game-state-tick storeId={} seasonId={} day={} customerCount={} cash={} totalStock={}",
                                store.getId(),
                                response.seasonId(),
                                response.day(),
                                response.customerCount(),
                                response.cash(),
                                response.inventory() == null ? null : response.inventory().totalStock()
                        ));
            } catch (Exception e) {
                log.error(
                        "Failed to refresh game day store state. seasonId={} storeId={}",
                        season.getId(),
                        store.getId(),
                        e
                );
            }
        }

        int day = season.getCurrentDay() == null ? 1 : season.getCurrentDay();

        // 영업 중 뉴스: 해당 day에 한 번만 생성 (데이터 부족 시 다음 틱에서 재시도)
        if (openingNewsGeneratedDay.getAndSet(day) != day) {
            try {
                boolean generated = newsService.generateOpeningNews(season.getId(), day);
                if (!generated) {
                    openingNewsGeneratedDay.set(-1);
                }
            } catch (Exception e) {
                openingNewsGeneratedDay.set(-1);
                log.error("Failed to generate opening news, will retry next tick. seasonId={} day={}", season.getId(), day, e);
            }
        }

    }
}
