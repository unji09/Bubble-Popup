package com.ssafy.S14P21A205.config;

import com.ssafy.S14P21A205.game.runtime.interceptor.GamePauseWriteGuardInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class GamePauseWebConfig implements WebMvcConfigurer {

    private final GamePauseWriteGuardInterceptor gamePauseWriteGuardInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(gamePauseWriteGuardInterceptor)
                .addPathPatterns(
                        "/actions/promotion",
                        "/actions/discount",
                        "/actions/donation",
                        "/actions/emergency-order",
                        "/orders/regular",
                        "/stores/location",
                        "/shop/purchase",
                        "/game/seasons/current/join"
                );
    }
}
