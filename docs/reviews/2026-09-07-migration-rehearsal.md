# 2026-09-07 双快照迁移演练（回灌后）验收报告

**结论：迁移 01→02 在回灌后的 `project006.sql`（current）与旧基线 `project006-base.sql`（base）两个快照上均演练通过（`exit=0`，两侧差分 0 字节，105 表 / 0 MyISAM）；迁移 01 已改造为真幂等，对「已迁移」的库重复执行无副作用；迁移 02（22 张表 `ALTER … ENGINE=InnoDB`）在含 34626 条数据行的演练库上实测 2.3–2.5 秒，可作为停服窗口的实测下界（非上界）。** 本轮演练同时捕获并修复了一处真实缺陷：MySQL 8.0 不支持 `ADD COLUMN IF NOT EXISTS`，原迁移 01 在已迁移库上直接 `ERROR 1060` 中断。所有版本化产物为纯 schema，不含业务数据行、容器凭据或令牌。

## 元信息

| 项 | 值 |
|---|---|
| 演练日期 | 2026-09-07（快照回灌之后，故证据目录后缀 `-postbackfill`） |
| 脚本 | `scripts/rehearse-migrations.sh`（224 行），调用方式 `REHEARSAL_OUT_NAME=2026-09-07-postbackfill bash scripts/rehearse-migrations.sh` |
| 引擎镜像 | `mysql:8.0`（`scripts/rehearse-migrations.sh:42` 硬编码），字符集 `utf8mb4` / `utf8mb4_0900_ai_ci`（`:126`） |
| current 快照 | `backend/database/project006.sql`（回灌后），36551 行 / 105 `CREATE TABLE` / 34626 `INSERT`。校验和随尾注注释变动：演练后、尾注交叉引用补写前实测 `sha256:2a93ff80…0532c5`，本报告定稿时 `sha256:444ebd08755d16ad1378c75d8081290c8903afb5f729f33a1e8e4fcc8bfce5eb`；两者差异仅在尾注注释区块（`:36451-36463`，补入证据目录与权威结论的交叉引用），DDL 与 34626 条数据行逐行一致，不影响演练结论 |
| base 快照 | `backend/database/project006-base.sql`，`sha256:725632dcb9e7b100cce09ff55d6135385387659bb831a2ca77c77dd713f11c0a`，31960 行 / 100 `CREATE TABLE` / 30139 `INSERT` |
| 迁移 01 | `backend/database/migrations/2026-08-26-01-schema-sync.sql`（128 行） |
| 迁移 02 | `backend/database/migrations/2026-08-26-02-engine-innodb.sql`（37 行，其中 `:16-37` 为 22 条 `ALTER TABLE … ENGINE = InnoDB`） |
| 实体权威 schema | `backend/database/rehearsal/2026-09-07-postbackfill/entity-schema.tsv`，`sha256:520f39f86a268280aa01dc928dc2f7757030886b367e6b642a1883e83137075f`，103 张实体表 / 875 列行 |
| 断言口径 | 表计数、MyISAM=0、5 张命名表存在、两处 `is_start_sign` 默认=1、`general_key_pat_page.id`=varchar、实体列 ⊆ 快照列（validate 等价差分为空）；详见「断言矩阵」 |
| 证据目录 | `backend/database/rehearsal/2026-09-07-postbackfill/`（8 个文件 + README） |
| 边界 | 一次性 Docker 容器 + 卷，全新唯一命名，`trap` 全出口清理；脚本不接受位置参数、拒绝 `DB_HOST`/`JDBC_URL`/`QUARKUS_DATASOURCE_JDBC_URL`、绝不读 `application.yml` 数据源（`:11-17,28-36`） |

演练总墙钟 550.61s（含两次容器冷启动与 6 万余行快照导入，非迁移耗时）。

## 1. 停服窗口评估（fix-plan Task 2.1 Step 4 验收项）

**实测（毫秒，两快照分别计时，来源 `timings.tsv`）：**

| 快照 | 迁移 01（schema-sync） | 迁移 02（engine-innodb） |
|---|---|---|
| current（已迁移状态再跑一遍） | 165 | **2542** |
| base（旧基线，真做全部变更） | 662 | **2319** |

**评估依据：**

- 迁移 02 的全部工作量就是 `backend/database/migrations/2026-08-26-02-engine-innodb.sql:16-37`
  的 **22 条 `ALTER TABLE … ENGINE = InnoDB`**（逐行数得 22 条，与脚本头注 `:11-12` 所述
  “22 张表”一致）。每条都会**重建整表并持表锁**（脚本头注 `:6-7` 已写明必须停服窗口执行）。
- 演练库不是空库：current 快照含 **34626 条 INSERT 数据行**，base 含 30139 条；22 张目标表
  在其中均为有数据的业务表。所以 2.3–2.5 秒是**带数据重建**的耗时，不是空表 DDL 的假象。
- 迁移 01 在 base 上 662ms（真建 5 表 + 加 2 列 + 2 次主键类型重建），在 current 上 165ms
  （5 表已存在、2 处 `ADD COLUMN` 走 `DO 0` 空操作，只剩两条同型 `MODIFY COLUMN`）。

**判断：** 两条迁移在演练数据量下合计 **< 3.5 秒**。生产窗口**只能给下界，不能给上界**——
`ALTER … ENGINE` 的耗时与表实际行数/数据文件大小近似线性，而生产库的行数未知且必然大于
演练快照（快照是导出样本，非生产全量）。因此本报告给出的结论是：

- **下界（实测）**：22 张表重建在 3.5 万行量级的库上耗时 2.3–2.5 秒，迁移全程 < 4 秒。
- **生产窗口取值方法**：上线前对生产库执行
  `select table_name, table_rows, data_length from information_schema.tables where table_schema='project006' and engine='MyISAM';`
  取得实际行数与数据长度，按与演练库的倍率线性外推，并在外推值上留足冗余（建议 ≥10×，
  覆盖磁盘 IO 差异与并发排队）。**未在生产数据量上演练前，不得把 3.5 秒当作窗口预算。**

## 2. 活库执行结果（docker `mysql-project006`，db `project006`）

迁移前对活库做了全库备份 `/tmp/project006-backup-20260907.sql`（**8271749 字节**，本报告
撰写时复核仍在），其内容实测确为**迁移前**状态：100 `CREATE TABLE` / 22 张 MyISAM /
5 张目标表（`general_telex_pat`、`general_telex_pat_page`、`general_telex_pat_user`、
`general_telex_pat_user_value`、`t_masthead`）全缺 / `is_start_sign` 0 处。

迁移 01→02 执行后，活库实测：

```sql
select count(*) tables, sum(engine='MyISAM') myisam
  from information_schema.tables where table_schema='project006';
-- → 105    0        （迁移前：100 表 / 22 MyISAM）
```

随后以默认 `%prod`（`quarkus.hibernate-orm.database.generation=validate`）启动 prod jar：

```
quarkus-template 1.0.0 on JVM (powered by Quarkus 3.20.4) started in 2.369s
Listening on: http://0.0.0.0:18002
Profile prod activated.
```

同一命令在修复前失败于
`SchemaManagementException: Schema-validation: missing table [general_telex_pat]`。
即：**validate 契约由活库实证满足，不是纸面推断。**

## 3. 快照回灌（`backend/database/project006.sql`）

回灌把迁移 01+02 的结果就地写进版本化快照，使仓库快照与活库、与实体三者一致
（消除评审 PS-P2-18 的自相矛盾）。改动口径记录在文件尾注 `:36451-36463`：

- 22 处 `ENGINE = MyISAM` → `ENGINE = InnoDB`（**DDL 侧 MyISAM 归零**；全文仅余 2 处
  `MyISAM` 字样，均为尾注里的说明性提及 `:36453`、`:36456`）；
- 追加 5 张缺表 DDL（`:36470-36544`）；
- `simulation_router_room`（`:295`）与 `t_post_ticker_tape_train`（`:28581`）的建表块内
  写入 `is_start_sign int(0) NULL DEFAULT 1`；
- **34626 条 INSERT 数据行一行未动**（实测计数与回灌前一致）。

回灌后快照仍是可导入的完整库：演练 current 分支即以它为输入，导入后跑完两条迁移仍是
105 表 / 0 MyISAM / 差分为空。

## 4. 迁移 01 幂等化（本波新增，非计划内）

**症状：** 回灌后首次演练即中断——
`ERROR 1060 (42S21) Duplicate column name 'is_start_sign'`。

**根因：** MySQL 8.0 **不支持** `ALTER TABLE … ADD COLUMN IF NOT EXISTS`（那是 MariaDB
方言）。原脚本靠注释「重复 ADD COLUMN 会报 1060（可忽略）」把非幂等当成可容忍——但脚本
经 `mysql` 客户端执行时 1060 是致命错误，会**中断后续语句**，迁移 02 根本不会被执行。

**改法（`backend/database/migrations/2026-08-26-01-schema-sync.sql:91-115`）：** 改为
`information_schema.columns` 判存 + 动态 SQL：

```sql
SET @add_router_sign = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'simulation_router_room'
        AND column_name = 'is_start_sign') = 0,
    'ALTER TABLE `simulation_router_room` ADD COLUMN `is_start_sign` int NULL DEFAULT 1',
    'DO 0'));
PREPARE add_router_sign FROM @add_router_sign;
EXECUTE add_router_sign;
DEALLOCATE PREPARE add_router_sign;
```

`t_post_ticker_tape_train` 同型（`:107-115`）。脚本头部 `:17-18` 的幂等声明同步改为
「可安全重复执行」。缺表侧本就是 `CREATE TABLE IF NOT EXISTS`，第 3 节两条
`MODIFY COLUMN`（`:127-128`）为同型重建、本就幂等，未改。

**为什么这是必须的而非权宜：** 三条独立理由。
(1) 回滚预案（见第 7 节）的形态就是「导入备份 → 重跑 01 → 重跑 02」，若 01 不幂等，
回滚路径本身在任何「部分已迁移」的库上都会炸；
(2) 演练 current 分支自此永久运行在已迁移快照上，幂等成为**每次演练都被验证**的性质，
而不是一句注释；
(3) 生产执行若在 01 中途失败重试（网络断开、客户端超时），非幂等脚本无法安全重跑。

## 5. 启动自检负样本（`LifecycleApplication.checkStorageEngine`）

`src/main/java/com/nip/common/LifecycleApplication.java:65-87` 在 `StartupEvent` 时查询
`information_schema` 列出 MyISAM 表；生产（`LaunchMode.NORMAL`）抛
`IllegalStateException` 阻断启动，dev/test 仅告警（`:82-86`）。

负样本实测（在活库临时建 `create table zz_engine_probe(id int primary key) engine=MyISAM`
后启动 prod jar）：

```
ERROR [io.qua.run.Application] Failed to start application:
java.lang.IllegalStateException: 检测到 1 张 MyISAM 表，拒绝启动 … zz_engine_probe
```

`drop table` 后启动恢复正常。**自检是活的，不是纸面功能。**

## 6. 断言矩阵

下表逐条取自 `scripts/rehearse-migrations.sh`（行号为断言所在行），
current/base 两列为本次演练结果。

| # | 断言 | 脚本位置 | current | base |
|---|---|---|---|---|
| 1 | 容器就绪（`mysqladmin ping` ≤120s + 可查询 ≤30s） | `:128-141` | 绿（未触发 `container did not become ready`） | 绿 |
| 2 | 迁前 `general_key_pat_page.id data_type=int`（仅 base） | `:147-151` | 不适用 | 绿 |
| 3 | 迁后 `BASE TABLE` 计数 = 105 | `:163-164` | **PASS (105)** | **PASS (105)** |
| 4 | 迁后 MyISAM 表计数 = 0 | `:165-166` | **PASS (0)** | **PASS (0)** |
| 5 | 表存在：`general_telex_pat` | `:167-171` | 绿 | 绿 |
| 6 | 表存在：`general_telex_pat_page` | `:167-171` | 绿 | 绿 |
| 7 | 表存在：`general_telex_pat_user` | `:167-171` | 绿 | 绿 |
| 8 | 表存在：`general_telex_pat_user_value` | `:167-171` | 绿 | 绿 |
| 9 | 表存在：`t_masthead` | `:167-171` | 绿 | 绿 |
| 10 | `simulation_router_room.is_start_sign` 默认值 = 1 | `:172-173` | 绿 | 绿 |
| 11 | `t_post_ticker_tape_train.is_start_sign` 默认值 = 1 | `:174-175` | 绿 | 绿 |
| 12 | 迁后 `general_key_pat_page.id data_type=varchar` | `:176-178` | 绿 | 绿 |
| 13 | 实体表 schema 差分为空（validate 等价） | `:206-212` | **PASS**，`diff-current.txt` 0 字节 | **PASS**，`diff-base.txt` 0 字节 |

取证口径说明：#3、#4、#13 的 `PASS` 行有逐条输出摘录；#1、#2、#5–#12 标「绿」的依据是
脚本的失败计数器 `FAILURES`（`:101-106`）为 0、终判行输出
`REHEARSAL PASSED: all assertions green for both snapshots`（`:224`）且 `exit=0`——
任一条失败都会走 `:220-223` 的非零退出，不会打印该行。

**产物侧交叉证据：**

```text
diff-current.txt   0 bytes  sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
diff-base.txt      0 bytes  sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
current-engine.tsv          sha256:2e15eab349af33e1a0a1cc20a84a5d2ecb991f043bb5260b022be120b73beca1
base-engine.tsv             sha256:2e15eab349af33e1a0a1cc20a84a5d2ecb991f043bb5260b022be120b73beca1
current-schema.tsv          sha256:d405061628ec280ee576149e1e124c7d11c65ee1f902157ecf6a4ea9f5394a22
base-schema.tsv             sha256:a61581d0e4efb2d2020aa14d497965c6756e2227b16bd2b8e9d468bf024c8cf8
```

`e3b0c442…` 是空输入的规范 SHA-256，即空差分的确定性证据。两份 engine TSV 校验和相同——
两条迁移路径收敛到同一引擎分布（105 张表全 InnoDB）。两份 schema TSV 校验和不同，逐行比对
仅 5 处差异：`t_post_telegraph_key_pat_train_more` 的 4 处 `ordinal_position`
与 `t_post_telegraph_key_pat_train_page_value.value` 的 `text` vs `varchar`——列顺序与
字符串族均在 Hibernate validate 契约之外，故不影响差分为空。

## 7. 回滚预案

预案成立的两个事实：(1) 迁移前全库备份 `/tmp/project006-backup-20260907.sql`（8271749 字节，
100 表 / 22 MyISAM 的迁移前状态）存在；(2) 迁移 01 与 02 现均幂等（01 见第 4 节，02 对已是
InnoDB 的表仅触发一次无害重建，脚本头注 `:13`）。因此**回滚到迁移前**与**从任意中间态重新
前滚**都是安全动作。

**⚠ 前置提醒：** `/tmp` 会被系统清理，该备份**不可当作长期回滚资产**。上线前必须把它复制
到受控留存路径（例如运维备份卷）。另需知悉：仓库内 `backend/database/project006.sql` 现已是
**迁移后**的等价快照（第 3 节），它可以用来重建「迁移完成态」，但**不能**用来回滚。

**回滚（回到迁移前状态）：**

```bash
# 0) 停应用，确认无写入连接
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql-project006 \
  mysql -uroot -N -B -e "select count(*) from information_schema.processlist where db='project006'"

# 1) 重建空库并导入迁移前备份
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql-project006 mysql -uroot \
  -e "drop database if exists project006; create database project006 default character set utf8mb4 collate utf8mb4_0900_ai_ci"
docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql-project006 \
  mysql -uroot --default-character-set=utf8mb4 project006 < /tmp/project006-backup-20260907.sql
```

**前滚（从备份或任意中间态重新迁移，两条脚本可重复执行）：**

```bash
docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql-project006 \
  mysql -uroot --default-character-set=utf8mb4 project006 \
  < backend/database/migrations/2026-08-26-01-schema-sync.sql
docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql-project006 \
  mysql -uroot --default-character-set=utf8mb4 project006 \
  < backend/database/migrations/2026-08-26-02-engine-innodb.sql
```

**校验 SQL（前滚后应全部满足；回滚后应得到括号内的迁移前值）：**

```sql
-- 表计数 / MyISAM 计数：期望 105 / 0   （迁移前：100 / 22）
select count(*) tables, sum(engine='MyISAM') myisam
  from information_schema.tables
 where table_schema='project006' and table_type='BASE TABLE';

-- 5 张目标表存在：期望 5              （迁移前：0）
select count(*) from information_schema.tables
 where table_schema='project006'
   and table_name in ('general_telex_pat','general_telex_pat_page',
                      'general_telex_pat_user','general_telex_pat_user_value','t_masthead');

-- 两处 is_start_sign 默认值：期望 2 行，column_default 均为 1   （迁移前：0 行）
select table_name, column_default from information_schema.columns
 where table_schema='project006' and column_name='is_start_sign';

-- 主键类型对齐：期望两行均为 varchar
select table_name, data_type from information_schema.columns
 where table_schema='project006' and column_name='id'
   and table_name in ('general_key_pat_page','general_ticker_pat_train_page');
```

前滚完成后，以 `%prod`（`generation: validate`）启动应用即为端到端验收：启动成功且无
`SchemaManagementException`，同时 `LifecycleApplication` 的 MyISAM 自检不阻断（第 5 节）。

## 8. 未验证边界

照实列出，本次演练**没有**覆盖的部分：

1. **生产数据量未演练。** 耗时数字来自 34626 行（current）/ 30139 行（base）的演练库；
   生产库行数未知。第 1 节的窗口结论只是下界。
2. **`ALTER` 期间的并发写未测。** 演练库无并发连接。生产上 `ALTER … ENGINE` 持表锁，
   并发写会排队或超时，实际窗口体感将长于纯 DDL 耗时。
3. **主从/复制场景未测。** 演练是单实例容器；主从延迟、`ALTER` 在从库的重放耗时、
   基于 binlog 的复制中断风险均未评估。
4. **回滚路径未实跑。** 第 7 节的命令基于「备份文件实测为迁移前状态」与「两条迁移幂等
   （演练 current 分支实证了 01+02 对已迁移库可重复执行）」推导，但**未**在容器上把
   「导入备份 → 前滚」整条链路跑过一遍。
5. **备份的可恢复性未验证。** 只核对了 `/tmp/project006-backup-20260907.sql` 的字节数与
   schema 特征计数，未做一次完整的导入还原试验。
6. **镜像 digest 未记录。** 本次仅固定 tag `mysql:8.0`，未留存 digest；严格可复现性弱于
   上一轮（上一轮报告记录了 digest）。
7. **数据正确性未断言。** 全部断言均为 schema 层（表/列/类型/引擎/默认值）；
   `ALTER ENGINE` 前后的行数与内容一致性不在演练范围内。

演练产物与复现命令见 `backend/database/rehearsal/2026-09-07-postbackfill/README.md`。
