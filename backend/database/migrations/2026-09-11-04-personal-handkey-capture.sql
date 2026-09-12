-- Stop writes and back up personal handkey tables before applying; DDL implicitly commits.
-- Historical scores and ambiguous rule units remain unchanged; protocol 0 cannot resume.
-- Reruns preserve existing column attributes and skip already-sufficient types/engines.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS mig_20260911_hand_engine;
DROP PROCEDURE IF EXISTS mig_20260911_hand_add;
DROP PROCEDURE IF EXISTS mig_20260911_hand_modify;
DELIMITER $$
CREATE PROCEDURE mig_20260911_hand_engine(IN target_table varchar(64))
BEGIN
  DECLARE original_engine varchar(64);
  SELECT engine INTO original_engine FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name=target_table AND table_type='BASE TABLE';
  IF original_engine IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Personal handkey prerequisite table missing';
  END IF;
  IF original_engine <> 'InnoDB' THEN
    SET @personal_hand_ddl=CONCAT('ALTER TABLE `',target_table,'` ENGINE=InnoDB');
    PREPARE personal_hand_statement FROM @personal_hand_ddl;
    EXECUTE personal_hand_statement;
    DEALLOCATE PREPARE personal_hand_statement;
  END IF;
END$$
CREATE PROCEDURE mig_20260911_hand_add(IN target_table varchar(64), IN target_column varchar(64), IN definition_text text)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name=target_table AND column_name=target_column) THEN
    SET @personal_hand_ddl=CONCAT('ALTER TABLE `',target_table,'` ADD COLUMN `',target_column,'` ',definition_text);
    PREPARE personal_hand_statement FROM @personal_hand_ddl;
    EXECUTE personal_hand_statement;
    DEALLOCATE PREPARE personal_hand_statement;
  END IF;
END$$
CREATE PROCEDURE mig_20260911_hand_modify(IN target_table varchar(64), IN target_column varchar(64), IN target_type varchar(64))
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
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Personal handkey prerequisite column missing';
  END IF;
  IF target_type='longtext' AND original_charset IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Personal handkey JSON column must already be textual';
  END IF;
  IF target_type='datetime(6)' AND original_type NOT LIKE 'datetime%' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Personal handkey clock column must already be datetime';
  END IF;
  IF original_type <> target_type THEN
    SET quoted_default=CONCAT('CONVERT(0x',HEX(original_default),' USING utf8mb4)');
    SET quoted_comment=IF(FIND_IN_SET('NO_BACKSLASH_ESCAPES',@@SESSION.sql_mode),
      CONCAT('''',REPLACE(original_comment,'''',''''''),''''),QUOTE(original_comment));
    SET @personal_hand_ddl=CONCAT('ALTER TABLE `',target_table,'` MODIFY COLUMN `',target_column,'` ',target_type,
      IF(original_charset IS NULL,'',CONCAT(' CHARACTER SET `',original_charset,'` COLLATE `',original_collation,'`')),
      IF(original_generation='','',CONCAT(' GENERATED ALWAYS AS (',original_generation,') ',IF(LOCATE('STORED',original_extra)>0,'STORED','VIRTUAL'))),
      IF(original_nullable='YES',' NULL',' NOT NULL'),
      IF(original_default IS NULL OR original_generation<>'','',CONCAT(' DEFAULT (',IF(LOCATE('DEFAULT_GENERATED',original_extra)>0,original_default,quoted_default),')')),
      IF(original_generation='',CONCAT(' ',REPLACE(original_extra,'DEFAULT_GENERATED','')),IF(LOCATE('INVISIBLE',original_extra)>0,' INVISIBLE','')),
      ' COMMENT ',quoted_comment);
    PREPARE personal_hand_statement FROM @personal_hand_ddl;
    EXECUTE personal_hand_statement;
    DEALLOCATE PREPARE personal_hand_statement;
  END IF;
END$$
DELIMITER ;

CALL mig_20260911_hand_engine('t_post_telegram_train');
CALL mig_20260911_hand_engine('t_post_telegram_train_floor_content');
CALL mig_20260911_hand_engine('t_post_telegram_train_floor_content_value');

CALL mig_20260911_hand_add('t_post_telegram_train','protocol_version','int NOT NULL DEFAULT 0');
CALL mig_20260911_hand_add('t_post_telegram_train','attempt','int NOT NULL DEFAULT 0');
CALL mig_20260911_hand_add('t_post_telegram_train','full_score','int NULL');
CALL mig_20260911_hand_add('t_post_telegram_train','active_millis','bigint NULL');
CALL mig_20260911_hand_add('t_post_telegram_train_floor_content_value','attempt','int NOT NULL DEFAULT 0');
CALL mig_20260911_hand_add('t_post_telegram_train_floor_content_value','capture_intervals','longtext NULL');
CALL mig_20260911_hand_add('t_post_telegram_train_floor_content_value','received_at','datetime(6) NULL');

CALL mig_20260911_hand_modify('t_post_telegram_train','rule_content','longtext');
CALL mig_20260911_hand_modify('t_post_telegram_train','statistic_info','longtext');
CALL mig_20260911_hand_modify('t_post_telegram_train','deduct_info','longtext');
CALL mig_20260911_hand_modify('t_post_telegram_train','start_time','datetime(6)');
CALL mig_20260911_hand_modify('t_post_telegram_train','end_time','datetime(6)');
CALL mig_20260911_hand_modify('t_post_telegram_train_floor_content_value','message_body','longtext');
CALL mig_20260911_hand_modify('t_post_telegram_train_floor_content_value','standard','longtext');
CALL mig_20260911_hand_modify('t_post_telegram_train_floor_content_value','finish_info','longtext');
CALL mig_20260911_hand_modify('t_post_telegram_train_floor_content_value','resolver','longtext');
CALL mig_20260911_hand_modify('t_post_telegram_train_floor_content_value','capture_intervals','longtext');
CALL mig_20260911_hand_modify('t_post_telegram_train_floor_content_value','received_at','datetime(6)');

DROP PROCEDURE mig_20260911_hand_engine;
DROP PROCEDURE mig_20260911_hand_add;
DROP PROCEDURE mig_20260911_hand_modify;
