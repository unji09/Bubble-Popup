package com.ssafy.S14P21A205.game.time.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.S14P21A205.game.time.model.SeasonPhase;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GameTimePolicyTests {

    private static final LocalDateTime SEASON_START_AT = LocalDateTime.of(2026, 3, 18, 10, 0, 0);
    private static final int TOTAL_DAYS = 7;

    private final GameTimePolicy gameTimePolicy = new GameTimePolicy();

    @Test
    void resolvesSeasonPhaseAcrossConfiguredBoundaries() {
        assertThat(gameTimePolicy.resolveSeasonPhase(SEASON_START_AT, TOTAL_DAYS, SEASON_START_AT))
                .isEqualTo(SeasonPhase.LOCATION_SELECTION);
        assertThat(gameTimePolicy.resolveSeasonPhase(SEASON_START_AT, TOTAL_DAYS, SEASON_START_AT.plusSeconds(59)))
                .isEqualTo(SeasonPhase.LOCATION_SELECTION);
        assertThat(gameTimePolicy.resolveSeasonPhase(SEASON_START_AT, TOTAL_DAYS, SEASON_START_AT.plusSeconds(60)))
                .isEqualTo(SeasonPhase.DAY_PREPARING);
        assertThat(gameTimePolicy.resolveSeasonPhase(SEASON_START_AT, TOTAL_DAYS, SEASON_START_AT.plusSeconds(100)))
                .isEqualTo(SeasonPhase.DAY_BUSINESS);
        assertThat(gameTimePolicy.resolveSeasonPhase(SEASON_START_AT, TOTAL_DAYS, SEASON_START_AT.plusSeconds(220)))
                .isEqualTo(SeasonPhase.DAY_REPORT);
        assertThat(gameTimePolicy.resolveSeasonPhase(SEASON_START_AT, TOTAL_DAYS, SEASON_START_AT.plusSeconds(240)))
                .isEqualTo(SeasonPhase.DAY_PREPARING);
        assertThat(gameTimePolicy.resolveSeasonPhase(SEASON_START_AT, TOTAL_DAYS, SEASON_START_AT.plusSeconds(1320)))
                .isEqualTo(SeasonPhase.SEASON_SUMMARY);
        assertThat(gameTimePolicy.resolveSeasonPhase(SEASON_START_AT, TOTAL_DAYS, SEASON_START_AT.plusSeconds(1500)))
                .isEqualTo(SeasonPhase.NEXT_SEASON_WAITING);
        assertThat(gameTimePolicy.resolveSeasonPhase(SEASON_START_AT, TOTAL_DAYS, SEASON_START_AT.plusSeconds(1800)))
                .isEqualTo(SeasonPhase.CLOSED);
    }

    @Test
    void convertsBusinessElapsedSecondsToGameTimeAndTick() {
        LocalDateTime dayOneBusinessStart = SEASON_START_AT.plusSeconds(100);

        assertThat(gameTimePolicy.resolveGameTime(SEASON_START_AT, TOTAL_DAYS, dayOneBusinessStart))
                .isEqualTo("10:00");
        assertThat(gameTimePolicy.resolveTick(SEASON_START_AT, TOTAL_DAYS, dayOneBusinessStart))
                .isEqualTo(0);

        assertThat(gameTimePolicy.resolveGameTime(SEASON_START_AT, TOTAL_DAYS, dayOneBusinessStart.plusSeconds(40)))
                .isEqualTo("14:00");
        assertThat(gameTimePolicy.resolveTick(SEASON_START_AT, TOTAL_DAYS, dayOneBusinessStart.plusSeconds(40)))
                .isEqualTo(4);

        assertThat(gameTimePolicy.resolveGameTime(SEASON_START_AT, TOTAL_DAYS, dayOneBusinessStart.plusSeconds(80)))
                .isEqualTo("18:00");
        assertThat(gameTimePolicy.resolveTick(SEASON_START_AT, TOTAL_DAYS, dayOneBusinessStart.plusSeconds(80)))
                .isEqualTo(8);

        assertThat(gameTimePolicy.formatGameTime(120)).isEqualTo("22:00");
        assertThat(gameTimePolicy.totalTickCount()).isEqualTo(12);
    }

    @Test
    void resolvesJoinPlayableFromDayByPhase() {
        assertThat(gameTimePolicy.resolveJoinPlayableFromDay(SEASON_START_AT, TOTAL_DAYS, SEASON_START_AT.plusSeconds(30)))
                .isEqualTo(1);

        LocalDateTime dayTwoBusiness = SEASON_START_AT.plusSeconds(300);
        assertThat(gameTimePolicy.resolveJoinPlayableFromDay(SEASON_START_AT, TOTAL_DAYS, dayTwoBusiness))
                .isEqualTo(3);
        assertThat(gameTimePolicy.isJoinEnabled(SEASON_START_AT, TOTAL_DAYS, dayTwoBusiness)).isTrue();

        LocalDateTime dayFiveReport = SEASON_START_AT.plusSeconds(60 + 4L * 180L + 170L);
        assertThat(gameTimePolicy.resolveJoinPlayableFromDay(SEASON_START_AT, TOTAL_DAYS, dayFiveReport))
                .isEqualTo(6);
        assertThat(gameTimePolicy.isJoinEnabled(SEASON_START_AT, TOTAL_DAYS, dayFiveReport)).isTrue();

        LocalDateTime daySixBusiness = SEASON_START_AT.plusSeconds(60 + 5L * 180L + 40L);
        assertThat(gameTimePolicy.resolveJoinPlayableFromDay(SEASON_START_AT, TOTAL_DAYS, daySixBusiness))
                .isNull();
        assertThat(gameTimePolicy.isJoinEnabled(SEASON_START_AT, TOTAL_DAYS, daySixBusiness)).isFalse();

        LocalDateTime daySevenReport = SEASON_START_AT.plusSeconds(1310);
        assertThat(gameTimePolicy.resolveJoinPlayableFromDay(SEASON_START_AT, TOTAL_DAYS, daySevenReport))
                .isNull();
        assertThat(gameTimePolicy.isJoinEnabled(SEASON_START_AT, TOTAL_DAYS, daySevenReport)).isFalse();
    }
}
