-- 2026-09-12-02 电传/数据报倒计时到期扫描的支撑索引
-- 部署前无需停写：只加二级索引，不改列、不改数据；MySQL DDL 隐式提交，不得 mysql --force。
-- 按 information_schema.STATISTICS 判存，可重复执行（重复执行 0 变更）。
--
-- 前置依赖（硬）：本脚本依赖 2026-09-11-03-post-telex-capture-clock.sql 已加出
-- `deadline` 列。两份快照（project006.sql / project006-base.sql）都还没有该列，
-- 因此迁移清单与 scripts/rehearse-migrations.sh 的 MIGRATIONS 数组必须保证
-- 2026-09-11-03 在本脚本之前执行（字典序天然满足）。若顺序被破坏，CREATE INDEX
-- 会以 ERROR 1072 "Key column 'deadline' doesn't exist in table" 显式失败，
-- 不会静默跳过。
--
-- 索引列选择：PostTelexPatTrainDao.findDueIds 的固定形态是
--   where protocol_version = 1 and status = 1 and paused_at is null
--     and deadline <= ? order by deadline, id
-- 由 PostTelexPatTrainRecovery 每 5 秒 + 启动时各扫一次。
--   * `status` 等值在前：status=1（进行中）把已完成/暂停/未开始的存量行全部排除。
--   * `deadline` 范围在后：等值列在范围列之前，范围可下压为索引区间扫描；
--     且 InnoDB 二级索引尾部隐含主键 id，物理序即 (status, deadline, id)，
--     status 固定后 `order by deadline, id` 由索引直接满足，不再 filesort。
--   * 不收 `paused_at`：暂停会同时置 status=2 与 paused_at（见 PostTelexPatTrainService
--     暂停/恢复），故 status=1 的行 paused_at 恒为 NULL，该谓词选择率为 0，
--     入索引只增宽条目不减扫描量。
--   * 不收 `protocol_version`：存量行 protocol_version=0 且 deadline 为 NULL，
--     而 `deadline <= ?` 对 NULL 恒不成立，已把老协议行挡在区间外，
--     再加一列低基数前缀无额外收益。
--
-- 还原（runbook）：DROP INDEX `idx_post_telex_due` ON `t_post_telex_pat_train`;
--   回滚只影响扫描性能，无数据与契约影响。
SET NAMES utf8mb4;

SET @post_telex_due_ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 't_post_telex_pat_train'
        AND index_name = 'idx_post_telex_due'),
    'DO 0',
    'CREATE INDEX `idx_post_telex_due` ON `t_post_telex_pat_train` (`status`,`deadline`)'));
PREPARE post_telex_due_statement FROM @post_telex_due_ddl;
EXECUTE post_telex_due_statement;
DEALLOCATE PREPARE post_telex_due_statement;
