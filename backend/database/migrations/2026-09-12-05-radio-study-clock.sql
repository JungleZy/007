-- 2026-09-12 persisted server-side clock for pre-job radio study.
-- Historical total_time/total_count values are intentionally untouched.
-- Each statement is guarded because MySQL 8 has no ADD COLUMN IF NOT EXISTS syntax.

SET @radio_clock_ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_radiotelephone_train'
        AND column_name = 'active_session_id') = 0,
    'ALTER TABLE `t_radiotelephone_train` ADD COLUMN `active_session_id` varchar(64) NULL',
    'DO 0'));
PREPARE radio_clock_ddl FROM @radio_clock_ddl;
EXECUTE radio_clock_ddl;
DEALLOCATE PREPARE radio_clock_ddl;

SET @radio_clock_ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_radiotelephone_train'
        AND column_name = 'session_started_at') = 0,
    'ALTER TABLE `t_radiotelephone_train` ADD COLUMN `session_started_at` datetime(6) NULL',
    'DO 0'));
PREPARE radio_clock_ddl FROM @radio_clock_ddl;
EXECUTE radio_clock_ddl;
DEALLOCATE PREPARE radio_clock_ddl;

SET @radio_clock_ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_radiotelephone_train'
        AND column_name = 'active_millis') = 0,
    'ALTER TABLE `t_radiotelephone_train` ADD COLUMN `active_millis` bigint NULL',
    'DO 0'));
PREPARE radio_clock_ddl FROM @radio_clock_ddl;
EXECUTE radio_clock_ddl;
DEALLOCATE PREPARE radio_clock_ddl;

SET @radio_clock_ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 't_radiotelephone_train'
        AND column_name = 'finalized_session_id') = 0,
    'ALTER TABLE `t_radiotelephone_train` ADD COLUMN `finalized_session_id` varchar(64) NULL',
    'DO 0'));
PREPARE radio_clock_ddl FROM @radio_clock_ddl;
EXECUTE radio_clock_ddl;
DEALLOCATE PREPARE radio_clock_ddl;
