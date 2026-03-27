package com.ssafy.S14P21A205.game.season.repository;

import com.ssafy.S14P21A205.game.season.entity.EtlJobRequest;
import com.ssafy.S14P21A205.game.season.entity.EtlJobStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtlJobRequestRepository extends JpaRepository<EtlJobRequest, Long> {

    Optional<EtlJobRequest> findBySeasonId(Long seasonId);

    long countByStatus(EtlJobStatus status);
}
