package com.ssafy.S14P21A205.game.environment.repository;

import com.ssafy.S14P21A205.config.RedisTtlProperties;
import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.environment.entity.WeatherType;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class SeasonWeatherRedisRepository {

    private static final String WEATHER_SCHEDULE_KEY_PATTERN = "season:%d:weather_schedule";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration seasonDataTtl;

    @Autowired
    public SeasonWeatherRedisRepository(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            RedisTtlProperties redisTtlProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.seasonDataTtl = redisTtlProperties.seasonData();
    }

    public SeasonWeatherRedisRepository(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this(stringRedisTemplate, objectMapper, RedisTtlProperties.defaults());
    }

    public Optional<List<SeasonWeatherEntry>> findSchedule(Long seasonId) {
        String payload = stringRedisTemplate.opsForValue().get(buildKey(seasonId));
        if (!StringUtils.hasText(payload)) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(payload, new TypeReference<>() {
            }));
        } catch (Exception e) {
            throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    public Optional<SeasonWeatherEntry> findDay(Long seasonId, int day) {
        return findSchedule(seasonId)
                .flatMap(schedule -> schedule.stream()
                        .filter(entry -> entry.day() != null && entry.day() == day)
                        .findFirst());
    }

    public void saveSchedule(Long seasonId, List<SeasonWeatherEntry> schedule) {
        try {
            stringRedisTemplate.opsForValue().set(
                    buildKey(seasonId),
                    objectMapper.writeValueAsString(schedule),
                    seasonDataTtl
            );
        } catch (Exception e) {
            throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    private String buildKey(Long seasonId) {
        return WEATHER_SCHEDULE_KEY_PATTERN.formatted(seasonId);
    }

    public record SeasonWeatherEntry(
            Integer day,
            WeatherType weatherType,
            BigDecimal populationMultiplier
    ) {
    }
}
