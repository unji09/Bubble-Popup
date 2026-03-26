UPDATE random_event
SET stock_flat = 0.00
WHERE event_category = 'INFECTIOUS_DISEASE'
  AND (stock_flat IS NULL OR stock_flat <> 0.00);
