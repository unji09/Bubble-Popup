package com.ssafy.S14P21A205.game.season.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTimeResponse;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.mockito.Mockito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrentSeasonTimeServiceTests {

    @Mock
    private SeasonRepository seasonRepository;

    private CurrentSeasonTimeService currentSeasonTimeService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(seasonRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeDescIdDesc(any(), any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(seasonRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeAscIdAsc(any(), any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(seasonRepository.findByStatusAndStartTimeLessThanEqualAndEndTimeAfterOrderByEndTimeDescIdDesc(any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void getCurrentSeasonTimeReturnsBusinessPhaseFromInProgressSeason() {
        currentSeasonTimeService = createService(
                Clock.fixed(Instant.parse("2026-03-18T01:02:20Z"), ZoneId.of("Asia/Seoul"))
        );

        Season season = season(1, 7, LocalDateTime.of(2026, 3, 18, 10, 0, 0));
        when(seasonRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeDescIdDesc(
                SeasonStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 3, 18, 10, 2, 20)
        )).thenReturn(List.of(season));

        CurrentSeasonTimeResponse response = currentSeasonTimeService.getCurrentSeasonTime();

        assertThat(response.seasonPhase()).isEqualTo("DAY_BUSINESS");
        assertThat(response.currentDay()).isEqualTo(1);
        assertThat(response.phaseRemainingSeconds()).isEqualTo(80);
        assertThat(response.serverTime()).isEqualTo(LocalDateTime.of(2026, 3, 18, 10, 2, 20));
        assertThat(response.seasonStartTime()).isEqualTo(LocalDateTime.of(2026, 3, 18, 10, 0, 0));
        assertThat(response.gameTime()).isEqualTo("14:00");
        assertThat(response.tick()).isEqualTo(4);
        assertThat(response.joinEnabled()).isTrue();
        assertThat(response.joinPlayableFromDay()).isEqualTo(2);
    }

    @Test
    void getCurrentSeasonTimeFallsBackToSeasonCurrentDayDuringWaitingPhase() {
        currentSeasonTimeService = createService(
                Clock.fixed(Instant.parse("2026-03-18T01:26:00Z"), ZoneId.of("Asia/Seoul"))
        );

        Season season = season(7, 7, LocalDateTime.of(2026, 3, 18, 10, 0, 0));
        when(seasonRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeDescIdDesc(
                SeasonStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 3, 18, 10, 26, 0)
        )).thenReturn(List.of(season));

        CurrentSeasonTimeResponse response = currentSeasonTimeService.getCurrentSeasonTime();

        assertThat(response.seasonPhase()).isEqualTo("NEXT_SEASON_WAITING");
        assertThat(response.currentDay()).isEqualTo(7);
        assertThat(response.phaseRemainingSeconds()).isEqualTo(240);
        assertThat(response.serverTime()).isEqualTo(LocalDateTime.of(2026, 3, 18, 10, 26, 0));
        assertThat(response.seasonStartTime()).isEqualTo(LocalDateTime.of(2026, 3, 18, 10, 0, 0));
        assertThat(response.gameTime()).isNull();
        assertThat(response.tick()).isNull();
        assertThat(response.joinEnabled()).isFalse();
        assertThat(response.joinPlayableFromDay()).isNull();
    }

    @Test
    void getCurrentSeasonTimeUsesStartedScheduledSeasonWhenLifecycleHasNotStartedItYet() {
        currentSeasonTimeService = createService(
                Clock.fixed(Instant.parse("2026-03-18T01:01:10Z"), ZoneId.of("Asia/Seoul"))
        );

        Season season = season(1, 7, LocalDateTime.of(2026, 3, 18, 10, 0, 0));
        when(seasonRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeAscIdAsc(
                SeasonStatus.SCHEDULED,
                LocalDateTime.of(2026, 3, 18, 10, 1, 10)
        )).thenReturn(List.of(season));

        CurrentSeasonTimeResponse response = currentSeasonTimeService.getCurrentSeasonTime();

        assertThat(response.seasonPhase()).isEqualTo("DAY_PREPARING");
        assertThat(response.currentDay()).isEqualTo(1);
        assertThat(response.phaseRemainingSeconds()).isEqualTo(30);
        assertThat(response.joinEnabled()).isTrue();
        assertThat(response.joinPlayableFromDay()).isEqualTo(2);
    }

    @Test
    void getCurrentSeasonTimeUsesFinishedSeasonDuringSeasonSummaryWindow() {
        currentSeasonTimeService = createService(
                Clock.fixed(Instant.parse("2026-03-18T01:23:30Z"), ZoneId.of("Asia/Seoul"))
        );

        Season season = season(7, 7, LocalDateTime.of(2026, 3, 18, 10, 0, 0));
        when(seasonRepository.findByStatusAndStartTimeLessThanEqualAndEndTimeAfterOrderByEndTimeDescIdDesc(
                SeasonStatus.FINISHED,
                LocalDateTime.of(2026, 3, 18, 10, 23, 30),
                LocalDateTime.of(2026, 3, 18, 10, 23, 30)
        )).thenReturn(List.of(season));

        CurrentSeasonTimeResponse response = currentSeasonTimeService.getCurrentSeasonTime();

        assertThat(response.seasonPhase()).isEqualTo("SEASON_SUMMARY");
        assertThat(response.currentDay()).isEqualTo(7);
        assertThat(response.phaseRemainingSeconds()).isEqualTo(90);
        assertThat(response.serverTime()).isEqualTo(LocalDateTime.of(2026, 3, 18, 10, 23, 30));
        assertThat(response.seasonStartTime()).isEqualTo(LocalDateTime.of(2026, 3, 18, 10, 0, 0));
        assertThat(response.gameTime()).isNull();
        assertThat(response.tick()).isNull();
        assertThat(response.joinEnabled()).isFalse();
        assertThat(response.joinPlayableFromDay()).isNull();
    }

    @Test
    void getCurrentSeasonTimeThrowsWhenNoCurrentSeasonExists() {
        currentSeasonTimeService = createService(Clock.system(ZoneId.of("Asia/Seoul")));
        assertThatThrownBy(() -> currentSeasonTimeService.getCurrentSeasonTime())
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEASON_NOT_FOUND);
    }

    private CurrentSeasonTimeService createService(Clock clock) {
        return new CurrentSeasonTimeService(seasonRepository, clock);
    }

    private Season season(
            Integer currentDay,
            Integer totalDays,
            LocalDateTime startTime
    ) {
        Season season = Mockito.mock(Season.class);
        Mockito.lenient().when(season.getCurrentDay()).thenReturn(currentDay);
        Mockito.lenient().when(season.getTotalDays()).thenReturn(totalDays);
        Mockito.lenient().when(season.resolveRuntimePlayableDays()).thenReturn(totalDays);
        Mockito.lenient().when(season.getStartTime()).thenReturn(startTime);
        return season;
    }
}
