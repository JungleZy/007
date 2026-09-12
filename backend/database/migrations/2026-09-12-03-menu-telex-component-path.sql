-- 2026-09-12-03 电传拍发菜单 component 路径迁移（数据迁移，非 schema 迁移）
--
-- 来源：从 2026-09-11-03-post-telex-capture-clock.sql 尾部（原 :100-111）原样拆出。
-- 原脚本主题是「电传拍发原始采集与持久化倒计时」的 DDL，本段既不改表结构、
-- 也与倒计时无关，而是改 t_menus 的业务数据行；混在 DDL 脚本里会把
-- 「schema 升级」与「前端路由契约对齐」绑成一次不可分割的部署。故拆分独立执行。
--
-- 前端契约依赖（必须与前端同版本落地）：
--   component 值是前端动态路由的组件路径，仅在包含以下组件的前端版本上有效：
--     bw-frontend/frontend/src/views/manage/postJob/datagram/telex/Index.vue
--     bw-frontend/frontend/src/views/manage/postJob/datagram/telex/TrainScore.vue
--     bw-frontend/frontend/src/views/manage/postJob/datagram/telexTrain/Index.vue
--   旧目录 postJob/telegram/telex 与 postJob/telegram/telexTrain 下已无对应组件。
--   先于该前端版本执行本脚本，会使这三个菜单项在旧前端上解析不到组件。
--
-- 幂等与重跑安全：
--   三条 UPDATE 只按旧路径匹配，改完后再跑匹配 0 行，不报错、不覆盖任何已迁移值。
--   本仓库无迁移账本表（无 flyway/liquibase，%prod 仅 validate），运维按脚本文件名
--   记录执行历史；已执行过 2026-09-11-03 原脚本的环境会把本脚本看成「未执行的新脚本」
--   而再跑一次 —— 由上述幂等性保证，重跑无害，可安全执行。
--
-- 仅迁移这三个旧组件路径，保留菜单的其他属性。
UPDATE `t_menus`
SET `component` = '/manage/postJob/datagram/telex/Index'
WHERE `component` = '/manage/postJob/telegram/telex/Index';

UPDATE `t_menus`
SET `component` = '/manage/postJob/datagram/telex/TrainScore'
WHERE `component` = '/manage/postJob/telegram/telex/TrainScore';

UPDATE `t_menus`
SET `component` = '/manage/postJob/datagram/telexTrain/Index'
WHERE `component` = '/manage/postJob/telegram/telexTrain/Index';
