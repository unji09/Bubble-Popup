package com.ssafy.S14P21A205.game.season.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.game.season.dto.ParticipationResponse;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ParticipationServiceTests {

    private final SeasonRepository seasonRepository = mock(SeasonRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);

    @Test
    void getCurrentParticipationReturnsNotJoinedWhenNoCurrentSeasonExists() {
        ParticipationService participationService = createService();
        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());

        ParticipationResponse response = participationService.getCurrentParticipation(1);

        assertThat(response.joinedCurrentSeason()).isFalse();
        assertThat(response.storeAccessible()).isFalse();
        assertThat(response.storeId()).isNull();
    }

    @Test
    void getCurrentParticipationReturnsNotJoinedWhenNoStoreExists() {
        ParticipationService participationService = createService();
        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(season(3, 7, 100L)));
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(storeRepository.findFirstIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());

        ParticipationResponse response = participationService.getCurrentParticipation(1);

        assertThat(response.joinedCurrentSeason()).isFalse();
        assertThat(response.storeAccessible()).isFalse();
    }

    @Test
    void getCurrentParticipationReturnsAccessibleWhenActiveStoreExists() {
        ParticipationService participationService = createService();
        Store store = store(15L, 3, 7, 100L);

        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store.getSeason()));
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));

        ParticipationResponse response = participationService.getCurrentParticipation(1);

        assertThat(response.joinedCurrentSeason()).isTrue();
        assertThat(response.storeAccessible()).isTrue();
        assertThat(response.storeId()).isEqualTo(15L);
        assertThat(response.storeName()).isEqualTo("Default Store");
        assertThat(response.playableFromDay()).isEqualTo(1);
    }

    @Test
    void getCurrentParticipationReturnsAccessibleForBankruptStoreDuringReportPhase() {
        ParticipationService participationService = createService();
        Store store = store(15L, 6, 7, 170L);

        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store.getSeason()));
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(storeRepository.findFirstIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));

        ParticipationResponse response = participationService.getCurrentParticipation(1);

        assertThat(response.joinedCurrentSeason()).isTrue();
        assertThat(response.storeAccessible()).isTrue();
    }

    @Test
    void getCurrentParticipationReturnsInaccessibleForBankruptStoreDuringBusinessPhase() {
        ParticipationService participationService = createService();
        Store store = store(15L, 6, 7, 100L);

        when(seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store.getSeason()));
        when(storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(storeRepository.findFirstIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(1, SeasonStatus.IN_PROGRESS))
                .thenReturn(Optional.of(store));

        ParticipationResponse response = participationService.getCurrentParticipation(1);

        assertThat(response.joinedCurrentSeason()).isTrue();
        assertThat(response.storeAccessible()).isFalse();
    }

    private ParticipationService createService() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-09T05:33:00Z"), ZoneId.of("Asia/Seoul"));
        return new ParticipationService(seasonRepository, storeRepository, fixedClock);
    }

    private Store store(Long storeId, int currentDay, int totalDays, long currentDayElapsedSeconds) {
        Season season = season(currentDay, totalDays, currentDayElapsedSeconds);

        Store store = instantiate(Store.class);
        ReflectionTestUtils.setField(store, "id", storeId);
        ReflectionTestUtils.setField(store, "season", season);
        ReflectionTestUtils.setField(store, "storeName", "Default Store");
        ReflectionTestUtils.setField(store, "playableFromDay", 1);
        return store;
    }

    private Season season(int currentDay, int totalDays, long currentDayElapsedSeconds) {
        Season season = instantiate(Season.class);
        ReflectionTestUtils.setField(season, "id", 9L);
        ReflectionTestUtils.setField(season, "status", SeasonStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(season, "currentDay", currentDay);
        ReflectionTestUtils.setField(season, "totalDays", totalDays);
        LocalDateTime now = LocalDateTime.of(2026, 3, 9, 14, 33, 0);
        LocalDateTime seasonStartAt = now.minusSeconds(60L + (currentDay - 1L) * 180L + currentDayElapsedSeconds);
        ReflectionTestUtils.setField(season, "startTime", seasonStartAt);
        ReflectionTestUtils.setField(season, "endTime", seasonStartAt.plusSeconds(120L + totalDays * 180L + 120L));
        return season;
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
