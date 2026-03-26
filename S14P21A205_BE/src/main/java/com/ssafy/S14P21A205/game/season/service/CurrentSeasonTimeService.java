package com.ssafy.S14P21A205.game.season.service;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTimeResponse;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.time.model.SeasonPhase;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentSeasonTimeService {

    private final SeasonRepository seasonRepository;
    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();
    private final Clock clock;

    public CurrentSeasonTimeResponse getCurrentSeasonTime() {
        LocalDateTime now = LocalDateTime.now(clock);
        CurrentSeasonContext currentSeasonContext = resolveCurrentSeasonContext(now);
        Season season = currentSeasonContext.season();
        SeasonTimePoint timePoint = currentSeasonContext.timePoint();

        return new CurrentSeasonTimeResponse(
                timePoint.phase().name(),
                resolveCurrentDay(season, timePoint),
                safeInt(timePoint.remainingPhaseSeconds()),
                timePoint.occurredAt(),
                season.getStartTime(),
                timePoint.gameTime(),
                timePoint.tick(),
                timePoint.joinEnabled(),
                timePoint.joinPlayableFromDay()
        );
    }

    private CurrentSeasonContext resolveCurrentSeasonContext(LocalDateTime now) {
        return findTimelineActiveContext(
                seasonRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeDescIdDesc(SeasonStatus.IN_PROGRESS, now),
                now
        ).or(() -> findTimelineActiveContext(
                seasonRepository.findByStatusAndStartTimeLessThanEqualOrderByStartTimeAscIdAsc(SeasonStatus.SCHEDULED, now),
                now
        )).or(() -> findTimelineActiveContext(
                seasonRepository.findByStatusAndStartTimeLessThanEqualAndEndTimeAfterOrderByEndTimeDescIdDesc(
                        SeasonStatus.FINISHED,
                        now,
                        now
                ),
                now
        )).orElseThrow(() -> new BaseException(ErrorCode.SEASON_NOT_FOUND));
    }

    private Optional<CurrentSeasonContext> findTimelineActiveContext(List<Season> seasons, LocalDateTime now) {
        if (seasons == null || seasons.isEmpty()) {
            return Optional.empty();
        }

        for (Season season : seasons) {
            SeasonTimePoint timePoint = resolveTimePoint(season, now);
            if (timePoint == null || timePoint.phase() == SeasonPhase.CLOSED) {
                continue;
            }
            return Optional.of(new CurrentSeasonContext(season, timePoint));
        }
        return Optional.empty();
    }

    private SeasonTimePoint resolveTimePoint(Season season, LocalDateTime now) {
        try {
            return seasonTimelineService.resolve(season, now);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private int resolveCurrentDay(Season season, SeasonTimePoint timePoint) {
        Integer currentDay = timePoint.currentDay();
        if (isValidDay(season, currentDay)) {
            return currentDay;
        }

        Integer syncedCurrentDay = season.getCurrentDay();
        if (isValidDay(season, syncedCurrentDay)) {
            return syncedCurrentDay;
        }

        return season.resolveRuntimePlayableDays();
    }

    private boolean isValidDay(Season season, Integer day) {
        return day != null
                && season.resolveRuntimePlayableDays() > 0
                && day >= 1
                && day <= season.resolveRuntimePlayableDays();
    }

    private int safeInt(long value) {
        return Math.toIntExact(Math.max(0L, value));
    }

    private record CurrentSeasonContext(
            Season season,
            SeasonTimePoint timePoint
    ) {
    }
}
