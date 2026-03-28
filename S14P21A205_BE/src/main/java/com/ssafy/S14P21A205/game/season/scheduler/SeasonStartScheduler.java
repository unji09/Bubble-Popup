package com.ssafy.S14P21A205.game.season.scheduler;

import com.ssafy.S14P21A205.game.runtime.service.GameRuntimeClockSupport;
import com.ssafy.S14P21A205.game.runtime.service.GameRuntimeControlStateHolder;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.season.service.SeasonLifecycleService;
import com.ssafy.S14P21A205.game.season.service.SeasonLifecycleService.SeasonStartResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SeasonStartScheduler {

    private final TaskScheduler taskScheduler;
    private final SeasonRepository seasonRepository;
    private final SeasonLifecycleService seasonLifecycleService;
    private final Clock clock;
    private final GameRuntimeClockSupport gameRuntimeClockSupport;
    private final GameRuntimeControlStateHolder runtimeControlStateHolder;

    @Value("${app.game.season-start.retry-delay-ms:10000}")
    private long retryDelayMs;

    private final Map<Long, SeasonStartScheduleState> scheduleStates = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void initializeCurrentSeasonStartSchedule() {
        if (runtimeControlStateHolder.isPaused()) {
            clearAll();
            return;
        }
        synchronizeCurrentScheduledSeason();
    }

    public void synchronizeCurrentScheduledSeason() {
        if (runtimeControlStateHolder.isPaused()) {
            clearAll();
            return;
        }
        seasonRepository.findFirstByStatusOrderByStartTimeAscIdAsc(SeasonStatus.SCHEDULED)
                .ifPresentOrElse(this::synchronize, this::clearAll);
    }

    public void synchronize(Season season) {
        if (runtimeControlStateHolder.isPaused()) {
            clear(season == null ? null : season.getId());
            return;
        }
        if (season == null || season.getId() == null || season.getStatus() != SeasonStatus.SCHEDULED) {
            clear(season == null ? null : season.getId());
            return;
        }
        if (season.getStartTime() == null) {
            clear(season.getId());
            return;
        }

        SeasonStartScheduleSignature signature = signatureOf(season);
        SeasonStartScheduleState existingState = scheduleStates.get(season.getId());
        if (existingState != null && existingState.signature().equals(signature)) {
            return;
        }

        clear(season.getId());

        LocalDateTime now = LocalDateTime.now(clock);
        if (!season.getStartTime().isAfter(now)) {
            triggerStart(season.getId(), signature);
            return;
        }

        schedule(season.getId(), signature, season.getStartTime(), false);
    }

    public void clear(Long seasonId) {
        if (seasonId == null) {
            return;
        }
        SeasonStartScheduleState state = scheduleStates.remove(seasonId);
        if (state == null || state.future() == null) {
            return;
        }
        state.future().cancel(false);
    }

    private void clearAll() {
        for (Long seasonId : scheduleStates.keySet().toArray(new Long[0])) {
            clear(seasonId);
        }
    }

    private void triggerStart(Long seasonId, SeasonStartScheduleSignature expectedSignature) {
        if (runtimeControlStateHolder.isPaused()) {
            clear(seasonId);
            return;
        }
        Season scheduledSeason = seasonRepository.findByIdAndStatus(seasonId, SeasonStatus.SCHEDULED).orElse(null);
        if (scheduledSeason == null) {
            clear(seasonId);
            return;
        }

        SeasonStartScheduleSignature currentSignature = signatureOf(scheduledSeason);
        if (!currentSignature.equals(expectedSignature)) {
            synchronize(scheduledSeason);
            return;
        }

        try {
            SeasonStartResult result = seasonLifecycleService.startScheduledSeason(seasonId);
            if (result == SeasonStartResult.WAITING_SOURCE_BATCH) {
                scheduleRetry(seasonId, currentSignature);
                return;
            }
            clear(seasonId);
        } catch (Exception e) {
            log.error("Season start trigger failed. seasonId={}", seasonId, e);
            scheduleRetry(seasonId, currentSignature);
        }
    }

    private void scheduleRetry(Long seasonId, SeasonStartScheduleSignature signature) {
        Instant retryAt = gameRuntimeClockSupport.realNow().plusMillis(retryDelayMs);
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> triggerStart(seasonId, signature),
                retryAt
        );
        if (future != null) {
            scheduleStates.put(seasonId, new SeasonStartScheduleState(signature, future));
        }
        log.info(
                "Season start retry scheduled. seasonId={} retryAt={} retryDelayMs={}",
                seasonId,
                LocalDateTime.ofInstant(retryAt, clock.getZone()),
                retryDelayMs
        );
    }

    private void schedule(
            Long seasonId,
            SeasonStartScheduleSignature signature,
            LocalDateTime startTime,
            boolean retry
    ) {
        Instant triggerAt = gameRuntimeClockSupport.toSchedulingInstant(startTime);
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> triggerStart(seasonId, signature),
                triggerAt
        );
        if (future != null) {
            scheduleStates.put(seasonId, new SeasonStartScheduleState(signature, future));
        }

        log.info(
                "{} season start schedule. seasonId={} startTime={}",
                retry ? "Updated" : "Synchronized",
                seasonId,
                startTime
        );
    }

    private SeasonStartScheduleSignature signatureOf(Season season) {
        return new SeasonStartScheduleSignature(
                season.getStartTime(),
                season.getStatus()
        );
    }

    private record SeasonStartScheduleSignature(
            LocalDateTime startTime,
            SeasonStatus status
    ) {
    }

    private record SeasonStartScheduleState(
            SeasonStartScheduleSignature signature,
            ScheduledFuture<?> future
    ) {
    }

    public void pauseAllSchedules() {
        clearAll();
    }
}
