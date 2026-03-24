DELETE duplicate_daily_event
FROM daily_event duplicate_daily_event
JOIN daily_event kept_daily_event
  ON duplicate_daily_event.season_id = kept_daily_event.season_id
 AND duplicate_daily_event.day = kept_daily_event.day
 AND duplicate_daily_event.event_id = kept_daily_event.event_id
 AND duplicate_daily_event.season_event_id > kept_daily_event.season_event_id;

ALTER TABLE daily_event
ADD CONSTRAINT uk_daily_event_season_day_event UNIQUE (season_id, day, event_id);
