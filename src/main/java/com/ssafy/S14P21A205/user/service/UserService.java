package com.ssafy.S14P21A205.user.service;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 용도: 이메일 기준 사용자 생성/조회 및 닉네임 변경 처리. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /** 용도: 이메일 기준 사용자 upsert. */
    @Transactional
    public User upsertByEmail(String rawEmail) {
        String email = rawEmail == null ? null : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(email)) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return userRepository.findByEmail(email)
                .orElseGet(() -> createOrGet(email));
    }

    /** 용도: 이메일 기준 닉네임 변경. */
    @Transactional
    public User changeNickname(String rawEmail, String rawNickname) {
        String email = rawEmail == null ? null : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(email)) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String nickname = rawNickname == null ? null : rawNickname.trim();
        if (!StringUtils.hasText(nickname) || nickname.length() > 30) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createOrGet(email));
        user.changeNickname(nickname);
        return user;
    }

    /** 용도: 인증 사용자 기준 닉네임 변경. */
    @Transactional
    public User changeMyNickname(OidcUser oidcUser, String rawNickname) {
        String email = oidcUser == null ? null : oidcUser.getEmail();
        if (!StringUtils.hasText(email)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }

        return changeNickname(email, rawNickname);
    }

    private User createOrGet(String email) {
        try {
            return userRepository.save(new User(email));
        } catch (DataIntegrityViolationException e) {
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> e);
        }
    }
}
