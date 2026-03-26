package com.ssafy.S14P21A205.game.season.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.game.season.dto.GameWaitingResponse;
import com.ssafy.S14P21A205.game.season.dto.GameWaitingStatus;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SeasonWaitingServiceTests {

    private final SeasonRepository seasonRepository = mock(SeasonRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);

    @Test
    void getWaitingStatusReturnsInProgressWhenSeasonIsActive() {
        SeasonWaitingService seasonWaitingService = createService(
                Clock.fixed(Instant.parse("2026-03-16T01:05:00Z"), ZoneId.of("Asia/Seoul"))
        );
        Season inProgressSeason = season(3L, SeasonStatus.IN_PROGRESS, 3, 7, LocalDateTime.of(2026, 3, 16, 9, 59, 10));
        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(inProgressSeason));
        when(storeRepository.countDistinctUsersBySeasonId(3L)).thenReturn(12L);

        GameWaitingResponse response = seasonWaitingService.getWaitingStatus();

        assertEquals(GameWaitingStatus.IN_PROGRESS, response.status());
        assertNull(response.nextSeasonNumber());
        assertEquals(2, response.currentDay());
        assertNull(response.nextSeasonStartTime());
        assertEquals("DAY_BUSINESS", response.seasonPhase());
        assertEquals("17:00", response.gameTime());
        assertEquals(7, response.tick());
        assertEquals(true, response.joinEnabled());
        assertEquals(3, response.joinPlayableFromDay());
        assertEquals(12, response.participantCount());
    }

    @Test
    void getWaitingStatusReturnsWaitingWhenNextSeasonIsScheduled() {
        SeasonWaitingService seasonWaitingService = createService(
                Clock.fixed(Instant.parse("2026-03-15T23:00:00Z"), ZoneId.of("Asia/Seoul"))
        );

        Season scheduledSeason = season(4L, SeasonStatus.SCHEDULED, null, 7, LocalDateTime.of(2026, 3, 16, 10, 0));
        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(seasonRepository.findFirstByStatusOrderByStartTimeAscIdAsc(SeasonStatus.SCHEDULED))
                .thenReturn(Optional.of(scheduledSeason));

        GameWaitingResponse response = seasonWaitingService.getWaitingStatus();

        assertEquals(GameWaitingStatus.WAITING, response.status());
        assertEquals(4, response.nextSeasonNumber());
        assertNull(response.currentDay());
        assertEquals(120, response.nextSeasonStartTime());
        assertEquals("NEXT_SEASON_WAITING", response.seasonPhase());
        assertEquals(7200, response.phaseRemainingSeconds());
        assertNull(response.participantCount());
    }

    @Test
    void getWaitingStatusKeepsOneSecondUntilSeasonActuallyStarts() {
        SeasonWaitingService seasonWaitingService = createService(
                Clock.fixed(Instant.parse("2026-03-16T00:59:59.200Z"), ZoneId.of("Asia/Seoul"))
        );

        Season scheduledSeason = season(4L, SeasonStatus.SCHEDULED, null, 7, LocalDateTime.of(2026, 3, 16, 10, 0));
        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(seasonRepository.findFirstByStatusOrderByStartTimeAscIdAsc(SeasonStatus.SCHEDULED))
                .thenReturn(Optional.of(scheduledSeason));

        GameWaitingResponse response = seasonWaitingService.getWaitingStatus();

        assertEquals(GameWaitingStatus.WAITING, response.status());
        assertEquals(1, response.phaseRemainingSeconds());
    }

    @Test
    void getWaitingStatusReturnsFallbackWaitingWhenNoSeasonExists() {
        SeasonWaitingService seasonWaitingService = createService(Clock.system(ZoneId.of("Asia/Seoul")));
        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(seasonRepository.findFirstByStatusOrderByStartTimeAscIdAsc(SeasonStatus.SCHEDULED))
                .thenReturn(Optional.empty());
        when(seasonRepository.findFirstByOrderByIdDesc())
                .thenReturn(Optional.empty());

        GameWaitingResponse response = seasonWaitingService.getWaitingStatus();

        assertEquals(GameWaitingStatus.WAITING, response.status());
        assertEquals(1, response.nextSeasonNumber());
        assertNull(response.currentDay());
        assertNull(response.nextSeasonStartTime());
        assertNull(response.participantCount());
    }

    private SeasonWaitingService createService(Clock clock) {
        return new SeasonWaitingService(seasonRepository, storeRepository, clock);
    }

    private Season season(
            Long id,
            SeasonStatus status,
            Integer currentDay,
            Integer totalDays,
            LocalDateTime startTime
    ) {
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(id);
        when(season.getStatus()).thenReturn(status);
        when(season.getCurrentDay()).thenReturn(currentDay);
        when(season.getTotalDays()).thenReturn(totalDays);
        when(season.resolveRuntimePlayableDays()).thenReturn(totalDays);
        when(season.getStartTime()).thenReturn(startTime);
        return season;
    }
}
