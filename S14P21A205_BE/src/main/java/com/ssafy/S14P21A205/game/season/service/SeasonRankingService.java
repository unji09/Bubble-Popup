package com.ssafy.S14P21A205.game.season.service;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonRankingItemResponse;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonRankingsResponse;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTopRankingItemResponse;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTopRankingsResponse;
import com.ssafy.S14P21A205.game.season.entity.DailyReport;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonRankingRecord;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRankingRecordRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRankingRedisRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.user.service.UserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonRankingService {

    private final SeasonRankingRedisRepository seasonRankingRedisRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonRankingRecordRepository seasonRankingRecordRepository;
    private final DailyReportRepository dailyReportRepository;
    private final StoreRepository storeRepository;
    private final UserService userService;
    private final Clock clock;

    public CurrentSeasonTopRankingsResponse getCurrentTopRankings(Authentication authentication) {
        Integer userId = userService.getCurrentUser(authentication).getId();
        CurrentSeasonTopRankingsResponse cachedResponse = seasonRankingRedisRepository.findCurrentTopRankings()
                .orElseGet(this::buildEmptyCurrentTopRankingsResponse);

        List<CurrentSeasonTopRankingItemResponse> rankings = normalizeTopRankings(cachedResponse.rankings(), userId)
                .stream()
                .limit(10)
                .toList();

        return new CurrentSeasonTopRankingsResponse(
                cachedResponse.seasonId(),
                rankings,
                cachedResponse.refreshedAt()
        );
    }

    public CurrentSeasonRankingsResponse getCurrentFinalRankings(Authentication authentication) {
        Integer userId = userService.getCurrentUser(authentication).getId();
        return getCurrentFinalRankings(userId);
    }

    private CurrentSeasonRankingsResponse getCurrentFinalRankings(Integer userId) {
        Season season = resolveCurrentFinalRankingSeason(LocalDateTime.now(clock));
        Map<Long, Integer> bankruptcyDays = bankruptcyDays(season);

        List<RankingView> allRankings = seasonRankingRecordRepository
                .findByStore_Season_IdOrderByFinalRankAsc(season.getId())
                .stream()
                .map(record -> toRankingView(record, bankruptcyDays.get(record.getStore().getId()), userId))
                .sorted(rankingViewComparator())
                .toList();
        if (allRankings.isEmpty()) {
            if (storeRepository.findAllBySeason_IdOrderByIdAsc(season.getId()).isEmpty()) {
                return new CurrentSeasonRankingsResponse(season.getId(), List.of(), List.of());
            }
            throw new BaseException(ErrorCode.FINAL_RANKING_NOT_READY);
        }

        List<CurrentSeasonRankingItemResponse> rankings = extractDisplayRankings(allRankings);
        return new CurrentSeasonRankingsResponse(
                season.getId(),
                rankings,
                extractMyRankings(userId, allRankings)
        );
    }

    private Season resolveCurrentFinalRankingSeason(LocalDateTime now) {
        Season inProgressSeason = seasonRepository
                .findByStatusAndStartTimeLessThanEqualOrderByStartTimeDescIdDesc(SeasonStatus.IN_PROGRESS, now)
                .stream()
                .findFirst()
                .orElse(null);
        if (inProgressSeason != null) {
            return inProgressSeason;
        }

        Season finishedSeason = seasonRepository
                .findByStatusAndStartTimeLessThanEqualAndEndTimeAfterOrderByEndTimeDescIdDesc(
                        SeasonStatus.FINISHED,
                        now,
                        now
                )
                .stream()
                .findFirst()
                .orElse(null);
        if (finishedSeason != null) {
            return finishedSeason;
        }

        boolean hasCurrentOrUpcomingSeason = seasonRepository.findFirstByStatusOrderByStartTimeAscIdAsc(SeasonStatus.SCHEDULED).isPresent()
                || seasonRepository.findFirstByOrderByIdDesc().isPresent();
        if (hasCurrentOrUpcomingSeason) {
            throw new BaseException(ErrorCode.FINAL_RANKING_NOT_READY);
        }

        throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private List<CurrentSeasonTopRankingItemResponse> normalizeTopRankings(
            List<CurrentSeasonTopRankingItemResponse> rankings,
            Integer userId
    ) {
        if (rankings == null || rankings.isEmpty()) {
            return List.of();
        }

        return rankings.stream()
                .filter(ranking -> ranking != null && ranking.rank() != null && ranking.userId() != null)
                .sorted(Comparator.comparing(CurrentSeasonTopRankingItemResponse::rank))
                .map(ranking -> withMine(ranking, ranking.userId().equals(userId)))
                .toList();
    }

    private CurrentSeasonTopRankingsResponse buildEmptyCurrentTopRankingsResponse() {
        Long seasonId = seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)
                .map(Season::getId)
                .orElse(null);
        return new CurrentSeasonTopRankingsResponse(seasonId, List.of(), null);
    }

    private RankingView toRankingView(SeasonRankingRecord record, Integer bankruptcyDay, Integer userId) {
        CurrentSeasonRankingItemResponse item = new CurrentSeasonRankingItemResponse(
                Boolean.TRUE.equals(record.getIsBankruptcy()) ? null : record.getFinalRank(),
                record.getStore().getUser().getId(),
                record.getStore().getUser().getNickname(),
                record.getStore().getStoreName(),
                record.getStore().getLocation().getLocationName(),
                record.getStore().getMenu().getMenuName(),
                normalizeRoi(record.getRoi()),
                valueOf(record.getTotalRevenue()),
                record.getRewardPoints(),
                record.getIsBankruptcy(),
                record.getStore().getUser().getId().equals(userId)
        );
        return new RankingView(
                record.getStore().getId(),
                item.userId(),
                Boolean.TRUE.equals(record.getIsBankruptcy()),
                bankruptcyDay,
                item.rank(),
                item
        );
    }

    private List<CurrentSeasonRankingItemResponse> extractDisplayRankings(List<RankingView> allRankings) {
        List<CurrentSeasonRankingItemResponse> topRankings = allRankings.stream()
                .filter(ranking -> !ranking.bankrupt())
                .map(RankingView::item)
                .filter(ranking -> ranking.rank() != null && ranking.rank() <= 10)
                .toList();

        if (allRankings.size() >= 10) {
            return topRankings;
        }

        List<CurrentSeasonRankingItemResponse> bankruptRankings = allRankings.stream()
                .filter(RankingView::bankrupt)
                .map(RankingView::item)
                .toList();

        if (bankruptRankings.isEmpty()) {
            return topRankings;
        }

        return java.util.stream.Stream.concat(topRankings.stream(), bankruptRankings.stream())
                .toList();
    }

    private List<CurrentSeasonRankingItemResponse> extractMyRankings(
            Integer userId,
            List<RankingView> allRankings
    ) {
        Set<Long> displayedStoreIds = resolveDisplayedStoreIds(allRankings);

        return allRankings.stream()
                .filter(ranking -> ranking.userId().equals(userId))
                .filter(ranking -> !displayedStoreIds.contains(ranking.storeId()))
                .map(RankingView::item)
                .toList();
    }

    private Set<Long> resolveDisplayedStoreIds(List<RankingView> allRankings) {
        if (allRankings.size() < 10) {
            return allRankings.stream()
                    .map(RankingView::storeId)
                    .collect(java.util.stream.Collectors.toSet());
        }

        Set<Long> displayedStoreIds = new HashSet<>();
        for (RankingView ranking : allRankings) {
            if (!ranking.bankrupt() && ranking.rank() != null && ranking.rank() <= 10) {
                displayedStoreIds.add(ranking.storeId());
            }
        }
        return displayedStoreIds;
    }

    private CurrentSeasonTopRankingItemResponse withMine(CurrentSeasonTopRankingItemResponse ranking, boolean isMine) {
        return new CurrentSeasonTopRankingItemResponse(
                ranking.rank(),
                ranking.userId(),
                ranking.nickname(),
                ranking.storeName(),
                ranking.roi(),
                ranking.totalRevenue(),
                ranking.rewardPoints(),
                isMine
        );
    }

    private Comparator<RankingView> rankingViewComparator() {
        return (left, right) -> {
            if (left.bankrupt() != right.bankrupt()) {
                return left.bankrupt() ? 1 : -1;
            }
            if (!left.bankrupt()) {
                int rankCompare = Comparator.nullsLast(Integer::compareTo).compare(left.rank(), right.rank());
                if (rankCompare != 0) {
                    return rankCompare;
                }
                int userCompare = left.userId().compareTo(right.userId());
                if (userCompare != 0) {
                    return userCompare;
                }
                return left.storeId().compareTo(right.storeId());
            }

            int bankruptcyDayCompare = Comparator.nullsLast(Integer::compareTo)
                    .compare(left.bankruptcyDay(), right.bankruptcyDay());
            if (bankruptcyDayCompare != 0) {
                return bankruptcyDayCompare;
            }
            return left.storeId().compareTo(right.storeId());
        };
    }

    private Map<Long, Integer> bankruptcyDays(Season season) {
        int dayUpperBound = season.resolveRuntimePlayableDays() <= 0
                ? Integer.MAX_VALUE
                : season.resolveRuntimePlayableDays() + 1;
        Map<Long, Integer> bankruptcyDays = new HashMap<>();
        List<DailyReport> reports = dailyReportRepository
                .findByStore_Season_IdAndDayLessThanOrderByStore_IdAscDayAsc(season.getId(), dayUpperBound);
        for (DailyReport report : reports) {
            if (!Boolean.TRUE.equals(report.getIsBankrupt())) {
                continue;
            }
            bankruptcyDays.putIfAbsent(report.getStore().getId(), report.getDay());
        }
        return bankruptcyDays;
    }

    private BigDecimal normalizeRoi(Float roi) {
        if (roi == null) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(roi.doubleValue()).setScale(1, RoundingMode.HALF_UP);
    }

    private long valueOf(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private record RankingView(
            Long storeId,
            Integer userId,
            boolean bankrupt,
            Integer bankruptcyDay,
            Integer rank,
            CurrentSeasonRankingItemResponse item
    ) {
    }
}
