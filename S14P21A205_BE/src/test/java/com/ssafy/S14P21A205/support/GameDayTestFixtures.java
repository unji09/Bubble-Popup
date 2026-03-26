package com.ssafy.S14P21A205.support;

import com.ssafy.S14P21A205.game.day.dto.GameDayStartResponse;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import com.ssafy.S14P21A205.game.environment.entity.Population;
import com.ssafy.S14P21A205.game.environment.entity.Traffic;
import com.ssafy.S14P21A205.game.environment.entity.TrafficStatus;
import com.ssafy.S14P21A205.game.event.entity.DailyEvent;
import com.ssafy.S14P21A205.game.event.entity.EventCategory;
import com.ssafy.S14P21A205.game.event.entity.EventEndTime;
import com.ssafy.S14P21A205.game.event.entity.EventStartTime;
import com.ssafy.S14P21A205.game.event.entity.RandomEvent;
import com.ssafy.S14P21A205.game.season.entity.DailyReport;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.order.entity.Order;
import com.ssafy.S14P21A205.shop.entity.Menu;
import com.ssafy.S14P21A205.store.entity.Location;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.user.entity.User;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.test.util.ReflectionTestUtils;

public final class GameDayTestFixtures {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final Long SEASON_ID = 9L;
    public static final Integer CURRENT_DAY = 4;
    public static final Integer TOTAL_DAYS = 7;
    public static final Long LOCATION_ID = 3L;
    public static final Long MENU_ID = 7L;
    public static final Long STORE_ID = 15L;
    public static final Long DUMMY_STORE_ID = 16L;
    public static final Integer USER_ID = 1;
    public static final Integer DUMMY_USER_ID = 2;
    public static final Integer RENT = 100_000;
    public static final Integer ORIGIN_PRICE = 2_000;
    public static final Integer SALE_PRICE = 4_000;
    public static final Integer PREVIOUS_BALANCE = 9_700_000;
    public static final Integer PREVIOUS_STOCK = 30;
    public static final Integer DAILY_ORDER_QUANTITY = 100;
    public static final Integer DAILY_ORDER_COST = 200_000;
    public static final Integer INITIAL_BALANCE = 4_990_000;
    public static final Integer INITIAL_STOCK = 130;
    public static final BigDecimal CAPTURE_RATE = new BigDecimal("0.50");
    public static final LocalDateTime DAY4_STARTED_AT = LocalDateTime.of(2026, 3, 17, 10, 0, 0);

    private GameDayTestFixtures() {
    }

    public static Clock fixedClockAtStart() {
        return Clock.fixed(toInstant(DAY4_STARTED_AT), KST);
    }

    public static Clock fixedClockAt(LocalDateTime dateTime) {
        return Clock.fixed(toInstant(dateTime), KST);
    }

    public static User user(Integer id) {
        User user = new User("fixture-%d@test.com".formatted(id), "fixture-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    public static Location location() {
        Location location = instantiate(Location.class);
        ReflectionTestUtils.setField(location, "id", LOCATION_ID);
        ReflectionTestUtils.setField(location, "locationName", "fixture-location");
        ReflectionTestUtils.setField(location, "rent", RENT);
        ReflectionTestUtils.setField(location, "interiorCost", 200_000);
        return location;
    }

    public static Menu menu() {
        Menu menu = instantiate(Menu.class);
        ReflectionTestUtils.setField(menu, "id", MENU_ID);
        ReflectionTestUtils.setField(menu, "menuName", "fixture-menu");
        ReflectionTestUtils.setField(menu, "originPrice", ORIGIN_PRICE);
        return menu;
    }

    public static Season season() {
        Season season = instantiate(Season.class);
        ReflectionTestUtils.setField(season, "id", SEASON_ID);
        ReflectionTestUtils.setField(season, "status", SeasonStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(season, "currentDay", CURRENT_DAY);
        ReflectionTestUtils.setField(season, "totalDays", TOTAL_DAYS);
        LocalDateTime seasonStartAt = DAY4_STARTED_AT.minusSeconds(60L + (CURRENT_DAY - 1L) * 180L + 40L);
        ReflectionTestUtils.setField(season, "startTime", seasonStartAt);
        ReflectionTestUtils.setField(season, "endTime", seasonStartAt.plusSeconds(60L + TOTAL_DAYS * 180L + 180L));
        return season;
    }

    public static Store store(User user, Season season, Location location, Menu menu) {
        return store(STORE_ID, user, season, location, menu, SALE_PRICE);
    }

    public static Store dummyStore(User user, Season season, Location location, Menu menu) {
        return store(DUMMY_STORE_ID, user, season, location, menu, 2_000);
    }

    public static Store store(Long storeId, User user, Season season, Location location, Menu menu, Integer price) {
        Store store = instantiate(Store.class);
        ReflectionTestUtils.setField(store, "id", storeId);
        ReflectionTestUtils.setField(store, "user", user);
        ReflectionTestUtils.setField(store, "location", location);
        ReflectionTestUtils.setField(store, "menu", menu);
        ReflectionTestUtils.setField(store, "season", season);
        ReflectionTestUtils.setField(store, "storeName", "fixture-store-" + storeId);
        ReflectionTestUtils.setField(store, "price", price);
        return store;
    }

    public static DailyReport previousDayReport(Store store) {
        DailyReport report = instantiate(DailyReport.class);
        ReflectionTestUtils.setField(report, "id", 100L);
        ReflectionTestUtils.setField(report, "store", store);
        ReflectionTestUtils.setField(report, "day", CURRENT_DAY - 1);
        ReflectionTestUtils.setField(report, "locationName", "fixture-location");
        ReflectionTestUtils.setField(report, "menuName", "fixture-menu");
        ReflectionTestUtils.setField(report, "revenue", 800_000);
        ReflectionTestUtils.setField(report, "totalCost", 300_000);
        ReflectionTestUtils.setField(report, "netProfit", 500_000);
        ReflectionTestUtils.setField(report, "visitors", 100);
        ReflectionTestUtils.setField(report, "salesCount", 120);
        ReflectionTestUtils.setField(report, "stockRemaining", PREVIOUS_STOCK);
        ReflectionTestUtils.setField(report, "consecutiveDeficitDays", 0);
        ReflectionTestUtils.setField(report, "isBankrupt", false);
        ReflectionTestUtils.setField(report, "balance", PREVIOUS_BALANCE);
        ReflectionTestUtils.setField(report, "captureRate", CAPTURE_RATE);
        return report;
    }

    public static Order dailyStartOrder(Store store) {
        return Order.create(store.getMenu(), store, DAILY_ORDER_QUANTITY, DAILY_ORDER_COST, CURRENT_DAY);
    }

    public static GameDayStartResponse startResponse(List<GameDayStartResponse.EventSchedule> eventSchedules) {
        return new GameDayStartResponse(
                "10:00",
                "22:00",
                hourlySchedule(),
                "SUNNY",
                BigDecimal.ONE,
                BigDecimal.ONE,
                CAPTURE_RATE,
                eventSchedules,
                INITIAL_BALANCE,
                INITIAL_STOCK,
                null,
                null
        );
    }

    public static GameDayLiveState liveState(
            GameDayStartResponse startResponse,
            List<Integer> purchaseList,
            LocalDateTime startedAt
    ) {
        return new GameDayLiveState(
                startedAt,
                purchaseList,
                0,
                startResponse,
                0,
                0,
                CAPTURE_RATE,
                SALE_PRICE,
                0,
                List.of(),
                0,
                0L,
                0,
                0,
                0L,
                0L,
                (long) INITIAL_BALANCE,
                INITIAL_STOCK,
                startedAt
        );
    }

    public static List<Integer> fixedPurchaseList() {
        int[] pattern = {1, 2, 0, 3, 1, 1, 0, 2};
        List<Integer> purchaseList = new ArrayList<>();
        for (int repeat = 0; repeat < 8; repeat++) {
            for (int quantity : pattern) {
                purchaseList.add(quantity);
            }
        }
        return purchaseList;
    }

    public static List<Population> populations(Location location) {
        List<Population> populations = new ArrayList<>();
        for (int hour = 10; hour < 22; hour++) {
            Population population = instantiate(Population.class);
            ReflectionTestUtils.setField(population, "location", location);
            ReflectionTestUtils.setField(population, "date", LocalDateTime.of(2026, 3, 17, hour, 0, 0));
            ReflectionTestUtils.setField(population, "floatingPopulation", 120);
            populations.add(population);
        }
        return populations;
    }

    public static List<Traffic> traffics(Location location) {
        List<Traffic> traffics = new ArrayList<>();
        for (int hour = 10; hour < 22; hour++) {
            Traffic traffic = instantiate(Traffic.class);
            ReflectionTestUtils.setField(traffic, "location", location);
            ReflectionTestUtils.setField(traffic, "date", LocalDateTime.of(2026, 3, 17, hour, 0, 0));
            ReflectionTestUtils.setField(traffic, "trafficStatus", TrafficStatus.NORMAL);
            traffics.add(traffic);
        }
        return traffics;
    }

    public static DailyEvent globalSupportEvent(Season season) {
        return dailyEvent(
                season,
                randomEvent(
                        31L,
                        EventCategory.GOVERNMENT_SUBSIDY,
                        "정부지원금",
                        "1.05",
                        "0.00",
                        "1.00",
                        200_000,
                        EventStartTime.IMMEDIATE,
                        EventEndTime.SAME_DAY
                ),
                CURRENT_DAY,
                0,
                null,
                null,
                null
        );
    }

    public static DailyEvent menuCostUpEvent(Season season) {
        return dailyEvent(
                season,
                randomEvent(
                        32L,
                        EventCategory.TACO_PRICE_UP,
                        "타코 원재료 가격 상승",
                        "1.00",
                        "0.00",
                        "1.05",
                        0,
                        EventStartTime.NEXT_DAY,
                        EventEndTime.SEASON_END
                ),
                CURRENT_DAY - 1,
                0,
                null,
                null,
                MENU_ID
        );
    }

    public static DailyEvent locationFestivalEvent(Season season) {
        return dailyEvent(
                season,
                randomEvent(
                        33L,
                        EventCategory.FESTIVAL,
                        "서울세계불꽃축제",
                        "1.20",
                        "0.00",
                        "1.00",
                        0,
                        EventStartTime.IMMEDIATE,
                        EventEndTime.SAME_DAY
                ),
                CURRENT_DAY,
                0,
                120,
                LOCATION_ID,
                null
        );
    }

    public static Map<String, GameDayStartResponse.HourlySchedule> hourlySchedule() {
        Map<String, GameDayStartResponse.HourlySchedule> hourlySchedule = new LinkedHashMap<>();
        for (int hour = 10; hour < 22; hour++) {
            hourlySchedule.put(String.valueOf(hour), new GameDayStartResponse.HourlySchedule(120, BigDecimal.ONE, BigDecimal.ONE, 120));
        }
        return hourlySchedule;
    }

    private static DailyEvent dailyEvent(
            Season season,
            RandomEvent event,
            Integer day,
            Integer applyOffsetSeconds,
            Integer expireOffsetSeconds,
            Long targetLocationId,
            Long targetMenuId
    ) {
        DailyEvent dailyEvent = instantiate(DailyEvent.class);
        ReflectionTestUtils.setField(dailyEvent, "id", event.getId() + 100L);
        ReflectionTestUtils.setField(dailyEvent, "season", season);
        ReflectionTestUtils.setField(dailyEvent, "event", event);
        ReflectionTestUtils.setField(dailyEvent, "day", day);
        ReflectionTestUtils.setField(dailyEvent, "applyOffsetSeconds", applyOffsetSeconds);
        ReflectionTestUtils.setField(dailyEvent, "expireOffsetSeconds", expireOffsetSeconds);
        ReflectionTestUtils.setField(dailyEvent, "targetLocationId", targetLocationId);
        ReflectionTestUtils.setField(dailyEvent, "targetMenuId", targetMenuId);
        return dailyEvent;
    }

    private static RandomEvent randomEvent(
            Long eventId,
            EventCategory eventCategory,
            String eventName,
            String populationRate,
            String stockFlat,
            String costRate,
            Integer capitalFlat,
            EventStartTime startTime,
            EventEndTime endTime
    ) {
        RandomEvent randomEvent = instantiate(RandomEvent.class);
        ReflectionTestUtils.setField(randomEvent, "id", eventId);
        ReflectionTestUtils.setField(randomEvent, "eventCategory", eventCategory);
        ReflectionTestUtils.setField(randomEvent, "eventName", eventName);
        ReflectionTestUtils.setField(randomEvent, "startTime", startTime);
        ReflectionTestUtils.setField(randomEvent, "endTime", endTime);
        ReflectionTestUtils.setField(randomEvent, "populationRate", new BigDecimal(populationRate));
        ReflectionTestUtils.setField(randomEvent, "stockFlat", new BigDecimal(stockFlat));
        ReflectionTestUtils.setField(randomEvent, "costRate", new BigDecimal(costRate));
        ReflectionTestUtils.setField(randomEvent, "capitalFlat", capitalFlat);
        return randomEvent;
    }

    private static Instant toInstant(LocalDateTime dateTime) {
        return dateTime.atZone(KST).toInstant();
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
