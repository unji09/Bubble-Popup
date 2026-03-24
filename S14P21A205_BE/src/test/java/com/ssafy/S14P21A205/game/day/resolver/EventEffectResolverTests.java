package com.ssafy.S14P21A205.game.day.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.game.day.dto.GameStateResponse;
import com.ssafy.S14P21A205.game.event.entity.DailyEvent;
import com.ssafy.S14P21A205.game.event.entity.EventCategory;
import com.ssafy.S14P21A205.game.event.entity.EventEndTime;
import com.ssafy.S14P21A205.game.event.entity.EventStartTime;
import com.ssafy.S14P21A205.game.event.entity.RandomEvent;
import com.ssafy.S14P21A205.game.event.repository.DailyEventRepository;
import com.ssafy.S14P21A205.game.season.entity.Season;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventEffectResolverTests {

    @Mock
    private DailyEventRepository dailyEventRepository;

    @Test
    void resolveAppliesScopedEventWhenLocationAndMenuMatch() {
        EventEffectResolver resolver = new EventEffectResolver(dailyEventRepository);
        Season season = season();
        DailyEvent dailyEvent = dailyEvent(season, 1, EventCategory.CELEBRITY_APPEARANCE, "celebrity", "1.50", "2.00", "1.05", 200, EventStartTime.IMMEDIATE, EventEndTime.SAME_DAY, 3L, 7L);
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(1L, 1, 1))
                .thenReturn(List.of(dailyEvent));

        EventEffectResolver.EventEffect effect = resolver.resolve(
                season,
                1,
                LocalDateTime.of(2026, 3, 17, 9, 1, 30),
                3L,
                7L
        );

        assertThat(effect.capitalChange()).isEqualTo(200L);
        assertThat(effect.stockChange()).isEqualTo(2);
        assertThat(effect.populationEventMultiplier()).isEqualByComparingTo("1.50");
        assertThat(effect.ingredientCostMultiplier()).isEqualByComparingTo("1.05");
        assertThat(effect.appliedEvents()).hasSize(1);
        assertThat(effect.appliedEvents().get(0).eventType()).isEqualTo("CELEBRITY_APPEARANCE");
        assertThat(effect.appliedEvents().get(0).eventName()).isEqualTo("celebrity");
    }

    @Test
    void resolveIgnoresScopedEventWhenLocationDoesNotMatch() {
        EventEffectResolver resolver = new EventEffectResolver(dailyEventRepository);
        Season season = season();
        DailyEvent dailyEvent = dailyEvent(season, 1, EventCategory.CELEBRITY_APPEARANCE, "celebrity", "1.50", "2.00", "1.05", 200, EventStartTime.IMMEDIATE, EventEndTime.SAME_DAY, 3L, null);
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(1L, 1, 1))
                .thenReturn(List.of(dailyEvent));

        EventEffectResolver.EventEffect effect = resolver.resolve(
                season,
                1,
                LocalDateTime.of(2026, 3, 17, 9, 1, 30),
                9L,
                7L
        );

        assertThat(effect.capitalChange()).isZero();
        assertThat(effect.stockChange()).isZero();
        assertThat(effect.populationEventMultiplier()).isEqualByComparingTo("1.00");
        assertThat(effect.ingredientCostMultiplier()).isEqualByComparingTo("1.00");
        assertThat(effect.appliedEvents()).isEmpty();
    }

    @Test
    void resolveAppliesMenuCostDownFromNextDay() {
        EventEffectResolver resolver = new EventEffectResolver(dailyEventRepository);
        Season season = season();
        DailyEvent menuCostDownEvent = dailyEvent(
                season,
                1,
                EventCategory.TACO_PRICE_DOWN,
                "taco price down",
                "1.00",
                "0.00",
                "0.95",
                0,
                EventStartTime.NEXT_DAY,
                EventEndTime.SEASON_END,
                null,
                7L
        );
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(1L, 1, 1))
                .thenReturn(List.of(menuCostDownEvent));
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(1L, 1, 2))
                .thenReturn(List.of(menuCostDownEvent));

        EventEffectResolver.EventEffect dayOneEffect = resolver.resolve(
                season,
                1,
                LocalDateTime.of(2026, 3, 17, 9, 1, 30),
                3L,
                7L
        );
        EventEffectResolver.EventEffect dayTwoEffect = resolver.resolve(
                season,
                2,
                LocalDateTime.of(2026, 3, 17, 9, 3, 30),
                3L,
                7L
        );

        assertThat(dayOneEffect.ingredientCostMultiplier()).isEqualByComparingTo("1.00");
        assertThat(dayOneEffect.appliedEvents()).isEmpty();
        assertThat(dayTwoEffect.ingredientCostMultiplier()).isEqualByComparingTo("0.95");
        assertThat(dayTwoEffect.appliedEvents()).extracting(GameStateResponse.AppliedEvent::eventType)
                .containsExactly("TACO_PRICE_DOWN");
    }

    @Test
    void resolveMultipliesOverlappingMenuCostEvents() {
        EventEffectResolver resolver = new EventEffectResolver(dailyEventRepository);
        Season season = season();
        DailyEvent menuCostDownEvent = dailyEvent(
                season,
                1,
                EventCategory.TACO_PRICE_DOWN,
                "taco price down",
                "1.00",
                "0.00",
                "0.95",
                0,
                EventStartTime.NEXT_DAY,
                EventEndTime.SEASON_END,
                null,
                7L
        );
        DailyEvent menuCostUpEvent = dailyEvent(
                season,
                2,
                EventCategory.TACO_PRICE_UP,
                "taco price up",
                "1.00",
                "0.00",
                "1.05",
                0,
                EventStartTime.NEXT_DAY,
                EventEndTime.SEASON_END,
                null,
                7L
        );
        when(dailyEventRepository.findBySeasonIdAndDayBetweenOrderByDayAscIdAsc(1L, 1, 3))
                .thenReturn(List.of(menuCostDownEvent, menuCostUpEvent));

        EventEffectResolver.EventEffect effect = resolver.resolve(
                season,
                3,
                LocalDateTime.of(2026, 3, 17, 9, 6, 30),
                3L,
                7L
        );

        assertThat(effect.ingredientCostMultiplier()).isEqualByComparingTo("0.9975");
        assertThat(effect.appliedEvents()).extracting(GameStateResponse.AppliedEvent::eventType)
                .containsExactly("TACO_PRICE_DOWN", "TACO_PRICE_UP");
    }

    private Season season() {
        Season season = instantiate(Season.class);
        ReflectionTestUtils.setField(season, "id", 1L);
        ReflectionTestUtils.setField(season, "totalDays", 7);
        ReflectionTestUtils.setField(season, "startTime", LocalDateTime.of(2026, 3, 17, 8, 57, 10));
        return season;
    }

    private DailyEvent dailyEvent(
            Season season,
            int day,
            EventCategory category,
            String eventName,
            String populationRate,
            String stockFlat,
            String costRate,
            Integer capitalFlat,
            EventStartTime startTime,
            EventEndTime endTime,
            Long targetLocationId,
            Long targetMenuId
    ) {
        RandomEvent randomEvent = instantiate(RandomEvent.class);
        ReflectionTestUtils.setField(randomEvent, "id", 2L);
        ReflectionTestUtils.setField(randomEvent, "eventCategory", category);
        ReflectionTestUtils.setField(randomEvent, "eventName", eventName);
        ReflectionTestUtils.setField(randomEvent, "startTime", startTime);
        ReflectionTestUtils.setField(randomEvent, "endTime", endTime);
        ReflectionTestUtils.setField(randomEvent, "populationRate", new BigDecimal(populationRate));
        ReflectionTestUtils.setField(randomEvent, "stockFlat", new BigDecimal(stockFlat));
        ReflectionTestUtils.setField(randomEvent, "costRate", new BigDecimal(costRate));
        ReflectionTestUtils.setField(randomEvent, "capitalFlat", capitalFlat);

        DailyEvent dailyEvent = instantiate(DailyEvent.class);
        ReflectionTestUtils.setField(dailyEvent, "id", 10L + day);
        ReflectionTestUtils.setField(dailyEvent, "season", season);
        ReflectionTestUtils.setField(dailyEvent, "event", randomEvent);
        ReflectionTestUtils.setField(dailyEvent, "day", day);
        ReflectionTestUtils.setField(dailyEvent, "applyOffsetSeconds", 0);
        ReflectionTestUtils.setField(dailyEvent, "expireOffsetSeconds", endTime == EventEndTime.SEASON_END ? null : 120);
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
