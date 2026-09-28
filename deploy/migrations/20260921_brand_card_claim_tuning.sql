-- 大牌券领取参数调优，兼容旧版 MySQL 的列存在性检查语法
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand_card_claim_config'
      AND column_name = 'start_delay_ms'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `brand_card_claim_config` ADD COLUMN `start_delay_ms` int NOT NULL DEFAULT 3000 COMMENT ''准备后首次请求延迟毫秒'' AFTER `max_interval_ms`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand_card_claim_config'
      AND column_name = 'window_duration_ms'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `brand_card_claim_config` ADD COLUMN `window_duration_ms` int NOT NULL DEFAULT 2000 COMMENT ''连续请求窗口毫秒'' AFTER `start_delay_ms`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand_card_claim_config'
      AND column_name = 'max_in_flight'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `brand_card_claim_config` ADD COLUMN `max_in_flight` int NOT NULL DEFAULT 5 COMMENT ''最大并发请求数'' AFTER `window_duration_ms`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand_card_claim_config'
      AND column_name = 'request_timeout_ms'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `brand_card_claim_config` ADD COLUMN `request_timeout_ms` int NOT NULL DEFAULT 1000 COMMENT ''单次请求超时毫秒'' AFTER `max_in_flight`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `brand_card_claim_config`
SET `max_attempts` = CASE WHEN `max_attempts` IS NULL OR `max_attempts` = 5 THEN 100 ELSE `max_attempts` END,
    `min_interval_ms` = COALESCE(`min_interval_ms`, 100),
    `max_interval_ms` = COALESCE(`max_interval_ms`, 300),
    `start_delay_ms` = COALESCE(`start_delay_ms`, 3000),
    `window_duration_ms` = COALESCE(`window_duration_ms`, 2000),
    `max_in_flight` = COALESCE(`max_in_flight`, 5),
    `request_timeout_ms` = COALESCE(`request_timeout_ms`, 1000);
