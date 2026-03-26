package com.ssafy.S14P21A205.game.time.policy;

import com.ssafy.S14P21A205.game.event.entity.EventEndTime;
import com.ssafy.S14P21A205.game.time.model.DayWindow;
import com.ssafy.S14P21A205.game.time.model.SeasonPhase;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class GameTimePolicy {

    public static final int BUSINESS_OPEN_HOUR = 10;
    public static final int BUSINESS_CLOSE_HOUR = 22;

    private static final Duration LOCATION_SELECTION_DURATION = Duration.ofMinutes(1);
    private static final Duration PREP_DURATION = Duration.ofSeconds(40);
    private static final Duration BUSINESS_DURATION = Duration.ofMinutes(2);
    private static final Duration REPORT_DURATION = Duration.ofSeconds(20);
    private static final Duration DAY_DURATION = PREP_DURATION.plus(BUSINESS_DURATION).plus(REPORT_DURATION);
    private static final Duration SEASON_SUMMARY_DURATION = Duration.ofMinutes(3);
    private static final Duration NEXT_SEASON_WAIT_DURATION = Duration.ofMinutes(5);
    private static final int GAME_BUSINESS_DURATION_MINUTES = (BUSINESS_CLOSE_HOUR - BUSINESS_OPEN_HOUR) * 60;
    private static final Duration TICK_INTERVAL = Duration.ofSeconds(10);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public DayWindow currentDay(LocalDateTime seasonStartTime, int totalDays, int currentDay) {
        return day(seasonStartTime, totalDays, currentDay);
    }

    public DayWindow day(LocalDateTime seasonStartTime, int totalDays, int targetDay) {
        int boundedDay = Math.max(1, Math.min(targetDay, totalDays));
        LocalDateTime dayStart = seasonStartTime
                .plus(LOCATION_SELECTION_DURATION)
                .plus(DAY_DURATION.multipliedBy(boundedDay - 1L));
        LocalDateTime businessStart = dayStart.plus(PREP_DURATION);
        LocalDateTime businessEnd = businessStart.plus(BUSINESS_DURATION);
        LocalDateTime reportEnd = businessEnd.plus(REPORT_DURATION);
        LocalDateTime seasonPlayableEnd = seasonStartTime.plus(playableDuration(totalDays));
        return new DayWindow(dayStart, businessStart, businessEnd, reportEnd, seasonPlayableEnd);
    }

    public LocalDateTime resolveAppliedAt(LocalDateTime seasonStartTime, int totalDays, int appliedDay, Integer offsetSeconds) {
        DayWindow appliedDayWindow = day(seasonStartTime, totalDays, appliedDay);
        return appliedDayWindow.businessStart().plusSeconds(normalizeOffsetSeconds(offsetSeconds));
    }

    public LocalDateTime resolveEndedAt(
            LocalDateTime seasonStartTime,
            int totalDays,
            int appliedDay,
            Integer expireOffsetSeconds,
            EventEndTime endTime
    ) {
        DayWindow appliedDayWindow = day(seasonStartTime, totalDays, appliedDay);
        if (expireOffsetSeconds != null && expireOffsetSeconds >= 0) {
            return appliedDayWindow.businessStart().plusSeconds(expireOffsetSeconds);
        }
        return endTime == EventEndTime.SEASON_END
                ? appliedDayWindow.seasonPlayableEnd()
                : appliedDayWindow.businessEnd();
    }

    public SeasonPhase resolveSeasonPhase(LocalDateTime seasonStartTime, int totalDays, LocalDateTime currentTime) {
        long elapsedSeconds = elapsedSeconds(seasonStartTime, currentTime);
        if (elapsedSeconds < 0L) {
            return SeasonPhase.CLOSED;
        }
        if (elapsedSeconds < LOCATION_SELECTION_DURATION.toSeconds()) {
            return SeasonPhase.LOCATION_SELECTION;
        }

        long playableElapsedSeconds = elapsedSeconds - LOCATION_SELECTION_DURATION.toSeconds();
        long playableDurationSeconds = DAY_DURATION.multipliedBy(totalDays).toSeconds();
        if (playableElapsedSeconds < playableDurationSeconds) {
            long offsetInDay = Math.floorMod(playableElapsedSeconds, DAY_DURATION.toSeconds());
            if (offsetInDay < PREP_DURATION.toSeconds()) {
                return SeasonPhase.DAY_PREPARING;
            }
            if (offsetInDay < PREP_DURATION.plus(BUSINESS_DURATION).toSeconds()) {
                return SeasonPhase.DAY_BUSINESS;
            }
            return SeasonPhase.DAY_REPORT;
        }

        long summaryElapsedSeconds = playableElapsedSeconds - playableDurationSeconds;
        if (summaryElapsedSeconds < SEASON_SUMMARY_DURATION.toSeconds()) {
            return SeasonPhase.SEASON_SUMMARY;
        }

        long waitingElapsedSeconds = summaryElapsedSeconds - SEASON_SUMMARY_DURATION.toSeconds();
        if (waitingElapsedSeconds < NEXT_SEASON_WAIT_DURATION.toSeconds()) {
            return SeasonPhase.NEXT_SEASON_WAITING;
        }
        return SeasonPhase.CLOSED;
    }

    public Integer resolveCurrentDay(LocalDateTime seasonStartTime, int totalDays, LocalDateTime currentTime) {
        SeasonPhase phase = resolveSeasonPhase(seasonStartTime, totalDays, currentTime);
        if (phase == SeasonPhase.LOCATION_SELECTION) {
            return 1;
        }
        if (phase == SeasonPhase.DAY_PREPARING || phase == SeasonPhase.DAY_BUSINESS || phase == SeasonPhase.DAY_REPORT) {
            long playableElapsedSeconds = elapsedSeconds(seasonStartTime, currentTime) - LOCATION_SELECTION_DURATION.toSeconds();
            return (int) Math.min(totalDays, (playableElapsedSeconds / DAY_DURATION.toSeconds()) + 1);
        }
        if (phase == SeasonPhase.SEASON_SUMMARY) {
            return totalDays;
        }
        return null;
    }

    public Integer resolveJoinPlayableFromDay(LocalDateTime seasonStartTime, int totalDays, LocalDateTime currentTime) {
        SeasonPhase phase = resolveSeasonPhase(seasonStartTime, totalDays, currentTime);
        if (phase == SeasonPhase.LOCATION_SELECTION) {
            return 1;
        }
        Integer currentDay = resolveCurrentDay(seasonStartTime, totalDays, currentTime);
        if (currentDay == null) {
            return null;
        }
        int playableFromDay = currentDay + 1;
        return playableFromDay >= totalDays ? null : playableFromDay;
    }

    public boolean isJoinEnabled(LocalDateTime seasonStartTime, int totalDays, LocalDateTime currentTime) {
        return resolveJoinPlayableFromDay(seasonStartTime, totalDays, currentTime) != null;
    }

    public long resolveElapsedPhaseSeconds(LocalDateTime seasonStartTime, int totalDays, LocalDateTime currentTime) {
        LocalDateTime phaseStart = resolvePhaseStartAt(seasonStartTime, totalDays, currentTime);
        if (phaseStart == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(phaseStart, currentTime).toSeconds());
    }

    public long resolveRemainingPhaseSeconds(LocalDateTime seasonStartTime, int totalDays, LocalDateTime currentTime) {
        LocalDateTime phaseEnd = resolvePhaseEndAt(seasonStartTime, totalDays, currentTime);
        if (phaseEnd == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(currentTime, phaseEnd).toSeconds());
    }

    public LocalDateTime resolvePhaseStartAt(LocalDateTime seasonStartTime, int totalDays, LocalDateTime currentTime) {
        SeasonPhase phase = resolveSeasonPhase(seasonStartTime, totalDays, currentTime);
        if (phase == SeasonPhase.CLOSED) {
            return null;
        }
        if (phase == SeasonPhase.LOCATION_SELECTION) {
            return seasonStartTime;
        }
        if (phase == SeasonPhase.DAY_PREPARING || phase == SeasonPhase.DAY_BUSINESS || phase == SeasonPhase.DAY_REPORT) {
            DayWindow dayWindow = day(seasonStartTime, totalDays, resolveCurrentDay(seasonStartTime, totalDays, currentTime));
            return switch (phase) {
                case DAY_PREPARING -> dayWindow.dayStart();
                case DAY_BUSINESS -> dayWindow.businessStart();
                case DAY_REPORT -> dayWindow.businessEnd();
                default -> dayWindow.dayStart();
            };
        }
        if (phase == SeasonPhase.SEASON_SUMMARY) {
            return seasonStartTime.plus(playableDuration(totalDays));
        }
        return resolveNextSeasonWaitStartAt(seasonStartTime, totalDays);
    }

    public LocalDateTime resolvePhaseEndAt(LocalDateTime seasonStartTime, int totalDays, LocalDateTime currentTime) {
        SeasonPhase phase = resolveSeasonPhase(seasonStartTime, totalDays, currentTime);
        if (phase == SeasonPhase.CLOSED) {
            return null;
        }
        if (phase == SeasonPhase.LOCATION_SELECTION) {
            return seasonStartTime.plus(LOCATION_SELECTION_DURATION);
        }
        if (phase == SeasonPhase.DAY_PREPARING || phase == SeasonPhase.DAY_BUSINESS || phase == SeasonPhase.DAY_REPORT) {
            DayWindow dayWindow = day(seasonStartTime, totalDays, resolveCurrentDay(seasonStartTime, totalDays, currentTime));
            return switch (phase) {
                case DAY_PREPARING -> dayWindow.businessStart();
                case DAY_BUSINESS -> dayWindow.businessEnd();
                case DAY_REPORT -> dayWindow.reportEnd();
                default -> dayWindow.reportEnd();
            };
        }
        if (phase == SeasonPhase.SEASON_SUMMARY) {
            return resolveSeasonSummaryEndAt(seasonStartTime, totalDays);
        }
        return resolveNextSeasonStartAt(seasonStartTime, totalDays);
    }

    public long resolveElapsedBusinessSeconds(LocalDateTime seasonStartTime, int totalDays, LocalDateTime currentTime) {
        if (resolveSeasonPhase(seasonStartTime, totalDays, currentTime) != SeasonPhase.DAY_BUSINESS) {
            return 0L;
        }
        DayWindow currentDayWindow = day(seasonStartTime, totalDays, resolveCurrentDay(seasonStartTime, totalDays, currentTime));
        return Math.max(0L, Duration.between(currentDayWindow.businessStart(), currentTime).toSeconds());
    }

    public String resolveGameTime(LocalDateTime seasonStartTime, int totalDays, LocalDateTime currentTime) {
        if (resolveSeasonPhase(seasonStartTime, totalDays, currentTime) != SeasonPhase.DAY_BUSINESS) {
            return null;
        }
        return formatGameTime((int) resolveElapsedBusinessSeconds(seasonStartTime, totalDays, currentTime));
    }

    public Integer resolveTick(LocalDateTime seasonStartTime, int totalDays, LocalDateTime currentTime) {
        if (resolveSeasonPhase(seasonStartTime, totalDays, currentTime) != SeasonPhase.DAY_BUSINESS) {
            return null;
        }
        return (int) Math.min(totalTickCount(), resolveElapsedBusinessSeconds(seasonStartTime, totalDays, currentTime) / TICK_INTERVAL.toSeconds());
    }

    public String formatGameTime(Integer offsetSeconds) {
        long boundedOffsetSeconds = Math.max(0L, Math.min(normalizeOffsetSeconds(offsetSeconds), BUSINESS_DURATION.toSeconds()));
        long gameMinutes = boundedOffsetSeconds * GAME_BUSINESS_DURATION_MINUTES / BUSINESS_DURATION.toSeconds();
        return LocalTime.of(BUSINESS_OPEN_HOUR, 0).plusMinutes(gameMinutes).format(TIME_FORMATTER);
    }

    public LocalDateTime resolveSeasonSummaryStartAt(LocalDateTime seasonStartTime, int totalDays) {
        return seasonStartTime.plus(playableDuration(totalDays));
    }

    public LocalDateTime resolveSeasonSummaryEndAt(LocalDateTime seasonStartTime, int totalDays) {
        return resolveSeasonSummaryStartAt(seasonStartTime, totalDays).plus(SEASON_SUMMARY_DURATION);
    }

    public LocalDateTime resolveNextSeasonWaitStartAt(LocalDateTime seasonStartTime, int totalDays) {
        return resolveSeasonSummaryEndAt(seasonStartTime, totalDays);
    }

    public LocalDateTime resolveNextSeasonStartAt(LocalDateTime seasonStartTime, int totalDays) {
        return resolveNextSeasonWaitStartAt(seasonStartTime, totalDays).plus(NEXT_SEASON_WAIT_DURATION);
    }

    public Duration selectionDuration() {
        return LOCATION_SELECTION_DURATION;
    }

    public Duration businessDuration() {
        return BUSINESS_DURATION;
    }

    public Duration reportDuration() {
        return REPORT_DURATION;
    }

    public Duration prepDuration() {
        return PREP_DURATION;
    }

    public Duration dayDuration() {
        return DAY_DURATION;
    }

    public Duration seasonSummaryDuration() {
        return SEASON_SUMMARY_DURATION;
    }

    public Duration nextSeasonWaitDuration() {
        return NEXT_SEASON_WAIT_DURATION;
    }

    public Duration playableDuration(int totalDays) {
        return LOCATION_SELECTION_DURATION.plus(DAY_DURATION.multipliedBy(totalDays));
    }

    public Duration seasonCycleDuration(int totalDays) {
        return playableDuration(totalDays).plus(SEASON_SUMMARY_DURATION).plus(NEXT_SEASON_WAIT_DURATION);
    }

    public int totalTickCount() {
        return (int) (BUSINESS_DURATION.toMillis() / TICK_INTERVAL.toMillis());
    }

    private long elapsedSeconds(LocalDateTime seasonStartTime, LocalDateTime currentTime) {
        return Duration.between(seasonStartTime, currentTime).toSeconds();
    }

    private int normalizeOffsetSeconds(Integer offsetSeconds) {
        return offsetSeconds == null ? 0 : Math.max(0, offsetSeconds);
    }
}

