-- 多账号、账号券统计与监控预约单次抢单
CREATE TABLE IF NOT EXISTS `xiaochan_account` (
    `id` int NOT NULL AUTO_INCREMENT,
    `user_id` int NOT NULL COMMENT '项目用户ID',
    `account_name` varchar(100) NOT NULL COMMENT '账号名称',
    `silk_id` bigint NOT NULL COMMENT '小蚕 silk_id',
    `x_vayne` bigint DEFAULT NULL COMMENT '小蚕 X-Vayne',
    `x_sivir` varchar(1024) NOT NULL DEFAULT '' COMMENT '小蚕登录态 X-Sivir',
    `enabled` tinyint(1) NOT NULL DEFAULT 1,
    `upstream_user_id` bigint DEFAULT NULL,
    `nickname` varchar(255) DEFAULT NULL,
    `phone` varchar(64) DEFAULT NULL,
    `vip_level` varchar(100) DEFAULT NULL,
    `card_total` int NOT NULL DEFAULT 0,
    `card_active` int NOT NULL DEFAULT 0,
    `card_expired` int NOT NULL DEFAULT 0,
    `redpack_total` int NOT NULL DEFAULT 0,
    `meituan_redpack_total` int NOT NULL DEFAULT 0,
    `eleme_redpack_total` int NOT NULL DEFAULT 0,
    `platform_redpack_total` int NOT NULL DEFAULT 0,
    `refresh_status` varchar(32) DEFAULT NULL,
    `last_refresh_error` varchar(500) DEFAULT NULL,
    `last_refresh_time` datetime DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` tinyint(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_xiaochan_account_user` (`user_id`),
    KEY `idx_xiaochan_account_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小蚕账号配置与券统计';

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'brand_card_claim_config' AND column_name = 'account_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `brand_card_claim_config` ADD COLUMN `account_id` int DEFAULT NULL COMMENT ''小蚕账号ID'' AFTER `user_id`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'brand_card_claim_history' AND column_name = 'account_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `brand_card_claim_history` ADD COLUMN `account_id` int DEFAULT NULL COMMENT ''小蚕账号ID'' AFTER `config_id`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'brand_card_claim_config'
      AND index_name = 'uk_brand_card_claim_user_id'
);
SET @sql := IF(@index_exists > 0,
    'ALTER TABLE `brand_card_claim_config` DROP INDEX `uk_brand_card_claim_user_id`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'brand_card_claim_config'
      AND index_name = 'uk_brand_card_claim_account_id'
);
SET @sql := IF(@index_exists = 0,
    'ALTER TABLE `brand_card_claim_config` ADD UNIQUE KEY `uk_brand_card_claim_account_id` (`account_id`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'store_auto_claim_history' AND column_name = 'account_id'
);
SET @sql := IF(@column_exists = 0,
    'ALTER TABLE `store_auto_claim_history` ADD COLUMN `account_id` int DEFAULT NULL COMMENT ''小蚕账号ID'' AFTER `brand_config_id`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `xiaochan_account`
    (`user_id`, `account_name`, `silk_id`, `x_vayne`, `x_sivir`, `enabled`)
SELECT b.`user_id`, '默认账号', b.`silk_id`, b.`x_vayne`, COALESCE(b.`x_sivir`, ''), 1
FROM `brand_card_claim_config` b
LEFT JOIN `xiaochan_account` a ON a.`user_id` = b.`user_id` AND a.`silk_id` = b.`silk_id` AND a.`deleted` = 0
WHERE b.`deleted` = 0 AND a.`id` IS NULL;

UPDATE `brand_card_claim_config` b
JOIN `xiaochan_account` a ON a.`user_id` = b.`user_id` AND a.`silk_id` = b.`silk_id` AND a.`deleted` = 0
SET b.`account_id` = a.`id`
WHERE b.`account_id` IS NULL;

CREATE TABLE IF NOT EXISTS `store_auto_claim_schedule` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` int NOT NULL,
    `monitor_config_id` int NOT NULL,
    `account_id` int DEFAULT NULL,
    `run_date` date NOT NULL,
    `store_uniq_id` varchar(128) NOT NULL DEFAULT '',
    `promotion_id` bigint NOT NULL,
    `rebate_condition` int NOT NULL,
    `scheduled_at` datetime NOT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'PENDING',
    `discovered_at` datetime NOT NULL,
    `executed_at` datetime DEFAULT NULL,
    `request_sent` tinyint(1) NOT NULL DEFAULT 0,
    `result_msg` varchar(500) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_store_auto_claim_schedule_activity`
        (`monitor_config_id`, `run_date`, `promotion_id`, `rebate_condition`),
    KEY `idx_store_auto_claim_schedule_due` (`status`, `scheduled_at`),
    KEY `idx_store_auto_claim_schedule_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='监控门店预约单次抢单任务';
