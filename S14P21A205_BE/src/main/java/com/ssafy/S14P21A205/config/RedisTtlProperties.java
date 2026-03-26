package com.ssafy.S14P21A205.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.Assert;

@ConfigurationProperties(prefix = "app.redis.ttl")
public record RedisTtlProperties(
        @DefaultValue("PT2H") Duration gameDay,
        @DefaultValue("PT2H") Duration seasonData,
        @DefaultValue("PT15M") Duration seasonRanking
) {

    public RedisTtlProperties {
        Assert.notNull(gameDay, "app.redis.ttl.game-day must not be null");
        Assert.notNull(seasonData, "app.redis.ttl.season-data must not be null");
        Assert.notNull(seasonRanking, "app.redis.ttl.season-ranking must not be null");
        Assert.isTrue(!gameDay.isNegative() && !gameDay.isZero(), "app.redis.ttl.game-day must be positive");
        Assert.isTrue(!seasonData.isNegative() && !seasonData.isZero(), "app.redis.ttl.season-data must be positive");
        Assert.isTrue(
                !seasonRanking.isNegative() && !seasonRanking.isZero(),
                "app.redis.ttl.season-ranking must be positive"
        );
    }

    public static RedisTtlProperties defaults() {
        return new RedisTtlProperties(Duration.ofHours(2), Duration.ofHours(2), Duration.ofMinutes(15));
    }
}
