-- 2026-09-12-06 岗位汉字录入与个人训练时钟精度：100%必须可保存，开始/结束时间保留微秒。
-- 现场复核：旧快照 accuracy double(2,0) 在100%提交时抛 MySQL 1264，整笔结算回滚；旧 DATETIME(0) 会把立即提交舍入到负时长。
-- 还原：停写后恢复备份；不能把已有100%或微秒时间缩回旧类型。
SET @entering_accuracy_ddl=(SELECT IF(EXISTS(
  SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='t_post_entering_exercise' AND column_name='accuracy'
    AND data_type='double' AND numeric_precision >= 15
), 'DO 0', 'ALTER TABLE `t_post_entering_exercise` MODIFY COLUMN `accuracy` DOUBLE NULL'));
PREPARE entering_accuracy_statement FROM @entering_accuracy_ddl;
EXECUTE entering_accuracy_statement;
DEALLOCATE PREPARE entering_accuracy_statement;

DROP PROCEDURE IF EXISTS mig_20260912_personal_clock;
DELIMITER $$
CREATE PROCEDURE mig_20260912_personal_clock(IN target_table varchar(64), IN target_column varchar(64))
BEGIN
  DECLARE original_comment text;
  DECLARE current_type varchar(64);
  DECLARE current_precision int;
  SELECT column_type, datetime_precision, column_comment INTO current_type, current_precision, original_comment
    FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name=target_table AND column_name=target_column;
  IF current_type IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Personal clock prerequisite column missing';
  END IF;
  IF current_type NOT LIKE 'datetime%' OR current_precision <> 6 THEN
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
