package com.ssafy.S14P21A205.game.day.resolver;

import com.ssafy.S14P21A205.game.day.dto.GameStateResponse;
import com.ssafy.S14P21A205.game.event.entity.DailyEvent;
import com.ssafy.S14P21A205.game.event.entity.EventCategory;
import com.ssafy.S14P21A205.game.event.entity.EventStartTime;
import com.ssafy.S14P21A205.game.event.entity.RandomEvent;
import com.ssafy.S14P21A205.game.event.repository.DailyEventRepository;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventEffectResolver {

    private static final BigDecimal DECIMAL_ONE = new BigDecimal("1.00");

    private final DailyEventRepository dailyEventRepository;
    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();

    public EventEffect resolve(
            Season season,
            int currentDay,
            LocalDateTime effectiveNow,
            Long locationId,
            Long menuId
    ) {
        List<DailyEvent> dailyEvents = dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(
                season.getId(),
                1,
                currentDay
        );

        long capitalChange = 0L;
        int stockChange = 0;
        BigDecimal populationEventMultiplier = DECIMAL_ONE;
        BigDecimal ingredientCostMultiplier = DECIMAL_ONE;
        List<GameStateResponse.AppliedEvent> appliedEvents = new ArrayList<>();
        List<StockRateEvent> appliedStockRateEvents = new ArrayList<>();
        Set<EventDedupKey> resolvedEventKeys = new HashSet<>();

        for (DailyEvent dailyEvent : dailyEvents) {
            if (!matchesScope(dailyEvent, locationId, menuId)) {
                continue;
            }

            int appliedDay = resolveAppliedDay(dailyEvent);
            if (appliedDay < 1 || appliedDay > currentDay) {
                continue;
            }
            EventDedupKey eventKey = EventDedupKey.from(dailyEvent, appliedDay);
            if (!resolvedEventKeys.add(eventKey)) {
                log.warn(
                        "Duplicate event effect suppressed. seasonId={} day={} eventCategory={} targetLocationId={} targetMenuId={}",
                        season.getId(),
                        appliedDay,
                        eventKey.eventCategory(),
                        eventKey.targetLocationId(),
                        eventKey.targetMenuId()
                );
                continue;
            }

            LocalDateTime appliedAt = seasonTimelineService.resolveAppliedAt(
                    season,
                    appliedDay,
                    dailyEvent.getApplyOffsetSeconds()
            );
            if (appliedAt.isAfter(effectiveNow)) {
                continue;
            }

            LocalDateTime endedAt = seasonTimelineService.resolveEndedAt(
                    season,
                    appliedDay,
                    dailyEvent.getExpireOffsetSeconds(),
                    dailyEvent.getEvent().getEndTime()
            );
            ResolvedEvent resolvedEvent = new ResolvedEvent(dailyEvent, appliedDay, appliedAt, endedAt);
            RandomEvent event = resolvedEvent.dailyEvent().getEvent();

            if (resolvedEvent.appliedDay() == currentDay) {
                capitalChange += event.getCapitalFlat() == null ? 0L : event.getCapitalFlat();
                if (!isNaturalDisaster(event.getEventCategory())) {
                    stockChange += toWholeNumber(event.getStockFlat());
                }
            }
            if (!appliedAt.isAfter(effectiveNow) && resolveStockRate(event) != null) {
                appliedStockRateEvents.add(new StockRateEvent(
                        dailyEvent.getId(),
                        resolveStockRate(event)
                ));
            }

            if (resolvedEvent.isActiveAt(effectiveNow)) {
                populationEventMultiplier = populationEventMultiplier.multiply(normalizeRate(event.getPopulationRate()));
                ingredientCostMultiplier = ingredientCostMultiplier.multiply(normalizeRate(event.getCostRate()));
                appliedEvents.add(new GameStateResponse.AppliedEvent(
                        event.getEventCategory().name(),
                        event.getEventName(),
                        event.getEventName(),
                        resolvedEvent.appliedAt()
                ));
            }
        }

        return new EventEffect(
                capitalChange,
                stockChange,
                populationEventMultiplier,
                ingredientCostMultiplier,
                appliedEvents,
                appliedStockRateEvents
        );
    }

    private boolean matchesScope(DailyEvent dailyEvent, Long locationId, Long menuId) {
        Long targetLocationId = dailyEvent.getTargetLocationId();
        Long targetMenuId = dailyEvent.getTargetMenuId();
        if (targetLocationId != null && !targetLocationId.equals(locationId)) {
            return false;
        }
        return targetMenuId == null || targetMenuId.equals(menuId);
    }

    private int resolveAppliedDay(DailyEvent dailyEvent) {
        return dailyEvent.getEvent().getStartTime() == EventStartTime.NEXT_DAY
                ? dailyEvent.getDay() + 1
                : dailyEvent.getDay();
    }

    private int toWholeNumber(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        return value.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private BigDecimal normalizeRate(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return DECIMAL_ONE;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveStockRate(RandomEvent event) {
        if (event == null || !isNaturalDisaster(event.getEventCategory())) {
            return null;
        }
        BigDecimal value = event.getStockFlat();
        if (value == null || value.signum() <= 0) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isNaturalDisaster(EventCategory category) {
        return category == EventCategory.EARTHQUAKE
                || category == EventCategory.FLOOD
                || category == EventCategory.TYPHOON
                || category == EventCategory.FIRE;
    }

    private record ResolvedEvent(
            DailyEvent dailyEvent,
            int appliedDay,
            LocalDateTime appliedAt,
            LocalDateTime endedAt
    ) {
        private boolean isActiveAt(LocalDateTime now) {
            return endedAt == null || now.isBefore(endedAt);
        }
    }

    public record EventEffect(
            long capitalChange,
            int stockChange,
            BigDecimal populationEventMultiplier,
            BigDecimal ingredientCostMultiplier,
            List<GameStateResponse.AppliedEvent> appliedEvents,
            List<StockRateEvent> appliedStockRateEvents
    ) {
    }

    public record StockRateEvent(
            Long dailyEventId,
            BigDecimal stockRate
    ) {
    }

    private record EventDedupKey(
            int appliedDay,
            String eventCategory,
            Long targetLocationId,
            Long targetMenuId
    ) {
        private static EventDedupKey from(DailyEvent dailyEvent, int appliedDay) {
            RandomEvent event = dailyEvent.getEvent();
            return new EventDedupKey(
                    appliedDay,
                    event == null || event.getEventCategory() == null ? null : event.getEventCategory().name(),
                    dailyEvent.getTargetLocationId(),
                    dailyEvent.getTargetMenuId()
            );
        }
    }
}

