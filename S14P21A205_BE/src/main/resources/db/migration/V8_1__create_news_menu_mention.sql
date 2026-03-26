CREATE TABLE IF NOT EXISTS news_menu_mention (
    mention_id BIGINT NOT NULL AUTO_INCREMENT,
    source_batch_key VARCHAR(64) NOT NULL,
    day INT NOT NULL,
    source_date DATE NOT NULL,
    menu_name VARCHAR(100) NOT NULL,
    mention_count BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_news_menu_mention PRIMARY KEY (mention_id),
    CONSTRAINT uk_news_menu_mention_batch_day_menu UNIQUE (source_batch_key, day, menu_name)
);
