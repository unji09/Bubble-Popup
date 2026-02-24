package com.ssafy.S14P21A205.auth.api;

import com.ssafy.S14P21A205.auth.dto.AuthMeResponse;
import com.ssafy.S14P21A205.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** 용도: 인증 API Swagger 문서 정의. */
@Tag(name = "Auth API", description = "Swagger 테스트를 위한 Google OAuth2 인증 API")
public interface AuthApiDoc {

    @Operation(
            summary = "Google 로그인 시작",
            description = """
                    Google OAuth2 로그인 플로우를 시작합니다.

                    - redirect 파라미터로 로그인 완료 후 돌아갈 경로를 지정할 수 있습니다.
                    - 상대 경로(/...) 또는 allow-list에 등록된 절대 URL만 허용됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Google OAuth2 인가 엔드포인트로 리다이렉트")
    })
    ResponseEntity<Void> login(
            @Parameter(description = "로그인 완료 후 이동 경로", example = "/swagger-ui/index.html")
            String redirect,
            @Parameter(hidden = true) HttpServletRequest request
    );

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자 정보를 반환합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AuthMeResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "미인증",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<AuthMeResponse> me(
            @Parameter(hidden = true) OidcUser oidcUser
    );
}
