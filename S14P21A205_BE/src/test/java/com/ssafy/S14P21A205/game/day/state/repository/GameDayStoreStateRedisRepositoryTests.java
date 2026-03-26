package com.ssafy.S14P21A205.game.day.state.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.config.RedisTtlProperties;
import com.ssafy.S14P21A205.exception.BaseException;
import com.ssafy.S14P21A205.exception.ErrorCode;
import com.ssafy.S14P21A205.game.day.dto.GameDayStartResponse;
import com.ssafy.S14P21A205.game.day.state.GameDayLiveState;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GameDayStoreStateRedisRepositoryTests {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private ObjectMapper objectMapper;
    private GameDayStoreStateRedisRepository repository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        repository = new GameDayStoreStateRedisRepository(
                stringRedisTemplate,
                objectMapper,
                new RedisTtlProperties(Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofMinutes(10))
        );
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void saveStoresStructuredStateFields() throws Exception {
        GameDayLiveState state = state();

        repository.save(15L, 3, state);

        ArgumentCaptor<Map<String, String>> stateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(org.mockito.ArgumentMatchers.eq("game:store:15:day:3:state"), stateCaptor.capture());
        verify(stringRedisTemplate).expire("game:store:15:day:3:state", Duration.ofMinutes(30));

        Map<String, String> savedEntries = stateCaptor.getValue();
        assertThat(savedEntries)
                .containsEntry("started_at", "2026-03-14T22:05")
                .containsEntry("purchase_cursor", "2")
                .containsEntry("cumulative_sales", "100000")
                .containsEntry("balance", "9845000");
        assertThat(objectMapper.readValue(savedEntries.get("purchase_list"), Integer[].class))
                .containsExactly(1, 2, 3);
        assertThat(objectMapper.readValue(savedEntries.get("start_response"), GameDayStartResponse.class).initialBalance())
                .isEqualTo(10_000_000);
    }

    @Test
    void findDeserializesStructuredStateFields() throws Exception {
        GameDayLiveState state = state();
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("started_at", state.startedAt().toString());
        entries.put("purchase_list", objectMapper.writeValueAsString(state.purchaseList()));
        entries.put("purchase_cursor", "2");
        entries.put("start_response", objectMapper.writeValueAsString(state.startResponse()));
        entries.put("tick", "4");
        entries.put("population_per_store", "80");
        entries.put("capture_rate", "0.25");
        entries.put("sale_price", "5000");
        entries.put("tick_customer_count", "12");
        entries.put("tick_purchase_count", "8");
        entries.put("tick_sales", "40000");
        entries.put("cumulative_customer_count", "30");
        entries.put("cumulative_purchase_count", "20");
        entries.put("cumulative_sales", "100000");
        entries.put("cumulative_total_cost", "55000");
        entries.put("balance", "9845000");
        entries.put("stock", "77");
        entries.put("last_calculated_at", "2026-03-14T22:05");
        when(hashOperations.entries("game:store:15:day:3:state")).thenReturn(entries);

        GameDayLiveState foundState = repository.find(15L, 3).orElseThrow();

        assertThat(foundState.startedAt()).isEqualTo(LocalDateTime.of(2026, 3, 14, 22, 5));
        assertThat(foundState.purchaseList()).containsExactly(1, 2, 3);
        assertThat(foundState.purchaseCursor()).isEqualTo(2);
        assertThat(foundState.startResponse().initialBalance()).isEqualTo(10_000_000);
        assertThat(foundState.cumulativeSales()).isEqualTo(100_000L);
    }

    @Test
    void findThrowsWhenNumericStateFieldIsMalformed() {
        when(hashOperations.entries("game:store:15:day:3:state"))
                .thenReturn(Map.of("cumulative_sales", "not-a-number"));

        assertThatThrownBy(() -> repository.find(15L, 3))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> {
                    BaseException baseException = (BaseException) exception;
                    assertThat(baseException.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
                });
    }

    @Test
    void saveStateAndTickLogUsesTickPrefixedFieldNames() {
        GameDayLiveState state = state();

        repository.saveStateAndTickLog(15L, 3, state);

        ArgumentCaptor<Map<String, String>> tickLogCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq("game:store:15:day:3:tick_log"), tickLogCaptor.capture());
        verify(stringRedisTemplate).expire("game:store:15:day:3:state", Duration.ofMinutes(30));
        verify(stringRedisTemplate).expire("game:store:15:day:3:tick_log", Duration.ofMinutes(30));

        Map<String, String> tickLogEntries = tickLogCaptor.getValue();
        assertThat(tickLogEntries)
                .containsEntry("tick:4:tick_customer_count", "12")
                .containsEntry("tick:4:tick_purchase_count", "8")
                .containsEntry("tick:4:tick_sales", "40000")
                .containsEntry("tick:4:cumulative_customer_count", "30");
    }

    private GameDayLiveState state() {
        return new GameDayLiveState(
                LocalDateTime.of(2026, 3, 14, 22, 5),
                List.of(1, 2, 3),
                2,
                new GameDayStartResponse(
                        "10:00",
                        "22:00",
                        Map.of("10", new GameDayStartResponse.HourlySchedule(100, BigDecimal.ONE, BigDecimal.ONE, 110)),
                        "SUNNY",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        new BigDecimal("0.15"),
                        List.of(),
                        10_000_000,
                        100,
                        null,
                        null
                ),
                4,
                80,
                new BigDecimal("0.25"),
                5000,
                12,
                List.of(),
                8,
                40_000L,
                30,
                20,
                100_000L,
                55_000L,
                9_845_000L,
                77,
                LocalDateTime.of(2026, 3, 14, 22, 5)
        );
    }
}
