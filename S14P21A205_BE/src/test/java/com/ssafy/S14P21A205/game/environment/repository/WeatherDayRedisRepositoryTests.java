package com.ssafy.S14P21A205.game.environment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.config.RedisTtlProperties;
import com.ssafy.S14P21A205.game.environment.entity.WeatherType;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WeatherDayRedisRepositoryTests {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private WeatherDayRedisRepository repository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        repository = new WeatherDayRedisRepository(
                stringRedisTemplate,
                objectMapper,
                new RedisTtlProperties(Duration.ofHours(3), Duration.ofMinutes(45), Duration.ofMinutes(10))
        );
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void saveDayStoresPayloadWithSeasonDataTtl() throws Exception {
        List<WeatherDayRedisRepository.WeatherDayEntry> entries = List.of(
                new WeatherDayRedisRepository.WeatherDayEntry(3L, 2, WeatherType.SUNNY, BigDecimal.ONE)
        );

        repository.saveDay(9L, 2, entries);

        verify(valueOperations).set(
                "season:9:weather:day:2",
                objectMapper.writeValueAsString(entries),
                Duration.ofMinutes(45)
        );
    }

    @Test
    void findLocationReturnsMatchingEntryFromCachedDay() throws Exception {
        List<WeatherDayRedisRepository.WeatherDayEntry> entries = List.of(
                new WeatherDayRedisRepository.WeatherDayEntry(3L, 2, WeatherType.SUNNY, BigDecimal.ONE),
                new WeatherDayRedisRepository.WeatherDayEntry(8L, 2, WeatherType.RAIN, new BigDecimal("0.75"))
        );
        when(valueOperations.get("season:9:weather:day:2"))
                .thenReturn(objectMapper.writeValueAsString(entries));

        var found = repository.findLocation(9L, 8L, 2);

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().weatherType()).isEqualTo(WeatherType.RAIN);
    }
}
