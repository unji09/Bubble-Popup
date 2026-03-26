package com.ssafy.S14P21A205.game.season.scheduler;

import com.ssafy.S14P21A205.game.day.policy.ProfitPolicy;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.scheduler.GameTickTask;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTopRankingItemResponse;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTopRankingsResponse;
import com.ssafy.S14P21A205.game.season.entity.DailyReport;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRankingRedisRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@org.springframework.core.annotation.Order(300)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RealtimeSeasonRankingTickTask implements GameTickTask {

    private static final Logger log = LoggerFactory.getLogger(RealtimeSeasonRankingTickTask.class);
    private static final DateTimeFormatter REFRESHED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final SeasonRepository seasonRepository;
    private final StoreRepository storeRepository;
    private final DailyReportRepository dailyReportRepository;
    private final GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;
    private final SeasonRankingRedisRepository seasonRankingRedisRepository;
    private final ProfitPolicy profitPolicy;

    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();
    private final Clock clock;

    @Override
    public String taskName() {
        return "realtimeSeasonRanking";
    }

    @Override
    public void execute() {
        refreshCurrentTopRankings();
    }

    public void refreshCurrentTopRankings() {
        try {
            // ?꾩옱 吏꾪뻾 以??쒖쫵 議고쉶
            Season season = seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS).orElse(null);
            if (season == null) {
                seasonRankingRedisRepository.deleteCurrentTopRankings();
                log.info("Realtime season rankings cache cleared. reason=no_in_progress_season");
                return;
            }

            // ?꾩옱 day 怨꾩궛
            int currentDay = resolveCurrentDay(season);
            List<Store> stores = storeRepository.findBySeason_IdOrderByIdAsc(season.getId());
            if (stores.isEmpty()) {
                seasonRankingRedisRepository.deleteCurrentTopRankings();
                log.info(
                        "Realtime season rankings cache cleared. seasonId={} currentDay={} reason=no_stores",
                        season.getId(),
                        currentDay
                );
                return;
            }

            // 怨쇨굅 day(?꾩옱 day ?댁쟾)???꾩쟻 留ㅼ텧/鍮꾩슜??DailyReport?먯꽌 吏묎퀎
            Map<Long, AggregatedStats> pastStatsByStoreId = aggregatePastStats(
                    dailyReportRepository.findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(season.getId(), currentDay)
            );
            // ?ㅻ뒛 day???ㅼ떆媛?留ㅼ텧/鍮꾩슜??Redis?먯꽌 議고쉶
            Map<Long, LiveDayStats> todayStatsByStoreId = loadTodayStatsByStoreId(stores, currentDay);

            // 媛寃뚮퀎 珥?留ㅼ텧/珥?鍮꾩슜/ROI 怨꾩궛
            List<LiveRankingCandidate> candidates = new ArrayList<>();
            for (Store store : stores) {
                AggregatedStats pastStats = pastStatsByStoreId.getOrDefault(store.getId(), AggregatedStats.empty());
                LiveDayStats todayStats = todayStatsByStoreId.getOrDefault(store.getId(), LiveDayStats.empty());

                long totalRevenue = pastStats.totalRevenue() + todayStats.totalRevenue();
                long totalCost = pastStats.totalCost() + todayStats.totalCost();
                candidates.add(new LiveRankingCandidate(
                        store,
                        totalRevenue,
                        totalCost,
                        profitPolicy.calculateRoi(totalRevenue, totalCost)
                ));
            }
            // ?뺣젹
            // 1) ROI ?대┝李⑥닚   2) userId ?ㅻ쫫李⑥닚   3)storeId ?ㅻ쫫李⑥닚
            candidates.sort(Comparator
                    .comparing(LiveRankingCandidate::roi, Comparator.reverseOrder())
                    .thenComparing(candidate -> candidate.store().getUser().getId())
                    .thenComparing(candidate -> candidate.store().getId()));

            List<CurrentSeasonTopRankingItemResponse> rankings = new ArrayList<>();
            int limit = Math.min(10, candidates.size());
            BigDecimal previousRoi = null;
            int currentRank = 0;
            for (int index = 0; index < limit; index++) {
                LiveRankingCandidate candidate = candidates.get(index);

                // 媛숈? ROI硫?怨듬룞 ?쒖쐞 泥섎━
                if (previousRoi == null || candidate.roi().compareTo(previousRoi) != 0) {
                    currentRank = index + 1;
                    previousRoi = candidate.roi();
                }
                rankings.add(new CurrentSeasonTopRankingItemResponse(
                        currentRank,
                        candidate.store().getUser().getId(),
                        candidate.store().getUser().getNickname(),
                        candidate.store().getStoreName(),
                        candidate.roi(),
                        candidate.totalRevenue(),
                        resolveRewardPoints(currentRank),
                        false
                ));
            }

            if (rankings.isEmpty()) {
                seasonRankingRedisRepository.deleteCurrentTopRankings();
                log.info(
                        "Realtime season rankings cache cleared. seasonId={} currentDay={} reason=no_rankings_generated",
                        season.getId(),
                        currentDay
                );
                return;
            }

            String refreshedAt = resolveRefreshedAt(todayStatsByStoreId);
            seasonRankingRedisRepository.saveCurrentTopRankings(new CurrentSeasonTopRankingsResponse(
                    season.getId(),
                    rankings,
                    refreshedAt
            ));
            log.info(
                    "Realtime season rankings refreshed. seasonId={} currentDay={} storeCount={} rankingCount={} refreshedAt={} topRankings={}",
                    season.getId(),
                    currentDay,
                    stores.size(),
                    rankings.size(),
                    refreshedAt,
                    summarizeTopRankings(rankings, candidates, 3)
            );
        } catch (Exception e) {
            log.error("Failed to refresh realtime season rankings.", e);
        }
    }

    private int resolveCurrentDay(Season season) {
        int currentDay = season.getCurrentDay() == null ? 1 : season.getCurrentDay();
        if (currentDay < 1 || currentDay > season.resolveRuntimePlayableDays()) {
            throw new IllegalStateException("Current season day is out of range.");
        }
        return currentDay;
    }

    // ?꾩옱 day ?댁쟾源뚯???DB ?쇱씪 由ы룷?몃? 媛寃뚮퀎濡??꾩쟻??
    private Map<Long, AggregatedStats> aggregatePastStats(List<DailyReport> dailyReports) {
        Map<Long, AggregatedStats> statsByStoreId = new HashMap<>();
        for (DailyReport dailyReport : dailyReports) {
            Long storeId = dailyReport.getStore().getId();
            AggregatedStats current = statsByStoreId.getOrDefault(storeId, AggregatedStats.empty());
            statsByStoreId.put(
                    storeId,
                    new AggregatedStats(
                            current.totalRevenue() + valueOf(dailyReport.getRevenue()),
                            current.totalCost() + valueOf(dailyReport.getTotalCost())
                    )
            );
        }
        return statsByStoreId;
    }

    // 紐⑤뱺 媛寃뚯뿉 ????ㅻ뒛 day???ㅼ떆媛??곹깭瑜?Redis?먯꽌 議고쉶
    private Map<Long, LiveDayStats> loadTodayStatsByStoreId(List<Store> stores, int currentDay) {
        Map<Long, LiveDayStats> todayStatsByStoreId = new HashMap<>();
        for (Store store : stores) {
            LiveDayStats liveDayStats = calculateLiveDayStats(store.getId(), currentDay);
            if (liveDayStats.isEmpty()) {
                continue;
            }
            todayStatsByStoreId.put(store.getId(), liveDayStats);
        }
        return todayStatsByStoreId;
    }
    // Load today's live revenue/cost state from Redis for a single store.
    private LiveDayStats calculateLiveDayStats(Long storeId, int currentDay) {
        return gameDayStoreStateRedisRepository.find(storeId, currentDay)
                .map(this::toLiveDayStats)
                .orElseGet(LiveDayStats::empty);
    }

    private LiveDayStats toLiveDayStats(GameDayLiveState state) {
        return new LiveDayStats(
                state.cumulativeSales(),
                state.cumulativeTotalCost(),
                state.lastCalculatedAt()
        );
    }

    private String resolveRefreshedAt(Map<Long, LiveDayStats> todayStatsByStoreId) {
        return todayStatsByStoreId.values().stream()
                .map(LiveDayStats::lastCalculatedAt)
                .filter(lastCalculatedAt -> lastCalculatedAt != null)
                .max(Comparator.naturalOrder())
                .orElse(LocalDateTime.now(clock))
                .withNano(0)
                .format(REFRESHED_AT_FORMATTER);
    }

    private int resolveRewardPoints(int rank) {
        return switch (rank) {
            case 1 -> 30;
            case 2 -> 20;
            case 3 -> 10;
            default -> 5;
        };
    }

    private long valueOf(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private String summarizeTopRankings(
            List<CurrentSeasonTopRankingItemResponse> rankings,
            List<LiveRankingCandidate> candidates,
            int limit
    ) {
        if (rankings == null || rankings.isEmpty() || candidates == null || candidates.isEmpty()) {
            return "[]";
        }

        int summarySize = Math.min(Math.min(limit, rankings.size()), candidates.size());
        StringBuilder summary = new StringBuilder("[");
        for (int index = 0; index < summarySize; index++) {
            CurrentSeasonTopRankingItemResponse ranking = rankings.get(index);
            LiveRankingCandidate candidate = candidates.get(index);
            if (index > 0) {
                summary.append(", ");
            }
            summary.append("#")
                    .append(ranking.rank())
                    .append("(userId=")
                    .append(ranking.userId())
                    .append(", storeId=")
                    .append(candidate.store().getId())
                    .append(", revenue=")
                    .append(ranking.totalRevenue())
                    .append(", cost=")
                    .append(candidate.totalCost())
                    .append(", roi=")
                    .append(ranking.roi())
                    .append(")");
        }
        summary.append("]");
        return summary.toString();
    }

    private record AggregatedStats(long totalRevenue, long totalCost) {
        private static AggregatedStats empty() {
            return new AggregatedStats(0L, 0L);
        }
    }

    private record LiveRankingCandidate(Store store, long totalRevenue, long totalCost, BigDecimal roi) {
    }

    private record LiveDayStats(long totalRevenue, long totalCost, LocalDateTime lastCalculatedAt) {
        private static LiveDayStats empty() {
            return new LiveDayStats(0L, 0L, null);
        }

        private boolean isEmpty() {
            return totalRevenue == 0L && totalCost == 0L && lastCalculatedAt == null;
        }
    }
}




