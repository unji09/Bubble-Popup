package com.ssafy.S14P21A205.store.repository;

import com.ssafy.S14P21A205.game.season.entity.SeasonStatus;
import com.ssafy.S14P21A205.store.entity.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

    // Reuse the same active-store rule for list queries used by gameplay services.
    @EntityGraph(attributePaths = {"user", "location", "menu", "season"})
    @Query("""
            select s
            from Store s
            where s.user.id = :userId
              and s.season.status = :seasonStatus
              and (
                    :seasonStatus <> com.ssafy.S14P21A205.game.season.entity.SeasonStatus.IN_PROGRESS
                    or not exists (
                        select 1
                        from DailyReport report
                        where report.store = s
                          and report.day = (
                                select max(latest.day)
                                from DailyReport latest
                                where latest.store = s
                          )
                          and report.isBankrupt = true
                    )
              )
            order by s.id desc
            """)
    List<Store> findActiveStoresByUserIdAndSeasonStatusOrderByIdDesc(
            @Param("userId") Integer userId,
            @Param("seasonStatus") SeasonStatus seasonStatus
    );

    default Optional<Store> findFirstByUser_IdAndSeasonStatusOrderByIdDesc(Integer userId, SeasonStatus seasonStatus) {
        return findActiveStoresByUserIdAndSeasonStatusOrderByIdDesc(userId, seasonStatus).stream().findFirst();
    }

    // Report view may still need the in-progress store even after it has gone bankrupt.
    @EntityGraph(attributePaths = {"user", "location", "menu", "season"})
    Optional<Store> findFirstByUser_IdAndSeason_StatusOrderByIdDesc(Integer userId, SeasonStatus seasonStatus);

    Optional<Store> findFirstByUser_IdOrderBySeason_IdDescIdDesc(Integer userId);

    Optional<Store> findFirstByUser_IdAndSeason_IdOrderByIdDesc(Integer userId, Long seasonId);

    @EntityGraph(attributePaths = {"user", "location", "menu", "season"})
    @Query("""
            select s
            from Store s
            where s.user.id = :userId
              and s.season.status = :seasonStatus
            order by s.id desc
            """)
    List<Store> findStoresIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(
            @Param("userId") Integer userId,
            @Param("seasonStatus") SeasonStatus seasonStatus
    );

    default Optional<Store> findFirstIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(
            Integer userId,
            SeasonStatus seasonStatus
    ) {
        return findStoresIncludingBankruptByUserIdAndSeasonStatusOrderByIdDesc(userId, seasonStatus).stream().findFirst();
    }

    boolean existsByUser_IdAndSeason_Id(Integer userId, Long seasonId);

    Optional<Store> findByUser_Id(Integer userId);

    // Season-wide gameplay calculations should ignore stores whose latest report is bankrupt.
    @EntityGraph(attributePaths = {"user", "location", "menu", "season"})
    @Query("""
            select s
            from Store s
            where s.season.id = :seasonId
              and not exists (
                    select 1
                    from DailyReport report
                    where report.store = s
                      and report.day = (
                            select max(latest.day)
                            from DailyReport latest
                            where latest.store = s
                      )
                      and report.isBankrupt = true
              )
            order by s.id asc
            """)
    List<Store> findBySeason_IdOrderByIdAsc(@Param("seasonId") Long seasonId);

    // Closing/final ranking still needs every store, including bankrupt ones.
    @EntityGraph(attributePaths = {"user", "location", "menu", "season"})
    List<Store> findAllBySeason_IdOrderByIdAsc(Long seasonId);

    @Query("""
            select count(distinct s.user.id)
            from Store s
            where s.season.id = :seasonId
              and not exists (
                    select 1
                    from DailyReport report
                    where report.store = s
                      and report.day = (
                            select max(latest.day)
                            from DailyReport latest
                            where latest.store = s
                      )
                      and report.isBankrupt = true
              )
            """)
    long countDistinctUsersBySeasonId(@Param("seasonId") Long seasonId);

    long countBySeason_IdAndLocation_Id(Long seasonId, Long locationId);

    Optional<Store> findByUserId(Integer userId);

    @Query("""
            select coalesce(avg(s.price), 0)
            from Store s
            where s.season.id = :seasonId
              and s.menu.id = :menuId
              and not exists (
                    select 1
                    from DailyReport report
                    where report.store = s
                      and report.day = (
                            select max(latest.day)
                            from DailyReport latest
                            where latest.store = s
                      )
                      and report.isBankrupt = true
              )
            """)
    int findAveragePriceBySeasonIdAndMenuId(
            @Param("seasonId") Long seasonId,
            @Param("menuId") Long menuId
    );

    @Query("""
            SELECT s.menu.menuName, COUNT(s)
            FROM Store s
            WHERE s.season.id = :seasonId
              AND NOT EXISTS (
                    SELECT 1
                    FROM DailyReport report
                    WHERE report.store = s
                      AND report.day = (
                            SELECT MAX(latest.day)
                            FROM DailyReport latest
                            WHERE latest.store = s
                      )
                      AND report.isBankrupt = true
              )
            GROUP BY s.menu.menuName
            ORDER BY COUNT(s) DESC
            """)
    List<Object[]> countStoresByMenu(@Param("seasonId") Long seasonId);

    @Query("""
            SELECT s.location.locationName, COUNT(s)
            FROM Store s
            WHERE s.season.id = :seasonId
              AND NOT EXISTS (
                    SELECT 1
                    FROM DailyReport report
                    WHERE report.store = s
                      AND report.day = (
                            SELECT MAX(latest.day)
                            FROM DailyReport latest
                            WHERE latest.store = s
                      )
                      AND report.isBankrupt = true
              )
            GROUP BY s.location.locationName
            ORDER BY COUNT(s) DESC
            """)
    List<Object[]> countStoresByLocation(@Param("seasonId") Long seasonId);

}
