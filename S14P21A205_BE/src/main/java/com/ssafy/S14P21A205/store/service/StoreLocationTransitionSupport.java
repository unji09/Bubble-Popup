package com.ssafy.S14P21A205.store.service;

import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import java.time.LocalDateTime;
import java.util.List;

public class StoreLocationTransitionSupport {

    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();

    public void applyPendingLocationIfDue(Store store, LocalDateTime now) {
        Integer currentDay = resolveCurrentDay(store, now);
        if (currentDay == null) {
            return;
        }
        applyPendingLocationIfDue(store, currentDay);
    }

    public void applyPendingLocationIfDue(Store store, int currentDay) {
        if (store == null
                || store.getPendingLocation() == null
                || store.getPendingLocationApplyDay() == null
                || currentDay < store.getPendingLocationApplyDay()) {
            return;
        }
        store.applyPendingLocationChange();
    }

    public void applyPendingLocationIfDue(List<Store> stores, LocalDateTime now) {
        if (stores == null || stores.isEmpty()) {
            return;
        }
        for (Store store : stores) {
            applyPendingLocationIfDue(store, now);
        }
    }

    public Location resolveLocationForDay(Store store, int targetDay) {
        if (store == null) {
            return null;
        }
        if (store.getPendingLocation() != null
                && store.getPendingLocationApplyDay() != null
                && targetDay >= store.getPendingLocationApplyDay()) {
            return store.getPendingLocation();
        }
        return store.getLocation();
    }

    public boolean hasFuturePendingLocationChange(Store store, int currentDay) {
        return store != null
                && store.getPendingLocation() != null
                && store.getPendingLocationApplyDay() != null
                && store.getPendingLocationApplyDay() > currentDay;
    }

    public boolean isLocationChangeApplyingToday(Store store, int currentDay) {
        return store != null
                && store.getPendingLocation() != null
                && store.getPendingLocationApplyDay() != null
                && store.getPendingLocationApplyDay() == currentDay;
    }

    public Integer resolveCurrentDay(Store store, LocalDateTime now) {
        if (store == null || store.getSeason() == null || now == null) {
            return null;
        }

        try {
            SeasonTimePoint timePoint = seasonTimelineService.resolve(store.getSeason(), now);
            Integer currentDay = timePoint.currentDay();
            if (currentDay != null
                    && currentDay >= 1
                    && store.getSeason().resolveRuntimePlayableDays() > 0
                    && currentDay <= store.getSeason().resolveRuntimePlayableDays()) {
                return currentDay;
            }
        } catch (IllegalStateException ignored) {
            // The caller may be outside an active day phase. In that case, use the season snapshot below.
        }

        Integer fallbackDay = store.getSeason().getCurrentDay();
        if (fallbackDay == null || fallbackDay < 1) {
            return null;
        }
        return fallbackDay;
    }
}
