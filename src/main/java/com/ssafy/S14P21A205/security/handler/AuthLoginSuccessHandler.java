package com.ssafy.S14P21A205.security.handler;

import com.ssafy.S14P21A205.auth.service.AuthRedirectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** 용도: OAuth2 로그인 성공 시 안전한 경로로 리다이렉트. */
@Component
public class AuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthRedirectService authRedirectService;
    private final String defaultRedirectUrl;

    /** 용도: 성공 핸들러 초기화. */
    public AuthLoginSuccessHandler(
            AuthRedirectService authRedirectService,
            @Value("${app.auth.default-redirect-url:/swagger-ui/index.html}") String defaultRedirectUrl
    ) {
        this.authRedirectService = authRedirectService;
        this.defaultRedirectUrl = defaultRedirectUrl;
    }

    /** 용도: 성공 시 리다이렉트 수행. */
    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        String redirect = authRedirectService.consumeLoginRedirect(request);
        String target = authRedirectService.isSafeRedirect(redirect) ? redirect.trim() : defaultRedirectUrl;
        if (!authRedirectService.isSafeRedirect(target)) {
            target = "/swagger-ui/index.html";
        }
        response.sendRedirect(target.trim());
    }
}
