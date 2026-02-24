package com.ssafy.S14P21A205.user.api.v1;

import com.ssafy.S14P21A205.auth.dto.AuthMeResponse;
import com.ssafy.S14P21A205.user.dto.UserNicknameUpdateRequest;
import com.ssafy.S14P21A205.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserApi implements UserApiDoc {

    private final UserService userService;

    @PatchMapping("/me/nickname")
    @Override
    public ResponseEntity<AuthMeResponse> updateMyNickname(
            @Valid @RequestBody UserNicknameUpdateRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        var user = userService.changeMyNickname(oidcUser, request.nickname());
        return ResponseEntity.ok(AuthMeResponse.from(oidcUser, user));
    }
}
