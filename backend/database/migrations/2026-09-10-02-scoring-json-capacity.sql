-- 2026-09-10-02 评分规则、拍发页与结算JSON容量
-- 在前四个迁移之后执行，并在部署对应实体映射前完成；DDL隐式提交，先停写和备份。
-- 多数列本在current/base快照中为text/longtext，仅ORM缺省误建varchar(255)。
-- general_ticker_pat_train_user.deduct_info原快照也只有varchar(255)，真实完整结算已复现超长失败，必须扩容。
-- 不截断/重算数据，不改字段名或JSON接口结构；重复执行后的列定义相同。

ALTER TABLE `t_grading_rule`
  MODIFY COLUMN `content` LONGTEXT NULL COMMENT '规则内容json';

ALTER TABLE `general_ticker_pat`
  MODIFY COLUMN `rule_content` LONGTEXT NULL;

ALTER TABLE `general_ticker_pat_train_user`
  MODIFY COLUMN `deduct_info` LONGTEXT NULL,
  MODIFY COLUMN `statistic_info` LONGTEXT NULL;

ALTER TABLE `general_ticker_pat_train_user_value`
  MODIFY COLUMN `finish_info` LONGTEXT NULL,
  MODIFY COLUMN `message_body` LONGTEXT NULL,
  MODIFY COLUMN `resolver` LONGTEXT NULL,
  MODIFY COLUMN `standard` LONGTEXT NULL;

ALTER TABLE `t_post_telex_pat_train`
  MODIFY COLUMN `content` TEXT NULL,
  MODIFY COLUMN `rule_content` LONGTEXT NULL COMMENT '评分规则id',
  MODIFY COLUMN `deduct_info` LONGTEXT NULL;

ALTER TABLE `t_post_telex_pat_train_page_value`
  MODIFY COLUMN `pat_value` LONGTEXT NULL COMMENT '页内容';
