package com.ssafy.S14P21A205.game.season.service;

import com.ssafy.S14P21A205.game.season.dto.GameWaitingResponse;
import com.ssafy.S14P21A205.game.season.dto.GameWaitingStatus;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.time.model.SeasonPhase;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonWaitingService {

    private final SeasonRepository seasonRepository;
    private final StoreRepository storeRepository;
    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();
    private final Clock clock;

    public GameWaitingResponse getWaitingStatus() {
        LocalDateTime now = LocalDateTime.now(clock);

        Season inProgressSeason = seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)
                .orElse(null);
        if (inProgressSeason != null) {
            SeasonTimePoint timePoint = seasonTimelineService.resolve(inProgressSeason, now);
            if (timePoint.phase() != SeasonPhase.CLOSED) {
                return new GameWaitingResponse(
                        GameWaitingStatus.IN_PROGRESS,
                        null,
                        timePoint.currentDay(),
                        null,
                        timePoint.phase().name(),
                        safeInt(timePoint.remainingPhaseSeconds()),
                        timePoint.gameTime(),
                        timePoint.tick(),
                        timePoint.joinEnabled(),
                        timePoint.joinPlayableFromDay(),
                        resolveParticipantCount(inProgressSeason.getId())
                );
            }
        }

        Season scheduledSeason = seasonRepository.findFirstByStatusOrderByStartTimeAscIdAsc(SeasonStatus.SCHEDULED)
                .orElse(null);
        if (scheduledSeason != null) {
            long remainingSeconds = resolveRemainingSeconds(scheduledSeason.getStartTime(), now);
            return new GameWaitingResponse(
                    GameWaitingStatus.WAITING,
                    resolveSeasonNumber(scheduledSeason.getId()),
                    null,
                    Math.toIntExact(Math.max(0L, remainingSeconds / 60L)),
                    SeasonPhase.NEXT_SEASON_WAITING.name(),
                    safeInt(remainingSeconds),
                    null,
                    null,
                    false,
                    null,
                    null
            );
        }

        Integer nextSeasonNumber = seasonRepository.findFirstByOrderByIdDesc()
                .map(Season::getId)
                .map(seasonId -> Math.toIntExact(seasonId + 1))
                .orElse(1);

        return new GameWaitingResponse(
                GameWaitingStatus.WAITING,
                nextSeasonNumber,
                null,
                null,
                SeasonPhase.NEXT_SEASON_WAITING.name(),
                null,
                null,
                null,
                false,
                null,
                null
        );
    }

    private long resolveRemainingSeconds(LocalDateTime startTime, LocalDateTime now) {
        if (startTime == null) {
            return 0L;
        }
        Duration remaining = Duration.between(now, startTime);
        if (remaining.isNegative() || remaining.isZero()) {
            return 0L;
        }

        long truncatedSeconds = remaining.toSeconds();
        if (remaining.minusSeconds(truncatedSeconds).isZero()) {
            return truncatedSeconds;
        }
        return truncatedSeconds + 1L;
    }

    private Integer resolveSeasonNumber(Long seasonId) {
        return seasonId == null ? null : Math.toIntExact(seasonId);
    }

    private Integer resolveParticipantCount(Long seasonId) {
        if (seasonId == null) {
            return null;
        }
        return Math.toIntExact(storeRepository.countDistinctUsersBySeasonId(seasonId));
    }

    private Integer safeInt(long value) {
        return Math.toIntExact(Math.max(0L, value));
    }
}

