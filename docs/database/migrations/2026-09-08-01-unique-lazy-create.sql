-- 2026-09-08-01 读路径懒建的唯一约束
--
-- 为何需要：
--   两条读路径在查不到记录时会在读请求里补建一行（「懒建」）：
--     1) POST /radiotelephone/listPage → RadiotelephoneService.listPage（以及同口径的 finish）
--        懒建 t_radiotelephone_train 的 (user_id, type) 统计行；
--     2) GET  /api/comprehensive/getUserOverallInfo → ComprehensiveService.getUserOverallInfo
--        → countErrorSubject 懒建 t_theory_knowledge_test_fallible 的 user_id 易错题缓存行。
--   两张表此前只有 PRIMARY KEY (id)，业务唯一键没有任何 DB 侧保护，两个并发首调会各插一行：
--   话报统计页重复显示同一 type、后续结算只累加 findByUserIdAndType 的 firstResult() 命中的那行
--   （另一行成孤儿）；易错题缓存同理。代码侧已改成「独立事务里查不到就插，撞唯一键换新事务重读」
--   的幂等写法，该写法**依赖**下面两条唯一索引存在，否则并发双插仍会各自成功。
--
-- 与实体的对应关系（实体侧同名约束，%prod 是 generation:validate，二者必须一致）：
--   src/main/java/com/nip/entity/RadiotelephoneEntity.java
--     @UniqueConstraint(name = "uk_radiotelephone_train_user_type", columnNames = {"user_id", "type"})
--   src/main/java/com/nip/entity/TheoryKnowledgeTestFallibleEntity.java
--     @UniqueConstraint(name = "uk_theory_test_fallible_user", columnNames = {"user_id"})
--
-- 执行顺序约束：
--   必须在 2026-08-26-01-schema-sync.sql 与 2026-08-26-02-engine-innodb.sql 之后执行
--   （两张表要先存在且已是 InnoDB），再部署带本次实体改动的 %prod 包。
--
-- 存量数据：
--   2026-09-08 实测活库 project006：t_radiotelephone_train 0 行 / 0 重复，
--   t_theory_knowledge_test_fallible 0 行 / 0 重复，无需去重即可加约束。
--   若在别的环境上执行前存在重复，ALTER 会失败并报 1062，此时必须先按
--   （user_id, type）/（user_id）去重保留一行再重跑本脚本 —— 禁止改成非唯一索引绕过。
--
-- 幂等：
--   MySQL 8.0 没有 `ADD CONSTRAINT IF NOT EXISTS`，因此用 information_schema.statistics
--   的 index_name 判存 + PREPARE/EXECUTE/DEALLOCATE，索引已存在时执行 `DO 0` 空操作。
--   幂等是硬需求 —— 快照回灌后 project006.sql 已含这两条索引，迁移演练
--   （scripts/rehearse-migrations.sh）会对 current 快照再跑一次本脚本。

SET @add_uk_radiotelephone = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 't_radiotelephone_train'
        AND index_name = 'uk_radiotelephone_train_user_type') = 0,
    'ALTER TABLE `t_radiotelephone_train` ADD CONSTRAINT `uk_radiotelephone_train_user_type` UNIQUE (`user_id`, `type`)',
    'DO 0'));
PREPARE add_uk_radiotelephone FROM @add_uk_radiotelephone;
EXECUTE add_uk_radiotelephone;
DEALLOCATE PREPARE add_uk_radiotelephone;

SET @add_uk_test_fallible = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 't_theory_knowledge_test_fallible'
        AND index_name = 'uk_theory_test_fallible_user') = 0,
    'ALTER TABLE `t_theory_knowledge_test_fallible` ADD CONSTRAINT `uk_theory_test_fallible_user` UNIQUE (`user_id`)',
    'DO 0'));
PREPARE add_uk_test_fallible FROM @add_uk_test_fallible;
EXECUTE add_uk_test_fallible;
DEALLOCATE PREPARE add_uk_test_fallible;
