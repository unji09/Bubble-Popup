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
public class WeatherDayRedisRepository {

    private static final String WEATHER_DAY_KEY_PATTERN = "season:%d:weather:day:%d";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration seasonDataTtl;

    @Autowired
    public WeatherDayRedisRepository(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            RedisTtlProperties redisTtlProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.seasonDataTtl = redisTtlProperties.seasonData();
    }

    public WeatherDayRedisRepository(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this(stringRedisTemplate, objectMapper, RedisTtlProperties.defaults());
    }

    public Optional<List<WeatherDayEntry>> findDay(Long seasonId, int day) {
        String payload = stringRedisTemplate.opsForValue().get(buildKey(seasonId, day));
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

    public Optional<WeatherDayEntry> findLocation(Long seasonId, Long locationId, int day) {
        return findDay(seasonId, day)
                .flatMap(entries -> entries.stream()
                        .filter(entry -> locationId.equals(entry.locationId()))
                        .findFirst());
    }

    public void saveDay(Long seasonId, int day, List<WeatherDayEntry> entries) {
        try {
            stringRedisTemplate.opsForValue().set(
                    buildKey(seasonId, day),
                    objectMapper.writeValueAsString(entries),
                    seasonDataTtl
            );
        } catch (Exception e) {
            throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    private String buildKey(Long seasonId, int day) {
        return WEATHER_DAY_KEY_PATTERN.formatted(seasonId, day);
    }

    public record WeatherDayEntry(
            Long locationId,
            Integer day,
            WeatherType weatherType,
            BigDecimal populationMultiplier
    ) {
    }
}
