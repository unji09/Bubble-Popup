-- =============================================
-- Seed Data for Game Tables
-- Uses INSERT ... WHERE NOT EXISTS for idempotent execution
-- =============================================

-- 1. Location (지역 목록)
INSERT INTO location (location_name, rent, interior_cost)
SELECT '잠실', 450000, 315000 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM location WHERE location_name = '잠실');

INSERT INTO location (location_name, rent, interior_cost)
SELECT '신도림', 250000, 175000 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM location WHERE location_name = '신도림');

INSERT INTO location (location_name, rent, interior_cost)
SELECT '여의도', 400000, 280000 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM location WHERE location_name = '여의도');

INSERT INTO location (location_name, rent, interior_cost)
SELECT '이태원', 500000, 350000 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM location WHERE location_name = '이태원');

INSERT INTO location (location_name, rent, interior_cost)
SELECT '서울숲/성수', 700000, 490000 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM location WHERE location_name = '서울숲/성수');

INSERT INTO location (location_name, rent, interior_cost)
SELECT '강남', 600000, 420000 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM location WHERE location_name = '강남');

INSERT INTO location (location_name, rent, interior_cost)
SELECT '명동', 800000, 560000 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM location WHERE location_name = '명동');

INSERT INTO location (location_name, rent, interior_cost)
SELECT '홍대', 350000, 245000 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM location WHERE location_name = '홍대');

-- 2. Festival (행사) - location FK via subquery
INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '서울세계불꽃축제', 1.20 FROM location l
WHERE l.location_name = '잠실'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '서울세계불꽃축제');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '프로야구 경기', 1.15 FROM location l
WHERE l.location_name = '잠실'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '프로야구 경기');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '대형 콘서트', 1.10 FROM location l
WHERE l.location_name = '잠실'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '대형 콘서트');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '디큐브시티 공연', 1.20 FROM location l
WHERE l.location_name = '신도림'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '디큐브시티 공연');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '테크노마트 게임·전자 행사', 1.15 FROM location l
WHERE l.location_name = '신도림'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '테크노마트 게임·전자 행사');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, 'G밸리 위크 행사', 1.10 FROM location l
WHERE l.location_name = '신도림'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = 'G밸리 위크 행사');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '서울세계불꽃축제', 1.20 FROM location l
WHERE l.location_name = '여의도'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '서울세계불꽃축제');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '여의도 벚꽃축제', 1.15 FROM location l
WHERE l.location_name = '여의도'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '여의도 벚꽃축제');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '한강 재즈 페스티벌', 1.10 FROM location l
WHERE l.location_name = '여의도'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '한강 재즈 페스티벌');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '이태원 지구촌 축제', 1.20 FROM location l
WHERE l.location_name = '이태원'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '이태원 지구촌 축제');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '이태원 할로윈 거리행사', 1.15 FROM location l
WHERE l.location_name = '이태원'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '이태원 할로윈 거리행사');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '이태원 글로벌 빌리지 페스티벌', 1.10 FROM location l
WHERE l.location_name = '이태원'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '이태원 글로벌 빌리지 페스티벌');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '서울숲 봄꽃축제', 1.20 FROM location l
WHERE l.location_name = '서울숲/성수'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '서울숲 봄꽃축제');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '서울숲 재즈페스티벌', 1.15 FROM location l
WHERE l.location_name = '서울숲/성수'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '서울숲 재즈페스티벌');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '서울숲 플리마켓', 1.10 FROM location l
WHERE l.location_name = '서울숲/성수'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '서울숲 플리마켓');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '강남페스티벌', 1.20 FROM location l
WHERE l.location_name = '강남'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '강남페스티벌');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '코엑스 문화행사', 1.15 FROM location l
WHERE l.location_name = '강남'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '코엑스 문화행사');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '코엑스 국제전시', 1.10 FROM location l
WHERE l.location_name = '강남'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '코엑스 국제전시');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '명동 글로벌 쇼핑 페스티벌', 1.20 FROM location l
WHERE l.location_name = '명동'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '명동 글로벌 쇼핑 페스티벌');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '명동 둘레길 걷기 축제', 1.15 FROM location l
WHERE l.location_name = '명동'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '명동 둘레길 걷기 축제');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '명동 거리 불꽃 행사', 1.10 FROM location l
WHERE l.location_name = '명동'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '명동 거리 불꽃 행사');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '홍대 할로윈 축제', 1.20 FROM location l
WHERE l.location_name = '홍대'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '홍대 할로윈 축제');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '홍대 거리예술제', 1.15 FROM location l
WHERE l.location_name = '홍대'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '홍대 거리예술제');

INSERT INTO festival (location_id, festival_name, population_rate)
SELECT l.location_id, '잔다리페스타', 1.10 FROM location l
WHERE l.location_name = '홍대'
AND NOT EXISTS (SELECT 1 FROM festival f WHERE f.location_id = l.location_id AND f.festival_name = '잔다리페스타');

-- 3. Menu (메뉴 목록)
INSERT INTO menu (menu_name, origin_price)
SELECT '빵', 1200 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_name = '빵');

INSERT INTO menu (menu_name, origin_price)
SELECT '마라꼬치', 1800 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_name = '마라꼬치');

INSERT INTO menu (menu_name, origin_price)
SELECT '젤리', 800 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_name = '젤리');

INSERT INTO menu (menu_name, origin_price)
SELECT '떡볶이', 1500 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_name = '떡볶이');

INSERT INTO menu (menu_name, origin_price)
SELECT '햄버거', 2500 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_name = '햄버거');

INSERT INTO menu (menu_name, origin_price)
SELECT '아이스크림', 900 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_name = '아이스크림');

INSERT INTO menu (menu_name, origin_price)
SELECT '닭강정', 2200 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_name = '닭강정');

INSERT INTO menu (menu_name, origin_price)
SELECT '타코', 2000 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_name = '타코');

INSERT INTO menu (menu_name, origin_price)
SELECT '핫도그', 1000 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_name = '핫도그');

INSERT INTO menu (menu_name, origin_price)
SELECT '버블티', 1300 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM menu WHERE menu_name = '버블티');

-- 4. Weather (날씨)
INSERT INTO weather (weather_type, population_percent)
SELECT 'SUNNY', 1.10 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM weather WHERE weather_type = 'SUNNY');

INSERT INTO weather (weather_type, population_percent)
SELECT 'RAIN', 0.90 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM weather WHERE weather_type = 'RAIN');

INSERT INTO weather (weather_type, population_percent)
SELECT 'SNOW', 0.80 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM weather WHERE weather_type = 'SNOW');

INSERT INTO weather (weather_type, population_percent)
SELECT 'HEATWAVE', 0.90 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM weather WHERE weather_type = 'HEATWAVE');

INSERT INTO weather (weather_type, population_percent)
SELECT 'FOG', 0.95 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM weather WHERE weather_type = 'FOG');

INSERT INTO weather (weather_type, population_percent)
SELECT 'COLDWAVE', 0.90 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM weather WHERE weather_type = 'COLDWAVE');

-- 5. Item (아이템)
INSERT INTO item (item_name, category, point, discount_rate)
SELECT '원재료값 할인권(대)', 'INGREDIENT', 30, 0.80 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM item WHERE item_name = '원재료값 할인권(대)');

INSERT INTO item (item_name, category, point, discount_rate)
SELECT '임대료 할인권(대)', 'RENT', 30, 0.80 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM item WHERE item_name = '임대료 할인권(대)');

INSERT INTO item (item_name, category, point, discount_rate)
SELECT '원재료값 할인권(소)', 'INGREDIENT', 10, 0.95 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM item WHERE item_name = '원재료값 할인권(소)');

INSERT INTO item (item_name, category, point, discount_rate)
SELECT '임대료 할인권(소)', 'RENT', 10, 0.95 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM item WHERE item_name = '임대료 할인권(소)');

-- 6. Random Event (랜덤 이벤트)
INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'CELEBRITY_APPEARANCE', '연예인 등장', 'IMMEDIATE', 'SAME_DAY', 1.15, 1.00, 1.00, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'CELEBRITY_APPEARANCE');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'BREAD_PRICE_DOWN', '빵 원재료 가격 하락', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 0.95, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'BREAD_PRICE_DOWN');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'MALA_SKEWER_PRICE_DOWN', '마라꼬치 원재료 가격 하락', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 0.95, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'MALA_SKEWER_PRICE_DOWN');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'JELLY_PRICE_DOWN', '젤리 원재료 가격 하락', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 0.95, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'JELLY_PRICE_DOWN');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'TTEOKBOKKI_PRICE_DOWN', '떡볶이 원재료 가격 하락', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 0.95, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'TTEOKBOKKI_PRICE_DOWN');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'HAMBURGER_PRICE_DOWN', '햄버거 원재료 가격 하락', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 0.95, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'HAMBURGER_PRICE_DOWN');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'ICE_CREAM_PRICE_DOWN', '아이스크림 원재료 가격 하락', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 0.95, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'ICE_CREAM_PRICE_DOWN');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'DAKGANGJEONG_PRICE_DOWN', '닭강정 원재료 가격 하락', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 0.95, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'DAKGANGJEONG_PRICE_DOWN');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'TACO_PRICE_DOWN', '타코 원재료 가격 하락', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 0.95, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'TACO_PRICE_DOWN');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'HOTDOG_PRICE_DOWN', '핫도그 원재료 가격 하락', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 0.95, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'HOTDOG_PRICE_DOWN');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'BUBBLE_TEA_PRICE_DOWN', '버블티 원재료 가격 하락', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 0.95, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'BUBBLE_TEA_PRICE_DOWN');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'SUBSTITUTE_HOLIDAY', '대체공휴일', 'NEXT_DAY', 'SAME_DAY', 1.10, 1.00, 1.00, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'SUBSTITUTE_HOLIDAY');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'GOVERNMENT_SUBSIDY', '정부지원금', 'IMMEDIATE', 'SAME_DAY', 1.05, 1.00, 1.00, 200000 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'GOVERNMENT_SUBSIDY');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'BREAD_PRICE_UP', '빵 원재료 가격 상승', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 1.05, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'BREAD_PRICE_UP');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'MALA_SKEWER_PRICE_UP', '마라꼬치 원재료 가격 상승', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 1.05, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'MALA_SKEWER_PRICE_UP');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'JELLY_PRICE_UP', '젤리 원재료 가격 상승', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 1.05, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'JELLY_PRICE_UP');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'TTEOKBOKKI_PRICE_UP', '떡볶이 원재료 가격 상승', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 1.05, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'TTEOKBOKKI_PRICE_UP');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'HAMBURGER_PRICE_UP', '햄버거 원재료 가격 상승', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 1.05, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'HAMBURGER_PRICE_UP');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'ICE_CREAM_PRICE_UP', '아이스크림 원재료 가격 상승', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 1.05, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'ICE_CREAM_PRICE_UP');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'DAKGANGJEONG_PRICE_UP', '닭강정 원재료 가격 상승', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 1.05, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'DAKGANGJEONG_PRICE_UP');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'TACO_PRICE_UP', '타코 원재료 가격 상승', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 1.05, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'TACO_PRICE_UP');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'HOTDOG_PRICE_UP', '핫도그 원재료 가격 상승', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 1.05, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'HOTDOG_PRICE_UP');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'BUBBLE_TEA_PRICE_UP', '버블티 원재료 가격 상승', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 1.05, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'BUBBLE_TEA_PRICE_UP');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'EARTHQUAKE', '지진', 'IMMEDIATE', 'SAME_DAY', 0.80, 0.50, 1.00, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'EARTHQUAKE');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'FLOOD', '침수', 'IMMEDIATE', 'SAME_DAY', 0.80, 0.50, 1.00, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'FLOOD');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'TYPHOON', '태풍', 'IMMEDIATE', 'SAME_DAY', 0.80, 0.50, 1.00, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'TYPHOON');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'FIRE', '화재', 'IMMEDIATE', 'SAME_DAY', 0.80, 0.50, 1.00, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'FIRE');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'POLICY_CHANGE', '정부 방침 변경', 'NEXT_DAY', 'SEASON_END', 1.00, 1.00, 1.05, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'POLICY_CHANGE');

INSERT INTO random_event (event_category, event_type, start_time, end_time, population_rate, stock_flat, cost_rate, capital_flat)
SELECT 'INFECTIOUS_DISEASE', '감염병', 'IMMEDIATE', 'SAME_DAY', 0.70, 0.00, 1.00, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM random_event WHERE event_category = 'INFECTIOUS_DISEASE');

-- 7. Action (액션)
INSERT INTO action (category, promotion_type, cost, capture_rate)
SELECT 'PROMOTION', 'INFLUENCER', 500000, 1.20 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM action WHERE category = 'PROMOTION' AND promotion_type = 'INFLUENCER');

INSERT INTO action (category, promotion_type, cost, capture_rate)
SELECT 'PROMOTION', 'SNS', 300000, 1.15 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM action WHERE category = 'PROMOTION' AND promotion_type = 'SNS');

INSERT INTO action (category, promotion_type, cost, capture_rate)
SELECT 'PROMOTION', 'LEAFLET', 100000, 1.10 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM action WHERE category = 'PROMOTION' AND promotion_type = 'LEAFLET');

INSERT INTO action (category, promotion_type, cost, capture_rate)
SELECT 'PROMOTION', 'FRIEND', 0, 1.05 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM action WHERE category = 'PROMOTION' AND promotion_type = 'FRIEND');

INSERT INTO action (category, promotion_type, cost, capture_rate)
SELECT 'DONATION', NULL, 0, 1.10 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM action WHERE category = 'DONATION');

INSERT INTO action (category, promotion_type, cost, capture_rate)
SELECT 'DISCOUNT', NULL, 0, 1.00 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM action WHERE category = 'DISCOUNT');

INSERT INTO action (category, promotion_type, cost, capture_rate)
SELECT 'EMERGENCY_ORDER', NULL, 0, 1.00 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM action WHERE category = 'EMERGENCY_ORDER');
