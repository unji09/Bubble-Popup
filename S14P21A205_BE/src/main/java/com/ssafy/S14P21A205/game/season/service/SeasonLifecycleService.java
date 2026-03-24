package com.ssafy.S14P21A205.game.season.service;

import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.scheduler.SeasonDayClosingScheduler;
import com.ssafy.S14P21A205.game.news.repository.NewsReportRepository;
import com.ssafy.S14P21A205.game.news.service.NewsService;
import com.ssafy.S14P21A205.game.scheduler.SparkEtlScheduler;
import com.ssafy.S14P21A205.game.environment.entity.Festival;
import com.ssafy.S14P21A205.game.environment.entity.Population;
import com.ssafy.S14P21A205.game.environment.entity.Traffic;
import com.ssafy.S14P21A205.game.environment.entity.Weather;
import com.ssafy.S14P21A205.game.environment.entity.WeatherLocation;
import com.ssafy.S14P21A205.game.environment.entity.WeatherType;
import com.ssafy.S14P21A205.game.environment.repository.FestivalRepository;
import com.ssafy.S14P21A205.game.environment.repository.PopulationRepository;
import com.ssafy.S14P21A205.game.environment.repository.TrafficDayRedisRepository;
import com.ssafy.S14P21A205.game.environment.repository.TrafficRepository;
import com.ssafy.S14P21A205.game.environment.repository.WeatherDayRedisRepository;
import com.ssafy.S14P21A205.game.environment.repository.WeatherLocationRepository;
import com.ssafy.S14P21A205.game.environment.repository.WeatherRepository;
import com.ssafy.S14P21A205.game.event.entity.DailyEvent;
import com.ssafy.S14P21A205.game.event.entity.EventCategory;
import com.ssafy.S14P21A205.game.event.entity.EventEndTime;
import com.ssafy.S14P21A205.game.event.entity.EventStartTime;
import com.ssafy.S14P21A205.game.event.entity.RandomEvent;
import com.ssafy.S14P21A205.game.event.repository.DailyEventRepository;
import com.ssafy.S14P21A205.game.event.repository.RandomEventRepository;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.time.model.SeasonPhase;
import com.ssafy.S14P21A205.game.time.model.SeasonTimePoint;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.repository.LocationRepository;
import com.ssafy.S14P21A205.store.repository.MenuRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class SeasonLifecycleService {

    private static final BigDecimal DECIMAL_ONE = new BigDecimal("1.00");
    private static final BigDecimal ZERO_DECIMAL = new BigDecimal("0.00");
    private static final BigDecimal DISASTER_STOCK_HALF = new BigDecimal("0.50");
    private static final int DEFAULT_TOTAL_DAYS = 7;
    private static final int EVENTS_PER_DAY = 2;
    private static final int FIRST_EVENT_OFFSET_SECONDS = 40;
    private static final int SECOND_EVENT_OFFSET_SECONDS = 80;
    private static final int FESTIVAL_DAY = 4;
    private static final int FESTIVAL_APPLY_OFFSET_SECONDS = 0;
    private static final int FESTIVAL_EXPIRE_OFFSET_SECONDS = 120;

    private final SeasonRepository seasonRepository;
    private final SeasonDayClosingScheduler seasonDayClosingScheduler;
    private final WeatherRepository weatherRepository;
    private final WeatherLocationRepository weatherLocationRepository;
    private final WeatherDayRedisRepository weatherDayRedisRepository;
    private final PopulationRepository populationRepository;
    private final TrafficRepository trafficRepository;
    private final TrafficDayRedisRepository trafficDayRedisRepository;
    private final DailyEventRepository dailyEventRepository;
    private final RandomEventRepository randomEventRepository;
    private final LocationRepository locationRepository;
    private final MenuRepository menuRepository;
    private final FestivalRepository festivalRepository;
    private final NewsReportRepository newsReportRepository;
    private final NewsService newsService;
    private final SparkEtlScheduler sparkEtlScheduler;

    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();
    private final Clock clock;

    public void synchronize() {
        LocalDateTime now = LocalDateTime.now(clock);

        Season inProgressSeason = seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS).orElse(null);
        if (inProgressSeason != null) {
            synchronizeInProgressSeason(inProgressSeason, now);
            return;
        }

        Season scheduledSeason = seasonRepository.findFirstByStatusOrderByStartTimeAscIdAsc(SeasonStatus.SCHEDULED).orElse(null);
        if (scheduledSeason == null) {
            scheduledSeason = bootstrapInitialSeasonIfNeeded(now);
            if (scheduledSeason == null) {
                logNoActiveSeason(now);
                return;
            }
        }
        if (scheduledSeason.getStartTime() == null) {
            logScheduledSeasonState(
                    now,
                    scheduledSeason,
                    "SEASON_WAITING_TO_START",
                    "BEFORE_START",
                    null
            );
            return;
        }

        // Keep develop's pre-start preparation flow for scheduled seasons.
        if (scheduledSeason.getStartTime().isAfter(now)) {
            logScheduledSeasonState(
                    now,
                    scheduledSeason,
                    "SEASON_WAITING_TO_START",
                    "BEFORE_START",
                    null
            );
            // 준비(ETL+뉴스)는 prepareScheduledSeasonIfNeeded()에서 트랜잭션 밖으로 처리
            return;
        }

        List<Location> locations = requireLocations();
        String sourceBatchKey = resolveStartableSourceBatchKey(scheduledSeason, locations);
        if (sourceBatchKey == null) {
            logScheduledSeasonState(
                    now,
                    scheduledSeason,
                    "SEASON_START_PENDING_DATA",
                    "WAITING_SOURCE_BATCH",
                    0L
            );
            return;
        }

        prepareDailyEventsIfMissing(scheduledSeason, locations);

        scheduledSeason.startAt(now, sourceBatchKey);
        Random random = new Random(resolveSeed(scheduledSeason));

        List<WeatherLocation> weatherSchedule = rebuildWeatherSchedule(scheduledSeason, locations, random);
        List<Traffic> trafficSchedule = rebuildTrafficSchedule(scheduledSeason, locations, sourceBatchKey);
        rebuildPopulationSchedule(scheduledSeason, locations, sourceBatchKey);
        preloadWeatherDay(scheduledSeason.getId(), weatherSchedule, 1);
        preloadTrafficDay(scheduledSeason, trafficSchedule, 1);
        scheduledSeason.updateEndTime(resolveSeasonEndAt(scheduledSeason));

        synchronizeInProgressSeason(scheduledSeason, now);
    }

    /**
     * Spark ETL + 뉴스 생성을 트랜잭션 밖에서 실행.
     * Spark TRUNCATE TABLE(DDL)이 REPEATABLE READ 스냅샷을 깨뜨리므로,
     * synchronize()의 @Transactional 범위 밖에서 호출해야 한다.
     * existsBySeasonId 가드로 중복 실행(10초 tick 재진입)을 방지.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void prepareScheduledSeasonIfNeeded() {
        Season scheduledSeason = seasonRepository
                .findFirstByStatusOrderByStartTimeAscIdAsc(SeasonStatus.SCHEDULED).orElse(null);
        if (scheduledSeason == null) return;
        if (scheduledSeason.getStartTime() == null) return;
        if (!scheduledSeason.getStartTime().isAfter(LocalDateTime.now(clock))) return;

        // 이미 뉴스가 생성된 시즌이면 스킵 (재진입 방지)
        Long seasonId = scheduledSeason.getId();
        boolean newsPrepared = newsReportRepository.existsBySeasonId(seasonId);
        boolean dailyEventsPrepared = dailyEventRepository.existsBySeasonId(seasonId);
        if (newsPrepared && dailyEventsPrepared) return;

        try {
            if (!newsPrepared && scheduledSeason.getSourceBatchKey() == null) {
                sparkEtlScheduler.runEtl();
            }
            if (!dailyEventsPrepared) {
                prepareDailyEventsIfMissing(scheduledSeason, requireLocations());
            }
            if (!newsPrepared) {
                newsService.generateSeasonNews(seasonId);
            }
        } catch (Exception e) {
            log.error("Failed to prepare scheduled season. seasonId={}", seasonId, e);
        }
    }

    private void synchronizeInProgressSeason(Season season, LocalDateTime now) {
        LocalDateTime nextSeasonStartAt = resolveSeasonEndAt(season);
        LocalDateTime seasonFinishAt = resolveSeasonFinishAt(season);
        season.updateEndTime(nextSeasonStartAt);
        seasonDayClosingScheduler.synchronize(season);

        SeasonTimePoint timePoint = seasonTimelineService.resolve(season, now);
        logInProgressSeasonState(now, season, timePoint, nextSeasonStartAt);
        Integer targetDay = timePoint.currentDay();
        if (targetDay != null) {
            int previousDay = normalizeCurrentDay(season);
            if (targetDay > previousDay) {
                preloadReachedDays(season, previousDay + 1, targetDay);
            }
            season.syncCurrentDay(targetDay);
        }

        if (!now.isBefore(seasonFinishAt)) {
            season.finish();
            seasonDayClosingScheduler.clear(season.getId());
            scheduleNextSeasonIfNeeded(season, nextSeasonStartAt);
        }
    }

    private void logInProgressSeasonState(
            LocalDateTime now,
            Season season,
            SeasonTimePoint timePoint,
            LocalDateTime seasonEndAt
    ) {
        log.info(
                "\n================ SEASON LIFECYCLE ================\n"
                        + "now={} seasonId={} status={}\n"
                        + "stage={} detailPhase={} day={}\n"
                        + "phaseRemaining={}s seasonRemaining={}s gameTime={} tick={}\n"
                        + "joinEnabled={} joinPlayableFromDay={}\n"
                        + "startTime={} endTime={} batchKey={}\n"
                        + "==================================================",
                now,
                season.getId(),
                season.getStatus(),
                describeLifecycleStage(timePoint.phase()),
                describeDetailPhase(timePoint.phase()),
                formatDay(timePoint.currentDay()),
                timePoint.remainingPhaseSeconds(),
                Math.max(0L, Duration.between(now, seasonEndAt).toSeconds()),
                formatValue(timePoint.gameTime()),
                formatValue(timePoint.tick()),
                timePoint.joinEnabled(),
                formatValue(timePoint.joinPlayableFromDay()),
                season.getStartTime(),
                seasonEndAt,
                formatValue(season.getSourceBatchKey())
        );
    }

    private void logScheduledSeasonState(
            LocalDateTime now,
            Season season,
            String stage,
            String detailPhase,
            Long remainingOverrideSeconds
    ) {
        Long remainingUntilStart = remainingOverrideSeconds != null
                ? remainingOverrideSeconds
                : resolveRemainingSeconds(now, season.getStartTime());
        log.info(
                "\n================ SEASON LIFECYCLE ================\n"
                        + "now={} seasonId={} status={}\n"
                        + "stage={} detailPhase={} day={}\n"
                        + "phaseRemaining={}s seasonRemaining={}s gameTime={} tick={}\n"
                        + "joinEnabled={} joinPlayableFromDay={}\n"
                        + "startTime={} endTime={} batchKey={}\n"
                        + "==================================================",
                now,
                season.getId(),
                season.getStatus(),
                stage,
                detailPhase,
                formatDay(season.getCurrentDay()),
                remainingUntilStart,
                remainingUntilStart,
                "-",
                "-",
                false,
                "-",
                season.getStartTime(),
                season.getEndTime(),
                formatValue(season.getSourceBatchKey())
        );
    }

    private void logNoActiveSeason(LocalDateTime now) {
        log.info(
                "[SEASON-LIFECYCLE] now={} seasonId={} status={} stage={} detailPhase={} day={} phaseRemaining={} seasonRemaining={} gameTime={} tick={} joinEnabled={} joinPlayableFromDay={} startTime={} endTime={} batchKey={}",
                now,
                "-",
                "-",
                "NO_ACTIVE_SEASON(활성 시즌 없음)",
                "-",
                "-",
                "-",
                "-",
                "-",
                "-",
                false,
                "-",
                "-",
                "-",
                "-"
        );
    }

    private Season bootstrapInitialSeasonIfNeeded(LocalDateTime now) {
        if (seasonRepository.findFirstByOrderByIdDesc().isPresent()) {
            return null;
        }

        LocalDateTime initialSeasonStartAt = now.plus(seasonTimelineService.nextSeasonWaitDuration());
        LocalDateTime initialSeasonEndAt = initialSeasonStartAt.plus(seasonTimelineService.seasonCycleDuration(DEFAULT_TOTAL_DAYS));
        Season initialSeason = seasonRepository.save(
                Season.createScheduled(DEFAULT_TOTAL_DAYS, initialSeasonStartAt, initialSeasonEndAt)
        );
        log.info(
                "Bootstrapped initial scheduled season. seasonId={} startTime={} endTime={} totalDays={}",
                initialSeason.getId(),
                initialSeason.getStartTime(),
                initialSeason.getEndTime(),
                initialSeason.getTotalDays()
        );
        return initialSeason;
    }

    private String describeLifecycleStage(SeasonPhase phase) {
        if (phase == null) {
            return "UNKNOWN(알 수 없음)";
        }
        return switch (phase) {
            case LOCATION_SELECTION -> "SEASON_PREPARING(시즌 준비)";
            case DAY_PREPARING, DAY_BUSINESS, DAY_REPORT -> "SEASON_IN_PROGRESS(시즌 진행 중)";
            case SEASON_SUMMARY -> "SEASON_CLOSING(시즌 마감)";
            case NEXT_SEASON_WAITING -> "NEXT_SEASON_WAITING(다음 시즌 대기)";
            case CLOSED -> "SEASON_CLOSED(시즌 종료)";
        };
    }

    private String describeDetailPhase(SeasonPhase phase) {
        if (phase == null) {
            return "UNKNOWN(알 수 없음)";
        }
        return switch (phase) {
            case LOCATION_SELECTION -> "LOCATION_SELECTION(입지 선정)";
            case DAY_PREPARING -> "DAY_PREPARING(영업 준비)";
            case DAY_BUSINESS -> "DAY_BUSINESS(영업 중)";
            case DAY_REPORT -> "DAY_REPORT(영업 마감)";
            case SEASON_SUMMARY -> "SEASON_SUMMARY(시즌 요약)";
            case NEXT_SEASON_WAITING -> "NEXT_SEASON_WAITING(다음 시즌 대기)";
            case CLOSED -> "CLOSED(종료)";
        };
    }

    private long resolveRemainingSeconds(LocalDateTime now, LocalDateTime targetTime) {
        if (targetTime == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(now, targetTime).toSeconds());
    }

    private String formatDay(Integer day) {
        return day == null ? "-" : "DAY " + day;
    }

    private String formatValue(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private void preloadReachedDays(Season season, int startDay, int endDay) {
        if (season.getId() == null || season.getTotalDays() == null) {
            return;
        }

        int boundedStartDay = Math.max(1, startDay);
        int boundedEndDay = Math.min(endDay, season.getTotalDays());
        for (int day = boundedStartDay; day <= boundedEndDay; day++) {
            preloadWeatherDay(season.getId(), day);
            preloadTrafficDay(season, day);
        }
    }

    private List<WeatherLocation> rebuildWeatherSchedule(Season season, List<Location> locations, Random random) {
        Map<WeatherType, Weather> weatherByType = loadWeatherByType();
        weatherLocationRepository.deleteAllInBatch();

        List<WeatherLocation> weatherSchedule = new ArrayList<>(locations.size() * season.getTotalDays());
        for (Location location : locations) {
            for (int day = 1; day <= season.getTotalDays(); day++) {
                WeatherType weatherType = drawWeatherType(random);
                Weather weather = weatherByType.get(weatherType);
                if (weather == null) {
                    throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND, "Weather master is missing: " + weatherType);
                }
                weatherSchedule.add(WeatherLocation.create(location, weather, day));
            }
        }

        return weatherLocationRepository.saveAll(weatherSchedule);
    }

    private void rebuildPopulationSchedule(Season season, List<Location> locations, String expectedSourceBatchKey) {
        List<Population> fixedRows = new ArrayList<>();
        List<LocalDate> expectedSourceDates = null;

        for (Location location : locations) {
            List<Population> sourceRows = populationRepository.findByLocationIdOrderByDateAsc(location.getId());
            ensureSourceBatchKey(location.getId(), "population", sourceRows, Population::getSourceBatchKey, expectedSourceBatchKey);
            List<List<Population>> dailyGroups = groupRowsByDate(sourceRows, Population::getDate);
            List<LocalDate> sourceDates = extractDistinctDates(sourceRows, Population::getDate);
            ensureExactSourceDays(location.getId(), "population", sourceDates.size(), season.getTotalDays());
            expectedSourceDates = ensureSameSourceDates(location.getId(), "population", expectedSourceDates, sourceDates);

            for (int day = 1; day <= season.getTotalDays(); day++) {
                for (Population population : dailyGroups.get(day - 1)) {
                    fixedRows.add(Population.create(
                            location,
                            normalizeSeasonDateTime(season, day, population.getDate()),
                            population.getFloatingPopulation(),
                            expectedSourceBatchKey
                    ));
                }
            }
        }

        populationRepository.deleteAllInBatch();
        populationRepository.saveAll(fixedRows);
    }

    private List<Traffic> rebuildTrafficSchedule(Season season, List<Location> locations, String expectedSourceBatchKey) {
        List<Traffic> fixedRows = new ArrayList<>();
        List<LocalDate> expectedSourceDates = null;

        for (Location location : locations) {
            List<Traffic> sourceRows = trafficRepository.findByLocationIdOrderByDateAsc(location.getId());
            ensureSourceBatchKey(location.getId(), "traffic", sourceRows, Traffic::getSourceBatchKey, expectedSourceBatchKey);
            List<List<Traffic>> dailyGroups = groupRowsByDate(sourceRows, Traffic::getDate);
            List<LocalDate> sourceDates = extractDistinctDates(sourceRows, Traffic::getDate);
            ensureExactSourceDays(location.getId(), "traffic", sourceDates.size(), season.getTotalDays());
            expectedSourceDates = ensureSameSourceDates(location.getId(), "traffic", expectedSourceDates, sourceDates);

            for (int day = 1; day <= season.getTotalDays(); day++) {
                for (Traffic traffic : dailyGroups.get(day - 1)) {
                    fixedRows.add(Traffic.create(
                            location,
                            normalizeSeasonDateTime(season, day, traffic.getDate()),
                            traffic.getTrafficStatus(),
                            expectedSourceBatchKey
                    ));
                }
            }
        }

        trafficRepository.deleteAllInBatch();
        return trafficRepository.saveAll(fixedRows);
    }

    private void rebuildDailyEvents(Season season, List<Menu> menus, Random random) {
        dailyEventRepository.deleteBySeasonId(season.getId());

        List<WeightedEventSpec> fullPool = buildWeightedEventPool(menus);
        List<WeightedEventSpec> remainingPool = new ArrayList<>(fullPool);
        List<DailyEvent> dailyEvents = new ArrayList<>(season.getTotalDays() * EVENTS_PER_DAY + 1);

        for (int day = 1; day <= season.getTotalDays(); day++) {
            Set<EventCategory> selectedCategoriesForDay = new LinkedHashSet<>();
            for (int index = 0; index < EVENTS_PER_DAY; index++) {
                WeightedEventSpec selectedBaseEvent = selectUniqueWeightedEvent(
                        fullPool,
                        remainingPool,
                        selectedCategoriesForDay,
                        day,
                        season.getTotalDays(),
                        random
                );
                removeSelectedCategory(remainingPool, selectedBaseEvent.category());
                selectedCategoriesForDay.add(selectedBaseEvent.category());

                WeightedEventSpec selectedEvent = selectedBaseEvent
                        .withApplyOffsetSeconds(index == 0 ? FIRST_EVENT_OFFSET_SECONDS : SECOND_EVENT_OFFSET_SECONDS);
                RandomEvent randomEvent = upsertRandomEvent(selectedEvent);
                dailyEvents.add(DailyEvent.create(
                        season,
                        randomEvent,
                        day,
                        selectedEvent.applyOffsetSeconds(),
                        selectedEvent.expireOffsetSeconds(),
                        selectedEvent.targetLocationId(),
                        selectedEvent.targetMenuId()
                ));
            }
        }

        if (season.getTotalDays() >= FESTIVAL_DAY) {
            Festival festival = selectFestival(random);
            RandomEvent festivalEvent = upsertRandomEvent(new WeightedEventSpec(
                    EventCategory.FESTIVAL,
                    festival.getFestivalName(),
                    1.0,
                    EventStartTime.IMMEDIATE,
                    EventEndTime.SAME_DAY,
                    festival.getPopulationRate(),
                    ZERO_DECIMAL,
                    DECIMAL_ONE,
                    0,
                    FESTIVAL_APPLY_OFFSET_SECONDS,
                    FESTIVAL_EXPIRE_OFFSET_SECONDS,
                    festival.getLocation().getId(),
                    null
            ));
            dailyEvents.add(DailyEvent.create(
                    season,
                    festivalEvent,
                    FESTIVAL_DAY,
                    FESTIVAL_APPLY_OFFSET_SECONDS,
                    FESTIVAL_EXPIRE_OFFSET_SECONDS,
                    festival.getLocation().getId(),
                    null
            ));
        }

        dailyEventRepository.saveAll(dailyEvents);
    }

    private WeightedEventSpec selectUniqueWeightedEvent(
            List<WeightedEventSpec> fullPool,
            List<WeightedEventSpec> remainingPool,
            Set<EventCategory> selectedCategoriesForDay,
            int day,
            int totalDays,
            Random random
    ) {
        List<WeightedEventSpec> eligiblePool = excludeAlreadySelectedForDay(
                filterEligiblePool(remainingPool, day, totalDays),
                selectedCategoriesForDay
        );
        if (!eligiblePool.isEmpty()) {
            return selectWeightedEvent(eligiblePool, random);
        }

        if (day == totalDays) {
            List<WeightedEventSpec> fallbackPool = excludeAlreadySelectedForDay(
                    filterEligiblePool(fullPool, day, totalDays),
                    selectedCategoriesForDay
            );
            if (!fallbackPool.isEmpty()) {
                return selectWeightedEvent(fallbackPool, random);
            }
        }

        throw new BaseException(
                ErrorCode.INVALID_INPUT_VALUE,
                "No eligible events remain for day " + day
        );
    }

    private List<WeightedEventSpec> excludeAlreadySelectedForDay(
            List<WeightedEventSpec> pool,
            Set<EventCategory> selectedCategoriesForDay
    ) {
        if (selectedCategoriesForDay.isEmpty()) {
            return pool;
        }
        return pool.stream()
                .filter(event -> !selectedCategoriesForDay.contains(event.category()))
                .toList();
    }

    private void removeSelectedCategory(List<WeightedEventSpec> remainingPool, EventCategory category) {
        remainingPool.removeIf(event -> event.category() == category);
    }

    private void prepareDailyEventsIfMissing(Season season, List<Location> locations) {
        if (season.getId() == null || dailyEventRepository.existsBySeasonId(season.getId())) {
            return;
        }

        List<Menu> menus = requireMenus();
        Random random = createDailyEventRandom(season, locations);
        rebuildDailyEvents(season, menus, random);
    }

    private Random createDailyEventRandom(Season season, List<Location> locations) {
        Random random = new Random(resolveSeed(season));
        // Keep event selection aligned with the previous flow, which consumed weather rolls first.
        for (Location ignored : locations) {
            for (int day = 1; day <= season.getTotalDays(); day++) {
                drawWeatherType(random);
            }
        }
        return random;
    }

    private void preloadWeatherDay(Long seasonId, List<WeatherLocation> weatherSchedule, int day) {
        List<WeatherDayRedisRepository.WeatherDayEntry> dayEntries = weatherSchedule.stream()
                .filter(entry -> entry.getDay() == day)
                .map(entry -> new WeatherDayRedisRepository.WeatherDayEntry(
                        entry.getLocation().getId(),
                        entry.getDay(),
                        entry.getWeather().getWeatherType(),
                        entry.getWeather().getPopulationPercent()
                ))
                .toList();
        if (!dayEntries.isEmpty()) {
            weatherDayRedisRepository.saveDay(seasonId, day, dayEntries);
        }
    }

    private void preloadWeatherDay(Long seasonId, int day) {
        List<WeatherLocation> dayEntries = weatherLocationRepository.findByDayOrderByLocation_IdAsc(day);
        if (dayEntries.isEmpty()) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND, "Weather cache source is missing for day " + day);
        }

        weatherDayRedisRepository.saveDay(
                seasonId,
                day,
                dayEntries.stream()
                        .map(entry -> new WeatherDayRedisRepository.WeatherDayEntry(
                                entry.getLocation().getId(),
                                entry.getDay(),
                                entry.getWeather().getWeatherType(),
                                entry.getWeather().getPopulationPercent()
                        ))
                        .toList()
        );
    }

    private void preloadTrafficDay(Season season, List<Traffic> trafficSchedule, int day) {
        Map<Long, List<TrafficDayRedisRepository.TrafficEntry>> entriesByLocation = new LinkedHashMap<>();
        LocalDate targetDate = resolveSeasonStartDate(season).plusDays(day - 1L);
        for (Traffic traffic : trafficSchedule) {
            if (!targetDate.equals(traffic.getDate().toLocalDate())) {
                continue;
            }
            entriesByLocation.computeIfAbsent(traffic.getLocation().getId(), key -> new ArrayList<>())
                    .add(new TrafficDayRedisRepository.TrafficEntry(
                            traffic.getDate().getHour(),
                            traffic.getTrafficStatus()
                    ));
        }
        for (Map.Entry<Long, List<TrafficDayRedisRepository.TrafficEntry>> entry : entriesByLocation.entrySet()) {
            trafficDayRedisRepository.saveDay(season.getId(), entry.getKey(), day, entry.getValue());
        }
    }

    private void preloadTrafficDay(Season season, int day) {
        LocalDate targetDate = resolveSeasonStartDate(season).plusDays(day - 1L);
        for (Location location : requireLocations()) {
            List<Traffic> dayEntries = trafficRepository.findByLocation_IdAndDateBetweenOrderByDateAsc(
                    location.getId(),
                    targetDate.atStartOfDay(),
                    targetDate.plusDays(1).atStartOfDay().minusNanos(1)
            );
            if (dayEntries.isEmpty()) {
                throw new BaseException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Traffic cache source is missing for location " + location.getId() + " day " + day
                );
            }
            trafficDayRedisRepository.saveDay(
                    season.getId(),
                    location.getId(),
                    day,
                    dayEntries.stream()
                            .map(entry -> new TrafficDayRedisRepository.TrafficEntry(
                                    entry.getDate().getHour(),
                                    entry.getTrafficStatus()
                            ))
                            .toList()
                    );
        }
    }

    private String resolveStartableSourceBatchKey(Season scheduledSeason, List<Location> locations) {
        String populationBatchKey = resolveSharedSourceBatchKey(
                locations,
                "population",
                scheduledSeason.getTotalDays(),
                location -> populationRepository.findByLocationIdOrderByDateAsc(location.getId()),
                Population::getSourceBatchKey,
                Population::getDate
        );
        String trafficBatchKey = resolveSharedSourceBatchKey(
                locations,
                "traffic",
                scheduledSeason.getTotalDays(),
                location -> trafficRepository.findByLocationIdOrderByDateAsc(location.getId()),
                Traffic::getSourceBatchKey,
                Traffic::getDate
        );

        if (populationBatchKey == null || trafficBatchKey == null) {
            return null;
        }
        if (!populationBatchKey.equals(trafficBatchKey)) {
            log.info(
                    "Season {} is waiting for aligned spark batches. populationBatchKey={}, trafficBatchKey={}",
                    scheduledSeason.getId(),
                    populationBatchKey,
                    trafficBatchKey
            );
            return null;
        }

        return populationBatchKey;
    }

    private Map<WeatherType, Weather> loadWeatherByType() {
        List<Weather> weathers = weatherRepository.findAllByOrderByIdAsc();
        if (weathers.isEmpty()) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND, "Weather schedule source is missing.");
        }

        Map<WeatherType, Weather> weatherByType = new EnumMap<>(WeatherType.class);
        for (Weather weather : weathers) {
            weatherByType.put(weather.getWeatherType(), weather);
        }
        return weatherByType;
    }

    private List<Location> requireLocations() {
        List<Location> locations = locationRepository.findAllByOrderByIdAsc();
        if (locations.isEmpty()) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND, "Location source is missing.");
        }
        return locations;
    }

    private List<Menu> requireMenus() {
        List<Menu> menus = menuRepository.findAllByOrderByIdAsc();
        if (menus.isEmpty()) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND, "Menu source is missing.");
        }
        return menus;
    }

    private Festival selectFestival(Random random) {
        List<Festival> festivals = festivalRepository.findAllByOrderByIdAsc();
        if (festivals.isEmpty()) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND, "Festival source is missing.");
        }
        return festivals.get(random.nextInt(festivals.size()));
    }

    private <T> String resolveSharedSourceBatchKey(
            List<Location> locations,
            String sourceName,
            int requiredDays,
            Function<Location, List<T>> rowsLoader,
            Function<T, String> batchKeyExtractor,
            Function<T, LocalDateTime> dateExtractor
    ) {
        String expectedBatchKey = null;
        List<LocalDate> expectedDates = null;

        for (Location location : locations) {
            List<T> rows = rowsLoader.apply(location);
            String batchKey = extractSingleSourceBatchKey(rows, batchKeyExtractor);
            if (batchKey == null) {
                log.info(
                        "Season start is waiting for a single {} spark batch. locationId={}",
                        sourceName,
                        location.getId()
                );
                return null;
            }

            List<LocalDate> sourceDates = extractDistinctDates(rows, dateExtractor);
            if (sourceDates.size() != requiredDays) {
                log.info(
                        "Season start is waiting for {} {} days. locationId={} availableDays={}",
                        requiredDays,
                        sourceName,
                        location.getId(),
                        sourceDates.size()
                );
                return null;
            }
            if (expectedDates == null) {
                expectedDates = sourceDates;
            } else if (!expectedDates.equals(sourceDates)) {
                log.info(
                        "Season start is waiting for aligned {} source dates across locations. locationId={} expectedDates={} actualDates={}",
                        sourceName,
                        location.getId(),
                        expectedDates,
                        sourceDates
                );
                return null;
            }

            if (expectedBatchKey == null) {
                expectedBatchKey = batchKey;
                continue;
            }
            if (!expectedBatchKey.equals(batchKey)) {
                log.info(
                        "Season start is waiting for aligned {} spark batches across locations. expectedBatchKey={}, locationId={}, actualBatchKey={}",
                        sourceName,
                        expectedBatchKey,
                        location.getId(),
                        batchKey
                );
                return null;
            }
        }
        return expectedBatchKey;
    }

    private <T> void ensureSourceBatchKey(
            Long locationId,
            String sourceName,
            List<T> rows,
            Function<T, String> batchKeyExtractor,
            String expectedBatchKey
    ) {
        String actualBatchKey = extractSingleSourceBatchKey(rows, batchKeyExtractor);
        if (actualBatchKey == null) {
            throw new BaseException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    sourceName + " source batch is missing for location " + locationId
            );
        }
        if (!expectedBatchKey.equals(actualBatchKey)) {
            throw new BaseException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    sourceName + " source batch mismatch for location " + locationId
            );
        }
    }

    private <T> String extractSingleSourceBatchKey(List<T> rows, Function<T, String> batchKeyExtractor) {
        Set<String> batchKeys = new LinkedHashSet<>();
        for (T row : rows) {
            String batchKey = batchKeyExtractor.apply(row);
            if (batchKey == null || batchKey.isBlank()) {
                continue;
            }
            batchKeys.add(batchKey);
        }
        if (batchKeys.size() != 1) {
            return null;
        }
        return batchKeys.iterator().next();
    }

    private WeatherType drawWeatherType(Random random) {
        int roll = random.nextInt(100);
        if (roll < 50) {
            return WeatherType.SUNNY;
        }
        if (roll < 65) {
            return WeatherType.RAIN;
        }
        if (roll < 80) {
            return WeatherType.SNOW;
        }
        if (roll < 85) {
            return WeatherType.HEATWAVE;
        }
        if (roll < 95) {
            return WeatherType.FOG;
        }
        return WeatherType.COLDWAVE;
    }

    private List<WeightedEventSpec> buildWeightedEventPool(List<Menu> menus) {
        List<WeightedEventSpec> pool = new ArrayList<>();
        pool.add(spec(
                EventCategory.CELEBRITY_APPEARANCE,
                "Celebrity Appearance",
                15.0,
                EventStartTime.IMMEDIATE,
                EventEndTime.SAME_DAY,
                new BigDecimal("1.15"),
                ZERO_DECIMAL,
                DECIMAL_ONE,
                0,
                null
        ));
        pool.add(spec(
                EventCategory.SUBSTITUTE_HOLIDAY,
                "Substitute Holiday",
                10.0,
                EventStartTime.NEXT_DAY,
                EventEndTime.SAME_DAY,
                new BigDecimal("1.10"),
                ZERO_DECIMAL,
                DECIMAL_ONE,
                0,
                null
        ));
        pool.add(spec(
                EventCategory.GOVERNMENT_SUBSIDY,
                "Government Subsidy",
                10.0,
                EventStartTime.IMMEDIATE,
                EventEndTime.SAME_DAY,
                new BigDecimal("1.05"),
                ZERO_DECIMAL,
                DECIMAL_ONE,
                200_000,
                null
        ));
        pool.add(spec(
                EventCategory.POLICY_CHANGE,
                "Policy Change",
                10.0,
                EventStartTime.NEXT_DAY,
                EventEndTime.SEASON_END,
                DECIMAL_ONE,
                ZERO_DECIMAL,
                new BigDecimal("1.05"),
                0,
                null
        ));
        pool.add(spec(
                EventCategory.INFECTIOUS_DISEASE,
                "Infectious Disease",
                10.0,
                EventStartTime.IMMEDIATE,
                EventEndTime.SAME_DAY,
                new BigDecimal("0.70"),
                ZERO_DECIMAL,
                DECIMAL_ONE,
                0,
                null
        ));
        pool.add(spec(
                EventCategory.EARTHQUAKE,
                "Earthquake",
                3.75,
                EventStartTime.IMMEDIATE,
                EventEndTime.SAME_DAY,
                new BigDecimal("0.80"),
                DISASTER_STOCK_HALF,
                DECIMAL_ONE,
                0,
                null
        ));
        pool.add(spec(
                EventCategory.FLOOD,
                "Flood",
                3.75,
                EventStartTime.IMMEDIATE,
                EventEndTime.SAME_DAY,
                new BigDecimal("0.80"),
                DISASTER_STOCK_HALF,
                DECIMAL_ONE,
                0,
                null
        ));
        pool.add(spec(
                EventCategory.TYPHOON,
                "Typhoon",
                3.75,
                EventStartTime.IMMEDIATE,
                EventEndTime.SAME_DAY,
                new BigDecimal("0.80"),
                DISASTER_STOCK_HALF,
                DECIMAL_ONE,
                0,
                null
        ));
        pool.add(spec(
                EventCategory.FIRE,
                "Fire",
                3.75,
                EventStartTime.IMMEDIATE,
                EventEndTime.SAME_DAY,
                new BigDecimal("0.80"),
                DISASTER_STOCK_HALF,
                DECIMAL_ONE,
                0,
                null
        ));

        for (Menu menu : menus) {
            pool.add(spec(
                    resolveMenuPriceCategory(menu.getMenuName(), true),
                    menu.getMenuName() + " price down",
                    1.5,
                    EventStartTime.NEXT_DAY,
                    EventEndTime.SEASON_END,
                    DECIMAL_ONE,
                    ZERO_DECIMAL,
                    new BigDecimal("0.95"),
                    0,
                    menu.getId()
            ));
            pool.add(spec(
                    resolveMenuPriceCategory(menu.getMenuName(), false),
                    menu.getMenuName() + " price up",
                    1.5,
                    EventStartTime.NEXT_DAY,
                    EventEndTime.SEASON_END,
                    DECIMAL_ONE,
                    ZERO_DECIMAL,
                    new BigDecimal("1.05"),
                    0,
                    menu.getId()
            ));
        }

        return pool;
    }

    private WeightedEventSpec spec(
            EventCategory category,
            String eventName,
            double weight,
            EventStartTime startTime,
            EventEndTime endTime,
            BigDecimal populationRate,
            BigDecimal stockFlat,
            BigDecimal costRate,
            Integer capitalFlat,
            Long targetMenuId
    ) {
        return new WeightedEventSpec(
                category,
                eventName,
                weight,
                startTime,
                endTime,
                populationRate,
                stockFlat,
                costRate,
                capitalFlat,
                0,
                null,
                null,
                targetMenuId
        );
    }

    private EventCategory resolveMenuPriceCategory(String menuName, boolean down) {
        String normalized = menuName == null ? "" : menuName.trim().toLowerCase().replace(" ", "");
        return switch (normalized) {
            case "빵", "bread" -> down ? EventCategory.BREAD_PRICE_DOWN : EventCategory.BREAD_PRICE_UP;
            case "마라꼬치", "malaskewer", "mala_skewer" -> down ? EventCategory.MALA_SKEWER_PRICE_DOWN : EventCategory.MALA_SKEWER_PRICE_UP;
            case "젤리", "jelly" -> down ? EventCategory.JELLY_PRICE_DOWN : EventCategory.JELLY_PRICE_UP;
            case "떡볶이", "tteokbokki" -> down ? EventCategory.TTEOKBOKKI_PRICE_DOWN : EventCategory.TTEOKBOKKI_PRICE_UP;
            case "햄버거", "hamburger", "burger" -> down ? EventCategory.HAMBURGER_PRICE_DOWN : EventCategory.HAMBURGER_PRICE_UP;
            case "아이스크림", "icecream", "ice_cream" -> down ? EventCategory.ICE_CREAM_PRICE_DOWN : EventCategory.ICE_CREAM_PRICE_UP;
            case "닭강정", "dakgangjeong" -> down ? EventCategory.DAKGANGJEONG_PRICE_DOWN : EventCategory.DAKGANGJEONG_PRICE_UP;
            case "타코", "taco" -> down ? EventCategory.TACO_PRICE_DOWN : EventCategory.TACO_PRICE_UP;
            case "핫도그", "hotdog", "hot_dog" -> down ? EventCategory.HOTDOG_PRICE_DOWN : EventCategory.HOTDOG_PRICE_UP;
            case "버블티", "bubbletea", "bubble_tea" -> down ? EventCategory.BUBBLE_TEA_PRICE_DOWN : EventCategory.BUBBLE_TEA_PRICE_UP;
            default -> throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "Unsupported menu for event category: " + menuName);
        };
    }

    private List<WeightedEventSpec> filterEligiblePool(List<WeightedEventSpec> pool, int day, int totalDays) {
        if (day < totalDays) {
            return pool;
        }
        return pool.stream()
                .filter(event -> event.startTime() != EventStartTime.NEXT_DAY)
                .toList();
    }

    private WeightedEventSpec selectWeightedEvent(List<WeightedEventSpec> pool, Random random) {
        double totalWeight = 0.0;
        for (WeightedEventSpec event : pool) {
            totalWeight += event.weight();
        }

        double roll = random.nextDouble(totalWeight);
        double cumulativeWeight = 0.0;
        for (WeightedEventSpec event : pool) {
            cumulativeWeight += event.weight();
            if (roll < cumulativeWeight) {
                return event;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private RandomEvent upsertRandomEvent(WeightedEventSpec spec) {
        RandomEvent randomEvent = randomEventRepository
                .findFirstByEventCategory(spec.category())
                .orElseGet(() -> RandomEvent.create(
                        spec.category(),
                        spec.eventName(),
                        spec.startTime(),
                        spec.endTime(),
                        spec.populationRate(),
                        spec.stockFlat(),
                        spec.costRate(),
                        spec.capitalFlat()
                ));
        randomEvent.sync(
                spec.category(),
                spec.eventName(),
                spec.startTime(),
                spec.endTime(),
                spec.populationRate(),
                spec.stockFlat(),
                spec.costRate(),
                spec.capitalFlat()
        );
        return randomEventRepository.save(randomEvent);
    }

    private long resolveSeed(Season season) {
        long seasonId = season.getId() == null ? 0L : season.getId();
        long startEpochSecond = (season.getStartTime() == null ? LocalDateTime.now(clock) : season.getStartTime())
                .atZone(clock.getZone())
                .toEpochSecond();
        return seasonId * 31L + startEpochSecond;
    }

    private LocalDateTime normalizeSeasonDateTime(Season season, int day, LocalDateTime sourceDateTime) {
        LocalDate targetDate = resolveSeasonStartDate(season).plusDays(day - 1L);
        return LocalDateTime.of(targetDate, sourceDateTime.toLocalTime());
    }

    private LocalDate resolveSeasonStartDate(Season season) {
        return (season.getStartTime() == null ? LocalDateTime.now(clock) : season.getStartTime()).toLocalDate();
    }

    private int normalizeCurrentDay(Season season) {
        if (season.getTotalDays() == null || season.getTotalDays() < 1) {
            return 1;
        }
        int currentDay = season.getCurrentDay() == null ? 1 : season.getCurrentDay();
        if (currentDay < 1) {
            return 1;
        }
        return Math.min(currentDay, season.getTotalDays());
    }

    private <T> List<LocalDate> extractDistinctDates(List<T> rows, Function<T, LocalDateTime> dateExtractor) {
        Map<LocalDate, Boolean> dates = new LinkedHashMap<>();
        for (T row : rows) {
            dates.putIfAbsent(dateExtractor.apply(row).toLocalDate(), Boolean.TRUE);
        }
        return new ArrayList<>(dates.keySet());
    }

    private <T> List<List<T>> groupRowsByDate(List<T> rows, Function<T, LocalDateTime> dateExtractor) {
        Map<LocalDate, List<T>> rowsByDate = new LinkedHashMap<>();
        for (T row : rows) {
            rowsByDate.computeIfAbsent(dateExtractor.apply(row).toLocalDate(), key -> new ArrayList<>()).add(row);
        }
        return new ArrayList<>(rowsByDate.values());
    }

    private void ensureExactSourceDays(Long locationId, String sourceName, int availableDays, int requiredDays) {
        if (availableDays != requiredDays) {
            throw new BaseException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Expected exactly " + requiredDays + " " + sourceName + " source days for location " + locationId
            );
        }
    }

    private List<LocalDate> ensureSameSourceDates(
            Long locationId,
            String sourceName,
            List<LocalDate> expectedDates,
            List<LocalDate> actualDates
    ) {
        if (expectedDates == null) {
            return actualDates;
        }
        if (!expectedDates.equals(actualDates)) {
            throw new BaseException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Mismatched " + sourceName + " source dates for location " + locationId
            );
        }
        return expectedDates;
    }

    private LocalDateTime resolveSeasonEndAt(Season season) {
        return seasonTimelineService.resolveNextSeasonStartAt(season);
    }

    private LocalDateTime resolveSeasonFinishAt(Season season) {
        return seasonTimelineService.resolveSeasonSummaryStartAt(season);
    }

    private void scheduleNextSeasonIfNeeded(Season finishedSeason, LocalDateTime nextSeasonStartAt) {
        if (seasonRepository.existsByStatusAndStartTime(SeasonStatus.SCHEDULED, nextSeasonStartAt)) {
            return;
        }

        int totalDays = finishedSeason.getTotalDays() == null || finishedSeason.getTotalDays() <= 0
                ? 7
                : finishedSeason.getTotalDays();
        LocalDateTime nextSeasonEndAt = nextSeasonStartAt.plus(seasonTimelineService.seasonCycleDuration(totalDays));
        seasonRepository.save(Season.createScheduled(totalDays, nextSeasonStartAt, nextSeasonEndAt));
    }

    private record WeightedEventSpec(
            EventCategory category,
            String eventName,
            double weight,
            EventStartTime startTime,
            EventEndTime endTime,
            BigDecimal populationRate,
            BigDecimal stockFlat,
            BigDecimal costRate,
            Integer capitalFlat,
            Integer applyOffsetSeconds,
            Integer expireOffsetSeconds,
            Long targetLocationId,
            Long targetMenuId
    ) {
        private WeightedEventSpec withApplyOffsetSeconds(Integer nextApplyOffsetSeconds) {
            return new WeightedEventSpec(
                    category,
                    eventName,
                    weight,
                    startTime,
                    endTime,
                    populationRate,
                    stockFlat,
                    costRate,
                    capitalFlat,
                    nextApplyOffsetSeconds,
                    expireOffsetSeconds,
                    targetLocationId,
                    targetMenuId
            );
        }
    }
}
