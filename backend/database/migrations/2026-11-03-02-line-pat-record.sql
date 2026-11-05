-- 线路通报：持久化每名拍发人员的原始按键时长，支持重新进入训练后查看本人记录。
-- 在部署新版后端前执行；可重复执行，不修改已有收报答案。
SET @line_pat_record_ddl = (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'simulation_router_room_user'
      AND column_name = 'pat_code_log'),
  'DO 0',
  'ALTER TABLE `simulation_router_room_user` ADD COLUMN `pat_code_log` LONGTEXT NULL COMMENT ''线路通报拍发记录'''));
PREPARE line_pat_record_ddl FROM @line_pat_record_ddl;
EXECUTE line_pat_record_ddl;
DEALLOCATE PREPARE line_pat_record_ddl;
