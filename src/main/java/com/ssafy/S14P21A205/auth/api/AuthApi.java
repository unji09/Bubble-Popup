package com.ssafy.S14P21A205.auth.api;

import com.ssafy.S14P21A205.auth.dto.AuthMeResponse;
import com.ssafy.S14P21A205.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 용도: 인증 API 엔드포인트 구현. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthApi implements AuthApiDoc {

    private final AuthService authService;

    /** 용도: Google OAuth2 로그인 시작. */
    @GetMapping("/login")
    @Override
    public ResponseEntity<Void> login(
            @RequestParam(value = "redirect", required = false) String redirect,
            HttpServletRequest request
    ) {
        URI location = authService.startLogin(redirect, request);
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    /** 용도: 현재 로그인 사용자 정보 조회. */
    @GetMapping("/me")
    @Override
        public ResponseEntity<AuthMeResponse> me(@AuthenticationPrincipal OidcUser oidcUser) {
        return ResponseEntity.ok(authService.me(oidcUser));
    }
}
