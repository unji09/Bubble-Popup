package com.ssafy.S14P21A205.game.environment.repository;

import com.ssafy.S14P21A205.game.environment.entity.Population;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PopulationRepository extends JpaRepository<Population, Long> {

    List<Population> findByLocationIdOrderByDateAsc(Long locationId);

    List<Population> findByLocationIdAndSourceBatchKeyOrderByDateAsc(Long locationId, String sourceBatchKey);

    @Query("""
            SELECT p.location.locationName, AVG(p.floatingPopulation)
            FROM Population p
            GROUP BY p.location.locationName
            ORDER BY AVG(p.floatingPopulation) DESC
            """)
    List<Object[]> avgPopulationByLocation();

    @Query("""
            SELECT p.location.locationName, AVG(p.floatingPopulation)
            FROM Population p
            WHERE p.sourceBatchKey = :sourceBatchKey
            GROUP BY p.location.locationName
            ORDER BY AVG(p.floatingPopulation) DESC
            """)
    List<Object[]> avgPopulationByLocationAndSourceBatchKey(@Param("sourceBatchKey") String sourceBatchKey);

    @Query("""
            SELECT DISTINCT CAST(p.date AS LocalDate)
            FROM Population p
            ORDER BY CAST(p.date AS LocalDate)
            """)
    List<LocalDate> findDistinctDatesOrdered();

    @Query("""
            SELECT DISTINCT CAST(p.date AS LocalDate)
            FROM Population p
            WHERE p.sourceBatchKey = :sourceBatchKey
            ORDER BY CAST(p.date AS LocalDate)
            """)
    List<LocalDate> findDistinctDatesOrderedBySourceBatchKey(@Param("sourceBatchKey") String sourceBatchKey);

    @Query("""
            SELECT p.location.locationName, AVG(p.floatingPopulation)
            FROM Population p
            WHERE CAST(p.date AS LocalDate) = :targetDate
            GROUP BY p.location.locationName
            ORDER BY AVG(p.floatingPopulation) DESC
            """)
    List<Object[]> avgPopulationByLocationAndDate(@Param("targetDate") LocalDate targetDate);

    @Query("""
            SELECT p.location.locationName, AVG(p.floatingPopulation)
            FROM Population p
            WHERE p.sourceBatchKey = :sourceBatchKey
              AND CAST(p.date AS LocalDate) = :targetDate
            GROUP BY p.location.locationName
            ORDER BY AVG(p.floatingPopulation) DESC
            """)
    List<Object[]> avgPopulationByLocationAndDateAndSourceBatchKey(
            @Param("targetDate") LocalDate targetDate,
            @Param("sourceBatchKey") String sourceBatchKey
    );
}
