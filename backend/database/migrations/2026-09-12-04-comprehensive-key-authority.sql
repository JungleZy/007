-- 2026-09-12-04 综合电子键：冻结题面与服务端有效时间。
-- 停写并备份后执行；历史完成成绩不变，旧未完成记录缺协议/时钟时返回208，须新建。
-- 还原：先停写并还原匹配版本的应用；完整恢复用备份。删除下列4列会丢失新记录重算依据。
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS mig_20260912_comprehensive_add;
DELIMITER $$
CREATE PROCEDURE mig_20260912_comprehensive_add(IN target_column varchar(64), IN definition_text text)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='t_telegraph_key_pat_synthetical_train' AND column_name=target_column) THEN
    SET @comprehensive_ddl=CONCAT('ALTER TABLE `t_telegraph_key_pat_synthetical_train` ADD COLUMN `',target_column,'` ',definition_text);
    PREPARE comprehensive_statement FROM @comprehensive_ddl;
    EXECUTE comprehensive_statement;
    DEALLOCATE PREPARE comprehensive_statement;
  END IF;
END$$
DELIMITER ;
CALL mig_20260912_comprehensive_add('started_at','datetime(6) NULL');
CALL mig_20260912_comprehensive_add('accumulated_active_millis','bigint NULL');
CALL mig_20260912_comprehensive_add('protocol_version','int NULL');
CALL mig_20260912_comprehensive_add('source_content','longtext NULL');
DROP PROCEDURE mig_20260912_comprehensive_add;
