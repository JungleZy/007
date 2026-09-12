-- 2026-09-11-03 数据报/电传拍发原始采集与持久化倒计时
-- 部署对应后端前停写并备份；MySQL DDL 隐式提交。
-- 按 information_schema 判存，可重复执行且不覆盖已采用新协议的训练。
-- 存量训练 protocol_version=0、attempt=0；保留所有历史成绩，不回填虚构采集时序。
-- 新建训练由服务显式设置 protocol_version=1；旧未完成训练须终止并重新创建。

-- 采集偏移以本轮 start_time 为锚点，持久化后仍须保留亚秒精度；不改已有时间值。
ALTER TABLE `t_post_telex_pat_train` MODIFY COLUMN `start_time` datetime(6) NULL;

SET @post_telex_capture_ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_post_telex_pat_train'
        AND column_name = 'protocol_version') = 0,
    'ALTER TABLE `t_post_telex_pat_train` ADD COLUMN `protocol_version` int NOT NULL DEFAULT 0',
    'DO 0'));
PREPARE post_telex_capture_ddl FROM @post_telex_capture_ddl;
EXECUTE post_telex_capture_ddl;
DEALLOCATE PREPARE post_telex_capture_ddl;

SET @post_telex_capture_ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_post_telex_pat_train'
        AND column_name = 'attempt') = 0,
    'ALTER TABLE `t_post_telex_pat_train` ADD COLUMN `attempt` int NOT NULL DEFAULT 0',
    'DO 0'));
PREPARE post_telex_capture_ddl FROM @post_telex_capture_ddl;
EXECUTE post_telex_capture_ddl;
DEALLOCATE PREPARE post_telex_capture_ddl;

SET @post_telex_capture_ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_post_telex_pat_train'
        AND column_name = 'countdown_seconds') = 0,
    'ALTER TABLE `t_post_telex_pat_train` ADD COLUMN `countdown_seconds` int NULL',
    'DO 0'));
PREPARE post_telex_capture_ddl FROM @post_telex_capture_ddl;
EXECUTE post_telex_capture_ddl;
DEALLOCATE PREPARE post_telex_capture_ddl;

SET @post_telex_capture_ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_post_telex_pat_train'
        AND column_name = 'deadline') = 0,
    'ALTER TABLE `t_post_telex_pat_train` ADD COLUMN `deadline` datetime(6) NULL',
    'DO 0'));
PREPARE post_telex_capture_ddl FROM @post_telex_capture_ddl;
EXECUTE post_telex_capture_ddl;
DEALLOCATE PREPARE post_telex_capture_ddl;

SET @post_telex_capture_ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_post_telex_pat_train'
        AND column_name = 'paused_at') = 0,
    'ALTER TABLE `t_post_telex_pat_train` ADD COLUMN `paused_at` datetime(6) NULL',
    'DO 0'));
PREPARE post_telex_capture_ddl FROM @post_telex_capture_ddl;
EXECUTE post_telex_capture_ddl;
DEALLOCATE PREPARE post_telex_capture_ddl;

SET @post_telex_capture_ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_post_telex_pat_train'
        AND column_name = 'pause_intervals') = 0,
    'ALTER TABLE `t_post_telex_pat_train` ADD COLUMN `pause_intervals` longtext NULL',
    'DO 0'));
PREPARE post_telex_capture_ddl FROM @post_telex_capture_ddl;
EXECUTE post_telex_capture_ddl;
DEALLOCATE PREPARE post_telex_capture_ddl;

SET @post_telex_capture_ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_post_telex_pat_train_page_value'
        AND column_name = 'attempt') = 0,
    'ALTER TABLE `t_post_telex_pat_train_page_value` ADD COLUMN `attempt` int NULL',
    'DO 0'));
PREPARE post_telex_capture_ddl FROM @post_telex_capture_ddl;
EXECUTE post_telex_capture_ddl;
DEALLOCATE PREPARE post_telex_capture_ddl;

SET @post_telex_capture_ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_post_telex_pat_train_page_value'
        AND column_name = 'capture_intervals') = 0,
    'ALTER TABLE `t_post_telex_pat_train_page_value` ADD COLUMN `capture_intervals` longtext NULL',
    'DO 0'));
PREPARE post_telex_capture_ddl FROM @post_telex_capture_ddl;
EXECUTE post_telex_capture_ddl;
DEALLOCATE PREPARE post_telex_capture_ddl;

SET @post_telex_capture_ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_post_telex_pat_train_page_value'
        AND column_name = 'received_at') = 0,
    'ALTER TABLE `t_post_telex_pat_train_page_value` ADD COLUMN `received_at` datetime(6) NULL',
    'DO 0'));
PREPARE post_telex_capture_ddl FROM @post_telex_capture_ddl;
EXECUTE post_telex_capture_ddl;
DEALLOCATE PREPARE post_telex_capture_ddl;

-- 原在此处的 t_menus 菜单 component 路径 UPDATE 属数据迁移且强耦合前端组件路径，
-- 已拆出到 2026-09-12-03-menu-telex-component-path.sql，须与对应前端版本一并执行。
