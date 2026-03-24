package com.ssafy.S14P21A205.game.day.service;

import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.news.service.NewsService;
import com.ssafy.S14P21A205.game.season.service.SeasonFinalRankingService;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SeasonDayClosingService {

    private final SeasonRepository seasonRepository;
    private final StoreRepository storeRepository;
    private final GameDayReportService gameDayReportService;
    private final SeasonFinalRankingService seasonFinalRankingService;
    private final NewsService newsService;

    public SeasonDayClosingService(
            SeasonRepository seasonRepository,
            StoreRepository storeRepository,
            GameDayReportService gameDayReportService,
            SeasonFinalRankingService seasonFinalRankingService,
            NewsService newsService
    ) {
        this.seasonRepository = seasonRepository;
        this.storeRepository = storeRepository;
        this.gameDayReportService = gameDayReportService;
        this.seasonFinalRankingService = seasonFinalRankingService;
        this.newsService = newsService;
    }

    public void handleBusinessEnd(Long seasonId, int day) {
        if (seasonId == null || day < 1) {
            return;
        }

        Season season = seasonRepository.findByIdAndStatus(seasonId, SeasonStatus.IN_PROGRESS).orElse(null);
        if (season == null || season.getTotalDays() == null || day > season.getTotalDays()) {
            return;
        }

        // Day closing must still persist reports for stores that already went bankrupt.
        List<Store> stores = storeRepository.findAllBySeason_IdOrderByIdAsc(seasonId);
        if (stores.isEmpty()) {
            log.info("Skipping day closing. seasonId={} day={} reason=no_stores", seasonId, day);
            return;
        }

        boolean isLastDay = day == season.getTotalDays();

        // 리포트 저장 (별도 스레드) — daily_report 테이블만 사용, 독립적
        CompletableFuture<Void> reportFuture = CompletableFuture.runAsync(() -> {
            for (Store store : stores) {
                gameDayReportService.recordClosedDayReport(store, day);
            }
            if (isLastDay) {
                seasonFinalRankingService.saveFinalRankings(season);
            }
            log.info("Daily reports saved. seasonId={} day={} storeCount={}", seasonId, day, stores.size());
        });

        // 순위 집계 + 마감 뉴스 (별도 스레드, 순차 실행) — news_report 테이블 사용
        CompletableFuture<Void> newsFuture = CompletableFuture.runAsync(() -> {
            try {
                newsService.updateDayRankingsFromRedis(seasonId, day, stores);
            } catch (Exception e) {
                log.error("Failed to update rankings/news from Redis. seasonId={} day={}", seasonId, day, e);
            }
        });

        // 두 작업 모두 완료 대기
        try {
            CompletableFuture.allOf(reportFuture, newsFuture).join();
        } catch (Exception e) {
            log.error("Day closing tasks failed. seasonId={} day={}", seasonId, day, e);
        }
    }
}
