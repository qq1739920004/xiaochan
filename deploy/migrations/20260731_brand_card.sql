CREATE TABLE IF NOT EXISTS `brand_card_claim_config` (
    `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` int NOT NULL COMMENT '项目用户ID',
    `silk_id` bigint NOT NULL COMMENT '小蚕 silk_id',
    `x_sivir` varchar(1024) NOT NULL COMMENT '小蚕登录态 X-Sivir',
    `enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用',
    `cron` varchar(50) NOT NULL DEFAULT '58 29 9 * * ?' COMMENT '每日准备时间',
    `max_attempts` int NOT NULL DEFAULT 12 COMMENT '最大请求次数',
    `min_interval_ms` int NOT NULL DEFAULT 100 COMMENT '最小请求间隔毫秒',
    `max_interval_ms` int NOT NULL DEFAULT 400 COMMENT '最大请求间隔毫秒',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_brand_card_claim_user_id` (`user_id`),
    KEY `idx_brand_card_claim_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动领取大牌券配置';

CREATE TABLE IF NOT EXISTS `brand_card_claim_history` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` int NOT NULL COMMENT '项目用户ID',
    `config_id` int NOT NULL COMMENT '配置ID',
    `start_time` datetime NOT NULL COMMENT '执行开始时间',
    `end_time` datetime NOT NULL COMMENT '执行结束时间',
    `request_count` int NOT NULL DEFAULT 0 COMMENT '请求次数',
    `success` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否成功',
    `result_code` int DEFAULT NULL COMMENT '最终响应码',
    `result_msg` varchar(500) DEFAULT NULL COMMENT '最终响应消息',
    `stop_reason` varchar(50) NOT NULL COMMENT '停止原因',
    PRIMARY KEY (`id`),
    KEY `idx_brand_card_history_user_id` (`user_id`),
    KEY `idx_brand_card_history_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动领取大牌券执行历史';
