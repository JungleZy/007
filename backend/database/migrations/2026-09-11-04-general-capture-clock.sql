-- 2026-09-11-04 General手键/电子键原始采集、轮次隔离与可恢复收尾
-- 先停全部实例写入并备份，再按迁移清单顺序执行。DDL隐式提交；不得mysql --force。
-- 存量protocol_version=0，原成绩不重算，缺原始时序的旧未完成训练须先完成或终止后新建。
-- 新协议按学员capture_started_at锚定；关闭先进入status=3，60秒补交后全部结算成功才status=2。
-- 手动结束按学员收到通知时停止；end_time用于60秒收尾窗口起点，不替代采集时刻。
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS mig_20260911_general_add;
DROP PROCEDURE IF EXISTS mig_20260911_general_modify;
DELIMITER $$
CREATE PROCEDURE mig_20260911_general_add(IN target_table varchar(64), IN target_column varchar(64), IN definition_text text)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name=target_table AND column_name=target_column) THEN
    SET @general_capture_ddl=CONCAT('ALTER TABLE `',target_table,'` ADD COLUMN `',target_column,'` ',definition_text);
    PREPARE general_capture_statement FROM @general_capture_ddl;
    EXECUTE general_capture_statement;
    DEALLOCATE PREPARE general_capture_statement;
  END IF;
END$$
CREATE PROCEDURE mig_20260911_general_modify(IN target_table varchar(64), IN target_column varchar(64), IN target_type varchar(64))
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
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='General capture prerequisite column missing';
  END IF;
  IF original_type <> target_type THEN
    SET quoted_default=CONCAT('CONVERT(0x',HEX(original_default),' USING utf8mb4)');
    SET quoted_comment=IF(FIND_IN_SET('NO_BACKSLASH_ESCAPES',@@SESSION.sql_mode),
      CONCAT('''',REPLACE(original_comment,'''',''''''),''''),QUOTE(original_comment));
    SET @general_capture_ddl=CONCAT('ALTER TABLE `',target_table,'` MODIFY COLUMN `',target_column,'` ',target_type,
      IF(original_charset IS NULL,'',CONCAT(' CHARACTER SET `',original_charset,'` COLLATE `',original_collation,'`')),
      IF(original_generation='','',CONCAT(' GENERATED ALWAYS AS (',original_generation,') ',IF(LOCATE('STORED',original_extra)>0,'STORED','VIRTUAL'))),
      IF(original_nullable='YES',' NULL',' NOT NULL'),
      IF(original_default IS NULL OR original_generation<>'','',CONCAT(' DEFAULT (',IF(LOCATE('DEFAULT_GENERATED',original_extra)>0,original_default,quoted_default),')')),
      IF(original_generation='',CONCAT(' ',REPLACE(original_extra,'DEFAULT_GENERATED','')),IF(LOCATE('INVISIBLE',original_extra)>0,' INVISIBLE','')),
      ' COMMENT ',quoted_comment);
    PREPARE general_capture_statement FROM @general_capture_ddl;
    EXECUTE general_capture_statement;
    DEALLOCATE PREPARE general_capture_statement;
  END IF;
END$$
DELIMITER ;

CALL mig_20260911_general_add('general_ticker_pat','rule_score','int NULL');
CALL mig_20260911_general_add('general_ticker_pat','protocol_version','int NOT NULL DEFAULT 0');
CALL mig_20260911_general_add('general_key_pat','protocol_version','int NOT NULL DEFAULT 0');
CALL mig_20260911_general_add('general_ticker_pat_train_user','attempt','int NOT NULL DEFAULT 0');
CALL mig_20260911_general_add('general_ticker_pat_train_user','capture_started_at','datetime(6) NULL');
CALL mig_20260911_general_add('general_ticker_pat_train_user','active_millis','bigint NULL');
CALL mig_20260911_general_add('general_key_pat_user','attempt','int NOT NULL DEFAULT 0');
CALL mig_20260911_general_add('general_key_pat_user','capture_started_at','datetime(6) NULL');
CALL mig_20260911_general_add('general_key_pat_user','active_millis','bigint NULL');
CALL mig_20260911_general_add('general_key_pat_user','capture_pages','longtext NOT NULL DEFAULT (''{}'')');
CALL mig_20260911_general_add('general_ticker_pat_train_user_value','attempt','int NULL');
CALL mig_20260911_general_add('general_ticker_pat_train_user_value','capture_intervals','longtext NULL');
CALL mig_20260911_general_add('general_ticker_pat_train_user_value','received_at','datetime(6) NULL');

-- 只改变必要类型/精度，保持既有列级字符集、排序规则、默认值和注释。
CALL mig_20260911_general_modify('general_key_pat','rule_content','longtext');
CALL mig_20260911_general_modify('general_key_pat_user','deduct_info','longtext');
CALL mig_20260911_general_modify('general_key_pat_user','statistic_info','longtext');
CALL mig_20260911_general_modify('general_key_pat_user_value','value','longtext');
CALL mig_20260911_general_modify('general_key_pat_user_value','time','longtext');
CALL mig_20260911_general_modify('general_key_pat_user_value_resolver','value','longtext');
CALL mig_20260911_general_modify('general_key_pat_user_value_resolver','time','longtext');
CALL mig_20260911_general_modify('general_key_pat_train_more','more_group','longtext');
CALL mig_20260911_general_modify('general_key_pat_train_more','more_line','longtext');
CALL mig_20260911_general_modify('general_ticker_pat','start_time','datetime(6)');
CALL mig_20260911_general_modify('general_ticker_pat','end_time','datetime(6)');
CALL mig_20260911_general_modify('general_key_pat','start_time','datetime(6)');
CALL mig_20260911_general_modify('general_key_pat','end_time','datetime(6)');

-- 空集合的存量规范化不伪造采集时序；新旧协议仍以protocol_version区分。
UPDATE general_key_pat_page SET `value`='[]' WHERE `value` IS NULL;

SET @general_capture_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='general_ticker_pat' AND index_name='idx_general_ticker_pat_closing'),'DO 0','CREATE INDEX `idx_general_ticker_pat_closing` ON `general_ticker_pat` (`status`,`end_time`)'));
PREPARE general_capture_statement FROM @general_capture_ddl;
EXECUTE general_capture_statement;
DEALLOCATE PREPARE general_capture_statement;

SET @general_capture_ddl=(SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='general_key_pat' AND index_name='idx_general_key_pat_closing'),'DO 0','CREATE INDEX `idx_general_key_pat_closing` ON `general_key_pat` (`status`,`end_time`)'));
PREPARE general_capture_statement FROM @general_capture_ddl;
EXECUTE general_capture_statement;
DEALLOCATE PREPARE general_capture_statement;

DROP PROCEDURE mig_20260911_general_add;
DROP PROCEDURE mig_20260911_general_modify;
