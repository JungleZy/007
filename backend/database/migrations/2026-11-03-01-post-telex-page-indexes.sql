-- 岗位数据报/电传：按训练、页码读取报底及原始采集记录，避免逐次扫描全表。
-- 本机取证：报底112342行，仅有id主键；取100组报底约1.8秒，EXPLAIN为ALL+filesort。
-- 执行前备份并安排停写窗口（旧库MyISAM建索引会持锁）；不修改正文、成绩或采集区间。
-- 非唯一索引，不对历史重复数据新增约束；可重复执行。

SET @post_telex_page_index_ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 't_post_telex_pat_train_page'
        AND index_name = 'idx_post_telex_page_train_page_sort'),
    'DO 0',
    'CREATE INDEX `idx_post_telex_page_train_page_sort` ON `t_post_telex_pat_train_page` (`train_id`,`page_number`,`sort`)'));
PREPARE post_telex_page_index_ddl FROM @post_telex_page_index_ddl;
EXECUTE post_telex_page_index_ddl;
DEALLOCATE PREPARE post_telex_page_index_ddl;

SET @post_telex_page_index_ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 't_post_telex_pat_train_page_value'
        AND index_name = 'idx_post_telex_value_train_page'),
    'DO 0',
    'CREATE INDEX `idx_post_telex_value_train_page` ON `t_post_telex_pat_train_page_value` (`train_id`,`page_number`)'));
PREPARE post_telex_page_index_ddl FROM @post_telex_page_index_ddl;
EXECUTE post_telex_page_index_ddl;
DEALLOCATE PREPARE post_telex_page_index_ddl;

-- 回退只删除索引：
-- DROP INDEX idx_post_telex_page_train_page_sort ON t_post_telex_pat_train_page;
-- DROP INDEX idx_post_telex_value_train_page ON t_post_telex_pat_train_page_value;
