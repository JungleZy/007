-- ============================================================================
-- 迁移 02：MyISAM → InnoDB（P1-7）
--
-- ⚠ 执行前提（必须满足，缺一不可）：
--   1. 全库备份（mysqldump --single-transaction 对 MyISAM 无效，需停写后备份）。
--   2. 停服窗口执行：ALTER ENGINE 会重建整表并持锁，期间对应功能不可用；
--      且应用在事务中混写 MyISAM/InnoDB，转换途中启动应用会产生不一致。
--   3. 先执行 2026-08-26-01-schema-sync.sql，再执行本脚本，
--      最后才可在 %prod 启用 quarkus.hibernate-orm.database.generation=validate。
--
-- 背景：以下 22 张表在 @Transactional 写路径中被删除/写入，但 MyISAM 不支持事务，
-- 写入立即生效且无法回滚（评审 P1-7，22 张清单经逐表实测核实）。
-- 本脚本幂等：对已是 InnoDB 的表执行仅触发一次无害重建。
-- ============================================================================

ALTER TABLE `general_key_pat` ENGINE = InnoDB;
ALTER TABLE `general_key_pat_page` ENGINE = InnoDB;
ALTER TABLE `general_key_pat_train_more` ENGINE = InnoDB;
ALTER TABLE `general_key_pat_user` ENGINE = InnoDB;
ALTER TABLE `general_key_pat_user_value` ENGINE = InnoDB;
ALTER TABLE `general_key_pat_user_value_resolver` ENGINE = InnoDB;
ALTER TABLE `general_ticker_pat` ENGINE = InnoDB;
ALTER TABLE `general_ticker_pat_train_page` ENGINE = InnoDB;
ALTER TABLE `general_ticker_pat_train_user` ENGINE = InnoDB;
ALTER TABLE `general_ticker_pat_train_user_value` ENGINE = InnoDB;
ALTER TABLE `hand_key_err_log` ENGINE = InnoDB;
ALTER TABLE `simulation_router_room_page` ENGINE = InnoDB;
ALTER TABLE `simulation_router_room_page_value` ENGINE = InnoDB;
ALTER TABLE `t_post_telegram_train_content_value` ENGINE = InnoDB;
ALTER TABLE `t_post_telegraph_key_pat_train_more` ENGINE = InnoDB;
ALTER TABLE `t_post_telegraph_key_pat_train_page` ENGINE = InnoDB;
ALTER TABLE `t_post_telegraph_key_pat_train_page_value` ENGINE = InnoDB;
ALTER TABLE `t_post_telex_pat_train_page` ENGINE = InnoDB;
ALTER TABLE `t_post_telex_pat_train_page_value` ENGINE = InnoDB;
ALTER TABLE `t_post_ticker_tape_train_page` ENGINE = InnoDB;
ALTER TABLE `t_post_ticker_tape_train_page_value` ENGINE = InnoDB;
ALTER TABLE `t_ticker_tape_train_stage_setting` ENGINE = InnoDB;
