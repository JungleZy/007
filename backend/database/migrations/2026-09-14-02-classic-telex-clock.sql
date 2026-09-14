-- Classic Telex server-authoritative active clock (idempotent).
SET @ddl = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_telex_pat_train' AND column_name = 'protocol_version') = 0,
  'ALTER TABLE `t_telex_pat_train` ADD COLUMN `protocol_version` int NOT NULL DEFAULT 0', 'DO 0'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_telex_pat_train' AND column_name = 'active_since') = 0,
  'ALTER TABLE `t_telex_pat_train` ADD COLUMN `active_since` bigint NULL', 'DO 0'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_telex_pat_train' AND column_name = 'accumulated_active_millis') = 0,
  'ALTER TABLE `t_telex_pat_train` ADD COLUMN `accumulated_active_millis` bigint NOT NULL DEFAULT 0', 'DO 0'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
