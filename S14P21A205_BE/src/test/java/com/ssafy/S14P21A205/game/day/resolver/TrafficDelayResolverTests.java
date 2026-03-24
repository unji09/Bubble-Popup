package com.ssafy.S14P21A205.game.day.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.game.environment.entity.Traffic;
import com.ssafy.S14P21A205.game.environment.entity.TrafficStatus;
import com.ssafy.S14P21A205.game.environment.repository.TrafficDayRedisRepository;
import com.ssafy.S14P21A205.game.environment.repository.TrafficRepository;
import com.ssafy.S14P21A205.store.entity.Location;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TrafficDelayResolverTests {

    @Mock
    private TrafficDayRedisRepository trafficDayRedisRepository;

    @Mock
    private TrafficRepository trafficRepository;

    private TrafficDelayResolver trafficDelayResolver;

    @BeforeEach
    void setUp() {
        trafficDelayResolver = new TrafficDelayResolver(trafficDayRedisRepository, trafficRepository);
    }

    @Test
    void resolveReturnsRedisTrafficForExactDayAndHour() {
        when(trafficDayRedisRepository.findHour(9L, 3L, 2, 13))
                .thenReturn(Optional.of(new TrafficDayRedisRepository.TrafficEntry(13, TrafficStatus.CONGESTED)));

        TrafficDelayResolver.ResolvedTraffic resolvedTraffic = trafficDelayResolver.resolve(
                9L,
                3L,
                2,
                7,
                LocalDateTime.of(2026, 3, 17, 10, 0),
                LocalDateTime.of(2026, 3, 17, 10, 1, 15)
        );

        assertThat(resolvedTraffic.resolvedDay()).isEqualTo(2);
        assertThat(resolvedTraffic.resolvedHour()).isEqualTo(13);
        assertThat(resolvedTraffic.trafficStatus()).isEqualTo(TrafficStatus.CONGESTED);
        assertThat(resolvedTraffic.delaySeconds()).isEqualTo(25);
    }

    @Test
    void resolveFallsBackToFixedDayTableWhenRedisMisses() {
        when(trafficDayRedisRepository.findHour(9L, 3L, 2, 12)).thenReturn(Optional.empty());
        when(trafficRepository.findByLocation_IdAndDateBetweenOrderByDateAsc(
                3L,
                LocalDateTime.of(2026, 3, 17, 0, 0),
                LocalDateTime.of(2026, 3, 17, 23, 59, 59, 999_999_999)
        )).thenReturn(List.of(
                traffic(3L, LocalDateTime.of(2026, 3, 17, 10, 0), TrafficStatus.SMOOTH),
                traffic(3L, LocalDateTime.of(2026, 3, 17, 12, 0), TrafficStatus.VERY_SMOOTH)
        ));

        TrafficDelayResolver.ResolvedTraffic resolvedTraffic = trafficDelayResolver.resolve(
                9L,
                3L,
                2,
                7,
                LocalDateTime.of(2026, 3, 17, 10, 0),
                LocalDateTime.of(2026, 3, 17, 10, 1, 0)
        );

        assertThat(resolvedTraffic.resolvedDay()).isEqualTo(2);
        assertThat(resolvedTraffic.resolvedHour()).isEqualTo(12);
        assertThat(resolvedTraffic.trafficStatus()).isEqualTo(TrafficStatus.VERY_SMOOTH);
        assertThat(resolvedTraffic.delaySeconds()).isEqualTo(5);
    }

    @Test
    void resolveReturnsNormalFallbackWhenFixedDayHourIsMissing() {
        when(trafficDayRedisRepository.findHour(9L, 3L, 2, 15)).thenReturn(Optional.empty());
        when(trafficRepository.findByLocation_IdAndDateBetweenOrderByDateAsc(
                3L,
                LocalDateTime.of(2026, 3, 17, 0, 0),
                LocalDateTime.of(2026, 3, 17, 23, 59, 59, 999_999_999)
        )).thenReturn(List.of(
                traffic(3L, LocalDateTime.of(2026, 3, 17, 10, 0), TrafficStatus.SMOOTH)
        ));

        TrafficDelayResolver.ResolvedTraffic resolvedTraffic = trafficDelayResolver.resolve(
                9L,
                3L,
                2,
                7,
                LocalDateTime.of(2026, 3, 17, 10, 0),
                LocalDateTime.of(2026, 3, 17, 10, 1, 35)
        );

        assertThat(resolvedTraffic.resolvedDay()).isEqualTo(2);
        assertThat(resolvedTraffic.resolvedHour()).isEqualTo(15);
        assertThat(resolvedTraffic.trafficStatus()).isEqualTo(TrafficStatus.NORMAL);
        assertThat(resolvedTraffic.delaySeconds()).isEqualTo(20);
    }

    @Test
    void resolveReturnsNormalFallbackWhenSeasonTrafficIsMissing() {
        when(trafficDayRedisRepository.findHour(9L, 3L, 1, 12)).thenReturn(Optional.empty());
        when(trafficRepository.findByLocation_IdAndDateBetweenOrderByDateAsc(
                3L,
                LocalDateTime.of(2026, 3, 17, 0, 0),
                LocalDateTime.of(2026, 3, 17, 23, 59, 59, 999_999_999)
        )).thenReturn(List.of());

        TrafficDelayResolver.ResolvedTraffic resolvedTraffic = trafficDelayResolver.resolve(
                9L,
                3L,
                1,
                7,
                LocalDateTime.of(2026, 3, 17, 10, 0),
                LocalDateTime.of(2026, 3, 17, 10, 1, 0)
        );

        assertThat(resolvedTraffic.resolvedDay()).isEqualTo(1);
        assertThat(resolvedTraffic.resolvedHour()).isEqualTo(12);
        assertThat(resolvedTraffic.trafficStatus()).isEqualTo(TrafficStatus.NORMAL);
        assertThat(resolvedTraffic.delaySeconds()).isEqualTo(20);
    }

    private Traffic traffic(Long locationId, LocalDateTime dateTime, TrafficStatus trafficStatus) {
        Location location = instantiate(Location.class);
        ReflectionTestUtils.setField(location, "id", locationId);

        Traffic traffic = instantiate(Traffic.class);
        ReflectionTestUtils.setField(traffic, "location", location);
        ReflectionTestUtils.setField(traffic, "date", dateTime);
        ReflectionTestUtils.setField(traffic, "trafficStatus", trafficStatus);
        return traffic;
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
