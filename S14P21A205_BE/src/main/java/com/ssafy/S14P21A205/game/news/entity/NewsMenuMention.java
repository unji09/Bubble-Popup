package com.ssafy.S14P21A205.game.news.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Entity
@Table(name = "news_menu_mention")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsMenuMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mention_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "source_batch_key", nullable = false, length = 64)
    private String sourceBatchKey;

    @Column(nullable = false)
    private Integer day;

    @Column(name = "source_date", nullable = false)
    private LocalDate sourceDate;

    @Column(name = "menu_name", nullable = false, length = 100)
    private String menuName;

    @Column(name = "mention_count", nullable = false)
    private Long mentionCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
