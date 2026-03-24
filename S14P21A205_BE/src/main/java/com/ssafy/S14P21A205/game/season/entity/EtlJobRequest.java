package com.ssafy.S14P21A205.game.season.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "etl_job_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EtlJobRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "season_id", nullable = false, unique = true)
    private Long seasonId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EtlJobStatus status;

    @Column(name = "selected_start_date")
    private LocalDate selectedStartDate;

    @Column(name = "source_batch_key", length = 64)
    private String sourceBatchKey;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private EtlJobRequest(Long seasonId, LocalDateTime requestedAt) {
        this.seasonId = seasonId;
        this.status = EtlJobStatus.PENDING;
        this.requestedAt = requestedAt;
    }

    public static EtlJobRequest createPending(Long seasonId, LocalDateTime requestedAt) {
        return new EtlJobRequest(seasonId, requestedAt);
    }

    public boolean isSucceeded() {
        return status == EtlJobStatus.SUCCEEDED;
    }
}
