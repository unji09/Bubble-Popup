package com.ssafy.S14P21A205.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 용도: 로그인 리다이렉트 경로를 세션 저장/소비하고 안전성 검증. */
@Service
public class AuthRedirectService {

    private static final String LOGIN_REDIRECT_SESSION_KEY = AuthRedirectService.class.getName() + ".loginRedirect";

    private record Origin(String scheme, String host, int port) {}

    private final Set<Origin> allowedRedirectOrigins;

    /** 용도: 허용 origin 목록 초기화. */
    public AuthRedirectService(@Value("${app.auth.allowed-redirect-origins:}") String allowedRedirectOriginsCsv) {
        this.allowedRedirectOrigins = parseAllowedOrigins(allowedRedirectOriginsCsv);
    }

    /** 용도: 안전한 redirect를 세션에 저장. */
    public void storeLoginRedirect(HttpServletRequest request, String redirect) {
        if (!isSafeRedirect(redirect)) {
            return;
        }
        request.getSession(true).setAttribute(LOGIN_REDIRECT_SESSION_KEY, redirect.trim());
    }

    /** 용도: 세션 redirect를 1회 소비 후 제거. */
    public String consumeLoginRedirect(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(LOGIN_REDIRECT_SESSION_KEY);
        session.removeAttribute(LOGIN_REDIRECT_SESSION_KEY);
        return value instanceof String s ? s : null;
    }

    /** 용도: redirect 안전성 검증 진입점. */
    public boolean isSafeRedirect(String redirect) {
        if (isSafeRelativeRedirect(redirect)) {
            return true;
        }
        return isSafeAbsoluteRedirect(redirect);
    }

    public boolean isSafeRelativeRedirect(String redirect) {
        String trimmed = normalizeInput(redirect);
        if (trimmed == null) {
            return false;
        }
        if (!trimmed.startsWith("/")) {
            return false;
        }
        if (trimmed.startsWith("//")) {
            return false;
        }
        if (trimmed.contains("\\")) {
            return false;
        }
        return !containsHeaderBreakingChars(trimmed);
    }

    private boolean isSafeAbsoluteRedirect(String redirect) {
        if (allowedRedirectOrigins.isEmpty()) {
            return false;
        }

        String trimmed = normalizeInput(redirect);
        if (trimmed == null || containsHeaderBreakingChars(trimmed)) {
            return false;
        }

        Origin origin = tryParseHttpOrigin(trimmed);
        return origin != null && allowedRedirectOrigins.contains(origin);
    }

    private static String normalizeInput(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static Origin tryParseHttpOrigin(String value) {
        String trimmed = normalizeInput(value);
        if (trimmed == null) {
            return null;
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return tryParseHttpOrigin(uri);
    }

    private static Origin tryParseHttpOrigin(URI uri) {
        if (!uri.isAbsolute()) {
            return null;
        }
        if (uri.getUserInfo() != null) {
            return null;
        }

        String scheme = normalizeHttpScheme(uri.getScheme());
        if (scheme == null) {
            return null;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return null;
        }

        int port = effectivePort(uri);
        if (port < 0) {
            return null;
        }

        return new Origin(scheme, host.toLowerCase(Locale.ROOT), port);
    }

    private static String normalizeHttpScheme(String scheme) {
        if (scheme == null) {
            return null;
        }
        String normalized = scheme.toLowerCase(Locale.ROOT);
        if (!normalized.equals("http") && !normalized.equals("https")) {
            return null;
        }
        return normalized;
    }

    private static int effectivePort(URI uri) {
        int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return -1;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        return -1;
    }

    private static boolean containsHeaderBreakingChars(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0;
    }

    private static Set<Origin> parseAllowedOrigins(String csv) {
        String normalized = normalizeInput(csv);
        if (normalized == null) {
            return Set.of();
        }

        return Arrays.stream(normalized.split(","))
                .map(AuthRedirectService::tryParseHttpOrigin)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }
}
