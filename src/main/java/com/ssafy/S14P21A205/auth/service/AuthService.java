package com.ssafy.S14P21A205.auth.service;

import com.ssafy.S14P21A205.auth.dto.AuthMeResponse;
import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 용도: 인증 플로우 시작/내 정보 조립 서비스. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REGISTRATION_ID = "google";

    private final AuthRedirectService authRedirectService;
    private final UserService userService;

    /** 용도: OAuth2 로그인 시작 URI 생성. */
    public URI startLogin(String redirect, HttpServletRequest request) {
        authRedirectService.storeLoginRedirect(request, redirect);
        return URI.create("/oauth2/authorization/" + REGISTRATION_ID);
    }

    /** 용도: 현재 인증 사용자 정보를 응답 DTO로 변환. */
    public AuthMeResponse me(OidcUser oidcUser) {
        if (oidcUser == null) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
        if (!StringUtils.hasText(oidcUser.getEmail())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
        var user = userService.upsertByEmail(oidcUser.getEmail());
        return AuthMeResponse.from(oidcUser, user);
    }
}
