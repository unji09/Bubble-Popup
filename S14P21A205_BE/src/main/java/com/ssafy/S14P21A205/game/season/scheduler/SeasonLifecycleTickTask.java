package com.ssafy.S14P21A205.game.season.scheduler;

import com.ssafy.S14P21A205.game.season.service.SeasonLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeasonLifecycleTickTask {

    private final SeasonLifecycleService seasonLifecycleService;
    private final SeasonStartScheduler seasonStartScheduler;

    @Scheduled(fixedRateString = "${app.game.season-lifecycle.prepare-fixed-rate-ms}")
    public void prepareScheduledSeason() {
        seasonLifecycleService.prepareScheduledSeasonIfNeeded();
        seasonStartScheduler.synchronizeCurrentScheduledSeason();
    }

    @Scheduled(fixedRateString = "${app.game.season-lifecycle.synchronize-fixed-rate-ms}")
    public void synchronizeSeasonLifecycle() {
        seasonLifecycleService.synchronize();
        seasonStartScheduler.synchronizeCurrentScheduledSeason();
    }
}
