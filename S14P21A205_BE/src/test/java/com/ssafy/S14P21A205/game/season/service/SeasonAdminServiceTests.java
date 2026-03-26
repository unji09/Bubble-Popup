package com.ssafy.S14P21A205.game.season.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.season.dto.SeasonDemoSkipRequest;
import com.ssafy.S14P21A205.game.season.dto.SeasonDemoSkipResponse;
import com.ssafy.S14P21A205.game.season.entity.DemoSkipStatus;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.user.service.UserService;
import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeasonAdminServiceTests {

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private UserService userService;

    private SeasonAdminService seasonAdminService;

    @BeforeEach
    void setUp() {
        seasonAdminService = new SeasonAdminService(
                seasonRepository,
                userService,
                Clock.fixed(Instant.parse("2026-03-18T00:59:00Z"), ZoneId.of("Asia/Seoul"))
        );
    }

    @Test
    void reserveDemoSkipMarksScheduledSeasonAsReserved() {
        User admin = user(1, User.UserRole.ADMIN);
        Season season = season(12L, SeasonStatus.SCHEDULED, LocalDateTime.of(2026, 3, 18, 10, 0, 0));

        when(userService.getCurrentUser(any(Authentication.class))).thenReturn(admin);
        when(seasonRepository.findByIdAndStatus(12L, SeasonStatus.SCHEDULED)).thenReturn(Optional.of(season));

        SeasonDemoSkipResponse response = seasonAdminService.reserveDemoSkip(
                org.mockito.Mockito.mock(Authentication.class),
                new SeasonDemoSkipRequest(12L)
        );

        assertThat(response.seasonId()).isEqualTo(12L);
        assertThat(response.status()).isEqualTo("RESERVED");
        assertThat(response.demoPlayableDays()).isEqualTo(3);
        assertThat(season.getDemoSkipStatus()).isEqualTo(DemoSkipStatus.RESERVED);
        assertThat(season.getDemoPlayableDays()).isEqualTo(3);
    }

    @Test
    void reserveDemoSkipRejectsNonAdminUser() {
        User generalUser = user(2, User.UserRole.GENERAL);

        when(userService.getCurrentUser(any(Authentication.class))).thenReturn(generalUser);

        assertThatThrownBy(() -> seasonAdminService.reserveDemoSkip(
                org.mockito.Mockito.mock(Authentication.class),
                new SeasonDemoSkipRequest(12L)
        ))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void reserveDemoSkipRejectsAlreadyStartedSeason() {
        User admin = user(1, User.UserRole.ADMIN);
        Season season = season(12L, SeasonStatus.SCHEDULED, LocalDateTime.of(2026, 3, 18, 9, 30, 0));

        when(userService.getCurrentUser(any(Authentication.class))).thenReturn(admin);
        when(seasonRepository.findByIdAndStatus(12L, SeasonStatus.SCHEDULED)).thenReturn(Optional.of(season));

        assertThatThrownBy(() -> seasonAdminService.reserveDemoSkip(
                org.mockito.Mockito.mock(Authentication.class),
                new SeasonDemoSkipRequest(12L)
        ))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEASON_STATE_CONFLICT);
    }

    private User user(Integer id, User.UserRole role) {
        User user = new User("admin-%d@test.com".formatted(id), "admin-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    private Season season(Long id, SeasonStatus status, LocalDateTime startTime) {
        Season season = instantiate(Season.class);
        ReflectionTestUtils.setField(season, "id", id);
        ReflectionTestUtils.setField(season, "status", status);
        ReflectionTestUtils.setField(season, "totalDays", 7);
        ReflectionTestUtils.setField(season, "demoSkipStatus", DemoSkipStatus.NONE);
        ReflectionTestUtils.setField(season, "startTime", startTime);
        return season;
    }

    private <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
