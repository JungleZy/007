-- 2026-09-15-02 电子综合练习统计：按已完成源记录重算毫秒总时长。
-- 停写并备份后执行；须先完成 2026-09-12-04-comprehensive-key-authority.sql。
-- protocol_version=1 使用服务端毫秒；旧协议（0/NULL）的 duration 原本就是毫秒。
-- 只更新 type=2 汇总，不改历史训练/成绩、不触碰 type=0/1；重复执行结果相同。
-- 回滚须恢复备份中的汇总行，不能反向乘除统计列。
SET NAMES utf8mb4;

UPDATE t_telegraph_key_train_statistical AS statistics
LEFT JOIN (
  SELECT create_user_id,
         SUM(CAST(COALESCE(CASE WHEN protocol_version = 1
             THEN accumulated_active_millis ELSE duration END, 0) AS DECIMAL(65, 3))) AS total_millis,
         COUNT(*) AS total_count,
         ROUND(AVG(CAST(COALESCE(speed, '0') AS DECIMAL(30, 10))), 2) AS avg_speed
  FROM t_telegraph_key_pat_synthetical_train
  WHERE status = 3
  GROUP BY create_user_id
) AS completed ON completed.create_user_id = statistics.user_id
SET statistics.total_time = CAST(COALESCE(completed.total_millis, 0) AS CHAR),
    statistics.total_count = COALESCE(completed.total_count, 0),
    statistics.avg_speed = COALESCE(completed.avg_speed, 0)
WHERE statistics.type = 2;
