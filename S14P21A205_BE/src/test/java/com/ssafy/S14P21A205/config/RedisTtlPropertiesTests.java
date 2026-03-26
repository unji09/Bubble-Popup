package com.ssafy.S14P21A205.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RedisTtlPropertiesTests {

    @Test
    void defaultsMatchApplicationFallbackValues() {
        RedisTtlProperties properties = RedisTtlProperties.defaults();

        assertThat(properties.gameDay()).isEqualTo(Duration.ofHours(2));
        assertThat(properties.seasonData()).isEqualTo(Duration.ofHours(2));
        assertThat(properties.seasonRanking()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void constructorRejectsNonPositiveSeasonRankingTtl() {
        assertThatThrownBy(() -> new RedisTtlProperties(
                Duration.ofHours(2),
                Duration.ofHours(2),
                Duration.ZERO
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.redis.ttl.season-ranking must be positive");
    }
}
