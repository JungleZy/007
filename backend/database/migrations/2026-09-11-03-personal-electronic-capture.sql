-- Stop all application writes and back up these personal electronic tables before applying.
-- DDL commits implicitly: do not use mysql --force. Reruns preserve acknowledged raw pages.
-- Existing scores and unknown rule units are deliberately not rewritten.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS mig_20260911_electronic_engine;
DROP PROCEDURE IF EXISTS mig_20260911_electronic_add;
DROP PROCEDURE IF EXISTS mig_20260911_electronic_modify;
DROP PROCEDURE IF EXISTS mig_20260911_electronic_check;
DELIMITER $$
CREATE PROCEDURE mig_20260911_electronic_engine(IN target_table varchar(64))
BEGIN
  DECLARE original_engine varchar(64);
  SELECT engine INTO original_engine FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name=target_table AND table_type='BASE TABLE';
  IF original_engine IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Personal electronic prerequisite table missing';
  END IF;
  IF original_engine <> 'InnoDB' THEN
    SET @electronic_capture_ddl=CONCAT('ALTER TABLE `',target_table,'` ENGINE = InnoDB');
    PREPARE electronic_capture_statement FROM @electronic_capture_ddl;
    EXECUTE electronic_capture_statement;
    DEALLOCATE PREPARE electronic_capture_statement;
  END IF;
END$$
CREATE PROCEDURE mig_20260911_electronic_add(IN target_table varchar(64), IN target_column varchar(64), IN definition_text text)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name=target_table AND column_name=target_column) THEN
    SET @electronic_capture_ddl=CONCAT('ALTER TABLE `',target_table,'` ADD COLUMN `',target_column,'` ',definition_text);
    PREPARE electronic_capture_statement FROM @electronic_capture_ddl;
    EXECUTE electronic_capture_statement;
    DEALLOCATE PREPARE electronic_capture_statement;
  END IF;
END$$
CREATE PROCEDURE mig_20260911_electronic_modify(IN target_table varchar(64), IN target_column varchar(64), IN target_type varchar(64))
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
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Personal electronic prerequisite column missing';
  END IF;
  IF target_type='datetime(6)' AND original_type NOT LIKE 'datetime%' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Personal electronic timestamp column must already be datetime';
  END IF;
  IF original_type <> target_type THEN
    SET quoted_default=CONCAT('CONVERT(0x',HEX(original_default),' USING utf8mb4)');
    SET quoted_comment=IF(FIND_IN_SET('NO_BACKSLASH_ESCAPES',@@SESSION.sql_mode),
      CONCAT('''',REPLACE(original_comment,'''',''''''),''''),QUOTE(original_comment));
    SET @electronic_capture_ddl=CONCAT('ALTER TABLE `',target_table,'` MODIFY COLUMN `',target_column,'` ',target_type,
      IF(original_charset IS NULL,'',CONCAT(' CHARACTER SET `',original_charset,'` COLLATE `',original_collation,'`')),
      IF(original_generation='','',CONCAT(' GENERATED ALWAYS AS (',original_generation,') ',IF(LOCATE('STORED',original_extra)>0,'STORED','VIRTUAL'))),
      IF(original_nullable='YES',' NULL',' NOT NULL'),
      IF(original_default IS NULL OR original_generation<>'','',CONCAT(' DEFAULT (',IF(LOCATE('DEFAULT_GENERATED',original_extra)>0,original_default,quoted_default),')')),
      IF(original_generation='',CONCAT(' ',REPLACE(original_extra,'DEFAULT_GENERATED','')),IF(LOCATE('INVISIBLE',original_extra)>0,' INVISIBLE','')),
      ' COMMENT ',quoted_comment);
    PREPARE electronic_capture_statement FROM @electronic_capture_ddl;
    EXECUTE electronic_capture_statement;
    DEALLOCATE PREPARE electronic_capture_statement;
  END IF;
END$$
CREATE PROCEDURE mig_20260911_electronic_check()
BEGIN
  -- Do not mistake an incompatible pre-existing table for a successful CREATE IF NOT EXISTS.
  IF (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='t_post_telegraph_key_pat_train_raw_page'
        AND is_nullable='NO' AND generation_expression=''
        AND ((column_name IN ('id','train_id') AND data_type='varchar' AND character_maximum_length>=255)
          OR (column_name IN ('page_number','attempt') AND data_type='int' AND column_type NOT LIKE '%unsigned%')
          OR (column_name IN ('value','capture_intervals') AND data_type='longtext')
          OR (column_name='received_at' AND data_type='datetime' AND datetime_precision=6))) <> 7 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Personal electronic raw-page column definition mismatch';
  END IF;
  IF NOT EXISTS (SELECT index_name FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='t_post_telegraph_key_pat_train_raw_page' AND index_name='PRIMARY'
      GROUP BY index_name HAVING COUNT(*)=1 AND MAX(column_name)='id' AND SUM(sub_part IS NOT NULL)=0) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Personal electronic raw-page primary key mismatch';
  END IF;
  IF NOT EXISTS (SELECT index_name FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='t_post_telegraph_key_pat_train_raw_page' AND non_unique=0
      GROUP BY index_name HAVING COUNT(*)=2 AND GROUP_CONCAT(column_name ORDER BY seq_in_index)='train_id,page_number'
        AND SUM(sub_part IS NOT NULL)=0) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Personal electronic raw-page unique page key missing';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='t_post_telegraph_key_pat_train'
        AND generation_expression=''
        AND ((column_name IN ('protocol_version','attempt') AND data_type='int' AND column_type NOT LIKE '%unsigned%'
              AND is_nullable='NO' AND column_default='0')
          OR (column_name='full_score' AND data_type='decimal' AND numeric_precision=38 AND numeric_scale=2
              AND is_nullable='YES'))) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Personal electronic authority column definition mismatch';
  END IF;
END$$
DELIMITER ;

CALL mig_20260911_electronic_engine('t_post_telegraph_key_pat_train');
CALL mig_20260911_electronic_engine('t_post_telegraph_key_pat_train_page');
CALL mig_20260911_electronic_engine('t_post_telegraph_key_pat_train_page_value');
CALL mig_20260911_electronic_engine('t_post_telegraph_key_pat_train_more');

CALL mig_20260911_electronic_add('t_post_telegraph_key_pat_train','protocol_version','int NOT NULL DEFAULT 0');
CALL mig_20260911_electronic_add('t_post_telegraph_key_pat_train','attempt','int NOT NULL DEFAULT 0');
CALL mig_20260911_electronic_add('t_post_telegraph_key_pat_train','full_score','decimal(38,2) NULL');

-- Change only insufficient type/precision; retain column metadata and all historical timestamps.
CALL mig_20260911_electronic_modify('t_post_telegraph_key_pat_train','rule_content','longtext');
CALL mig_20260911_electronic_modify('t_post_telegraph_key_pat_train','deduct_info','longtext');
CALL mig_20260911_electronic_modify('t_post_telegraph_key_pat_train','begin_time','datetime(6)');
CALL mig_20260911_electronic_modify('t_post_telegraph_key_pat_train','end_time','datetime(6)');
CALL mig_20260911_electronic_modify('t_post_telegraph_key_pat_train_page','key','longtext');
CALL mig_20260911_electronic_modify('t_post_telegraph_key_pat_train_page','value','longtext');
CALL mig_20260911_electronic_modify('t_post_telegraph_key_pat_train_page','time','longtext');
CALL mig_20260911_electronic_modify('t_post_telegraph_key_pat_train_page_value','key','longtext');
CALL mig_20260911_electronic_modify('t_post_telegraph_key_pat_train_page_value','value','longtext');
CALL mig_20260911_electronic_modify('t_post_telegraph_key_pat_train_page_value','time','longtext');

UPDATE `t_post_telegraph_key_pat_train_page`
SET `value` = '[]' WHERE `value` IS NULL OR TRIM(`value`) = '';
UPDATE `t_post_telegraph_key_pat_train_page_value`
SET `value` = '[]' WHERE `value` IS NULL OR TRIM(`value`) = '';

CREATE TABLE IF NOT EXISTS `t_post_telegraph_key_pat_train_raw_page` (
  `id` VARCHAR(255) NOT NULL,
  `train_id` VARCHAR(255) NOT NULL,
  `page_number` INT NOT NULL,
  `attempt` INT NOT NULL,
  `value` LONGTEXT NOT NULL,
  `capture_intervals` LONGTEXT NOT NULL,
  `received_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_personal_key_raw_page` (`train_id`, `page_number`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CALL mig_20260911_electronic_engine('t_post_telegraph_key_pat_train_raw_page');
CALL mig_20260911_electronic_modify('t_post_telegraph_key_pat_train_raw_page','received_at','datetime(6)');
CALL mig_20260911_electronic_check();

DROP PROCEDURE mig_20260911_electronic_engine;
DROP PROCEDURE mig_20260911_electronic_add;
DROP PROCEDURE mig_20260911_electronic_modify;
DROP PROCEDURE mig_20260911_electronic_check;
