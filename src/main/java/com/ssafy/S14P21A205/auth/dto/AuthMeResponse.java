package com.ssafy.S14P21A205.auth.dto;

import com.ssafy.S14P21A205.user.entity.User;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** 용도: 현재 로그인 사용자 정보 응답 DTO. */
public record AuthMeResponse(
        String subject,
        String email,
        String name,
        String picture,
        String nickname,
        String role
) {

    /** 용도: OIDC 사용자 객체를 DTO로 변환. */
    public static AuthMeResponse from(OidcUser oidcUser) {
        if (oidcUser == null) {
            return null;
        }
        return new AuthMeResponse(
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                oidcUser.getFullName(),
                oidcUser.getPicture(),
                null,
                null
        );
    }

    /** 용도: OIDC 정보와 DB 사용자 정보를 함께 담아 반환. */
    public static AuthMeResponse from(OidcUser oidcUser, User user) {
        if (oidcUser == null) {
            return null;
        }
        return new AuthMeResponse(
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                oidcUser.getFullName(),
                oidcUser.getPicture(),
                user == null ? null : user.getNickname(),
                (user == null || user.getRole() == null) ? null : user.getRole().name()
        );
    }
}
