SET @ddl := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 'season_ranking_record'
              AND constraint_name = 'uk_season_ranking_record_store'
        ),
        'SELECT 1',
        'ALTER TABLE season_ranking_record ADD CONSTRAINT uk_season_ranking_record_store UNIQUE (store_id)'
    )
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
