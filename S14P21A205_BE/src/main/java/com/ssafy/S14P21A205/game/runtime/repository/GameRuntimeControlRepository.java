package com.ssafy.S14P21A205.game.runtime.repository;

import com.ssafy.S14P21A205.game.runtime.entity.GameRuntimeControl;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameRuntimeControlRepository extends JpaRepository<GameRuntimeControl, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select control from GameRuntimeControl control where control.id = :id")
    Optional<GameRuntimeControl> findByIdForUpdate(@Param("id") Long id);
}
