package com.ssafy.S14P21A205.game.season.repository;

import com.ssafy.S14P21A205.game.season.entity.Season;
import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonRepository extends JpaRepository<Season, Long> {

    Optional<Season> findFirstByStatusOrderByIdDesc(SeasonStatus status);

    Optional<Season> findFirstByStatusOrderByStartTimeAscIdAsc(SeasonStatus status);

    List<Season> findByStatusAndStartTimeLessThanEqualOrderByStartTimeDescIdDesc(
            SeasonStatus status,
            LocalDateTime startTime
    );

    List<Season> findByStatusAndStartTimeLessThanEqualOrderByStartTimeAscIdAsc(
            SeasonStatus status,
            LocalDateTime startTime
    );

    List<Season> findByStatusAndStartTimeLessThanEqualAndEndTimeAfterOrderByEndTimeDescIdDesc(
            SeasonStatus status,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    Optional<Season> findFirstByOrderByIdDesc();

    Optional<Season> findByIdAndStatus(Long id, SeasonStatus status);

    boolean existsByStatusAndStartTime(SeasonStatus status, LocalDateTime startTime);

    long countByStatus(SeasonStatus status);
}
