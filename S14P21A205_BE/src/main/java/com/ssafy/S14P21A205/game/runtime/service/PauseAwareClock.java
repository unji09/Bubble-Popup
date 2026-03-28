package com.ssafy.S14P21A205.game.runtime.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class PauseAwareClock extends Clock {

    private final Clock baseClock;
    private final GameRuntimeControlStateHolder stateHolder;

    public PauseAwareClock(Clock baseClock, GameRuntimeControlStateHolder stateHolder) {
        this.baseClock = baseClock;
        this.stateHolder = stateHolder;
    }

    @Override
    public ZoneId getZone() {
        return baseClock.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new PauseAwareClock(baseClock.withZone(zone), stateHolder);
    }

    @Override
    public Instant instant() {
        GameRuntimeControlStateHolder.RuntimeControlState state = stateHolder.currentState();
        if (state.paused() && state.pausedAt() != null) {
            return state.pausedAt()
                    .atZone(baseClock.getZone())
                    .toInstant()
                    .minusMillis(state.accumulatedPauseMillis());
        }
        return baseClock.instant().minusMillis(state.accumulatedPauseMillis());
    }
}
