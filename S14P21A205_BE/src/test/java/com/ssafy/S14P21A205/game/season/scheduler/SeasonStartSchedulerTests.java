package com.ssafy.S14P21A205.game.season.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.season.service.SeasonLifecycleService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeasonStartSchedulerTests {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private SeasonLifecycleService seasonLifecycleService;

    @Mock
    private ScheduledFuture<Object> scheduledFuture;

    @Test
    void synchronizeSchedulesSeasonStartAtExactStartTime() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T05:00:00Z"), ZoneId.of("Asia/Seoul"));
        SeasonStartScheduler scheduler = createScheduler(clock, 10_000L);
        Season scheduledSeason = scheduledSeason(5L, LocalDateTime.of(2026, 3, 24, 14, 5, 0));

        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenAnswer(invocation -> scheduledFuture);

        scheduler.synchronize(scheduledSeason);

        ArgumentCaptor<Instant> triggerAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), triggerAtCaptor.capture());
        assertThat(triggerAtCaptor.getValue())
                .isEqualTo(scheduledSeason.getStartTime().atZone(clock.getZone()).toInstant());
    }

    @Test
    void synchronizeTriggersImmediateCatchUpAndSchedulesRetryWhenBatchIsNotReady() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T05:10:00Z"), ZoneId.of("Asia/Seoul"));
        SeasonStartScheduler scheduler = createScheduler(clock, 10_000L);
        Season scheduledSeason = scheduledSeason(7L, LocalDateTime.of(2026, 3, 24, 14, 9, 55));

        when(seasonRepository.findByIdAndStatus(7L, SeasonStatus.SCHEDULED))
                .thenReturn(Optional.of(scheduledSeason));
        when(seasonLifecycleService.startScheduledSeason(7L))
                .thenReturn(SeasonLifecycleService.SeasonStartResult.WAITING_SOURCE_BATCH);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenAnswer(invocation -> scheduledFuture);

        scheduler.synchronize(scheduledSeason);

        verify(seasonLifecycleService).startScheduledSeason(7L);
        ArgumentCaptor<Instant> retryAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), retryAtCaptor.capture());
        assertThat(retryAtCaptor.getValue())
                .isEqualTo(clock.instant().plusMillis(10_000L));
    }

    private SeasonStartScheduler createScheduler(Clock clock, long retryDelayMs) {
        SeasonStartScheduler scheduler = new SeasonStartScheduler(
                taskScheduler,
                seasonRepository,
                seasonLifecycleService,
                clock
        );
        ReflectionTestUtils.setField(scheduler, "retryDelayMs", retryDelayMs);
        return scheduler;
    }

    private Season scheduledSeason(Long id, LocalDateTime startTime) {
        Season season = Season.createScheduled(7, startTime, startTime.plusMinutes(30));
        ReflectionTestUtils.setField(season, "id", id);
        return season;
    }
}
