package com.ssafy.S14P21A205.game.runtime.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class GameRuntimeClockSupport {

    private final Clock baseClock;
    private final GameRuntimeControlStateHolder stateHolder;

    public GameRuntimeClockSupport(
            @Qualifier("baseClock") Clock baseClock,
            GameRuntimeControlStateHolder stateHolder
    ) {
        this.baseClock = baseClock;
        this.stateHolder = stateHolder;
    }

    public boolean isPaused() {
        return stateHolder.isPaused();
    }

    public Instant realNow() {
        return baseClock.instant();
    }

    public LocalDateTime realNowDateTime() {
        return LocalDateTime.now(baseClock);
    }

    public Instant toSchedulingInstant(LocalDateTime effectiveDateTime) {
        GameRuntimeControlStateHolder.RuntimeControlState state = stateHolder.currentState();
        return effectiveDateTime
                .atZone(baseClock.getZone())
                .toInstant()
                .plusMillis(state.accumulatedPauseMillis());
    }
}
