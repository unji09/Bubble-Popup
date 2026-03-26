package com.ssafy.S14P21A205.game.season.service;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.season.dto.SeasonDemoSkipRequest;
import com.ssafy.S14P21A205.game.season.dto.SeasonDemoSkipResponse;
import com.ssafy.S14P21A205.game.season.entity.DemoSkipStatus;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SeasonAdminService {

    private static final int DEMO_PLAYABLE_DAYS = 3;

    private final SeasonRepository seasonRepository;
    private final UserService userService;
    private final Clock clock;

    public SeasonDemoSkipResponse reserveDemoSkip(Authentication authentication, SeasonDemoSkipRequest request) {
        User user = userService.getCurrentUser(authentication);
        if (user.getRole() != User.UserRole.ADMIN) {
            throw new BaseException(ErrorCode.ACCESS_DENIED);
        }
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
}
