package com.ssafy.S14P21A205.game.season.repository;

import com.ssafy.S14P21A205.config.RedisTtlProperties;
import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTopRankingsResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Repository
public class SeasonRankingRedisRepository {

    // 현재 시즌 실시간 TOP 랭킹을 저장하는 Redis Key
    private static final String CURRENT_TOP_RANKING_KEY = "game:season:rankings:current:top";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration seasonRankingTtl;

    @Autowired
    public SeasonRankingRedisRepository(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            RedisTtlProperties redisTtlProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.seasonRankingTtl = redisTtlProperties.seasonRanking();
    }

    public SeasonRankingRedisRepository(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this(stringRedisTemplate, objectMapper, RedisTtlProperties.defaults());
    }

    public void saveCurrentTopRankings(CurrentSeasonTopRankingsResponse rankings) {
        try {
            stringRedisTemplate.opsForValue().set(
                    CURRENT_TOP_RANKING_KEY,
                    objectMapper.writeValueAsString(rankings),
                    seasonRankingTtl
            );
        } catch (Exception e) {
            throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    public Optional<CurrentSeasonTopRankingsResponse> findCurrentTopRankings() {
        String payload = stringRedisTemplate.opsForValue().get(CURRENT_TOP_RANKING_KEY);
        if (!StringUtils.hasText(payload)) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(payload, CurrentSeasonTopRankingsResponse.class));
        } catch (Exception e) {
            throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    public void deleteCurrentTopRankings() {
        stringRedisTemplate.delete(CURRENT_TOP_RANKING_KEY);
    }
}
