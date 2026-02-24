package com.ssafy.S14P21A205.security.handler;

import com.ssafy.S14P21A205.auth.service.AuthRedirectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 용도: OAuth2 로그인 실패 시 안전한 경로로 리다이렉트.
 */
@Slf4j
@Component
public class AuthLoginFailureHandler implements AuthenticationFailureHandler {

    private final AuthRedirectService authRedirectService;
    private final String defaultRedirectUrl;

    /**
     * 용도: 실패 핸들러 초기화.
     */
    public AuthLoginFailureHandler(
                  AuthRedirectService authRedirectService,
                  @Value("${app.auth.default-redirect-url:/swagger-ui/index.html}") String defaultRedirectUrl
    ) {
        this.authRedirectService = authRedirectService;
        this.defaultRedirectUrl = defaultRedirectUrl;
    }

    /**
     * 용도: 실패 로그 기록 후 리다이렉트 수행.
     */
    @Override
    public void onAuthenticationFailure(
                  HttpServletRequest request,
                  HttpServletResponse response,
                  AuthenticationException exception
    ) throws IOException {
        String errorCode = "oauth2_login_failed";
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            errorCode = oauth2.getError().getErrorCode();
            log.error("OAuth2 login failed: code={}, description={}", oauth2.getError().getErrorCode(), oauth2.getError().getDescription(), exception);
        } else {
            log.error("OAuth2 login failed: {}", exception.getMessage(), exception);
        }

        String redirect = authRedirectService.consumeLoginRedirect(request);
        if (!authRedirectService.isSafeRedirect(redirect)) {
            redirect = defaultRedirectUrl;
        }
        if (!authRedirectService.isSafeRedirect(redirect)) {
            redirect = "/swagger-ui/index.html";
        }

        redirect = redirect.trim();

        String encoded = URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
        String target = redirect.contains("?")
                      ? redirect + "&loginError=" + encoded
                      : redirect + "?loginError=" + encoded;

        response.sendRedirect(target);
    }
}
