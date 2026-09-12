-- 2026-09-12-01 组训数据报/电传：原始采集时间轴、轮次栅栏、冻结满分与可恢复收尾
-- 先停全部实例写入并备份，再按迁移清单顺序执行。DDL 隐式提交；不得 mysql --force。
-- 存量 protocol_version=0：历史训练没有原始采集时间轴，成绩保持不变、也不重算；
-- 缺时序的旧未完成训练须先完成或作废后新建。
-- 新协议按学员 capture_started_at 锚定采集零点；训练行的 end_time 只作为 60 秒补交/收尾窗口起点。
-- 组训电传无倒计时与暂停语义（状态只有 0 未开始 / 1 进行中 / 2 已完成），
-- 因此本脚本不引入 deadline / paused_at / pause_intervals / countdown_seconds 这类恒为 NULL 的死列。
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS mig_20260912_telex_add;
DROP PROCEDURE IF EXISTS mig_20260912_telex_modify;
DELIMITER $$
CREATE PROCEDURE mig_20260912_telex_add(IN target_table varchar(64), IN target_column varchar(64), IN definition_text text)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name=target_table AND column_name=target_column) THEN
    SET @telex_capture_ddl=CONCAT('ALTER TABLE `',target_table,'` ADD COLUMN `',target_column,'` ',definition_text);
    PREPARE telex_capture_statement FROM @telex_capture_ddl;
    EXECUTE telex_capture_statement;
    DEALLOCATE PREPARE telex_capture_statement;
  END IF;
END$$
CREATE PROCEDURE mig_20260912_telex_modify(IN target_table varchar(64), IN target_column varchar(64), IN target_type varchar(64))
BEGIN
  DECLARE original_type varchar(1024) CHARACTER SET utf8mb4;
  DECLARE original_charset varchar(64);
  DECLARE original_collation varchar(64);
  DECLARE original_nullable varchar(3);
  DECLARE original_default longtext CHARACTER SET utf8mb4;
  DECLARE original_extra varchar(256) CHARACTER SET utf8mb4;
  DECLARE original_comment text CHARACTER SET utf8mb4;
  DECLARE original_generation longtext CHARACTER SET utf8mb4;
  DECLARE quoted_default longtext CHARACTER SET utf8mb4;
  DECLARE quoted_comment longtext CHARACTER SET utf8mb4;
  SELECT column_type,character_set_name,collation_name,is_nullable,column_default,extra,column_comment,generation_expression
    INTO original_type,original_charset,original_collation,original_nullable,original_default,original_extra,original_comment,original_generation
    FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=target_table AND column_name=target_column;
  IF original_type IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Telex capture prerequisite column missing';
  END IF;
  IF original_type <> target_type THEN
    SET quoted_default=CONCAT('CONVERT(0x',HEX(original_default),' USING utf8mb4)');
    SET quoted_comment=IF(FIND_IN_SET('NO_BACKSLASH_ESCAPES',@@SESSION.sql_mode),
      CONCAT('''',REPLACE(original_comment,'''',''''''),''''),QUOTE(original_comment));
    SET @telex_capture_ddl=CONCAT('ALTER TABLE `',target_table,'` MODIFY COLUMN `',target_column,'` ',target_type,
      IF(original_charset IS NULL,'',CONCAT(' CHARACTER SET `',original_charset,'` COLLATE `',original_collation,'`')),
      IF(original_generation='','',CONCAT(' GENERATED ALWAYS AS (',original_generation,') ',IF(LOCATE('STORED',original_extra)>0,'STORED','VIRTUAL'))),
      IF(original_nullable='YES',' NULL',' NOT NULL'),
      IF(original_default IS NULL OR original_generation<>'','',CONCAT(' DEFAULT (',IF(LOCATE('DEFAULT_GENERATED',original_extra)>0,original_default,quoted_default),')')),
      IF(original_generation='',CONCAT(' ',REPLACE(original_extra,'DEFAULT_GENERATED','')),IF(LOCATE('INVISIBLE',original_extra)>0,' INVISIBLE','')),
      ' COMMENT ',quoted_comment);
    PREPARE telex_capture_statement FROM @telex_capture_ddl;
    EXECUTE telex_capture_statement;
    DEALLOCATE PREPARE telex_capture_statement;
  END IF;
END$$
DELIMITER ;

-- ---- 训练行：冻结满分 + 采集协议版本 ----
CALL mig_20260912_telex_add('general_telex_pat','rule_score','int NULL');
CALL mig_20260912_telex_add('general_telex_pat','protocol_version','int NOT NULL DEFAULT 0');

-- ---- 成员行：轮次栅栏 + 采集锚点 + 服务端重算的有效采集时长 ----
CALL mig_20260912_telex_add('general_telex_pat_user','attempt','int NOT NULL DEFAULT 0');
CALL mig_20260912_telex_add('general_telex_pat_user','capture_started_at','datetime(6) NULL');
CALL mig_20260912_telex_add('general_telex_pat_user','active_millis','bigint NULL');

-- ---- 页行：原始提交行的轮次、采集区间与收到时刻（分析行这三列为空） ----
CALL mig_20260912_telex_add('general_telex_pat_user_value','attempt','int NULL');
CALL mig_20260912_telex_add('general_telex_pat_user_value','capture_intervals','longtext NULL');
CALL mig_20260912_telex_add('general_telex_pat_user_value','received_at','datetime(6) NULL');

-- 只改变必要类型/精度，保持既有列级字符集、排序规则、默认值和注释。
-- value 现在是不可重建的原始整页文本，rule_content 是结算基准的冻结规则 JSON，
-- 两者都早已超出 varchar(255)；口径与 2026-09-11-04 对 general 电子键做的完全一致。
CALL mig_20260912_telex_modify('general_telex_pat','rule_content','longtext');
CALL mig_20260912_telex_modify('general_telex_pat_user','deduct_info','longtext');
CALL mig_20260912_telex_modify('general_telex_pat_user','statistic_info','longtext');
CALL mig_20260912_telex_modify('general_telex_pat_user_value','value','longtext');

-- 收尾扫描按 (status,end_time) 定位「教员已结束且已过补交窗口」的训练，每 5 秒一次，必须走索引。
SET @telex_capture_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='general_telex_pat' AND index_name='idx_general_telex_pat_closing'),'DO 0','CREATE INDEX `idx_general_telex_pat_closing` ON `general_telex_pat` (`status`,`end_time`)'));
PREPARE telex_capture_statement FROM @telex_capture_ddl;
EXECUTE telex_capture_statement;
DEALLOCATE PREPARE telex_capture_statement;

DROP PROCEDURE mig_20260912_telex_add;
DROP PROCEDURE mig_20260912_telex_modify;
