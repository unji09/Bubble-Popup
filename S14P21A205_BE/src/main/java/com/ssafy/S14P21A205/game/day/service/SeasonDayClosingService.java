package com.ssafy.S14P21A205.game.day.service;

import com.ssafy.S14P21A205.game.news.service.NewsService;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.season.service.SeasonFinalRankingService;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SeasonDayClosingService {

    private final SeasonRepository seasonRepository;
    private final StoreRepository storeRepository;
    private final GameDayReportService gameDayReportService;
    private final SeasonFinalRankingService seasonFinalRankingService;
    private final NewsService newsService;
    private final Executor dayClosingExecutor;

    public SeasonDayClosingService(
            SeasonRepository seasonRepository,
            StoreRepository storeRepository,
            GameDayReportService gameDayReportService,
            SeasonFinalRankingService seasonFinalRankingService,
            NewsService newsService,
            @Qualifier("dayClosingExecutor") Executor dayClosingExecutor
    ) {
        this.seasonRepository = seasonRepository;
        this.storeRepository = storeRepository;
        this.gameDayReportService = gameDayReportService;
        this.seasonFinalRankingService = seasonFinalRankingService;
        this.newsService = newsService;
        this.dayClosingExecutor = dayClosingExecutor;
    }

    public void handleBusinessEnd(Long seasonId, int day) {
        if (seasonId == null || day < 1) {
            return;
        }

        Season season = seasonRepository.findByIdAndStatus(seasonId, SeasonStatus.IN_PROGRESS).orElse(null);
        if (season == null || season.resolveRuntimePlayableDays() <= 0 || day > season.resolveRuntimePlayableDays()) {
            return;
        }

        List<Store> stores = storeRepository.findAllBySeason_IdOrderByIdAsc(seasonId);
        if (stores.isEmpty()) {
            log.info("Skipping day closing. seasonId={} day={} reason=no_stores", seasonId, day);
            return;
        }

        boolean isLastDay = day == season.resolveRuntimePlayableDays();

        CompletableFuture<Void> reportFuture = CompletableFuture.runAsync(() -> {
            int successCount = 0;
            int failureCount = 0;

            for (Store store : stores) {
                try {
                    gameDayReportService.recordClosedDayReport(store, day);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    log.error(
                            "Failed to save daily report. seasonId={} day={} storeId={}",
                            seasonId,
                            day,
                            store.getId(),
                            e
                    );
                }
            }
            if (isLastDay) {
                seasonFinalRankingService.saveFinalRankings(season);
            }
            log.info(
                    "Daily reports saved. seasonId={} day={} storeCount={} successCount={} failureCount={}",
                    seasonId,
                    day,
                    stores.size(),
                    successCount,
                    failureCount
            );
        }, dayClosingExecutor);

        CompletableFuture<Void> newsFuture = CompletableFuture.runAsync(() -> {
            try {
                newsService.updateDayRankingsFromRedis(seasonId, day, stores);
            } catch (Exception e) {
                log.error("Failed to update rankings/news from Redis. seasonId={} day={}", seasonId, day, e);
            }
        }, dayClosingExecutor);

        try {
            CompletableFuture.allOf(reportFuture, newsFuture).join();
        } catch (Exception e) {
            log.error("Day closing tasks failed. seasonId={} day={}", seasonId, day, e);
        }
    }
}
