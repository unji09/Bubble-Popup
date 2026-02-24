package com.ssafy.S14P21A205.config;

import com.ssafy.S14P21A205.security.handler.AuthLoginFailureHandler;
import com.ssafy.S14P21A205.security.handler.AuthLoginSuccessHandler;
import com.ssafy.S14P21A205.security.handler.RestAccessDeniedHandler;
import com.ssafy.S14P21A205.security.handler.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** 용도: 전역 보안 정책과 OAuth2 로그인 필터 체인 설정. */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] SWAGGER_WHITELIST = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private static final String[] PUBLIC_WHITELIST = {
            "/error",
            "/oauth2/**",
            "/login/oauth2/**",
            "/api/v1/auth/login",
            "/api/v1/auth/logout"
    };

    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final AuthLoginSuccessHandler authLoginSuccessHandler;
    private final AuthLoginFailureHandler authLoginFailureHandler;

    /** 용도: SecurityFilterChain 구성. */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(SWAGGER_WHITELIST).permitAll()
                        .requestMatchers(PUBLIC_WHITELIST).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(authLoginSuccessHandler)
                        .failureHandler(authLoginFailureHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("JSESSIONID", "SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204))
                );

        return http.build();
    }
}
