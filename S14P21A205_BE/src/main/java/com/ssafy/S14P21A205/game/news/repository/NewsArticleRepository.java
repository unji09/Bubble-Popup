package com.ssafy.S14P21A205.game.news.repository;

import com.ssafy.S14P21A205.game.news.entity.NewsArticle;
import com.ssafy.S14P21A205.game.news.entity.NewsCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    List<NewsArticle> findByNewsReportIdOrderByIdAsc(Long newsReportId);

    List<NewsArticle> findByDayAndCategory(Integer day, NewsCategory category);

    List<NewsArticle> findByNewsReport_Season_IdAndDayOrderByIdAsc(Long seasonId, Integer day);

    boolean existsByNewsReportIdAndCategory(Long newsReportId, NewsCategory category);

    long countByNewsReportId(Long newsReportId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM NewsArticle na WHERE na.newsReport.season.id = :seasonId")
    void deleteBySeasonId(@Param("seasonId") Long seasonId);
}
