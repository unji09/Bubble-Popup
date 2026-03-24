package com.ssafy.S14P21A205.game.event.entity;

import com.ssafy.S14P21A205.game.season.entity.Season;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "daily_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_daily_event_season_day_event", columnNames = {"season_id", "day", "event_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "season_event_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private RandomEvent event;

    @Column(nullable = false)
    private Integer day;

    @Column(name = "apply_offset_seconds")
    private Integer applyOffsetSeconds;

    @Column(name = "expire_offset_seconds")
    private Integer expireOffsetSeconds;
    @Column(name = "target_location_id")
    private Long targetLocationId;

    @Column(name = "target_menu_id")
    private Long targetMenuId;

    public static DailyEvent create(
            Season season,
            RandomEvent event,
            Integer day,
            Integer applyOffsetSeconds,
            Integer expireOffsetSeconds,
            Long targetLocationId,
            Long targetMenuId
    ) {
        DailyEvent dailyEvent = new DailyEvent();
        dailyEvent.season = season;
        dailyEvent.event = event;
        dailyEvent.day = day;
        dailyEvent.applyOffsetSeconds = applyOffsetSeconds;
        dailyEvent.expireOffsetSeconds = expireOffsetSeconds;
        dailyEvent.targetLocationId = targetLocationId;
        dailyEvent.targetMenuId = targetMenuId;
        return dailyEvent;
    }
}
