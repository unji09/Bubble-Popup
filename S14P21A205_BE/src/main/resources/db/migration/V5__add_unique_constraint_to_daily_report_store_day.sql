SET @ddl := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 'daily_report'
              AND constraint_name = 'uk_daily_report_store_day'
        ),
        'SELECT 1',
        'ALTER TABLE daily_report ADD CONSTRAINT uk_daily_report_store_day UNIQUE (store_id, day)'
    )
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
