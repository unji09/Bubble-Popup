package com.ssafy.S14P21A205.game.season.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "season")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "season_id", nullable = false, updatable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeasonStatus status;

    @Column(name = "current_day")
    private Integer currentDay;

    @Column(name = "source_batch_key", length = 64)
    private String sourceBatchKey;

    @Column(name = "total_days", nullable = false)
    private Integer totalDays;

    @Column(name = "demo_playable_days")
    private Integer demoPlayableDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "demo_skip_status", nullable = false, length = 20)
    private DemoSkipStatus demoSkipStatus;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Season(SeasonStatus status, Integer currentDay, Integer totalDays, LocalDateTime startTime, LocalDateTime endTime) {
        this.status = status;
        this.currentDay = currentDay;
        this.totalDays = totalDays;
        this.demoPlayableDays = null;
        this.demoSkipStatus = DemoSkipStatus.NONE;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static Season createScheduled(int totalDays, LocalDateTime startTime, LocalDateTime endTime) {
        return new Season(SeasonStatus.SCHEDULED, 1, totalDays, startTime, endTime);
    }

    public void start() {
        start(null);
    }

    public void start(String sourceBatchKey) {
        startAt(null, sourceBatchKey);
    }

    public void startAt(LocalDateTime actualStartTime, String sourceBatchKey) {
        this.status = SeasonStatus.IN_PROGRESS;
        this.currentDay = 1;
        this.sourceBatchKey = sourceBatchKey;
        if (actualStartTime != null) {
            this.startTime = actualStartTime;
        }
    }

    public void reserveDemoSkip(int playableDays) {
        this.demoPlayableDays = playableDays;
        this.demoSkipStatus = DemoSkipStatus.RESERVED;
    }

    public void applyReservedDemoSkip() {
        if (demoSkipStatus == DemoSkipStatus.RESERVED) {
            demoSkipStatus = DemoSkipStatus.APPLIED;
        }
    }

    public boolean isDemoSkipReserved() {
        return demoSkipStatus == DemoSkipStatus.RESERVED;
    }

    public int resolveRuntimePlayableDays() {
        if (demoSkipStatus == DemoSkipStatus.APPLIED
                && demoPlayableDays != null
                && demoPlayableDays > 0) {
            return demoPlayableDays;
        }
        return totalDays == null ? 0 : totalDays;
    }

    public void advanceToDay(int nextDay) {
        this.currentDay = nextDay;
    }

    public void finish() {
        this.status = SeasonStatus.FINISHED;
        int runtimePlayableDays = resolveRuntimePlayableDays();
        if (runtimePlayableDays > 0) {
            this.currentDay = runtimePlayableDays;
        } else if (totalDays != null) {
            this.currentDay = totalDays;
        }
    }

    public void syncCurrentDay(Integer currentDay) {
        if (currentDay == null) {
            return;
        }
        this.currentDay = currentDay;
    }

    public void updateEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
