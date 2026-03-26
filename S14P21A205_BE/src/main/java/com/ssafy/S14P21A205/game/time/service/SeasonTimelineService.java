package com.ssafy.S14P21A205.game.time.service;

import com.ssafy.S14P21A205.game.event.entity.EventEndTime;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.time.model.DayWindow;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.policy.GameTimePolicy;
import java.time.Duration;
import java.time.LocalDateTime;

public class SeasonTimelineService {

    private final GameTimePolicy gameTimePolicy = new GameTimePolicy();
    private final GameClockService gameClockService = new GameClockService();

    public SeasonTimePoint resolve(Season season, LocalDateTime currentTime) {
        validateSeason(season);
        return gameClockService.resolve(currentTime, season.getStartTime(), season.resolveRuntimePlayableDays());
    }

    public DayWindow currentDay(Season season, LocalDateTime currentTime) {
        validateSeason(season);
        Integer currentDay = gameTimePolicy.resolveCurrentDay(season.getStartTime(), season.resolveRuntimePlayableDays(), currentTime);
        if (currentDay == null) {
            throw new IllegalStateException("Current season phase does not have an active day.");
        }
        return day(season, currentDay);
    }

    public DayWindow day(Season season, int targetDay) {
        validateSeason(season);
        return gameTimePolicy.day(season.getStartTime(), season.resolveRuntimePlayableDays(), targetDay);
    }

    public int resolveCurrentDay(Season season, LocalDateTime currentTime) {
        validateSeason(season);
        Integer currentDay = gameTimePolicy.resolveCurrentDay(season.getStartTime(), season.resolveRuntimePlayableDays(), currentTime);
        if (currentDay == null) {
            throw new IllegalStateException("Current season phase does not have an active day.");
        }
        return currentDay;
    }

    public Integer resolveJoinPlayableFromDay(Season season, LocalDateTime currentTime) {
        validateSeason(season);
        return gameTimePolicy.resolveJoinPlayableFromDay(season.getStartTime(), season.resolveRuntimePlayableDays(), currentTime);
    }

    public boolean isJoinEnabled(Season season, LocalDateTime currentTime) {
        validateSeason(season);
        return gameTimePolicy.isJoinEnabled(season.getStartTime(), season.resolveRuntimePlayableDays(), currentTime);
    }

    public LocalDateTime resolveAppliedAt(Season season, int appliedDay, Integer offsetSeconds) {
        validateSeason(season);
        return gameTimePolicy.resolveAppliedAt(season.getStartTime(), season.resolveRuntimePlayableDays(), appliedDay, offsetSeconds);
    }

    public LocalDateTime resolveEndedAt(
            Season season,
            int appliedDay,
            Integer expireOffsetSeconds,
            EventEndTime endTime
    ) {
        validateSeason(season);
        return gameTimePolicy.resolveEndedAt(
                season.getStartTime(),
                season.resolveRuntimePlayableDays(),
                appliedDay,
                expireOffsetSeconds,
                endTime
        );
    }

    public String formatGameTime(Integer offsetSeconds) {
        return gameTimePolicy.formatGameTime(offsetSeconds);
    }

    public LocalDateTime resolveSeasonSummaryEndAt(Season season) {
        validateSeason(season);
        return gameTimePolicy.resolveSeasonSummaryEndAt(season.getStartTime(), season.resolveRuntimePlayableDays());
    }

    public LocalDateTime resolveSeasonSummaryStartAt(Season season) {
        validateSeason(season);
        return gameTimePolicy.resolveSeasonSummaryStartAt(season.getStartTime(), season.resolveRuntimePlayableDays());
    }

    public LocalDateTime resolveNextSeasonStartAt(Season season) {
        validateSeason(season);
        return gameTimePolicy.resolveNextSeasonStartAt(season.getStartTime(), season.resolveRuntimePlayableDays());
    }

    public Duration selectionDuration() {
        return gameTimePolicy.selectionDuration();
    }

    public Duration businessDuration() {
        return gameTimePolicy.businessDuration();
    }

    public Duration reportDuration() {
        return gameTimePolicy.reportDuration();
    }

    public Duration prepDuration() {
        return gameTimePolicy.prepDuration();
    }

    public Duration dayDuration() {
        return gameTimePolicy.dayDuration();
    }

    public Duration seasonSummaryDuration() {
        return gameTimePolicy.seasonSummaryDuration();
    }

    public Duration nextSeasonWaitDuration() {
        return gameTimePolicy.nextSeasonWaitDuration();
    }

    public Duration playableDuration(int totalDays) {
        return gameTimePolicy.playableDuration(totalDays);
    }

    public Duration seasonCycleDuration(int totalDays) {
        return gameTimePolicy.seasonCycleDuration(totalDays);
    }

    public int totalTickCount() {
        return gameTimePolicy.totalTickCount();
    }

    private void validateSeason(Season season) {
        if (season == null || season.getStartTime() == null || season.resolveRuntimePlayableDays() <= 0) {
            throw new IllegalStateException("Season timeline cannot be resolved without startTime and totalDays.");
        }
    }
}

