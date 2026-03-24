package com.ssafy.S14P21A205.game.event.repository;

import com.ssafy.S14P21A205.game.event.entity.RandomEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RandomEventRepository extends JpaRepository<RandomEvent, Long> {

    Optional<RandomEvent> findFirstByEventCategory(
            com.ssafy.S14P21A205.game.event.entity.EventCategory eventCategory
    );
}
