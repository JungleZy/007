-- Personal code-string exercises: historical rows remain protocol 0 and read-only.
-- No historical content, results or statuses are rewritten.

SET @entering_clock_ddl = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 't_entering_exercise'
     AND column_name = 'protocol_version') = 0,
  'ALTER TABLE t_entering_exercise ADD COLUMN protocol_version int NOT NULL DEFAULT 0',
  'DO 0'));
PREPARE entering_clock_ddl FROM @entering_clock_ddl;
EXECUTE entering_clock_ddl;
DEALLOCATE PREPARE entering_clock_ddl;

SET @entering_clock_ddl = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 't_entering_exercise'
     AND column_name = 'source_content') = 0,
  'ALTER TABLE t_entering_exercise ADD COLUMN source_content longtext NULL',
  'DO 0'));
PREPARE entering_clock_ddl FROM @entering_clock_ddl;
EXECUTE entering_clock_ddl;
DEALLOCATE PREPARE entering_clock_ddl;

SET @entering_clock_ddl = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 't_entering_exercise'
     AND column_name = 'elapsed_millis') = 0,
  'ALTER TABLE t_entering_exercise ADD COLUMN elapsed_millis bigint NOT NULL DEFAULT 0',
  'DO 0'));
PREPARE entering_clock_ddl FROM @entering_clock_ddl;
EXECUTE entering_clock_ddl;
DEALLOCATE PREPARE entering_clock_ddl;

SET @entering_clock_ddl = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 't_entering_exercise'
     AND column_name = 'active_started_at') = 0,
  'ALTER TABLE t_entering_exercise ADD COLUMN active_started_at datetime(6) NULL',
  'DO 0'));
PREPARE entering_clock_ddl FROM @entering_clock_ddl;
EXECUTE entering_clock_ddl;
DEALLOCATE PREPARE entering_clock_ddl;
