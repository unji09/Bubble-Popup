package com.ssafy.S14P21A205.game.environment.repository;

import com.ssafy.S14P21A205.game.environment.entity.Traffic;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrafficRepository extends JpaRepository<Traffic, Long> {

    List<Traffic> findByLocationIdOrderByDateAsc(Long locationId);

    List<Traffic> findByLocationIdAndSourceBatchKeyOrderByDateAsc(Long locationId, String sourceBatchKey);

    Optional<Traffic> findFirstByLocation_IdAndDate(Long locationId, LocalDateTime date);

    List<Traffic> findByLocation_IdAndDateBetweenOrderByDateAsc(
            Long locationId,
            LocalDateTime start,
            LocalDateTime end
    );
}
