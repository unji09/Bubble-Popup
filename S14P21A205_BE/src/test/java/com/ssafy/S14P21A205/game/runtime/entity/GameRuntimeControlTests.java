package com.ssafy.S14P21A205.game.runtime.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GameRuntimeControlTests {

    @Test
    void pauseAndResumeAccumulatesPauseDuration() {
        GameRuntimeControl control = GameRuntimeControl.createDefault();
        LocalDateTime pausedAt = LocalDateTime.of(2026, 3, 28, 12, 0, 0);
        LocalDateTime resumedAt = pausedAt.plusMinutes(5);

        assertThat(control.pause(pausedAt)).isTrue();
        assertThat(control.isPaused()).isTrue();
        assertThat(control.resume(resumedAt)).isTrue();

        assertThat(control.isPaused()).isFalse();
        assertThat(control.getPausedAt()).isNull();
        assertThat(control.getAccumulatedPauseMillis()).isEqualTo(300_000L);
    }

    @Test
    void repeatedPauseOrResumeIsIdempotent() {
        GameRuntimeControl control = GameRuntimeControl.createDefault();
        LocalDateTime now = LocalDateTime.of(2026, 3, 28, 12, 0, 0);

        assertThat(control.resume(now)).isFalse();
        assertThat(control.pause(now)).isTrue();
        assertThat(control.pause(now.plusMinutes(1))).isFalse();
    }
}
