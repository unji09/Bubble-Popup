CREATE TEMPORARY TABLE tmp_random_event_keep AS
SELECT event_category, MAX(event_id) AS keep_event_id
FROM random_event
GROUP BY event_category;

UPDATE daily_event de
JOIN random_event re ON re.event_id = de.event_id
JOIN tmp_random_event_keep tk ON tk.event_category = re.event_category
SET de.event_id = tk.keep_event_id
WHERE de.event_id <> tk.keep_event_id;

DELETE re
FROM random_event re
JOIN tmp_random_event_keep tk ON tk.event_category = re.event_category
WHERE re.event_id <> tk.keep_event_id;

DROP TEMPORARY TABLE tmp_random_event_keep;

ALTER TABLE random_event
ADD CONSTRAINT uk_random_event_event_category UNIQUE (event_category);
