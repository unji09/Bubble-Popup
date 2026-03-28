package com.ssafy.S14P21A205.game.runtime.service;

import com.ssafy.S14P21A205.game.runtime.entity.GameRuntimeControl;
import com.ssafy.S14P21A205.game.runtime.repository.GameRuntimeControlRepository;
import com.ssafy.S14P21A205.game.season.dto.SeasonRuntimeControlResponse;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.season.scheduler.SeasonStartScheduler;
import com.ssafy.S14P21A205.game.day.scheduler.SeasonDayClosingScheduler;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GameRuntimeControlService {

    private final GameRuntimeControlRepository gameRuntimeControlRepository;
    private final GameRuntimeControlStateHolder stateHolder;
    private final SeasonRepository seasonRepository;
    private final SeasonStartScheduler seasonStartScheduler;
    private final SeasonDayClosingScheduler seasonDayClosingScheduler;

    private final Clock baseClock;
    private final Clock clock;

    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();

    public GameRuntimeControlService(
            GameRuntimeControlRepository gameRuntimeControlRepository,
            GameRuntimeControlStateHolder stateHolder,
            SeasonRepository seasonRepository,
            SeasonStartScheduler seasonStartScheduler,
            SeasonDayClosingScheduler seasonDayClosingScheduler,
            @Qualifier("baseClock") Clock baseClock,
            Clock clock
    ) {
        this.gameRuntimeControlRepository = gameRuntimeControlRepository;
        this.stateHolder = stateHolder;
        this.seasonRepository = seasonRepository;
        this.seasonStartScheduler = seasonStartScheduler;
        this.seasonDayClosingScheduler = seasonDayClosingScheduler;
        this.baseClock = baseClock;
        this.clock = clock;
    }

    @PostConstruct
    public void initializeState() {
        GameRuntimeControl control = gameRuntimeControlRepository.findById(GameRuntimeControl.SINGLETON_ID)
                .orElseGet(() -> gameRuntimeControlRepository.save(GameRuntimeControl.createDefault()));
        stateHolder.update(control);
    }

    @Transactional(readOnly = true)
    public boolean isPaused() {
        return stateHolder.isPaused();
    }

    @Transactional(readOnly = true)
    public SeasonRuntimeControlResponse getRuntimeControl() {
        GameRuntimeControl control = gameRuntimeControlRepository.findById(GameRuntimeControl.SINGLETON_ID)
                .orElseGet(() -> gameRuntimeControlRepository.save(GameRuntimeControl.createDefault()));
        stateHolder.update(control);
        return buildResponse(control);
    }

    public SeasonRuntimeControlResponse pauseRuntime() {
        GameRuntimeControl control = gameRuntimeControlRepository.findByIdForUpdate(GameRuntimeControl.SINGLETON_ID)
                .orElseGet(() -> gameRuntimeControlRepository.save(GameRuntimeControl.createDefault()));

        if (control.pause(LocalDateTime.now(baseClock))) {
            gameRuntimeControlRepository.save(control);
            stateHolder.update(control);
            seasonStartScheduler.pauseAllSchedules();
            seasonDayClosingScheduler.pauseAllSchedules();
        } else {
            stateHolder.update(control);
        }
        return buildResponse(control);
    }

    public SeasonRuntimeControlResponse resumeRuntime() {
        GameRuntimeControl control = gameRuntimeControlRepository.findByIdForUpdate(GameRuntimeControl.SINGLETON_ID)
                .orElseGet(() -> gameRuntimeControlRepository.save(GameRuntimeControl.createDefault()));

        if (control.resume(LocalDateTime.now(baseClock))) {
            gameRuntimeControlRepository.save(control);
            stateHolder.update(control);
            seasonStartScheduler.synchronizeCurrentScheduledSeason();
            seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)
                    .ifPresent(seasonDayClosingScheduler::synchronize);
        } else {
            stateHolder.update(control);
        }
        return buildResponse(control);
    }

    private SeasonRuntimeControlResponse buildResponse(GameRuntimeControl control) {
        LocalDateTime effectiveNow = LocalDateTime.now(clock);
        Season season = resolveRelevantSeason();
        SeasonTimePoint timePoint = resolveTimePoint(season, effectiveNow);

        return new SeasonRuntimeControlResponse(
                control.isPaused(),
                control.getPausedAt(),
                effectiveNow,
                season == null ? null : season.getId(),
                season == null ? null : season.getStatus().name(),
                resolveCurrentDay(season, timePoint),
                timePoint == null || timePoint.phase() == null ? null : timePoint.phase().name(),
                timePoint == null ? null : safeInt(timePoint.remainingPhaseSeconds())
        );
    }

    private Season resolveRelevantSeason() {
        return seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)
                .or(() -> seasonRepository.findFirstByStatusOrderByStartTimeAscIdAsc(SeasonStatus.SCHEDULED))
                .orElse(null);
    }

    private SeasonTimePoint resolveTimePoint(Season season, LocalDateTime effectiveNow) {
        if (season == null || season.getStartTime() == null) {
            return null;
        }
        try {
            return seasonTimelineService.resolve(season, effectiveNow);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private Integer resolveCurrentDay(Season season, SeasonTimePoint timePoint) {
        if (timePoint != null && timePoint.currentDay() != null) {
            return timePoint.currentDay();
        }
        if (season != null && season.getCurrentDay() != null) {
            return season.getCurrentDay();
        }
        return null;
    }

    private Integer safeInt(long value) {
        return Math.toIntExact(Math.max(0L, value));
    }
}
