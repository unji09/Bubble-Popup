package com.ssafy.S14P21A205.game.day.resolver;

import com.ssafy.S14P21A205.game.day.dto.GameDayStartResponse;
import com.ssafy.S14P21A205.game.event.entity.DailyEvent;
import com.ssafy.S14P21A205.game.event.entity.EventStartTime;
import com.ssafy.S14P21A205.game.event.entity.RandomEvent;
import com.ssafy.S14P21A205.game.event.repository.DailyEventRepository;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventScheduleResolver {

    private final DailyEventRepository dailyEventRepository;
    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();

    public List<GameDayStartResponse.EventSchedule> resolve(Long seasonId, int day, Long locationId, Long menuId) {
        return resolveInternal(seasonId, day, locationId, menuId, false);
    }

    public List<GameDayStartResponse.EventSchedule> resolveAll(Long seasonId, int day) {
        List<DailyEvent> dailyEvents = dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(
                seasonId,
                day,
                day
        );
        List<ResolvedSchedule> resolvedSchedules = new ArrayList<>();
        Set<EventDedupKey> resolvedEventKeys = new HashSet<>();
        for (DailyEvent dailyEvent : dailyEvents) {
            EventDedupKey eventKey = EventDedupKey.from(dailyEvent, dailyEvent.getDay());
            if (!resolvedEventKeys.add(eventKey)) {
                log.warn(
                        "Duplicate event schedule suppressed. seasonId={} day={} eventCategory={} targetLocationId={} targetMenuId={}",
                        seasonId,
                        day,
                        eventKey.eventCategory(),
                        eventKey.targetLocationId(),
                        eventKey.targetMenuId()
                );
                continue;
            }
            resolvedSchedules.add(new ResolvedSchedule(
                    dailyEvent.getApplyOffsetSeconds() == null ? 0 : dailyEvent.getApplyOffsetSeconds(),
                    toEventSchedule(dailyEvent)
            ));
        }
        return resolvedSchedules.stream()
                .sorted(Comparator.comparingInt(ResolvedSchedule::offsetSeconds))
                .map(ResolvedSchedule::eventSchedule)
                .toList();
    }

    private List<GameDayStartResponse.EventSchedule> resolveInternal(
            Long seasonId,
            int day,
            Long locationId,
            Long menuId,
            boolean includeAllScopes
    ) {
        int startDay = Math.max(1, day - 1);
        List<DailyEvent> dailyEvents = dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(seasonId, startDay, day);
        List<ResolvedSchedule> resolvedSchedules = new ArrayList<>();
        Set<EventDedupKey> resolvedEventKeys = new HashSet<>();
        for (DailyEvent dailyEvent : dailyEvents) {
            if (!includeAllScopes && !matchesScope(dailyEvent, locationId, menuId)) {
                continue;
            }
            int appliedDay = resolveAppliedDay(dailyEvent);
            if (appliedDay != day) {
                continue;
            }
            EventDedupKey eventKey = EventDedupKey.from(dailyEvent, appliedDay);
            if (!resolvedEventKeys.add(eventKey)) {
                log.warn(
                        "Duplicate event schedule suppressed. seasonId={} day={} eventCategory={} targetLocationId={} targetMenuId={}",
                        seasonId,
                        appliedDay,
                        eventKey.eventCategory(),
                        eventKey.targetLocationId(),
                        eventKey.targetMenuId()
                );
                continue;
            }
            RandomEvent event = dailyEvent.getEvent();
            resolvedSchedules.add(new ResolvedSchedule(
                    dailyEvent.getApplyOffsetSeconds() == null ? 0 : dailyEvent.getApplyOffsetSeconds(),
                    toEventSchedule(dailyEvent)
            ));
        }
        return resolvedSchedules.stream()
                .sorted(Comparator.comparingInt(ResolvedSchedule::offsetSeconds))
                .map(ResolvedSchedule::eventSchedule)
                .toList();
    }

    private GameDayStartResponse.EventSchedule toEventSchedule(DailyEvent dailyEvent) {
        RandomEvent event = dailyEvent.getEvent();
        Integer balanceChange = event.getCapitalFlat() == null || event.getCapitalFlat() == 0
                ? null
                : event.getCapitalFlat();
        String category = event.getEventCategory() == null
                ? null
                : event.getEventCategory().name();
        return new GameDayStartResponse.EventSchedule(
                seasonTimelineService.formatGameTime(dailyEvent.getApplyOffsetSeconds()),
                event.getEventName(),
                resolveScope(dailyEvent),
                event.getEventName(),
                normalizeScale(event.getPopulationRate()),
                balanceChange,
                category
        );
    }

    private boolean matchesScope(DailyEvent dailyEvent, Long locationId, Long menuId) {
        if (dailyEvent.getTargetLocationId() != null && !dailyEvent.getTargetLocationId().equals(locationId)) {
            return false;
        }
        return dailyEvent.getTargetMenuId() == null || dailyEvent.getTargetMenuId().equals(menuId);
    }

    private GameDayStartResponse.Scope resolveScope(DailyEvent dailyEvent) {
        if (dailyEvent.getTargetLocationId() == null && dailyEvent.getTargetMenuId() == null) {
            return null;
        }
        return new GameDayStartResponse.Scope(dailyEvent.getTargetLocationId(), dailyEvent.getTargetMenuId());
    }

    private int resolveAppliedDay(DailyEvent dailyEvent) {
        return dailyEvent.getEvent().getStartTime() == EventStartTime.NEXT_DAY
                ? dailyEvent.getDay() + 1
                : dailyEvent.getDay();
    }

    private BigDecimal normalizeScale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record ResolvedSchedule(
            int offsetSeconds,
            GameDayStartResponse.EventSchedule eventSchedule
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
