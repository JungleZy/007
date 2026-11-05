# 个人岗位数据报／电传进入与结束耗时复核

本机实测瓶颈是报底表缺少按训练和页码查询的索引，前端恢复页面时还重复读取了一次当前页。已增加非唯一索引，并复用同一轮恢复过程取到的当前页报底。

## 现场证据

- 数据库 `project006` 的 `t_post_telex_pat_train_page` 有 112342 行，`t_post_telex_pat_train_page_value` 有 640 行；两表原来均只有 `id` 主键索引。
- 原查询 `WHERE train_id=? AND page_number=? ORDER BY sort` 的执行计划为 `ALL`，估计扫描 112342 行，并使用 filesort。直接 JDBC 取 100 组报底约 1778～1840ms，`MAX(page_number)` 约 1749～1867ms。
- 浏览器请求本机 `localhost:18001`：`detail` 三次为 3327/3682/3129ms，`getPage` 为 1710/1799/1478ms；主要耗时在等待服务端响应。
- 原前端单页初始化顺序为 `detail → getPage(1) → getPage(1) → detail`，重复读取当前页。

## 改动

- `backend/database/migrations/2026-11-03-01-post-telex-page-indexes.sql`：报底增加 `(train_id,page_number,sort)`，原始采集表增加 `(train_id,page_number)`；不增加唯一性约束，提供重复执行守卫和索引回退命令。
- `backend/scripts/rehearse-migrations.sh`：将新索引迁移纳入 schema 演练清单。
- `bw-frontend/frontend/src/views/manage/postJob/datagram/js/usePageSubmission.js`：同一轮恢复中的各页响应保存在本地 Map，选择当前页后直接复用报底。末尾仍重取服务器时钟，排除加载用时，并检查训练轮次。
- `bw-frontend/frontend/test/postPageSubmission.test.mjs`：覆盖单页请求次数、多页恢复、加载中轮次变化、上传失败重试和结算失败重试。

## 验证结果

在同一台机器、同一服务和相同 100 组配置下，使用真实 `usePageSubmission` 与真实 HTTP 接口创建临时训练，顺序执行进入、开始、填写报文、结束。临时训练验证后删除；没有改写用户已有训练。以下测量不包含前端资源首次下载和成绩页路由渲染时间。

| 流程 | 优化前 | 优化后 |
|---|---:|---:|
| 电传进入数据加载 | 8600ms | 53ms |
| 数据报进入数据加载 | 8397ms | 57ms |
| 电传结束上传及结算 | 7101ms | 63ms |
| 数据报结束上传及结算 | 7001ms | 51ms |

- 新报底查询计划为 `ref`，命中 `idx_post_telex_page_train_page_sort`，无 filesort；三次取页耗时 2/1/1ms，`MAX(page_number)` 均低于 1ms（整数毫秒输出为 0）。
- 本机应用迁移前，锁定相关表并检查无新协议进行中／暂停中的训练；备份两表的建表语句和全部行。迁移后按主键逐行、逐字段核对，112342 条报底和 640 条采集记录值完全一致。
- 同一 SQL 连续执行两次，第一次 5924ms，第二次 2ms，重复执行成功。
- 本机备份在 `C:\Users\11726\AppData\Local\Temp\opencode\t_post_telex_pat_train_page-before-index-20261103.sql` 与 `t_post_telex_pat_train_page_value-before-index-20261103.sql`；业务正文备份未入库。
- `node --test test/postPageSubmission.test.mjs`：5 项通过。
- `npm test`（在 `bw-frontend/frontend/`）：65 项通过；Linux 串口授权 suite 在 Windows 不适用。

## 验证边界

本次没有修改 Java 业务逻辑、接口字段或采集校验。环境没有 Docker，未运行 Maven 全套测试或 Docker 双快照演练；本次迁移的本机重复执行、数据保真和真实业务流程已验证。

本机这两张表实际仍为 MyISAM，与仓库当前快照的 InnoDB 状态不同。本次只增加查询索引，没有执行全库引擎转换；本次耗时和数据一致性证据不代表 MyISAM 具备事务回滚能力。后续完整部署仍须按既有迁移顺序落实引擎迁移。
