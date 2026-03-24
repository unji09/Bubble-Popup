CREATE TABLE IF NOT EXISTS etl_job_request (
    request_id BIGINT NOT NULL AUTO_INCREMENT,
    season_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    selected_start_date DATE NULL,
    source_batch_key VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    requested_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_etl_job_request PRIMARY KEY (request_id),
    CONSTRAINT uk_etl_job_request_season UNIQUE (season_id)
);
