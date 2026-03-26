package com.ssafy.S14P21A205.auth.service;

import com.ssafy.S14P21A205.auth.dto.AuthMeResponse;
import com.ssafy.S14P21A205.auth.dto.AuthTokenResponse;
import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 용도: access/refresh JWT 발급, 회전, 폐기 처리. */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final String ISSUER = "S14P21A205";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String PROVIDER_CLAIM = "provider";
    private static final String PROVIDER_USER_ID_CLAIM = "providerUserId";
    private static final String EMAIL_CLAIM = "email";
    private static final String NAME_CLAIM = "name";
    private static final String PICTURE_CLAIM = "picture";
    private static final String NICKNAME_CLAIM = "nickname";
    private static final String ROLE_CLAIM = "role";
    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";

    private record TokenPayload(
            String userId,
            String provider,
            String providerUserId,
            String email,
            String name,
            String picture,
            String nickname,
            String role
    ) {
    }

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;

    @Value("${app.jwt.access-token-ttl:P1D}")
    private Duration accessTokenTtl;

    @Value("${app.jwt.refresh-token-ttl:P1D}")
    private Duration refreshTokenTtl;

    /** 용도: OAuth 로그인 성공 직후 access/refresh 토큰 발급. */
    public AuthTokenResponse issueTokens(User user, AuthMeResponse profile) {
        TokenPayload payload = new TokenPayload(
                user.getId().toString(),
                profile == null ? null : profile.provider(),
                profile == null ? null : profile.subject(),
                profile == null ? null : profile.email(),
                profile == null ? null : profile.name(),
                profile == null ? null : profile.picture(),
                user.getNickname(),
                user.getRole() == null ? null : user.getRole().name()
        );
        return issueTokens(payload);
    }

    /** 용도: refresh token으로 access/refresh 토큰 회전 재발급. */
    public AuthTokenResponse refresh(String refreshToken) {
        Jwt jwt = decode(refreshToken);
        validateRefreshToken(jwt);

        String refreshId = asString(jwt.getClaim(JwtClaimNames.JTI));
        String storedUserId = stringRedisTemplate.opsForValue().get(refreshKey(refreshId));
        if (!StringUtils.hasText(storedUserId) || !storedUserId.equals(jwt.getSubject())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }

        stringRedisTemplate.delete(refreshKey(refreshId));

        User user = findUser(jwt.getSubject());
        TokenPayload payload = new TokenPayload(
                user.getId().toString(),
                asString(jwt.getClaim(PROVIDER_CLAIM)),
                asString(jwt.getClaim(PROVIDER_USER_ID_CLAIM)),
                asString(jwt.getClaim(EMAIL_CLAIM)),
                asString(jwt.getClaim(NAME_CLAIM)),
                asString(jwt.getClaim(PICTURE_CLAIM)),
                user.getNickname(),
                user.getRole() == null ? null : user.getRole().name()
        );
        return issueTokens(payload);
    }

    /** 용도: refresh token 저장소에서 폐기. */
    public void revoke(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        Jwt jwt = decode(refreshToken);
        validateRefreshToken(jwt);
        String refreshId = asString(jwt.getClaim(JwtClaimNames.JTI));
        if (StringUtils.hasText(refreshId)) {
            stringRedisTemplate.delete(refreshKey(refreshId));
        }
    }

    private AuthTokenResponse issueTokens(TokenPayload payload) {
        String accessToken = encodeToken(payload, "access", accessTokenTtl, null);
        String refreshId = UUID.randomUUID().toString();
        String refreshToken = encodeToken(payload, "refresh", refreshTokenTtl, refreshId);
        stringRedisTemplate.opsForValue().set(
                refreshKey(refreshId),
                payload.userId(),
                refreshTokenTtl
        );
        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                accessTokenTtl.toSeconds(),
                refreshTokenTtl.toSeconds()
        );
    }

    private String encodeToken(TokenPayload payload, String tokenType, Duration ttl, String refreshId) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(payload.userId())
                .claim(TOKEN_TYPE_CLAIM, tokenType);

        addClaimIfPresent(claims, PROVIDER_CLAIM, payload.provider());
        addClaimIfPresent(claims, PROVIDER_USER_ID_CLAIM, payload.providerUserId());
        addClaimIfPresent(claims, EMAIL_CLAIM, payload.email());
        addClaimIfPresent(claims, NAME_CLAIM, payload.name());
        addClaimIfPresent(claims, PICTURE_CLAIM, payload.picture());
        addClaimIfPresent(claims, NICKNAME_CLAIM, payload.nickname());
        addClaimIfPresent(claims, ROLE_CLAIM, payload.role());

        if (StringUtils.hasText(refreshId)) {
            claims.id(refreshId);
        }

        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build()
        )).getTokenValue();
    }

    private void addClaimIfPresent(JwtClaimsSet.Builder claims, String claimName, Object value) {
        if (value != null) {
            claims.claim(claimName, value);
        }
    }

    private Jwt decode(String tokenValue) {
        try {
            return jwtDecoder.decode(tokenValue);
        } catch (JwtException e) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void validateRefreshToken(Jwt jwt) {
        String tokenType = asString(jwt.getClaim(TOKEN_TYPE_CLAIM));
        String refreshId = asString(jwt.getClaim(JwtClaimNames.JTI));
        if (!"refresh".equals(tokenType) || !StringUtils.hasText(refreshId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }

    private User findUser(String rawUserId) {
        if (!StringUtils.hasText(rawUserId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
        try {
            Integer userId = Integer.parseInt(rawUserId);
            return userRepository.findById(userId)
                    .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        } catch (NumberFormatException e) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String refreshKey(String refreshId) {
        return REFRESH_KEY_PREFIX + refreshId;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
