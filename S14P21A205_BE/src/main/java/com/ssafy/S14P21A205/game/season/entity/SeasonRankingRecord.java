package com.ssafy.S14P21A205.game.season.entity;

import com.ssafy.S14P21A205.store.entity.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Entity
@Table(
        name = "season_ranking_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_season_ranking_record_store", columnNames = {"store_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonRankingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "season_ranking_record_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "final_rank", nullable = false)
    private Integer finalRank;

    @Column(name = "total_revenue", nullable = false)
    private Integer totalRevenue;

    @Column(name = "total_cost", nullable = false)
    private Integer totalCost;

    @Column(name = "total_net_profit", nullable = false)
    private Integer totalNetProfit;

    @Column(name = "total_visitors", nullable = false)
    private Integer totalVisitors;

    @Column(nullable = false)
    private Float roi;

    @Column(name = "days_played", nullable = false)
    private Integer daysPlayed;

    @Column(name = "reward_points", nullable = false)
    private Integer rewardPoints;

    @Column(name = "is_bankruptcy", nullable = false)
    private Boolean isBankruptcy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private SeasonRankingRecord(
            Store store,
            Integer finalRank,
            Integer totalRevenue,
            Integer totalCost,
            Integer totalNetProfit,
            Integer totalVisitors,
            Float roi,
            Integer daysPlayed,
            Integer rewardPoints,
            Boolean isBankruptcy
    ) {
        this.store = store;
        this.finalRank = finalRank;
        this.totalRevenue = totalRevenue;
        this.totalCost = totalCost;
        this.totalNetProfit = totalNetProfit;
        this.totalVisitors = totalVisitors;
        this.roi = roi;
        this.daysPlayed = daysPlayed;
        this.rewardPoints = rewardPoints;
        this.isBankruptcy = isBankruptcy;
    }

    public static SeasonRankingRecord create(
            Store store,
            Integer finalRank,
            Integer totalRevenue,
            Integer totalCost,
            Integer totalNetProfit,
            Integer totalVisitors,
            Float roi,
            Integer daysPlayed,
            Integer rewardPoints,
            Boolean isBankruptcy
    ) {
        return new SeasonRankingRecord(
                store,
                finalRank,
                totalRevenue,
                totalCost,
                totalNetProfit,
                totalVisitors,
                roi,
                daysPlayed,
                rewardPoints,
                isBankruptcy
        );
    }
}
