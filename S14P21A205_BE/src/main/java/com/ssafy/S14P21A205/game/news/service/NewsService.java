package com.ssafy.S14P21A205.game.news.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.news.dto.AreaRankingItemResponse;
import com.ssafy.S14P21A205.game.news.dto.MenuMentionCount;
import com.ssafy.S14P21A205.game.news.dto.NewsListResponse;
import com.ssafy.S14P21A205.game.news.dto.NewsRankingResponse;
import com.ssafy.S14P21A205.game.news.entity.NewsArticle;
import com.ssafy.S14P21A205.game.news.entity.NewsMenuMention;
import com.ssafy.S14P21A205.game.news.entity.NewsReport;
import com.ssafy.S14P21A205.game.news.repository.NewsArticleRepository;
import com.ssafy.S14P21A205.game.news.repository.NewsMenuMentionRepository;
import com.ssafy.S14P21A205.game.news.repository.NewsReportRepository;
import com.ssafy.S14P21A205.game.season.entity.EtlJobRequest;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.EtlJobRequestRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.store.entity.Store;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 뉴스 생성 오케스트레이터.
 * 준비된 ETL 결과를 사용해 DB 저장(NewsDataSaver, 트랜잭션 안)을 분리.
 */
@Service
@RequiredArgsConstructor
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NewsDataSaver newsDataSaver;
    private final NewsArticleRepository newsArticleRepository;
    private final NewsMenuMentionRepository newsMenuMentionRepository;
    private final NewsReportRepository newsReportRepository;
    private final EtlJobRequestRepository etlJobRequestRepository;
    private final SeasonRepository seasonRepository;

    public void generateSeasonNews(Long seasonId) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BaseException(ErrorCode.SEASON_NOT_FOUND));
        int totalDays = season.getTotalDays();
        EtlJobRequest etlJobRequest = etlJobRequestRepository.findBySeasonId(seasonId)
                .filter(EtlJobRequest::isSucceeded)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND, "ETL source is not ready."));
        String sourceBatchKey = etlJobRequest.getSourceBatchKey();

        log.info("[NEWS] Step 1/3: Reading menu mentions from DB for season {}", seasonId);
        Map<Integer, List<MenuMentionCount>> dayMentions = loadDayMentions(sourceBatchKey, totalDays);
        log.info("[NEWS] Step 1/3 done: got mentions for {} days", dayMentions.size());

        // DB 저장 + AI 호출 (별도 빈 → @Transactional 프록시 정상 동작)
        newsDataSaver.saveNewsData(seasonId, season, totalDays, dayMentions, sourceBatchKey);
    }

    /**
     * 영업 마감 시 당일 순위 업데이트 + 마감 뉴스 1건 생성.
     */
    public void updateDayRankings(Long seasonId, int day) {
        newsDataSaver.updateDayRankings(seasonId, day);
    }

    /**
     * Redis state에서 직접 순위 집계 + 마감 뉴스 생성 (daily_report 의존 없음).
     */
    public void updateDayRankingsFromRedis(Long seasonId, int day, List<Store> stores) {
        newsDataSaver.updateDayRankingsFromRedis(seasonId, day, stores);
    }

    /**
     * 영업 중 뉴스 생성 (메뉴 입점수 + 지역 입점수).
     */
    public void generateOpeningNews(Long seasonId, int day) {
        newsDataSaver.generateOpeningNews(seasonId, day);
    }


    //오늘의 뉴스 조회. 프론트에서 요청한 day 기준.
    public NewsListResponse getTodayNews(int day) {
        Season season = seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)
                .orElseThrow(() -> new BaseException(ErrorCode.SEASON_NOT_FOUND));
        List<NewsArticle> articles = newsArticleRepository
                .findByNewsReport_Season_IdAndDayOrderByIdAsc(season.getId(), day);
        if (articles.isEmpty()) {
            throw new BaseException(ErrorCode.NEWS_NOT_FOUND);
        }
        return NewsListResponse.of(articles);
    }

    /**
     * 지역별 매출 순위 + 유동인구 순위 조회.
     */
    public NewsRankingResponse getAreaRankings(int day) {
        Season season = findCurrentSeason();

        List<AreaRankingItemResponse> revenueRanking = limitTop3(buildRevenueRanking(season.getId(), day));
        List<AreaRankingItemResponse> trafficRanking = buildTrafficRanking(season.getId(), day);

        return new NewsRankingResponse(revenueRanking, trafficRanking);
    }

    private List<AreaRankingItemResponse> buildRevenueRanking(Long seasonId, int currentDay) {
        if (currentDay <= 1) {
            return List.of();
        }

        int targetDay = currentDay - 1;
        List<Map<String, Object>> current = parseRevenueFromReport(seasonId, targetDay);
        if (current.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> previous = targetDay >= 2
                ? parseRevenueFromReport(seasonId, targetDay - 1)
                : List.of();

        return buildRankingWithChangeRate(current, previous, "revenue");
    }

    private List<Map<String, Object>> parseRevenueFromReport(Long seasonId, int day) {
        return newsReportRepository.findBySeasonIdAndDay(seasonId, day)
                .map(NewsReport::getAreaRevenueRanking)
                .map(this::parseJsonArray)
                .orElse(List.of());
    }

    private List<AreaRankingItemResponse> buildTrafficRanking(Long seasonId, int currentDay) {
        List<Map<String, Object>> current = parseTrafficFromReport(seasonId, currentDay);
        if (current.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> previous = currentDay >= 2
                ? parseTrafficFromReport(seasonId, currentDay - 1)
                : List.of();

        return buildRankingWithChangeRate(current, previous, "avgPopulation");
    }

    private List<Map<String, Object>> parseTrafficFromReport(Long seasonId, int day) {
        return newsReportRepository.findBySeasonIdAndDay(seasonId, day)
                .map(NewsReport::getAreaPopulationRanking)
                .map(this::parseJsonArray)
                .orElse(List.of());
    }

    private List<AreaRankingItemResponse> buildRankingWithChangeRate(
            List<Map<String, Object>> current,
            List<Map<String, Object>> previous,
            String valueKey) {

        Map<String, Double> prevValues = new HashMap<>();
        for (Map<String, Object> item : previous) {
            String name = (String) item.get("name");
            double value = ((Number) item.get(valueKey)).doubleValue();
            prevValues.put(name, value);
        }

        List<AreaRankingItemResponse> result = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            String name = (String) current.get(i).get("name");
            double value = ((Number) current.get(i).get(valueKey)).doubleValue();
            Double prev = prevValues.get(name);

            double changeRate = 0.0;
            if (prev != null && prev != 0) {
                changeRate = Math.round(((value - prev) / prev) * 1000.0) / 10.0;
            }

            result.add(new AreaRankingItemResponse(i + 1, name, changeRate));
        }
        return result;
    }

    private List<AreaRankingItemResponse> limitTop3(List<AreaRankingItemResponse> list) {
        return list.size() <= 3 ? list : list.subList(0, 3);
    }

    private Season findCurrentSeason() {
        return seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)
                .or(() -> seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.SCHEDULED))
                .orElseThrow(() -> new BaseException(ErrorCode.SEASON_NOT_FOUND));
    }

    private List<Map<String, Object>> parseJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse ranking JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<Integer, List<MenuMentionCount>> loadDayMentions(String sourceBatchKey, int totalDays) {
        List<NewsMenuMention> mentions = newsMenuMentionRepository
                .findBySourceBatchKeyOrderByDayAscMentionCountDescMenuNameAsc(sourceBatchKey);

        Map<Integer, List<MenuMentionCount>> dayMentions = new LinkedHashMap<>();
        for (int day = 1; day <= totalDays; day++) {
            dayMentions.put(day, new ArrayList<>());
        }

        mentions.stream()
                .filter(mention -> mention.getDay() != null && mention.getDay() >= 1 && mention.getDay() <= totalDays)
                .sorted(Comparator
                        .comparing(NewsMenuMention::getDay)
                        .thenComparing(NewsMenuMention::getMentionCount, Comparator.reverseOrder())
                        .thenComparing(NewsMenuMention::getMenuName))
                .forEach(mention -> dayMentions.get(mention.getDay()).add(
                        new MenuMentionCount(mention.getMenuName(), mention.getMentionCount())
                ));
        return dayMentions;
    }
}
