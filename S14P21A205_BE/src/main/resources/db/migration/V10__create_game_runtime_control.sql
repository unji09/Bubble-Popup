CREATE TABLE IF NOT EXISTS game_runtime_control (
  runtime_control_id BIGINT NOT NULL,
  paused TINYINT(1) NOT NULL,
  paused_at DATETIME(6) NULL,
  accumulated_pause_millis BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (runtime_control_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO game_runtime_control (
  runtime_control_id,
  paused,
  paused_at,
  accumulated_pause_millis,
  created_at,
  updated_at
) VALUES (
  1,
  0,
  NULL,
  0,
  NOW(6),
  NOW(6)
)
ON DUPLICATE KEY UPDATE runtime_control_id = runtime_control_id;
