CREATE TABLE IF NOT EXISTS news_daily_menu_aggregate (
    source_date DATE NOT NULL,
    menu_name VARCHAR(100) NOT NULL,
    mention_count BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_news_daily_menu_aggregate PRIMARY KEY (source_date, menu_name)
);
