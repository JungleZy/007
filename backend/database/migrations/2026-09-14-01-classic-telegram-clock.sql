-- 2026-09-14-01 classic Telegram server-authoritative active clock
-- Idempotent MySQL 8 migration. Existing protocolVersion=0 rows retain legacy metrics.
SET @ddl = (SELECT IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_telegram_train' AND column_name='protocol_version')=0, 'ALTER TABLE `t_telegram_train` ADD COLUMN `protocol_version` int NOT NULL DEFAULT 0', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = (SELECT IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_telegram_train' AND column_name='active_since')=0, 'ALTER TABLE `t_telegram_train` ADD COLUMN `active_since` bigint NULL', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
SET @ddl = (SELECT IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_telegram_train' AND column_name='accumulated_active_millis')=0, 'ALTER TABLE `t_telegram_train` ADD COLUMN `accumulated_active_millis` bigint NOT NULL DEFAULT 0', 'DO 0'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
