package com.ssafy.S14P21A205.store.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.store.entity.Store;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class StoreRepositoryDefaultMethodsTests {

    @Test
    void findFirstIncludingBankruptReturnsLatestStoreWhenMultipleRowsMatch() {
        StoreRepository repository = mock(StoreRepository.class, Mockito.CALLS_REAL_METHODS);
        Store latestStore = mock(Store.class);
        Store olderStore = mock(Store.class);

        when(repository.findStoresIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(7, SeasonStatus.IN_PROGRESS))
                .thenReturn(List.of(latestStore, olderStore));

        assertThat(repository.findFirstIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(7, SeasonStatus.IN_PROGRESS))
                .containsSame(latestStore);
    }

    @Test
    void findFirstActiveReturnsLatestStoreWhenMultipleRowsMatch() {
        StoreRepository repository = mock(StoreRepository.class, Mockito.CALLS_REAL_METHODS);
        Store latestStore = mock(Store.class);
        Store olderStore = mock(Store.class);

        when(repository.findActiveStoresByUserIdAndSeasonStatusOrderByIdDesc(7, SeasonStatus.IN_PROGRESS))
                .thenReturn(List.of(latestStore, olderStore));

        assertThat(repository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(7, SeasonStatus.IN_PROGRESS))
                .containsSame(latestStore);
    }
}
