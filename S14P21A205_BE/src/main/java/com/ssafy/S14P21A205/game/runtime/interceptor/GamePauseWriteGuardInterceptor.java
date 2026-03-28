package com.ssafy.S14P21A205.game.runtime.interceptor;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.runtime.service.GameRuntimeControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class GamePauseWriteGuardInterceptor implements HandlerInterceptor {

    private final GameRuntimeControlService gameRuntimeControlService;

    @Override
    public boolean preHandle(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            Object handler
    ) {
        if (gameRuntimeControlService.isPaused()) {
            throw new BaseException(ErrorCode.GAME_PAUSED, "Game is temporarily paused by an administrator.");
        }
        return true;
    }
}
