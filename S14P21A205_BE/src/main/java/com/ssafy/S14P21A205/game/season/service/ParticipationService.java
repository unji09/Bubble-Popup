package com.ssafy.S14P21A205.game.season.service;

import com.ssafy.S14P21A205.game.season.dto.ParticipationResponse;
import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.time.model.SeasonPhase;
import com.ssafy.S14P21A205.game.time.service.SeasonTimelineService;
import com.ssafy.S14P21A205.store.entity.Store;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipationService {

    private final SeasonRepository seasonRepository;
    private final StoreRepository storeRepository;
    private final Clock clock;

    private final SeasonTimelineService seasonTimelineService = new SeasonTimelineService();

    public ParticipationResponse getCurrentParticipation(Integer userId) {
        if (!hasCurrentSeason()) {
            return ParticipationResponse.notJoined();
        }

        Optional<Store> activeStore = storeRepository.findFirstByUser_IdAndSeasonStatusOrderByIdDesc(
                userId,
                SeasonStatus.IN_PROGRESS
        );

        Optional<Store> joinedStore = activeStore.isPresent()
                ? activeStore
                : storeRepository.findFirstIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(
                        userId,
                        SeasonStatus.IN_PROGRESS
                );

        if (joinedStore.isEmpty()) {
            return ParticipationResponse.notJoined();
        }

        Store store = joinedStore.get();
        boolean storeAccessible = activeStore.isPresent() || isReadableBankruptStore(store, LocalDateTime.now(clock));

        return new ParticipationResponse(
                true,
                storeAccessible,
                store.getId(),
                store.getStoreName(),
                resolvePlayableFromDay(store)
        );
    }

    private boolean hasCurrentSeason() {
        Optional<Season> currentSeason = seasonRepository.findFirstByStatusOrderByIdDesc(SeasonStatus.IN_PROGRESS);
        if (currentSeason.isEmpty()) {
            return false;
        }

        try {
            return seasonTimelineService.resolve(currentSeason.get(), LocalDateTime.now(clock)).phase() != SeasonPhase.CLOSED;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private boolean isReadableBankruptStore(Store store, LocalDateTime now) {
        if (store == null || store.getSeason() == null) {
            return false;
        }

        try {
            SeasonPhase phase = seasonTimelineService.resolve(store.getSeason(), now).phase();
            return phase == SeasonPhase.DAY_REPORT
                    || phase == SeasonPhase.SEASON_SUMMARY
                    || phase == SeasonPhase.NEXT_SEASON_WAITING;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private Integer resolvePlayableFromDay(Store store) {
        Integer playableFromDay = store.getPlayableFromDay();
        return playableFromDay == null || playableFromDay <= 0 ? 1 : playableFromDay;
    }
}
