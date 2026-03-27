package com.ssafy.S14P21A205.monitoring.service;

import com.ssafy.S14P21A205.game.season.entity.EtlJobStatus;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.EtlJobRequestRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.season.entity.DailyReport;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplicationMonitoringService {

    private final MeterRegistry meterRegistry;
    private final SeasonRepository seasonRepository;
    private final StoreRepository storeRepository;
    private final EtlJobRequestRepository etlJobRequestRepository;

    @PostConstruct
    void registerGauges() {
        meterRegistry.gauge("game_in_progress_season_count", this, ApplicationMonitoringService::countInProgressSeasons);
        meterRegistry.gauge("game_current_day", this, ApplicationMonitoringService::currentDay);
        meterRegistry.gauge("game_active_store_count", this, ApplicationMonitoringService::activeStoreCount);
        meterRegistry.gauge("game_etl_request_pending_count", this, self -> self.countEtlJobs(EtlJobStatus.PENDING));
        meterRegistry.gauge("game_etl_request_running_count", this, self -> self.countEtlJobs(EtlJobStatus.RUNNING));
        meterRegistry.gauge("game_etl_request_failed_count", this, self -> self.countEtlJobs(EtlJobStatus.FAILED));
    }

    public Timer.Sample startTimerSample() {
        return Timer.start(meterRegistry);
    }

    public void recordSeasonStart(String result) {
        increment("game_season_start_total", tags("result", result));
    }

    public void recordSeasonStartDelay(String reason) {
        increment("game_season_start_delay_total", tags("reason", reason));
    }

    public void recordSourceBatchPending() {
        increment("game_source_batch_pending_total", List.of());
    }

    public void recordSourceBatchReady() {
        increment("game_source_batch_ready_total", List.of());
    }

    public void recordDayClosing(String result) {
        increment("game_day_close_total", tags("result", result));
    }

    public void recordAction(String type, String result) {
        increment("game_action_total", tags("type", type, "result", result));
    }

    public void recordLocationChange(String result) {
        increment("game_store_location_change_total", tags("result", result));
        recordAction("popup_move", result);
    }

    public void recordDailyReport(DailyReport report) {
        if (report == null) {
            return;
        }
        if (report.getVisitors() != null) {
            Counter.builder("game_customer_total")
                    .register(meterRegistry)
                    .increment(Math.max(report.getVisitors(), 0));
        }
        if (report.getRevenue() != null) {
            Counter.builder("game_sales_amount_total")
                    .register(meterRegistry)
                    .increment(Math.max(report.getRevenue(), 0));
        }
        if (report.getStockRemaining() != null && report.getStockRemaining() <= 0) {
            increment("game_stockout_total", List.of());
        }
        if (Boolean.TRUE.equals(report.getIsBankrupt())) {
            increment("game_bankrupt_total", List.of());
        }
    }

    public void recordNewsGeneration(String category, String result, Timer.Sample sample) {
        Iterable<Tag> tags = tags("category", category, "result", result);
        increment("game_news_generation_total", tags);
        if (sample != null) {
            sample.stop(Timer.builder("game_news_generation_duration_seconds")
                    .tags(tags)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
        }
    }

    public void recordAiRequest(String type, String result, Timer.Sample sample) {
        Iterable<Tag> tags = tags("type", type, "result", result);
        increment("game_ai_request_total", tags);
        if (sample != null) {
            sample.stop(Timer.builder("game_ai_request_duration_seconds")
                    .tags(tags)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
        }
    }

    public void recordAiFallback(String type) {
        increment("game_ai_fallback_total", tags("type", type));
    }

    public String classifyAiFailure(Throwable throwable) {
        String message = throwable == null ? "" : buildThrowableMessage(throwable);
        return message.contains("timeout") || message.contains("timed out") ? "timeout" : "failure";
    }

    public void recordStateRestore(String result, String reason) {
        increment("game_state_restore_total", tags("result", result, "reason", reason));
    }

    public void recordStateMissing() {
        increment("game_state_missing_total", List.of());
    }

    public void recordReportMaterialization(String result) {
        increment("game_report_materialization_total", tags("result", result));
    }

    private double countInProgressSeasons() {
        return seasonRepository.countByStatus(SeasonStatus.IN_PROGRESS);
    }

    private double currentDay() {
        return seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)
                .map(Season::getCurrentDay)
                .orElse(0);
    }

    private double activeStoreCount() {
        return seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS)
                .map(Season::getId)
                .map(storeRepository::countDistinctUsersBySeasonId)
                .orElse(0L);
    }

    private double countEtlJobs(EtlJobStatus status) {
        return etlJobRequestRepository.countByStatus(status);
    }

    private void increment(String meterName, Iterable<Tag> tags) {
        Counter.builder(meterName)
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    private Iterable<Tag> tags(String... keyValues) {
        java.util.ArrayList<Tag> tags = new java.util.ArrayList<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = keyValues[i];
            String value = i + 1 < keyValues.length ? keyValues[i + 1] : "unknown";
            tags.add(Tag.of(key, normalizeTagValue(value)));
        }
        return tags;
    }

    private String normalizeTagValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String buildThrowableMessage(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }
}
