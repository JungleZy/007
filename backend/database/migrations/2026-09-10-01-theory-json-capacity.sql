-- 2026-09-10-01 理论考试JSON载荷容量对齐
-- 执行于既有schema-sync、engine-innodb及unique-lazy-create迁移之后，部署新实体映射之前。
-- current/base快照本已使用longtext；旧ORM默认建出的varchar(255)无法保存正常试卷快照。
-- 仅扩容六个既有JSON载荷列，不改接口字段、不截断/重算内容；重复执行结果相同。
-- DDL隐式提交，生产执行前按既定停写/备份流程操作，不将DDL视作可事务回滚。

ALTER TABLE `t_theory_knowledge_exam_test_paper`
  MODIFY COLUMN `single_choice_list` LONGTEXT NULL,
  MODIFY COLUMN `multiple_choice_list` LONGTEXT NULL,
  MODIFY COLUMN `judge_list` LONGTEXT NULL,
  MODIFY COLUMN `completion_list` LONGTEXT NULL,
  MODIFY COLUMN `short_answer` LONGTEXT NULL;

ALTER TABLE `t_theory_knowledge_exam_user`
  MODIFY COLUMN `content` LONGTEXT NULL;
