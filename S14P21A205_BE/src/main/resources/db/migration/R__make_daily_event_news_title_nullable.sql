SET @daily_event_news_title_nullable_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'daily_event'
              AND column_name = 'news_title'
              AND is_nullable = 'NO'
        ),
        'ALTER TABLE daily_event MODIFY COLUMN news_title VARCHAR(120) NULL',
        'SELECT 1'
    )
);

PREPARE daily_event_news_title_nullable_stmt FROM @daily_event_news_title_nullable_sql;
EXECUTE daily_event_news_title_nullable_stmt;
DEALLOCATE PREPARE daily_event_news_title_nullable_stmt;
