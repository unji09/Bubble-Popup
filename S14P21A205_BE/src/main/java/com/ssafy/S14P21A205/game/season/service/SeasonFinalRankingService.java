package com.ssafy.S14P21A205.game.season.service;

import com.ssafy.S14P21A205.game.day.policy.ProfitPolicy;
import com.ssafy.S14P21A205.game.day.service.GameDayReportService;
import com.ssafy.S14P21A205.game.season.entity.DailyReport;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonRankingRecord;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRankingRecordRepository;
import com.ssafy.S14P21A205.shop.service.ShopService;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class SeasonFinalRankingService {

    private static final int DEFAULT_TOTAL_DAYS = 7;
    private static final int UNRANKED_FINAL_RANK = 0;

    private final StoreRepository storeRepository;
    private final DailyReportRepository dailyReportRepository;
    private final SeasonRankingRecordRepository seasonRankingRecordRepository;
    private final GameDayReportService gameDayReportService;
    private final ProfitPolicy profitPolicy;
    private final UserRepository userRepository;
    private final ShopService shopService;

    public void saveFinalRankings(Season season) {
        if (season == null || season.getId() == null) {
            return;
        }
        if (seasonRankingRecordRepository.existsByStore_Season_Id(season.getId())) {
            log.info("Final season rankings already exist. seasonId={}", season.getId());
            return;
        }

        int totalDays = season.resolveRuntimePlayableDays() <= 0
                ? DEFAULT_TOTAL_DAYS
                : season.resolveRuntimePlayableDays();
        // Final ranking is based on the full season history, so bankrupt stores stay included here.
        List<Store> stores = storeRepository.findAllBySeason_IdOrderByIdAsc(season.getId());
        if (stores.isEmpty()) {
            log.info("Skipping final season rankings save. seasonId={} reason=no_stores", season.getId());
            return;
        }

        stores.forEach(store -> gameDayReportService.recordClosedDayReport(store, totalDays));

        List<DailyReport> reports = dailyReportRepository.findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(
                season.getId(),
                totalDays + 1
        );

        Map<Long, AggregatedStats> aggregatedStatsByStoreId = aggregateReports(reports);
        List<FinalRankingCandidate> candidates = buildCandidates(stores, aggregatedStatsByStoreId);
        if (candidates.isEmpty()) {
            log.info("Skipping final season rankings save. seasonId={} reason=no_candidates", season.getId());
            return;
        }

        List<SeasonRankingRecord> records = buildRecords(candidates);
        if (records.isEmpty()) {
            log.info("Skipping final season rankings save. seasonId={} reason=no_records_generated", season.getId());
            return;
        }

        seasonRankingRecordRepository.saveAll(records);
        grantRewardPoints(records);
        resetPurchasedItems(records);
        log.info(
                "Final season rankings saved. seasonId={} savedCount={} lastSavedRank={} topSummary={}",
                season.getId(),
                records.size(),
                records.stream()
                        .map(SeasonRankingRecord::getFinalRank)
                        .filter(rank -> rank != null && rank > 0)
                        .max(Integer::compareTo)
                        .orElse(UNRANKED_FINAL_RANK),
                summarize(records, 3)
        );
    }

    private Map<Long, AggregatedStats> aggregateReports(List<DailyReport> reports) {
        Map<Long, AggregatedStats> aggregatedStatsByStoreId = new HashMap<>();
        for (DailyReport report : reports) {
            Long storeId = report.getStore().getId();
            AggregatedStats current = aggregatedStatsByStoreId.getOrDefault(storeId, AggregatedStats.empty());
            aggregatedStatsByStoreId.put(
                    storeId,
                    new AggregatedStats(
                            current.totalRevenue() + valueOf(report.getRevenue()),
                            current.totalCost() + valueOf(report.getTotalCost()),
                            current.totalNetProfit() + valueOf(report.getNetProfit()),
                            current.totalVisitors() + valueOf(report.getVisitors()),
                            current.daysPlayed() + 1,
                            current.bankruptcy() || Boolean.TRUE.equals(report.getIsBankrupt())
                    )
            );
        }
        return aggregatedStatsByStoreId;
    }

    private List<FinalRankingCandidate> buildCandidates(
            List<Store> stores,
            Map<Long, AggregatedStats> aggregatedStatsByStoreId
    ) {
        List<FinalRankingCandidate> candidates = new ArrayList<>();
        for (Store store : stores) {
            AggregatedStats stats = aggregatedStatsByStoreId.getOrDefault(store.getId(), AggregatedStats.empty());
            candidates.add(new FinalRankingCandidate(
                    store,
                    stats,
                    profitPolicy.calculateRoi(stats.totalRevenue(), stats.totalCost())
            ));
        }

        candidates.sort(Comparator
                .comparing(FinalRankingCandidate::roi, Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.store().getUser().getId())
                .thenComparing(candidate -> candidate.store().getId()));
        return candidates;
    }

    private List<SeasonRankingRecord> buildRecords(List<FinalRankingCandidate> candidates) {
        List<SeasonRankingRecord> records = new ArrayList<>();
        List<FinalRankingCandidate> rankedCandidates = candidates.stream()
                .filter(candidate -> !candidate.stats().bankruptcy())
                .toList();
        List<FinalRankingCandidate> bankruptCandidates = candidates.stream()
                .filter(candidate -> candidate.stats().bankruptcy())
                .toList();

        BigDecimal previousRoi = null;
        int currentRank = 0;
        for (int index = 0; index < rankedCandidates.size(); index++) {
            FinalRankingCandidate candidate = rankedCandidates.get(index);
            if (previousRoi == null || candidate.roi().compareTo(previousRoi) != 0) {
                currentRank = index + 1;
                previousRoi = candidate.roi();
            }

            AggregatedStats stats = candidate.stats();
            records.add(SeasonRankingRecord.create(
                    candidate.store(),
                    currentRank,
                    safeToInt(stats.totalRevenue()),
                    safeToInt(stats.totalCost()),
                    safeToInt(stats.totalNetProfit()),
                    safeToInt(stats.totalVisitors()),
                    candidate.roi().floatValue(),
                    stats.daysPlayed(),
                    resolveRewardPoints(currentRank),
                    false
            ));
        }

        for (FinalRankingCandidate candidate : bankruptCandidates) {
            AggregatedStats stats = candidate.stats();
            records.add(SeasonRankingRecord.create(
                    candidate.store(),
                    UNRANKED_FINAL_RANK,
                    safeToInt(stats.totalRevenue()),
                    safeToInt(stats.totalCost()),
                    safeToInt(stats.totalNetProfit()),
                    safeToInt(stats.totalVisitors()),
                    candidate.roi().floatValue(),
                    stats.daysPlayed(),
                    0,
                    true
            ));
        }

        return records;
    }

    private void grantRewardPoints(List<SeasonRankingRecord> records) {
        Map<Integer, Integer> rewardPointsByUserId = new LinkedHashMap<>();
        for (SeasonRankingRecord record : records) {
            Integer rewardPoints = record.getRewardPoints();
            if (rewardPoints == null || rewardPoints <= 0) {
                continue;
            }
            Integer userId = record.getStore().getUser().getId();
            rewardPointsByUserId.merge(userId, rewardPoints, Integer::sum);
        }

        for (Map.Entry<Integer, Integer> entry : rewardPointsByUserId.entrySet()) {
            User user = userRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new IllegalStateException("User not found for reward grant."));
            user.addPoints(entry.getValue());
        }
    }

    private void resetPurchasedItems(List<SeasonRankingRecord> records) {
        records.stream()
                .map(record -> record.getStore().getUser().getId())
                .distinct()
                .forEach(shopService::resetPurchasedItems);
    }

    private String summarize(List<SeasonRankingRecord> records, int limit) {
        StringBuilder summary = new StringBuilder("[");
        int summarySize = Math.min(limit, records.size());
        for (int index = 0; index < summarySize; index++) {
            SeasonRankingRecord record = records.get(index);
            if (index > 0) {
                summary.append(", ");
            }
            summary.append("#")
                    .append(record.getFinalRank())
                    .append("(userId=")
                    .append(record.getStore().getUser().getId())
                    .append(", storeId=")
                    .append(record.getStore().getId())
                    .append(", revenue=")
                    .append(record.getTotalRevenue())
                    .append(", cost=")
                    .append(record.getTotalCost())
                    .append(", roi=")
                    .append(record.getRoi())
                    .append(")");
        }
        summary.append("]");
        return summary.toString();
    }

    private int resolveRewardPoints(int rank) {
        if (rank < 1) {
            return 0;
        }
        return switch (rank) {
            case 1 -> 30;
            case 2 -> 20;
            case 3 -> 10;
            default -> 5;
        };
    }

    private int safeToInt(long value) {
        return Math.toIntExact(value);
    }

    private long valueOf(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private record FinalRankingCandidate(Store store, AggregatedStats stats, BigDecimal roi) {
    }

    private record AggregatedStats(
            long totalRevenue,
            long totalCost,
            long totalNetProfit,
            long totalVisitors,
            int daysPlayed,
            boolean bankruptcy
    ) {
        private static AggregatedStats empty() {
            return new AggregatedStats(0L, 0L, 0L, 0L, 0, false);
        }
    }
}
