package com.ssafy.S14P21A205.game.news.repository;

import com.ssafy.S14P21A205.game.news.entity.NewsMenuMention;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsMenuMentionRepository extends JpaRepository<NewsMenuMention, Long> {

    List<NewsMenuMention> findBySourceBatchKeyOrderByDayAscMentionCountDescMenuNameAsc(String sourceBatchKey);
}
