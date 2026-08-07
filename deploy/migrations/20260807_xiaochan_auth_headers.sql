SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand_card_claim_config'
      AND column_name = 'x_vayne'
);
SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE `brand_card_claim_config` ADD COLUMN `x_vayne` bigint DEFAULT NULL COMMENT ''小蚕用户标识 X-Vayne'' AFTER `silk_id`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
