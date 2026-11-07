-- 岗位电子键拍发：按训练/页码取报底、拍发值与多组多行，避免翻页与结算全表扫描。
-- 只加二级索引，不改列、不改数据、不改契约；MySQL DDL 隐式提交，不得 mysql --force。
-- 按 information_schema.statistics 判存，可重复执行（重复执行 0 变更）。
-- 回退只删除索引：
-- DROP INDEX idx_post_key_page_train_page_sort ON t_post_telegraph_key_pat_train_page;
-- DROP INDEX idx_post_key_value_train_page_sort ON t_post_telegraph_key_pat_train_page_value;
-- DROP INDEX idx_post_key_more_train_page ON t_post_telegraph_key_pat_train_more;
-- DROP INDEX idx_post_key_train_create_user_time ON t_post_telegraph_key_pat_train;
SET NAMES utf8mb4;

SET @post_key_page_index_ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 't_post_telegraph_key_pat_train_page'
        AND index_name = 'idx_post_key_page_train_page_sort'),
    'DO 0',
    'CREATE INDEX `idx_post_key_page_train_page_sort` ON `t_post_telegraph_key_pat_train_page` (`train_id`,`page_number`,`sort`)'));
PREPARE post_key_page_index_statement FROM @post_key_page_index_ddl;
EXECUTE post_key_page_index_statement;
DEALLOCATE PREPARE post_key_page_index_statement;

SET @post_key_value_index_ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 't_post_telegraph_key_pat_train_page_value'
        AND index_name = 'idx_post_key_value_train_page_sort'),
    'DO 0',
    'CREATE INDEX `idx_post_key_value_train_page_sort` ON `t_post_telegraph_key_pat_train_page_value` (`train_id`,`page_number`,`sort`)'));
PREPARE post_key_value_index_statement FROM @post_key_value_index_ddl;
EXECUTE post_key_value_index_statement;
DEALLOCATE PREPARE post_key_value_index_statement;

SET @post_key_more_index_ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 't_post_telegraph_key_pat_train_more'
        AND index_name = 'idx_post_key_more_train_page'),
    'DO 0',
    'CREATE INDEX `idx_post_key_more_train_page` ON `t_post_telegraph_key_pat_train_more` (`train_id`,`page_number`)'));
PREPARE post_key_more_index_statement FROM @post_key_more_index_ddl;
EXECUTE post_key_more_index_statement;
DEALLOCATE PREPARE post_key_more_index_statement;

SET @post_key_train_index_ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 't_post_telegraph_key_pat_train'
        AND index_name = 'idx_post_key_train_create_user_time'),
    'DO 0',
    'CREATE INDEX `idx_post_key_train_create_user_time` ON `t_post_telegraph_key_pat_train` (`create_user_id`,`create_time`)'));
PREPARE post_key_train_index_statement FROM @post_key_train_index_ddl;
EXECUTE post_key_train_index_statement;
DEALLOCATE PREPARE post_key_train_index_statement;
