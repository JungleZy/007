-- 2026-09-10-01 理论考试JSON载荷容量对齐
-- 在前序迁移后执行；停写并备份，DDL隐式提交。
-- 仅扩容，不截断或重算数据；保留列级字符集、排序规则、默认值、可空性及注释。
-- 已达目标容量则不改写，允许重复执行。
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS mig_20260910_theory_capacity;
DELIMITER $$
CREATE PROCEDURE mig_20260910_theory_capacity(IN target_table varchar(64), IN target_column varchar(64), IN target_type varchar(64))
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
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='JSON capacity prerequisite column missing';
  END IF;
  IF target_type IN ('text','longtext') AND original_charset IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='JSON capacity column must already be textual';
  END IF;
  IF original_type <> target_type AND NOT (target_type='text' AND original_type IN ('mediumtext','longtext')) THEN
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
CALL mig_20260910_theory_capacity('t_theory_knowledge_exam_test_paper','single_choice_list','longtext');
CALL mig_20260910_theory_capacity('t_theory_knowledge_exam_test_paper','multiple_choice_list','longtext');
CALL mig_20260910_theory_capacity('t_theory_knowledge_exam_test_paper','judge_list','longtext');
CALL mig_20260910_theory_capacity('t_theory_knowledge_exam_test_paper','completion_list','longtext');
CALL mig_20260910_theory_capacity('t_theory_knowledge_exam_test_paper','short_answer','longtext');
CALL mig_20260910_theory_capacity('t_theory_knowledge_exam_user','content','longtext');
DROP PROCEDURE mig_20260910_theory_capacity;
