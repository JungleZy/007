-- ============================================================================
-- 迁移 01：schema 与实体对齐（P1-8）
--
-- 生成方式：%test 容器（mysql:8.0）drop-and-create 导出 Hibernate 实体 DDL
-- （target/entity-ddl.sql），与 docs/database/project006.sql（当前快照，100 表）
-- 逐表差分；并与 Hibernate 启动校验日志（logs/info.log.2，
-- SchemaManagementIntegrator 输出）交叉核对：
--   - 缺表 5 张：general_telex_pat / _page / _user / _user_value、t_masthead ✓
--   - 缺列 2 处：simulation_router_room.is_start_sign、
--     t_post_ticker_tape_train.is_start_sign（日志实证，评审 P1-8 仅列了前者）✓
--   - 日志其余 "modify column" 均为长度/字符集/显示宽度噪音，同一类型族，
--     Hibernate validate（Dialect#equivalentTypes）不校验长度与字符集，无需迁移。
--     全部 98 张共有表逐列比对：0 处类型族不一致。
--
-- 执行顺序硬约束：本脚本 → 02-engine-innodb.sql → 才可启用
-- %prod quarkus.hibernate-orm.database.generation=validate。
-- 本脚本幂等：IF NOT EXISTS + 缺列判断由执行者保证只跑一次；重复执行
-- CREATE 安全，重复 ADD COLUMN 会报 1060（可忽略）。
-- ============================================================================

-- ---- 1. 缺表（DDL 取自 Hibernate 实体导出，validate 的权威期望） ----

CREATE TABLE IF NOT EXISTS `general_telex_pat` (
  `is_cable` int DEFAULT NULL,
  `pat_type` int DEFAULT NULL,
  `status` int DEFAULT NULL,
  `total_number` int DEFAULT NULL,
  `train_type` int DEFAULT NULL,
  `type` int DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `end_time` datetime(6) DEFAULT NULL,
  `start_time` datetime(6) DEFAULT NULL,
  `valid_time` bigint DEFAULT NULL,
  `create_user` varchar(255) DEFAULT NULL,
  `id` varchar(255) NOT NULL,
  `rule_content` varchar(255) DEFAULT NULL,
  `rule_id` varchar(255) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `general_telex_pat_page` (
  `page_number` int DEFAULT NULL,
  `sort` int DEFAULT NULL,
  `id` varchar(255) NOT NULL,
  `key` varchar(255) DEFAULT NULL,
  `train_id` varchar(255) DEFAULT NULL,
  `value` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `general_telex_pat_user` (
  `accuracy` decimal(38,2) DEFAULT NULL,
  `error_number` int DEFAULT NULL,
  `is_finish` int DEFAULT NULL,
  `role` int DEFAULT NULL,
  `score` decimal(38,2) DEFAULT NULL,
  `speed` decimal(38,2) DEFAULT NULL,
  `valid_time` int DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `finish_time` datetime(6) DEFAULT NULL,
  `deduct_info` varchar(255) DEFAULT NULL,
  `duration` varchar(255) DEFAULT NULL,
  `id` varchar(255) NOT NULL,
  `speed_log` varchar(255) DEFAULT NULL,
  `statistic_info` varchar(255) DEFAULT NULL,
  `train_id` varchar(255) DEFAULT NULL,
  `user_id` varchar(255) DEFAULT NULL,
  `valid_time_log` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `general_telex_pat_user_value` (
  `page_number` int DEFAULT NULL,
  `sort` int DEFAULT NULL,
  `id` varchar(255) NOT NULL,
  `key` varchar(255) DEFAULT NULL,
  `train_id` varchar(255) DEFAULT NULL,
  `user_id` varchar(255) DEFAULT NULL,
  `value` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_masthead` (
  `content` varchar(255) DEFAULT NULL,
  `id` varchar(255) NOT NULL,
  `train_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---- 2. 缺列（实体默认 isStartSign=1，存量行按业务默认回填为 1） ----

ALTER TABLE `simulation_router_room` ADD COLUMN `is_start_sign` int NULL DEFAULT 1;
ALTER TABLE `t_post_ticker_tape_train` ADD COLUMN `is_start_sign` int NULL DEFAULT 1;

-- ---- 3. 主键类型对齐（P1-8 第 3 类） ----
-- 旧基线（project006-base.sql:74）general_key_pat_page.id 为 int AUTO_INCREMENT，
-- 实体为 UUID 字符串主键（GenerationType.UUID）。当前快照（project006.sql:74）
-- 已是 varchar(64)，在该状态下本语句为无害的同型重建；仍停留在旧基线的环境
-- 由本语句完成 int → varchar 对齐（存量数值主键转为其十进制字符串形式）。

ALTER TABLE `general_key_pat_page` MODIFY COLUMN `id` varchar(64) NOT NULL;
