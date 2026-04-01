package com.ssafy.S14P21A205.game.season.service;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.news.service.NewsService;
import com.ssafy.S14P21A205.game.runtime.service.GameRuntimeControlService;
import com.ssafy.S14P21A205.game.season.dto.SeasonDemoSkipRequest;
import com.ssafy.S14P21A205.game.season.dto.SeasonDemoSkipResponse;
import com.ssafy.S14P21A205.game.season.dto.SeasonNewsLockReproductionRequest;
import com.ssafy.S14P21A205.game.season.dto.SeasonNewsLockReproductionResponse;
import com.ssafy.S14P21A205.game.season.dto.SeasonRuntimeControlResponse;
import com.ssafy.S14P21A205.game.season.entity.DemoSkipStatus;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class SeasonAdminService {

    private static final int DEMO_PLAYABLE_DAYS = 3;
    private static final long DEFAULT_SECOND_TRIGGER_DELAY_MS = 1000L;

    private final SeasonRepository seasonRepository;
    private final UserService userService;
    private final GameRuntimeControlService gameRuntimeControlService;
    private final NewsService newsService;
    private final TaskScheduler taskScheduler;
    private final Clock clock;

    @Value("${app.game.demo.reproduce-season-start-lock-enabled:false}")
    private boolean reproduceSeasonStartLockEnabled;

    @Value("${app.game.demo.reproduce-global-news-delete-enabled:false}")
    private boolean reproduceGlobalNewsDeleteEnabled;

    public SeasonDemoSkipResponse reserveDemoSkip(Authentication authentication, SeasonDemoSkipRequest request) {
        requireAdmin(authentication);
        if (request == null || request.seasonId() == null || request.seasonId() <= 0L) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "seasonId must be positive.");
        }

        Season season = seasonRepository.findByIdAndStatus(request.seasonId(), SeasonStatus.SCHEDULED)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND, "Scheduled season was not found."));

        LocalDateTime now = LocalDateTime.now(clock);
        if (season.getStartTime() == null || !season.getStartTime().isAfter(now)) {
            throw new BaseException(ErrorCode.SEASON_STATE_CONFLICT, "Demo skip can only be reserved before the season starts.");
        }
        if (season.isDemoSkipReserved() || season.getDemoSkipStatus() == DemoSkipStatus.APPLIED) {
            throw new BaseException(ErrorCode.SEASON_STATE_CONFLICT, "Demo skip is already reserved for this season.");
        }

        season.reserveDemoSkip(DEMO_PLAYABLE_DAYS);
        return new SeasonDemoSkipResponse(
                season.getId(),
                season.getDemoSkipStatus().name(),
                season.getDemoPlayableDays(),
                "This season will run as a 3-day demo season when it starts."
        );
    }

    public SeasonRuntimeControlResponse getRuntimeControl(Authentication authentication) {
        requireAdmin(authentication);
        return gameRuntimeControlService.getRuntimeControl();
    }

    public SeasonRuntimeControlResponse pauseRuntime(Authentication authentication) {
        requireAdmin(authentication);
        return gameRuntimeControlService.pauseRuntime();
    }

    public SeasonRuntimeControlResponse resumeRuntime(Authentication authentication) {
        requireAdmin(authentication);
        return gameRuntimeControlService.resumeRuntime();
    }

    public SeasonNewsLockReproductionResponse reproduceNewsLock(
            Authentication authentication,
            SeasonNewsLockReproductionRequest request
    ) {
        requireAdmin(authentication);
        if (!reproduceSeasonStartLockEnabled || !reproduceGlobalNewsDeleteEnabled) {
            throw new BaseException(
                    ErrorCode.SEASON_STATE_CONFLICT,
                    "Reproduction env flags must be enabled before triggering deterministic news lock reproduction."
            );
        }
        if (request == null || request.seasonId() == null || request.seasonId() <= 0L) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "seasonId must be positive.");
        }

        Season season = seasonRepository.findById(request.seasonId())
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND, "Target season was not found."));
        long secondTriggerDelayMs = normalizeSecondTriggerDelayMs(request.secondTriggerDelayMs());
        Instant firstTriggerAt = Instant.now(clock);
        Instant secondTriggerAt = firstTriggerAt.plusMillis(secondTriggerDelayMs);

        scheduleNewsGenerationReproduction(season.getId(), firstTriggerAt, "first");
        scheduleNewsGenerationReproduction(season.getId(), secondTriggerAt, "second");

        return new SeasonNewsLockReproductionResponse(
                season.getId(),
                secondTriggerDelayMs,
                true,
                LocalDateTime.ofInstant(firstTriggerAt, clock.getZone()),
                "Triggered two concurrent news generation jobs for deterministic lock reproduction."
        );
    }

    private User requireAdmin(Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        if (user.getRole() != User.UserRole.ADMIN) {
            throw new BaseException(ErrorCode.ACCESS_DENIED);
        }
        return user;
    }

    private long normalizeSecondTriggerDelayMs(Long secondTriggerDelayMs) {
        if (secondTriggerDelayMs == null || secondTriggerDelayMs <= 0L) {
            return DEFAULT_SECOND_TRIGGER_DELAY_MS;
        }
        return secondTriggerDelayMs;
    }

    private void scheduleNewsGenerationReproduction(Long seasonId, Instant triggerAt, String label) {
        taskScheduler.schedule(() -> {
            log.warn("[DEMO] Triggering {} concurrent news generation for season {}", label, seasonId);
            try {
                newsService.generateSeasonNews(seasonId);
            } catch (RuntimeException e) {
                log.error("[DEMO] Concurrent news generation failed. seasonId={} label={}", seasonId, label, e);
                throw e;
            }
        }, triggerAt);
    }
}
