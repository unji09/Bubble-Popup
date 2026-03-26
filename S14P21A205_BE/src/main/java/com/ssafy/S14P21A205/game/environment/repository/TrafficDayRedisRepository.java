package com.ssafy.S14P21A205.game.environment.repository;

import com.ssafy.S14P21A205.config.RedisTtlProperties;
import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.environment.entity.TrafficStatus;
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
public class TrafficDayRedisRepository {

    private static final String TRAFFIC_DAY_KEY_PATTERN = "season:%d:traffic:location:%d:day:%d";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration seasonDataTtl;

    @Autowired
    public TrafficDayRedisRepository(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            RedisTtlProperties redisTtlProperties
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.seasonDataTtl = redisTtlProperties.seasonData();
    }

    public TrafficDayRedisRepository(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this(stringRedisTemplate, objectMapper, RedisTtlProperties.defaults());
    }

    public Optional<List<TrafficEntry>> findDay(Long seasonId, Long locationId, int day) {
        String payload = stringRedisTemplate.opsForValue().get(buildKey(seasonId, locationId, day));
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

    public Optional<TrafficEntry> findHour(Long seasonId, Long locationId, int day, int hour) {
        return findDay(seasonId, locationId, day)
                .flatMap(entries -> entries.stream()
                        .filter(entry -> entry.hour() == hour)
                        .findFirst());
    }

    public void saveDay(Long seasonId, Long locationId, int day, List<TrafficEntry> entries) {
        try {
            stringRedisTemplate.opsForValue().set(
                    buildKey(seasonId, locationId, day),
                    objectMapper.writeValueAsString(entries),
                    seasonDataTtl
            );
        } catch (Exception e) {
            throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    private String buildKey(Long seasonId, Long locationId, int day) {
        return TRAFFIC_DAY_KEY_PATTERN.formatted(seasonId, locationId, day);
    }

    public record TrafficEntry(
            Integer hour,
            TrafficStatus trafficStatus
    ) {
    }
}
