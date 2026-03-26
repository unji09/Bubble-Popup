package com.ssafy.S14P21A205.game.season.controller;

import com.ssafy.S14P21A205.exception.ErrorResponse;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Game Season API", description = "Season APIs")
public interface SeasonControllerDoc {

    @Operation(
            summary = "Get game waiting status",
            description = "Return lobby waiting status. If a season is in progress, the client can route to the game screen."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Waiting status lookup success",
                    content = @Content(schema = @Schema(implementation = GameWaitingResponse.class))
            )
    })
    ResponseEntity<GameWaitingResponse> getWaitingStatus();

    @Operation(
            summary = "Get current season global time state",
            description = "Return the authoritative current season timeline state for screen resynchronization.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Current season time lookup success",
                    content = @Content(schema = @Schema(implementation = CurrentSeasonTimeResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Current season not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<CurrentSeasonTimeResponse> getCurrentSeasonTime(
            @Parameter(hidden = true) Authentication authentication
    );

    @Operation(
            summary = "Get current season participation state",
            description = "Return whether the authenticated user has joined the current season and whether the store screen is currently accessible.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Current season participation lookup success",
                    content = @Content(schema = @Schema(implementation = ParticipationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<ParticipationResponse> getCurrentParticipation(
            @Parameter(hidden = true) Authentication authentication
    );

    @Operation(
            summary = "Join current season",
            description = "Join the current season and create a store immediately. The response balance is the remaining balance after paying a 10% location interior.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Season join success",
                    content = @Content(schema = @Schema(implementation = SeasonJoinResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Season or location not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Already joined current season",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<SeasonJoinResponse> joinCurrentSeason(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "Current season join request")
            SeasonJoinRequest request
    );

    @Operation(
            summary = "Reserve demo skip for a scheduled season",
            description = "Admin-only API. The target season keeps 7-day preparation data but runs as a 3-day demo season when it starts.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Demo skip reservation success",
                    content = @Content(schema = @Schema(implementation = SeasonDemoSkipResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Admin role required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Scheduled season not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Season already started or demo skip already reserved",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<SeasonDemoSkipResponse> reserveDemoSkip(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "Demo skip reservation request")
            SeasonDemoSkipRequest request
    );

    @Operation(
            summary = "Get current top rankings",
            description = "Return the current top 10 realtime rankings from Redis.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Top ranking lookup success",
                    content = @Content(schema = @Schema(implementation = CurrentSeasonTopRankingsResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Top ranking cache not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<CurrentSeasonTopRankingsResponse> getCurrentTopRankings(
            @Parameter(hidden = true) Authentication authentication
    );

    @Operation(
            summary = "Get finalized season rankings",
            description = "Return the finalized rankings for the current result-target season. If the current season final ranking is not ready yet, return 409 instead of falling back to an older season.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Final ranking lookup success",
                    content = @Content(schema = @Schema(implementation = CurrentSeasonRankingsResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Current final ranking is not ready yet",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Season data not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<CurrentSeasonRankingsResponse> getCurrentFinalRankings(
            @Parameter(hidden = true) Authentication authentication
    );

    @Operation(
            summary = "Get finished season summary",
            description = "Return the finished season summary for the specified user. If seasonId is omitted, the latest finished season is used. If userId is omitted, the authenticated user is used.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Season summary lookup success",
                    content = @Content(schema = @Schema(implementation = SeasonSummaryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Season summary data not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<SeasonSummaryResponse> getSeasonSummary(
            @Parameter(hidden = true) Authentication authentication,
            @Parameter(description = "Finished season ID. If omitted, the latest finished season is used.", example = "12")
            @RequestParam(required = false) Long seasonId,
            @Parameter(description = "Target user ID. If omitted, the authenticated user is used.", example = "27")
            @RequestParam(required = false) Integer userId
    );
}
