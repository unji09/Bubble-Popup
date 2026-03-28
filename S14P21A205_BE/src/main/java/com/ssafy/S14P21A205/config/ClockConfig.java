package com.ssafy.S14P21A205.config;

import com.ssafy.S14P21A205.game.runtime.service.GameRuntimeControlStateHolder;
import com.ssafy.S14P21A205.game.runtime.service.PauseAwareClock;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ClockConfig {

    public static final ZoneId APP_ZONE_ID = ZoneId.of("Asia/Seoul");

    @PostConstruct
    void initializeDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(APP_ZONE_ID));
    }

    @Bean("baseClock")
    public Clock baseClock() {
        return Clock.system(APP_ZONE_ID);
    }

    @Bean
    @Primary
    public Clock systemClock(
            @Qualifier("baseClock") Clock baseClock,
            GameRuntimeControlStateHolder stateHolder
    ) {
        return new PauseAwareClock(baseClock, stateHolder);
    }
}
