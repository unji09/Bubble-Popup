package com.ssafy.S14P21A205.game.day.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.game.day.dto.GameDayStartResponse;
import com.ssafy.S14P21A205.game.event.entity.DailyEvent;
import com.ssafy.S14P21A205.game.event.entity.EventCategory;
import com.ssafy.S14P21A205.game.event.entity.EventEndTime;
import com.ssafy.S14P21A205.game.event.entity.EventStartTime;
import com.ssafy.S14P21A205.game.event.entity.RandomEvent;
import com.ssafy.S14P21A205.game.event.repository.DailyEventRepository;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventScheduleResolverTests {

    @Mock
    private DailyEventRepository dailyEventRepository;

    @Test
    void resolveSuppressesDuplicateScheduleForSameLogicalEvent() {
        EventScheduleResolver resolver = new EventScheduleResolver(dailyEventRepository);
        DailyEvent firstEvent = dailyEvent(
                11L,
                101L,
                1,
                EventCategory.SUBSTITUTE_HOLIDAY,
                "Substitute Holiday",
                EventStartTime.NEXT_DAY,
                EventEndTime.SAME_DAY,
                null,
                null
        );
        DailyEvent duplicatedEvent = dailyEvent(
                12L,
                102L,
                1,
                EventCategory.SUBSTITUTE_HOLIDAY,
                "Substitute Holiday",
                EventStartTime.NEXT_DAY,
                EventEndTime.SAME_DAY,
                null,
                null
        );
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(1L, 1, 2))
                .thenReturn(List.of(firstEvent, duplicatedEvent));

        List<GameDayStartResponse.EventSchedule> schedules = resolver.resolve(1L, 2, 3L, 7L);

        assertThat(schedules).hasSize(1);
        assertThat(schedules.get(0).type()).isEqualTo("Substitute Holiday");
    }

    private DailyEvent dailyEvent(
            Long dailyEventId,
            Long randomEventId,
            int day,
            EventCategory category,
            String eventName,
            EventStartTime startTime,
            EventEndTime endTime,
            Long targetLocationId,
            Long targetMenuId
    ) {
        RandomEvent randomEvent = instantiate(RandomEvent.class);
        ReflectionTestUtils.setField(randomEvent, "id", randomEventId);
        ReflectionTestUtils.setField(randomEvent, "eventCategory", category);
        ReflectionTestUtils.setField(randomEvent, "eventName", eventName);
        ReflectionTestUtils.setField(randomEvent, "startTime", startTime);
        ReflectionTestUtils.setField(randomEvent, "endTime", endTime);
        ReflectionTestUtils.setField(randomEvent, "populationRate", new BigDecimal("1.10"));
        ReflectionTestUtils.setField(randomEvent, "stockFlat", BigDecimal.ZERO);
        ReflectionTestUtils.setField(randomEvent, "costRate", BigDecimal.ONE);
        ReflectionTestUtils.setField(randomEvent, "capitalFlat", 0);

        DailyEvent dailyEvent = instantiate(DailyEvent.class);
        ReflectionTestUtils.setField(dailyEvent, "id", dailyEventId);
        ReflectionTestUtils.setField(dailyEvent, "event", randomEvent);
        ReflectionTestUtils.setField(dailyEvent, "day", day);
        ReflectionTestUtils.setField(dailyEvent, "applyOffsetSeconds", 40);
        ReflectionTestUtils.setField(dailyEvent, "expireOffsetSeconds", 120);
        ReflectionTestUtils.setField(dailyEvent, "targetLocationId", targetLocationId);
        ReflectionTestUtils.setField(dailyEvent, "targetMenuId", targetMenuId);
        return dailyEvent;
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
