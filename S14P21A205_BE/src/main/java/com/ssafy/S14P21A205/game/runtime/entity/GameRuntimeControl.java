package com.ssafy.S14P21A205.game.runtime.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "game_runtime_control")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameRuntimeControl {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "runtime_control_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "paused", nullable = false)
    private boolean paused;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "accumulated_pause_millis", nullable = false)
    private long accumulatedPauseMillis;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private GameRuntimeControl(Long id, boolean paused, LocalDateTime pausedAt, long accumulatedPauseMillis) {
        this.id = id;
        this.paused = paused;
        this.pausedAt = pausedAt;
        this.accumulatedPauseMillis = accumulatedPauseMillis;
    }

    public static GameRuntimeControl createDefault() {
        return new GameRuntimeControl(SINGLETON_ID, false, null, 0L);
    }

    public boolean pause(LocalDateTime pausedAt) {
        if (this.paused) {
            return false;
        }
        this.paused = true;
        this.pausedAt = pausedAt;
        return true;
    }

    public boolean resume(LocalDateTime resumedAt) {
        if (!this.paused || this.pausedAt == null) {
            return false;
        }

        long elapsedPauseMillis = Math.max(0L, Duration.between(this.pausedAt, resumedAt).toMillis());
        this.accumulatedPauseMillis += elapsedPauseMillis;
        this.paused = false;
        this.pausedAt = null;
        return true;
    }
}
