package com.ssafy.S14P21A205.game.season.controller;

import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonRankingsResponse;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTimeResponse;
import com.ssafy.S14P21A205.game.season.dto.CurrentSeasonTopRankingsResponse;
import com.ssafy.S14P21A205.game.season.dto.GameWaitingResponse;
import com.ssafy.S14P21A205.game.season.dto.ParticipationResponse;
import com.ssafy.S14P21A205.game.season.dto.SeasonDemoSkipRequest;
import com.ssafy.S14P21A205.game.season.dto.SeasonDemoSkipResponse;
import com.ssafy.S14P21A205.game.season.dto.SeasonJoinRequest;
import com.ssafy.S14P21A205.game.season.dto.SeasonJoinResponse;
import com.ssafy.S14P21A205.game.season.dto.SeasonSummaryResponse;
import com.ssafy.S14P21A205.game.season.service.SeasonAdminService;
import com.ssafy.S14P21A205.game.season.service.CurrentSeasonTimeService;
import com.ssafy.S14P21A205.game.season.service.ParticipationService;
import com.ssafy.S14P21A205.game.season.service.SeasonJoinService;
import com.ssafy.S14P21A205.game.season.service.SeasonRankingService;
import com.ssafy.S14P21A205.game.season.service.SeasonSummaryService;
import com.ssafy.S14P21A205.game.season.service.SeasonWaitingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game")
public class SeasonController implements SeasonControllerDoc {

    private final CurrentSeasonTimeService currentSeasonTimeService;
    private final ParticipationService participationService;
    private final SeasonJoinService seasonJoinService;
    private final SeasonRankingService seasonRankingService;
    private final SeasonSummaryService seasonSummaryService;
    private final SeasonWaitingService seasonWaitingService;
    private final SeasonAdminService seasonAdminService;

    @Override
    @GetMapping("/waiting")
    public ResponseEntity<GameWaitingResponse> getWaitingStatus() {
        return ResponseEntity.ok(seasonWaitingService.getWaitingStatus());
    }

    @Override
    @GetMapping("/seasons/time")
    public ResponseEntity<CurrentSeasonTimeResponse> getCurrentSeasonTime(Authentication authentication) {
        return ResponseEntity.ok(currentSeasonTimeService.getCurrentSeasonTime());
    }

    @Override
    @GetMapping("/seasons/current/participation")
    public ResponseEntity<ParticipationResponse> getCurrentParticipation(Authentication authentication) {
        Integer userId = Integer.valueOf(authentication.getName());
        return ResponseEntity.ok(participationService.getCurrentParticipation(userId));
    }

    @Override
    @PostMapping("/seasons/current/join")
    public ResponseEntity<SeasonJoinResponse> joinCurrentSeason(
            Authentication authentication,
            @Valid @RequestBody SeasonJoinRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seasonJoinService.joinCurrentSeason(authentication, request));
    }

    @Override
    @PostMapping("/seasons/admin/demo-skip")
    public ResponseEntity<SeasonDemoSkipResponse> reserveDemoSkip(
            Authentication authentication,
            @Valid @RequestBody SeasonDemoSkipRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seasonAdminService.reserveDemoSkip(authentication, request));
    }

    @Override
    @GetMapping("/seasons/current/rankings/top")
    public ResponseEntity<CurrentSeasonTopRankingsResponse> getCurrentTopRankings(Authentication authentication) {
        return ResponseEntity.ok(seasonRankingService.getCurrentTopRankings(authentication));
    }

    @Override
    @GetMapping("/seasons/current/rankings/final")
    public ResponseEntity<CurrentSeasonRankingsResponse> getCurrentFinalRankings(Authentication authentication) {
        return ResponseEntity.ok(seasonRankingService.getCurrentFinalRankings(authentication));
    }

    @Override
    @GetMapping("/seasons/summary")
    public ResponseEntity<SeasonSummaryResponse> getSeasonSummary(
            Authentication authentication,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) Integer userId
    ) {
        return ResponseEntity.ok(seasonSummaryService.getSeasonSummary(authentication, seasonId, userId));
    }
}
