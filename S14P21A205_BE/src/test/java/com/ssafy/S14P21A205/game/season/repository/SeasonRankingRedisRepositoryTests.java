package com.ssafy.S14P21A205.game.season.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.config.RedisTtlProperties;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTopRankingItemResponse;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTopRankingsResponse;
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
class SeasonRankingRedisRepositoryTests {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private SeasonRankingRedisRepository repository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        repository = new SeasonRankingRedisRepository(
                stringRedisTemplate,
                objectMapper,
                new RedisTtlProperties(Duration.ofHours(2), Duration.ofHours(2), Duration.ofMinutes(3))
        );
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void saveCurrentTopRankingsStoresPayloadWithRankingTtl() throws Exception {
        CurrentSeasonTopRankingsResponse response = new CurrentSeasonTopRankingsResponse(
                5L,
                List.of(new CurrentSeasonTopRankingItemResponse(
                        1,
                        99,
                        "nick",
                        "store",
                        new BigDecimal("12.5"),
                        120000L,
                        30,
                        false
                )),
                "2026-03-26T10:00:00"
        );

        repository.saveCurrentTopRankings(response);

        verify(valueOperations).set(
                "game:season:rankings:current:top",
                objectMapper.writeValueAsString(response),
                Duration.ofMinutes(3)
        );
    }

    @Test
    void findCurrentTopRankingsDeserializesCachedPayload() throws Exception {
        CurrentSeasonTopRankingsResponse response = new CurrentSeasonTopRankingsResponse(
                5L,
                List.of(new CurrentSeasonTopRankingItemResponse(
                        1,
                        99,
                        "nick",
                        "store",
                        new BigDecimal("12.5"),
                        120000L,
                        30,
                        false
                )),
                "2026-03-26T10:00:00"
        );
        when(valueOperations.get("game:season:rankings:current:top"))
                .thenReturn(objectMapper.writeValueAsString(response));

        var found = repository.findCurrentTopRankings();

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().seasonId()).isEqualTo(5L);
        assertThat(found.orElseThrow().rankings()).hasSize(1);
    }
}
