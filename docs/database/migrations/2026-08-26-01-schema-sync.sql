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
-- 本脚本幂等：CREATE TABLE IF NOT EXISTS + information_schema 判存的条件 ADD COLUMN，
-- 可安全重复执行（2026-09-07 起，迁移演练会对已迁移的 current 快照再跑一次）。
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
-- MySQL 8.0 不支持 `ADD COLUMN IF NOT EXISTS`（那是 MariaDB 语法），因此用
-- information_schema 判存 + PREPARE 做成幂等：列已存在时执行 `DO 0` 空操作。
-- 幂等是硬需求——2026-09-07 快照回灌后 project006.sql 已含该列，迁移演练
-- （scripts/rehearse-migrations.sh）会对 current 快照再跑一次本脚本。

SET @add_router_sign = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'simulation_router_room'
        AND column_name = 'is_start_sign') = 0,
    'ALTER TABLE `simulation_router_room` ADD COLUMN `is_start_sign` int NULL DEFAULT 1',
    'DO 0'));
PREPARE add_router_sign FROM @add_router_sign;
EXECUTE add_router_sign;
DEALLOCATE PREPARE add_router_sign;

SET @add_ticker_sign = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_post_ticker_tape_train'
        AND column_name = 'is_start_sign') = 0,
    'ALTER TABLE `t_post_ticker_tape_train` ADD COLUMN `is_start_sign` int NULL DEFAULT 1',
    'DO 0'));
PREPARE add_ticker_sign FROM @add_ticker_sign;
EXECUTE add_ticker_sign;
DEALLOCATE PREPARE add_ticker_sign;

-- ---- 3. 主键类型对齐（P1-8 第 3 类） ----
-- 旧基线（project006-base.sql）general_key_pat_page.id 与 general_ticker_pat_train_page.id
-- 均为 int AUTO_INCREMENT，实体为 UUID 字符串主键（GenerationType.UUID）。当前快照
-- （project006.sql）二者已是 varchar(64)，在该状态下两条语句均为无害的同型重建；
-- 仍停留在旧基线的环境由本节完成 int → varchar 对齐（存量数值主键转为其十进制
-- 字符串形式，AUTO_INCREMENT 随类型变更自动失效）。
-- 注：general_ticker_pat_train_page.id 由 2026-08-28 双快照迁移演练补入——迁移最初
-- 仅针对 current 快照编写，遗漏了该表在 base 快照的同类 int→varchar 缺口
-- （演练 diff-base 实证）；两表缺陷同型，故同节处理。

ALTER TABLE `general_key_pat_page` MODIFY COLUMN `id` varchar(64) NOT NULL;
ALTER TABLE `general_ticker_pat_train_page` MODIFY COLUMN `id` varchar(64) NOT NULL;
