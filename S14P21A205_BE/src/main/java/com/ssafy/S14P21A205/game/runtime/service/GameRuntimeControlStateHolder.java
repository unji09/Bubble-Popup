package com.ssafy.S14P21A205.game.runtime.service;

import com.ssafy.S14P21A205.game.runtime.entity.GameRuntimeControl;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class GameRuntimeControlStateHolder {

    private volatile RuntimeControlState currentState = RuntimeControlState.defaultState();

    public RuntimeControlState currentState() {
        return currentState;
    }

    public boolean isPaused() {
        return currentState.paused();
    }

    public void update(GameRuntimeControl control) {
        if (control == null) {
            this.currentState = RuntimeControlState.defaultState();
            return;
        }
        this.currentState = new RuntimeControlState(
                control.isPaused(),
                control.getPausedAt(),
                control.getAccumulatedPauseMillis()
        );
    }

    public record RuntimeControlState(
            boolean paused,
            LocalDateTime pausedAt,
            long accumulatedPauseMillis
    ) {
        public static RuntimeControlState defaultState() {
            return new RuntimeControlState(false, null, 0L);
        }
    }
}
