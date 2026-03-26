package com.ssafy.S14P21A205.game.day.scheduler;

import com.ssafy.S14P21A205.game.day.service.SeasonDayClosingService;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.time.model.DayWindow;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SeasonDayClosingScheduler {

    private final TaskScheduler taskScheduler;
    private final SeasonRepository seasonRepository;
    private final SeasonDayClosingService seasonDayClosingService;
    private final Clock clock;

    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();
    private final Map<Long, SeasonScheduleState> scheduleStates = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void initializeCurrentSeasonSchedule() {
        seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)
                .ifPresent(this::synchronize);
    }

    public void synchronize(Season season) {
        if (season == null || season.getId() == null || season.getStatus() != SeasonStatus.IN_PROGRESS) {
            return;
        }
        if (season.getStartTime() == null || season.getTotalDays() == null || season.getTotalDays() <= 0) {
            clear(season == null ? null : season.getId());
            return;
        }

        SeasonScheduleSignature signature = new SeasonScheduleSignature(
                season.getStartTime(),
                season.resolveRuntimePlayableDays(),
                season.getStatus()
        );
        SeasonScheduleState existingState = scheduleStates.get(season.getId());
        if (existingState != null && existingState.signature().equals(signature)) {
            return;
        }

        clear(season.getId());

        Map<Integer, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
        LocalDateTime now = LocalDateTime.now(clock);
        int runtimePlayableDays = season.resolveRuntimePlayableDays();
        for (int day = 1; day <= runtimePlayableDays; day++) {
            DayWindow dayWindow = seasonTimelineService.day(season, day);
            LocalDateTime businessEndAt = dayWindow.businessEnd();

            if (!now.isBefore(businessEndAt)) {
                try {
                    seasonDayClosingService.handleBusinessEnd(season.getId(), day);
                } catch (Exception e) {
                    log.error("Day closing catch-up failed. seasonId={} day={}", season.getId(), day, e);
                }
                continue;
            }

            final int targetDay = day;
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> {
                        try {
                            seasonDayClosingService.handleBusinessEnd(season.getId(), targetDay);
                        } catch (Exception e) {
                            log.error("Day closing failed. seasonId={} day={}", season.getId(), targetDay, e);
                        }
                    },
                    businessEndAt.atZone(clock.getZone()).toInstant()
            );
            if (future != null) {
                futures.put(day, future);
            }
        }

        scheduleStates.put(season.getId(), new SeasonScheduleState(signature, futures));
        log.info("Synchronized day closing schedule. seasonId={} scheduledDays={}", season.getId(), futures.keySet());
    }

    public void clear(Long seasonId) {
        if (seasonId == null) {
            return;
        }
        SeasonScheduleState state = scheduleStates.remove(seasonId);
        if (state == null) {
            return;
        }
        state.futures().values().forEach(future -> future.cancel(false));
    }

    private record SeasonScheduleSignature(
            LocalDateTime startTime,
            Integer runtimePlayableDays,
            SeasonStatus status
    ) {
    }

    private record SeasonScheduleState(
            SeasonScheduleSignature signature,
            Map<Integer, ScheduledFuture<?>> futures
    ) {
    }
}
