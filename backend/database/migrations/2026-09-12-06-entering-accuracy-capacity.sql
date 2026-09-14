-- 2026-09-12-06 岗位汉字录入正确率：100%必须可保存，小数不得被截成整数。
-- 现场复核：旧快照 double(2,0) 在100%提交时抛 MySQL 1264，整笔结算回滚。
-- 实体 Double 对应无精度限制的 double；仅扩容此列，不改历史值。
-- 还原：停写后恢复备份，不能把已有100%和小数正确率强行缩回 double(2,0)。
SET @entering_accuracy_ddl=(SELECT IF(EXISTS(
  SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='t_post_entering_exercise' AND column_name='accuracy' AND column_type='double'
), 'DO 0', 'ALTER TABLE `t_post_entering_exercise` MODIFY COLUMN `accuracy` DOUBLE NULL'));
PREPARE entering_accuracy_statement FROM @entering_accuracy_ddl;
EXECUTE entering_accuracy_statement;
DEALLOCATE PREPARE entering_accuracy_statement;

-- 旧 DATETIME(0) 会把开始时间舍入到下一秒，立即提交可能得到负时长。
-- 与实体生成的 datetime(6) 对齐；不改历史时间值，只保留后续写入的微秒精度。
DROP PROCEDURE IF EXISTS mig_20260912_personal_clock;
DELIMITER $$
CREATE PROCEDURE mig_20260912_personal_clock(IN target_table varchar(64), IN target_column varchar(64))
BEGIN
  DECLARE original_comment text;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name=target_table AND column_name=target_column AND datetime_precision<>6) THEN
    SELECT column_comment INTO original_comment FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name=target_table AND column_name=target_column;
    SET @personal_clock_ddl=CONCAT('ALTER TABLE `',target_table,'` MODIFY COLUMN `',target_column,'` datetime(6) NULL COMMENT ',QUOTE(original_comment));
    PREPARE personal_clock_statement FROM @personal_clock_ddl;
    EXECUTE personal_clock_statement;
    DEALLOCATE PREPARE personal_clock_statement;
  END IF;
END$$
DELIMITER ;
CALL mig_20260912_personal_clock('t_post_entering_exercise','start_time');
CALL mig_20260912_personal_clock('t_post_entering_exercise','end_time');
CALL mig_20260912_personal_clock('t_post_radiotelephone_train','start_time');
CALL mig_20260912_personal_clock('t_post_radiotelephone_train','end_time');
CALL mig_20260912_personal_clock('t_post_military_term_train','start_time');
CALL mig_20260912_personal_clock('t_post_military_term_train','end_time');
DROP PROCEDURE mig_20260912_personal_clock;
